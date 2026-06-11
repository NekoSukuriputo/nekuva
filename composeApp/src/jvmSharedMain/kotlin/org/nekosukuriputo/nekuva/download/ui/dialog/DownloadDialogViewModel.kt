package org.nekosukuriputo.nekuva.download.ui.dialog

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.nekosukuriputo.nekuva.core.parser.MangaDataRepository
import org.nekosukuriputo.nekuva.core.parser.MangaRepository
import org.nekosukuriputo.nekuva.core.prefs.AppSettings
import org.nekosukuriputo.nekuva.core.prefs.DownloadFormat
import org.nekosukuriputo.nekuva.download.domain.ChapterSelectOptions
import org.nekosukuriputo.nekuva.download.domain.ChaptersSelectMacro
import org.nekosukuriputo.nekuva.download.domain.DownloadManager
import org.nekosukuriputo.nekuva.download.domain.DownloadTask
import org.nekosukuriputo.nekuva.history.data.HistoryRepository
import org.nekosukuriputo.nekuva.local.data.LocalStorageManager
import org.nekosukuriputo.nekuva.parsers.model.Manga
import org.nekosukuriputo.nekuva.parsers.model.MangaParserSource
import java.io.File

/** A place a download can be written to. [dir] == null means "let the engine pick the default". */
data class DownloadDestination(
    val name: String?,
    val dir: File?,
    val isDefault: Boolean,
)

