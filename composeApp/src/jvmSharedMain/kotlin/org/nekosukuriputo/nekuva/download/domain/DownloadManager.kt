package org.nekosukuriputo.nekuva.download.domain

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.nekosukuriputo.nekuva.core.parser.MangaDataRepository
import org.nekosukuriputo.nekuva.core.parser.MangaRepository
import org.nekosukuriputo.nekuva.core.parser.ParserMangaRepository
import org.nekosukuriputo.nekuva.core.prefs.AppSettings
import org.nekosukuriputo.nekuva.core.util.MimeTypes
import org.nekosukuriputo.nekuva.core.util.ext.deleteAwait
import org.nekosukuriputo.nekuva.core.util.ext.printStackTraceDebug
import org.nekosukuriputo.nekuva.core.util.ext.toFileNameSafe
import org.nekosukuriputo.nekuva.local.data.LocalMangaRepository
import org.nekosukuriputo.nekuva.local.data.input.LocalMangaParser
import org.nekosukuriputo.nekuva.local.data.output.LocalMangaOutput
import org.nekosukuriputo.nekuva.core.network.imageproxy.ImageProxyInterceptor
import org.nekosukuriputo.nekuva.local.domain.MangaLock
import org.nekosukuriputo.nekuva.parsers.model.Manga
import org.nekosukuriputo.nekuva.parsers.model.MangaChapter
import org.nekosukuriputo.nekuva.parsers.model.MangaParserSource
import java.io.File
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

/**
 * Coroutine-based download engine — KMP replacement for Doki's WorkManager `DownloadWorker`.
 *
 * Runs entirely in-process (works on Android + Desktop). Downloads run one-at-a-time (queue via a
 * [Semaphore]); pages within a chapter are fetched with limited parallelism. Pause/resume/cancel are
 * driven by per-download job control. Android foreground-service + system notifications, survival across
 * app-kill, and metered-network constraints are deferred (see MIGRATION.md / download-architecture memory).
 */
