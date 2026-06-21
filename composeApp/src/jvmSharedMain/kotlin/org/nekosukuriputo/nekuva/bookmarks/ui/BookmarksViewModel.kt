package org.nekosukuriputo.nekuva.bookmarks.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.nekosukuriputo.nekuva.bookmarks.data.BookmarkEntity
import org.nekosukuriputo.nekuva.bookmarks.domain.Bookmark
import org.nekosukuriputo.nekuva.bookmarks.domain.BookmarksRepository
import org.nekosukuriputo.nekuva.parsers.model.Manga
import org.nekosukuriputo.nekuva.parsers.model.MangaPage
import org.nekosukuriputo.nekuva.reader.domain.PageSaveHelper

class BookmarksViewModel(
    private val repository: BookmarksRepository,
    private val pageSaveHelper: PageSaveHelper,
) : ViewModel() {

    val content: StateFlow<BookmarksUiState> = repository.observeBookmarks()
        .map { grouped ->
            if (grouped.isEmpty()) BookmarksUiState.Empty else BookmarksUiState.Success(grouped.toList())
        }
        .catch { e -> emit(BookmarksUiState.Error(e)) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), BookmarksUiState.Loading)

    private val _selection = MutableStateFlow<Set<Long>>(emptySet())
    val selection: StateFlow<Set<Long>> = _selection.asStateFlow()

    /** Emits the number of bookmarks just removed, so the UI can offer an undo. */
    val onRemoved = MutableSharedFlow<Int>(extraBufferCapacity = 1)

    /** Emits the number of selected pages just saved to storage (Doki bookmarks "save"), for a snackbar. */
    val onPagesSaved = MutableSharedFlow<Int>(extraBufferCapacity = 1)

    private var lastRemoved: List<BookmarkEntity> = emptyList()

    val isSelectionActive: Boolean get() = _selection.value.isNotEmpty()

    fun toggleSelection(pageId: Long) {
        _selection.update { if (pageId in it) it - pageId else it + pageId }
    }

    fun clearSelection() {
        _selection.value = emptySet()
    }

    fun removeSelected() {
        val ids = _selection.value
        if (ids.isEmpty()) return
        viewModelScope.launch {
            lastRemoved = repository.removeBookmarks(ids)
            _selection.value = emptySet()
            onRemoved.tryEmit(lastRemoved.size)
        }
    }

    /** Save the selected bookmarked pages to platform storage (Doki bookmarks "save" selection action). */
    fun saveSelected() {
        val ids = _selection.value
        if (ids.isEmpty()) return
        val state = content.value as? BookmarksUiState.Success ?: return
        val bookmarks = state.groups.flatMap { it.second }.filter { it.pageId in ids }
        if (bookmarks.isEmpty()) return
        viewModelScope.launch {
            var saved = 0
            for (b in bookmarks) {
                val page = MangaPage(b.pageId, b.imageUrl, null, b.manga.source)
                runCatching { pageSaveHelper.save(page) }.getOrNull()?.let { saved++ }
            }
            _selection.value = emptySet()
            onPagesSaved.tryEmit(saved)
        }
    }

    fun undoRemove() {
        val toRestore = lastRemoved
        if (toRestore.isEmpty()) return
        lastRemoved = emptyList()
        viewModelScope.launch { repository.restore(toRestore) }
    }
}

sealed interface BookmarksUiState {
    data object Loading : BookmarksUiState
    data object Empty : BookmarksUiState
    data class Success(val groups: List<Pair<Manga, List<Bookmark>>>) : BookmarksUiState
    data class Error(val error: Throwable) : BookmarksUiState
}