class DownloadDialogViewModel(
    private val mangaId: Long,
    private val mangaDataRepository: MangaDataRepository,
    private val repositoryFactory: MangaRepository.Factory,
    private val historyRepository: HistoryRepository,
    private val localStorageManager: LocalStorageManager,
    private val settings: AppSettings,
    private val downloadManager: DownloadManager,
) : ViewModel() {

    private val _manga = MutableStateFlow<Manga?>(null)
    val manga: StateFlow<Manga?> = _manga.asStateFlow()

    val isOptionsLoading = MutableStateFlow(true)
    val chaptersSelectOptions = MutableStateFlow(
        ChapterSelectOptions(
            wholeManga = ChaptersSelectMacro.WholeManga(0),
            wholeBranch = null,
            firstChapters = null,
            unreadChapters = null,
        ),
    )
    val destinations = MutableStateFlow(listOf(DownloadDestination(null, null, isDefault = true)))
    val defaultFormat = MutableStateFlow(settings.preferredDownloadFormat)

    val onScheduled = MutableSharedFlow<Boolean>(extraBufferCapacity = 1)
    val onError = MutableSharedFlow<String>(extraBufferCapacity = 1)

    init {
        load()
    }

    private fun load() {
        viewModelScope.launch {
            try {
                var m = mangaDataRepository.findMangaById(mangaId, withChapters = true)
                if (m == null) {
                    onError.emit("Manga not found")
                    return@launch
                }
                val source = MangaParserSource.entries.find { it.name == m!!.source.name }
                if (source != null && m!!.chapters.isNullOrEmpty()) {
                    m = repositoryFactory.create(source).getDetails(m!!)
                }
                _manga.value = m
                loadOptions(m!!)
                loadDestinations()
            } catch (e: Exception) {
                onError.emit(e.message ?: e.javaClass.simpleName)
            } finally {
                isOptionsLoading.value = false
            }
        }
    }

    fun setSelectedBranch(branch: String?) {
        val snapshot = chaptersSelectOptions.value
        chaptersSelectOptions.value = snapshot.copy(
            wholeBranch = snapshot.wholeBranch?.copy(selectedBranch = branch),
        )
    }

    fun setFirstChaptersCount(count: Int) {
        val snapshot = chaptersSelectOptions.value
        chaptersSelectOptions.value = snapshot.copy(
            firstChapters = snapshot.firstChapters?.copy(chaptersCount = count),
        )
    }

    fun setUnreadChaptersCount(count: Int) {
        val snapshot = chaptersSelectOptions.value
        chaptersSelectOptions.value = snapshot.copy(
            unreadChapters = snapshot.unreadChapters?.copy(chaptersCount = count),
        )
    }

    fun confirm(
        startNow: Boolean,
        macro: ChaptersSelectMacro,
        format: DownloadFormat?,
        destination: DownloadDestination?,
    ) {
        val m = _manga.value ?: return
        viewModelScope.launch {
            try {
                val chapters = m.chapters ?: error("Manga \"${m.title}\" has no chapters")
                val ids = macro.getChaptersIds(m.id, chapters)
                downloadManager.schedule(
                    listOf(
                        DownloadTask(
                            manga = m,
                            chaptersIds = ids,
                            destination = destination?.dir,
                            format = format,
                            startPaused = !startNow,
                        ),
                    ),
                )
                onScheduled.emit(startNow)
            } catch (e: Exception) {
                onError.emit(e.message ?: e.javaClass.simpleName)
            }
        }
    }

    private suspend fun loadOptions(m: Manga) {
        val chapters = m.chapters.orEmpty()
        val branches = LinkedHashMap<String?, Int>()
        for (c in chapters) {
            branches[c.branch] = (branches[c.branch] ?: 0) + 1
        }
        val history = historyRepository.getOne(m)
        val currentChapterId = history?.chapterId
        val defaultBranch = chapters.firstOrNull { it.id == currentChapterId }?.branch
            ?: chapters.firstOrNull()?.branch
        val branchChaptersCount = chapters.count { it.branch == defaultBranch }
        val unreadCount = if (currentChapterId != null) {
            chapters.dropWhile { it.id != currentChapterId }.count { it.branch == defaultBranch }
        } else {
            0
        }
        chaptersSelectOptions.value = ChapterSelectOptions(
            wholeManga = ChaptersSelectMacro.WholeManga(chapters.size),
            wholeBranch = if (branches.size > 1) {
                ChaptersSelectMacro.WholeBranch(branches = branches, selectedBranch = defaultBranch)
            } else {
                null
            },
            firstChapters = if (branchChaptersCount > 0) {
                ChaptersSelectMacro.FirstChapters(
                    chaptersCount = minOf(5, branchChaptersCount),
                    maxAvailableCount = branchChaptersCount,
                    branch = defaultBranch,
                )
            } else {
                null
            },
            unreadChapters = if (currentChapterId != null && unreadCount > 0) {
                ChaptersSelectMacro.UnreadChapters(
                    chaptersCount = minOf(5, unreadCount),
                    maxAvailableCount = unreadCount,
                    currentChaptersIds = mapOf(m.id to currentChapterId),
                )
            } else {
                null
            },
        )
    }

    /** Adds a user-picked folder to the destination list + persists it so it's remembered next time. */
    fun addDestination(path: String): Int {
        val dir = File(path)
        // Persist so the folder appears as a saved storage dir (dialog dropdown + Local list).
        settings.userSpecifiedMangaDirectories = settings.userSpecifiedMangaDirectories + dir.absolutePath
        val current = destinations.value
        val existingIndex = current.indexOfFirst { it.dir?.absolutePath == dir.absolutePath }
        if (existingIndex >= 0) return existingIndex
        val updated = current + DownloadDestination(name = dir.name, dir = dir, isDefault = false)
        destinations.value = updated
        return updated.lastIndex
    }

    private suspend fun loadDestinations() {
        val dirs = localStorageManager.getWriteableDirs()
        val defaultDir = localStorageManager.getDefaultWriteableDir()
        destinations.value = buildList(dirs.size + 1) {
            add(DownloadDestination(null, null, isDefault = true))
            for (dir in dirs) {
                add(
                    DownloadDestination(
                        name = localStorageManager.getDirectoryDisplayName(dir, isFullPath = false),
                        dir = dir,
                        isDefault = dir == defaultDir,
                    ),
                )
            }
        }
    }
}