@OptIn(ExperimentalTime::class)
class DownloadManager(
    private val okHttp: OkHttpClient,
    private val imageProxyInterceptor: ImageProxyInterceptor,
    private val localMangaRepository: LocalMangaRepository,
    private val mangaLock: MangaLock,
    private val mangaDataRepository: MangaDataRepository,
    private val repositoryFactory: MangaRepository.Factory,
    private val settings: AppSettings,
    private val localStorageChanges: MutableSharedFlow<org.nekosukuriputo.nekuva.local.domain.model.LocalManga?>,
    private val networkState: org.nekosukuriputo.nekuva.core.os.NetworkState,
) {

    // Exception handler so an uncaught failure in any download coroutine is logged, never force-closing the
    // app (e.g. a storage error writing to a custom dir on Android). Per-download failures are still handled
    // in runDownload; this is the last-resort safety net for the whole engine scope.
    private val scope = CoroutineScope(
        SupervisorJob() + Dispatchers.Default +
            kotlinx.coroutines.CoroutineExceptionHandler { _, e -> e.printStackTraceDebug() },
    )
    private val queueSemaphore = Semaphore(1)
    private val controllers = ConcurrentHashMap<String, Controller>()
    private val tasks = ConcurrentHashMap<String, DownloadTask>() // kept so a download can be retried
    private val stateMutex = Mutex()

    private val _downloads = MutableStateFlow<List<DownloadState>>(emptyList())
    val downloads: StateFlow<List<DownloadState>> = _downloads.asStateFlow()

    private class Controller(
        val paused: MutableStateFlow<Boolean>,
    ) {
        var job: Job? = null
    }

    /** Enqueue downloads; returns whether anything was started immediately (vs added paused). */
    suspend fun schedule(tasks: Collection<DownloadTask>): Boolean {
        if (tasks.isEmpty()) return false
        var startedAny = false
        for (task in tasks) {
            mangaDataRepository.storeManga(task.manga, replaceExisting = true)
            val id = UUID.randomUUID().toString()
            this.tasks[id] = task
            val controller = Controller(MutableStateFlow(task.startPaused))
            controllers[id] = controller
            putState(
                DownloadState(
                    id = id,
                    manga = task.manga,
                    status = DownloadStatus.QUEUED,
                    isIndeterminate = true,
                    timestamp = Clock.System.now().toEpochMilliseconds(),
                ),
            )
            controller.job = scope.launch {
                runDownload(id, task, controller)
            }
            if (!task.startPaused) startedAny = true
        }
        return startedAny
    }

    fun pause(id: String) {
        controllers[id]?.paused?.value = true
    }

    fun resume(id: String) {
        controllers[id]?.paused?.value = false
    }

    fun cancel(id: String) {
        controllers[id]?.job?.cancel()
    }

    /** Retry all failed chapters of a download (already-downloaded chapters stay checked, like Doki). */
    fun retry(id: String) = relaunch(id, retryOnly = null)

    /** Retry a single chapter while the others keep their status. */
    fun retryChapter(id: String, chapterId: Long) = relaunch(id, retryOnly = setOf(chapterId))

    private fun relaunch(id: String, retryOnly: Set<Long>?) {
        val task = tasks[id] ?: return
        controllers[id]?.job?.cancel()
        val controller = Controller(MutableStateFlow(false))
        controllers[id] = controller
        controller.job = scope.launch { runDownload(id, task, controller, retryOnly) }
    }

    fun pauseAll() {
        controllers.values.forEach { it.paused.value = true }
    }

    fun resumeAll() {
        controllers.values.forEach { it.paused.value = false }
    }

    fun cancelAll() {
        controllers.values.forEach { it.job?.cancel() }
    }

    fun remove(id: String) {
        controllers.remove(id)?.job?.cancel()
        tasks.remove(id)
        _downloads.update { list -> list.filterNot { it.id == id } }
    }

    fun removeCompleted() {
        val toRemove = _downloads.value.filter { it.isFinished }.map { it.id }
        toRemove.forEach { controllers.remove(it); tasks.remove(it) }
        _downloads.update { list -> list.filterNot { it.isFinished } }
    }

    private suspend fun runDownload(id: String, task: DownloadTask, controller: Controller, retryOnly: Set<Long>? = null) {
        try {
            // Wait here if the download was added paused, so it doesn't occupy the single queue slot.
            awaitResume(id, controller)
            // Metered-network constraint (Doki downloads_metered_network): when "Don't allow", hold the
            // download until the network is no longer metered (Wi-Fi/Ethernet). ENABLED/ASK proceed.
            awaitMeteredAllowed(id)
            queueSemaphore.withPermit {
                downloadImpl(id, task, controller, retryOnly)
            }
        } catch (e: CancellationException) {
            withContext(NonCancellable) {
                updateState(id) { it.copy(status = DownloadStatus.CANCELLED, eta = -1L) }
            }
        } catch (e: Throwable) {
            // Throwable (not just Exception): an Error (e.g. OOM / a platform error writing to a custom dir
            // on Android) used to escape → force-close, then "stuck". Surface it as a visible Failed instead.
            e.printStackTraceDebug()
            updateState(id) {
                it.copy(
                    status = DownloadStatus.FAILED,
                    errorMessage = e.message ?: e.javaClass.simpleName,
                    eta = -1L,
                )
            }
        }
    }

    /** Holds the download while "Don't allow on cellular" is set AND the network is metered. */
    private suspend fun awaitMeteredAllowed(id: String) {
        if (settings.allowDownloadOnMeteredNetwork != org.nekosukuriputo.nekuva.core.prefs.TriStateOption.DISABLED) return
        if (!networkState.isMetered()) return
        updateState(id) { it.copy(status = DownloadStatus.QUEUED, isIndeterminate = true) }
        while (networkState.isMetered()) {
            delay(5_000)
        }
    }

    private suspend fun downloadImpl(id: String, task: DownloadTask, controller: Controller, retryOnly: Set<Long>? = null) {
        var manga = task.manga
        updateState(id) { it.copy(status = DownloadStatus.RUNNING, isIndeterminate = true) }
        val source = MangaParserSource.entries.find { it.name == manga.source.name }
            ?: error("Manga \"${manga.title}\" has no parser source")
        val repo = repositoryFactory.create(source)
        if (manga.chapters.isNullOrEmpty() || manga.description.isNullOrEmpty()) {
            manga = repo.getDetails(manga)
        }
        val destination = localMangaRepository.getOutputDir(manga, task.destination)
            ?: error("No writeable storage available")
        mangaLock.withLock(manga) {
            var output: LocalMangaOutput? = null
            try {
                output = LocalMangaOutput.getOrCreate(
                    root = destination,
                    manga = manga,
                    format = task.format ?: settings.preferredDownloadFormat,
                )
                output.mergeWithExisting()
                // Store the real manga cover (like Doki) so the local entry shows the proper cover art,
                // not the first page. Cover failure must not abort the download.
                val coverUrl = manga.largeCoverUrl?.takeIf { it.isNotEmpty() } ?: manga.coverUrl
                if (!coverUrl.isNullOrEmpty()) {
                    runCatching {
                        val (coverFile, coverMime) = downloadFile(repo, coverUrl, destination)
                        output.addCover(coverFile, coverMime)
                        coverFile.deleteAwait()
                    }.onFailure { it.printStackTraceDebug() }
                }
                // Each selected chapter paired with its GLOBAL index in the manga (stable page names).
                val chapters = resolveChapters(manga, task.chaptersIds)
                // Chapters already present in an existing download are skipped (Doki resume behaviour).
                val alreadyDownloaded: Set<Long> = runCatching {
                    LocalMangaParser(output.rootFile).getManga(withDetails = true).manga.chapters
                        ?.mapTo(HashSet()) { it.id }
                }.getOrNull().orEmpty()
                // Decide each chapter's status: already-on-disk = done; chapters being (re)downloaded =
                // pending; others keep their previous status (preserves failures not part of a retry).
                val prev = _downloads.value.find { it.id == id }?.chapters?.associateBy { it.id }.orEmpty()
                val seeded = chapters.map { (gi, c) ->
                    val status = when {
                        c.id in alreadyDownloaded -> ChapterDownloadStatus.DONE
                        retryOnly == null || c.id in retryOnly -> ChapterDownloadStatus.PENDING
                        else -> prev[c.id]?.status ?: ChapterDownloadStatus.PENDING
                    }
                    DownloadChapterState(
                        id = c.id,
                        name = chapterDisplayName(c, gi),
                        status = status,
                        error = if (status == ChapterDownloadStatus.FAILED) prev[c.id]?.error else null,
                    )
                }
                // Only chapters marked PENDING are (re)downloaded in this run.
                val toDownload = seeded.filterTo(HashSet()) { it.status == ChapterDownloadStatus.PENDING }.mapTo(HashSet()) { it.id }
                updateState(id) {
                    it.copy(
                        totalChapters = chapters.size,
                        downloadedChapters = seeded.count { c -> c.status == ChapterDownloadStatus.DONE },
                        errorMessage = null,
                        chapters = seeded,
                    )
                }
                val startTime = Clock.System.now().toEpochMilliseconds()
                var pagesDoneTotal = 0
                var pagesExpectedTotal = 0
                var anyDone = seeded.any { it.status == ChapterDownloadStatus.DONE }
                for ((loopIndex, indexedChapter) in chapters.withIndex()) {
                    val chapter = indexedChapter.value
                    awaitResume(id, controller)
                    if (chapter.id !in toDownload) continue // done already, or not part of this (retry) run
                    setChapterStatus(id, chapter.id, ChapterDownloadStatus.DOWNLOADING)
                    try {
                        val pages = repo.getPages(chapter)
                        if (pagesExpectedTotal == 0) {
                            // Rough total for percentage (assumes uniform page count, like Doki).
                            pagesExpectedTotal = pages.size * chapters.size
                        }
                        val pageCounter = java.util.concurrent.atomic.AtomicInteger(0)
                        channelFlowDownloadPages(repo, pages, destination, controller, id) { pageIndex, file, mime ->
                            output.addPage(indexedChapter, file, pageIndex, mime)
                            file.deleteAwait()
                            val done = pageCounter.incrementAndGet()
                            val elapsed = Clock.System.now().toEpochMilliseconds() - startTime
                            val totalDone = pagesDoneTotal + done
                            val eta = if (totalDone > 0 && pagesExpectedTotal > totalDone) {
                                (elapsed.toDouble() / totalDone * (pagesExpectedTotal - totalDone)).toLong()
                            } else {
                                -1L
                            }
                            updateState(id) {
                                it.copy(
                                    // Don't clobber PAUSED: an in-flight page finishing while paused must not flip back to RUNNING.
                                    status = if (controller.paused.value) DownloadStatus.PAUSED else DownloadStatus.RUNNING,
                                    isIndeterminate = false,
                                    totalChapters = chapters.size,
                                    currentChapter = loopIndex,
                                    totalPages = pages.size,
                                    currentPage = done - 1,
                                    eta = eta,
                                )
                            }
                        }
                        // Finalize this chapter's .cbz (DIR output); SINGLE_CBZ flushes at the end.
                        output.flushChapter(chapter)
                        pagesDoneTotal += pages.size
                        setChapterStatus(id, chapter.id, ChapterDownloadStatus.DONE)
                        anyDone = true
                        updateState(id) { it.copy(downloadedChapters = it.downloadedChapters + 1) }
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        // A failed chapter doesn't abort the whole download — record the error and continue (Doki behaviour).
                        e.printStackTraceDebug()
                        setChapterStatus(id, chapter.id, ChapterDownloadStatus.FAILED, e.message ?: e.javaClass.simpleName)
                    }
                }
                updateState(id) { it.copy(isIndeterminate = true, eta = -1L) }
                if (anyDone) {
                    output.finish()
                    val localManga = LocalMangaParser(output.rootFile).getManga(withDetails = false)
                    localStorageChanges.emit(localManga)
                    updateState(id) {
                        it.copy(status = DownloadStatus.COMPLETED, localManga = localManga, isIndeterminate = false, eta = -1L)
                    }
                } else {
                    output.cleanup()
                    updateState(id) {
                        it.copy(
                            status = DownloadStatus.FAILED,
                            errorMessage = it.chapters.firstNotNullOfOrNull { c -> c.error },
                            isIndeterminate = false,
                            eta = -1L,
                        )
                    }
                }
            } catch (e: Throwable) {
                withContext(NonCancellable) {
                    output?.cleanup()
                    // On cancel/error, no chapter must keep spinning (DOWNLOADING).
                    updateState(id) { st ->
                        st.copy(
                            chapters = st.chapters.map { c ->
                                if (c.status == ChapterDownloadStatus.DOWNLOADING) {
                                    c.copy(status = ChapterDownloadStatus.PENDING)
                                } else {
                                    c
                                }
                            },
                        )
                    }
                }
                throw e
            } finally {
                output?.close()
                // Remove stray page temp files (cover + pages left by cancelled in-flight downloads).
                withContext(NonCancellable) {
                    runCatching {
                        destination.listFiles { f -> f.isFile && f.name.startsWith("page") && f.name.endsWith(".tmp") }
                            ?.forEach { it.delete() }
                    }
                }
            }
        }
    }

    /** Download all pages of a chapter with bounded parallelism, invoking [onPage] as each finishes. */
    private suspend fun channelFlowDownloadPages(
        repo: MangaRepository,
        pages: List<org.nekosukuriputo.nekuva.parsers.model.MangaPage>,
        destination: File,
        controller: Controller,
        id: String,
        onPage: suspend (pageIndex: Int, file: File, mimeType: String) -> Unit,
    ) {
        val flow = channelFlow<Triple<Int, File, String>> {
            val semaphore = Semaphore(MAX_PAGES_PARALLELISM)
            for ((pageIndex, page) in pages.withIndex()) {
                launch {
                    semaphore.withPermit {
                        awaitResume(id, controller)
                        val url = repo.getPageUrl(page)
                        val (file, mime) = downloadFile(repo, url, destination)
                        send(Triple(pageIndex, file, mime))
                    }
                }
            }
        }
        flow.collect { (pageIndex, file, mime) ->
            onPage(pageIndex, file, mime)
        }
    }

    /** Returns the downloaded temp file and its (image) mime type for the output's entry naming. */
    private suspend fun downloadFile(repo: MangaRepository, url: String, destination: File): Pair<File, String> {
        val builder = Request.Builder().url(url)
        (repo as? ParserMangaRepository)?.getRequestHeaders()?.let { builder.headers(it) }
        val response = imageProxyInterceptor.interceptPageRequest(builder.build(), okHttp)
        if (!response.isSuccessful) {
            response.close()
            throw java.io.IOException("HTTP ${response.code} for $url")
        }
        return response.use { resp ->
            val body = resp.body ?: throw java.io.IOException("Empty body for $url")
            val contentType = body.contentType()?.let { "${it.type}/${it.subtype}" }
            val ext = pageExtension(url, contentType)
            val file = File.createTempFile("page", ".$ext.tmp", destination)
            try {
                file.outputStream().use { out ->
                    body.byteStream().use { input -> input.copyTo(out) }
                }
            } catch (e: Exception) {
                file.delete()
                throw e
            }
            val mime = contentType?.takeIf { it.startsWith("image/") }
                ?: MimeTypes.getMimeTypeFromExtension(ext)
                ?: "image/jpeg"
            file to mime
        }
    }

    private fun pageExtension(url: String, mimeType: String?): String {
        val fromUrl = url.substringBefore('?').substringAfterLast('.', "")
        if (fromUrl.length in 2..4) return fromUrl
        return MimeTypes.getExtensionFromMimeType(mimeType) ?: "jpg"
    }

    /** Selected chapters paired with their GLOBAL index in the full manga, so page entry names
     * (`FILENAME_PATTERN` uses chapter index) stay stable when more chapters are downloaded later. */
    private fun resolveChapters(manga: Manga, chaptersIds: Set<Long>?): List<IndexedValue<MangaChapter>> {
        val all = manga.chapters ?: error("Manga \"${manga.title}\" has no chapters")
        val result = if (chaptersIds == null) all.withIndex().toList() else all.withIndex().filter { it.value.id in chaptersIds }
        check(result.isNotEmpty()) { "No chapters selected for \"${manga.title}\"" }
        return result
    }

    private fun chapterDisplayName(chapter: MangaChapter, index: Int): String =
        chapter.title?.takeIf { it.isNotEmpty() } ?: chapter.name?.takeIf { it.isNotEmpty() } ?: "Chapter ${index + 1}"

    private suspend fun setChapterStatus(
        id: String,
        chapterId: Long,
        status: ChapterDownloadStatus,
        error: String? = null,
    ) {
        updateState(id) { st ->
            st.copy(
                chapters = st.chapters.map { c ->
                    if (c.id == chapterId) c.copy(status = status, error = error) else c
                },
            )
        }
    }


    private suspend fun awaitResume(id: String, controller: Controller) {
        if (controller.paused.value) {
            updateState(id) { it.copy(status = DownloadStatus.PAUSED, eta = -1L) }
            controller.paused.first { !it }
            updateState(id) { it.copy(status = DownloadStatus.RUNNING) }
        }
    }

    private suspend fun putState(state: DownloadState) {
        stateMutex.withLock {
            _downloads.update { list -> list.filterNot { it.id == state.id } + state }
        }
    }

    private suspend fun updateState(id: String, transform: (DownloadState) -> DownloadState) {
        stateMutex.withLock {
            _downloads.update { list -> list.map { if (it.id == id) transform(it) else it } }
        }
    }

    private companion object {
        const val MAX_PAGES_PARALLELISM = 4
    }
}
