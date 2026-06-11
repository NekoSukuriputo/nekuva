package org.nekosukuriputo.nekuva.download.domain

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
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
) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val queueSemaphore = Semaphore(1)
    private val controllers = ConcurrentHashMap<String, Controller>()
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
        _downloads.update { list -> list.filterNot { it.id == id } }
    }

    fun removeCompleted() {
        val toRemove = _downloads.value.filter { it.isFinished }.map { it.id }
        toRemove.forEach { controllers.remove(it) }
        _downloads.update { list -> list.filterNot { it.isFinished } }
    }

    private suspend fun runDownload(id: String, task: DownloadTask, controller: Controller) {
        try {
            // Wait here if the download was added paused, so it doesn't occupy the single queue slot.
            awaitResume(id, controller)
            queueSemaphore.withPermit {
                downloadImpl(id, task, controller)
            }
        } catch (e: CancellationException) {
            withContext(NonCancellable) {
                updateState(id) { it.copy(status = DownloadStatus.CANCELLED, eta = -1L) }
            }
        } catch (e: Exception) {
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

    private suspend fun downloadImpl(id: String, task: DownloadTask, controller: Controller) {
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
                val chapters = resolveChapters(manga, task.chaptersIds)
                // Seed the per-chapter list (all pending) so the expandable UI can show progress.
                updateState(id) { st ->
                    st.copy(
                        totalChapters = chapters.size,
                        chapters = chapters.mapIndexed { i, c ->
                            DownloadChapterState(id = c.id, name = chapterDisplayName(c, i), status = ChapterDownloadStatus.PENDING)
                        },
                    )
                }
                val startTime = Clock.System.now().toEpochMilliseconds()
                var pagesDoneTotal = 0
                var pagesExpectedTotal = 0
                var anyDone = false
                for ((chapterIndex, chapter) in chapters.withIndex()) {
                    awaitResume(id, controller)
                    setChapterStatus(id, chapter.id, ChapterDownloadStatus.DOWNLOADING)
                    try {
                        val pages = repo.getPages(chapter)
                        if (pagesExpectedTotal == 0) {
                            // Rough total for percentage (assumes uniform page count, like Doki).
                            pagesExpectedTotal = pages.size * chapters.size
                        }
                        val chapterDir = chapterDirName(chapter, chapterIndex)
                        val pageCounter = java.util.concurrent.atomic.AtomicInteger(0)
                        channelFlowDownloadPages(repo, pages, destination, controller, id) { pageIndex, file ->
                            output.addPage(chapterDir, pageFileName(pageIndex, file), file)
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
                                    currentChapter = chapterIndex,
                                    totalPages = pages.size,
                                    currentPage = done - 1,
                                    eta = eta,
                                )
                            }
                        }
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
                }
                throw e
            } finally {
                output?.close()
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
        onPage: suspend (pageIndex: Int, file: File) -> Unit,
    ) {
        val flow = channelFlow<Pair<Int, File>> {
            val semaphore = Semaphore(MAX_PAGES_PARALLELISM)
            for ((pageIndex, page) in pages.withIndex()) {
                launch {
                    semaphore.withPermit {
                        awaitResume(id, controller)
                        val url = repo.getPageUrl(page)
                        val file = downloadFile(repo, url, destination)
                        send(Pair(pageIndex, file))
                    }
                }
            }
        }
        flow.collect { pair ->
            onPage(pair.first, pair.second)
        }
    }

    private suspend fun downloadFile(repo: MangaRepository, url: String, destination: File): File {
        val builder = Request.Builder().url(url)
        (repo as? ParserMangaRepository)?.getRequestHeaders()?.let { builder.headers(it) }
        val response = imageProxyInterceptor.interceptPageRequest(builder.build(), okHttp)
        if (!response.isSuccessful) {
            response.close()
            throw java.io.IOException("HTTP ${response.code} for $url")
        }
        return response.use { resp ->
            val body = resp.body ?: throw java.io.IOException("Empty body for $url")
            val ext = pageExtension(url, body.contentType()?.let { "${it.type}/${it.subtype}" })
            val file = File.createTempFile("page", ".$ext.tmp", destination)
            try {
                file.outputStream().use { out ->
                    body.byteStream().use { input -> input.copyTo(out) }
                }
            } catch (e: Exception) {
                file.delete()
                throw e
            }
            file
        }
    }

    private fun pageExtension(url: String, mimeType: String?): String {
        val fromUrl = url.substringBefore('?').substringAfterLast('.', "")
        if (fromUrl.length in 2..4) return fromUrl
        return MimeTypes.getExtensionFromMimeType(mimeType) ?: "jpg"
    }

    private fun resolveChapters(manga: Manga, chaptersIds: Set<Long>?): List<MangaChapter> {
        val all = manga.chapters ?: error("Manga \"${manga.title}\" has no chapters")
        val result = if (chaptersIds == null) all else all.filter { it.id in chaptersIds }
        check(result.isNotEmpty()) { "No chapters selected for \"${manga.title}\"" }
        return result
    }

    private fun chapterDirName(chapter: MangaChapter, index: Int): String {
        val safe = chapterDisplayName(chapter, index).toFileNameSafe()
        return "%04d_%s".format(index + 1, safe)
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

    private fun pageFileName(pageIndex: Int, source: File): String {
        val ext = source.name.substringBeforeLast(".tmp").substringAfterLast('.', "jpg")
        return "%04d.%s".format(pageIndex, ext)
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
