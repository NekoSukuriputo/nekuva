package org.nekosukuriputo.nekuva.local.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch
import org.nekosukuriputo.nekuva.local.data.LocalMangaRepository
import org.nekosukuriputo.nekuva.local.domain.model.LocalManga

class LocalListViewModel(
    private val localMangaRepository: LocalMangaRepository,
    private val localStorageChanges: MutableSharedFlow<LocalManga?>,
    private val mangaDataRepository: org.nekosukuriputo.nekuva.core.parser.MangaDataRepository,
    private val settings: org.nekosukuriputo.nekuva.core.prefs.AppSettings,
    private val filterHolder: org.nekosukuriputo.nekuva.local.domain.LocalFilterHolder,
) : ViewModel() {

    private val _uiState = MutableStateFlow<LocalListUiState>(LocalListUiState.Loading)
    val uiState: StateFlow<LocalListUiState> = _uiState.asStateFlow()

    private val _sortOrder = MutableStateFlow(settings.localListOrder)
    val sortOrder: StateFlow<org.nekosukuriputo.nekuva.parsers.model.SortOrder> = _sortOrder.asStateFlow()

    /** Change the local list sort (Doki KEY_LOCAL_LIST_ORDER), persist it, and reload. */
    fun setSortOrder(order: org.nekosukuriputo.nekuva.parsers.model.SortOrder) {
        settings.localListOrder = order
        _sortOrder.value = order
        loadManga()
    }

    // Library-wide tags for the inline quick-filter chip row (Doki QuickFilter header). Queried from the
    // local index via getFilterOptions() so it stays populated even when the active filter empties the list
    // (lets the user un-toggle a tag that yielded no results). Refreshed only when the library content
    // changes, NOT on every filter change.
    private val _availableTags = MutableStateFlow<List<org.nekosukuriputo.nekuva.parsers.model.MangaTag>>(emptyList())
    val availableTags: StateFlow<List<org.nekosukuriputo.nekuva.parsers.model.MangaTag>> = _availableTags.asStateFlow()

    init {
        loadManga()
        loadAvailableTags()
        // Refresh when storage changes (e.g. a download finished, or a manga was deleted).
        viewModelScope.launch {
            localStorageChanges.collect { loadManga(); loadAvailableTags() }
        }
        // Re-query when the filter sheet applies (one bump = one reload; covers tags/exclude/rating/sort).
        viewModelScope.launch {
            filterHolder.revision.drop(1).collect { loadManga() }
        }
    }

    /** Currently-applied local filter tags (Doki filter), exposed for the filter sheet's initial state. */
    val appliedTags: StateFlow<Set<org.nekosukuriputo.nekuva.parsers.model.MangaTag>> get() = filterHolder.tags

    /** Toggle a tag from the quick-filter chip row (Doki QuickFilterListener.toggleFilterOption → toggleTag).
     *  Only touches the include-tags set (exclude/rating from the full filter sheet are left as-is). */
    fun toggleTag(tag: org.nekosukuriputo.nekuva.parsers.model.MangaTag) {
        val current = filterHolder.tags.value
        filterHolder.tags.value = if (tag in current) current - tag else current + tag
        filterHolder.notifyApplied() // bumps revision → list re-queries once
    }

    private fun loadAvailableTags() {
        viewModelScope.launch {
            _availableTags.value = runCatching {
                localMangaRepository.getFilterOptions().availableTags
            }.getOrNull().orEmpty().sortedBy { it.title }
        }
    }

    fun deleteManga(manga: org.nekosukuriputo.nekuva.parsers.model.Manga) {
        viewModelScope.launch {
            runCatching { localMangaRepository.delete(manga) }
            loadManga()
        }
    }

    /** Selection-mode: delete several local manga (Doki mode_local action_remove). */
    fun deleteManga(mangas: Collection<org.nekosukuriputo.nekuva.parsers.model.Manga>) {
        viewModelScope.launch {
            mangas.forEach { runCatching { localMangaRepository.delete(it) } }
            loadManga()
        }
    }

    /** Selection-mode (single): save a custom title/cover override (Doki mode_local action_edit_override). */
    fun setOverride(manga: org.nekosukuriputo.nekuva.parsers.model.Manga, title: String?, coverUrl: String?) {
        viewModelScope.launch {
            val existing = runCatching { mangaDataRepository.getOverride(manga.id) }.getOrNull()
            runCatching {
                mangaDataRepository.setOverride(
                    manga,
                    org.nekosukuriputo.nekuva.core.model.MangaOverride(
                        coverUrl = coverUrl?.trim()?.ifEmpty { null },
                        title = title?.trim()?.ifEmpty { null },
                        contentRating = existing?.contentRating,
                    ),
                )
            }
            loadManga()
        }
    }

    fun loadManga() {
        viewModelScope.launch {
            _uiState.value = LocalListUiState.Loading
            try {
                _sortOrder.value = settings.localListOrder // keep the displayed sort in sync with the sheet
                val filter = if (filterHolder.isEmpty) {
                    null
                } else {
                    org.nekosukuriputo.nekuva.parsers.model.MangaListFilter.EMPTY.copy(
                        tags = filterHolder.tags.value,
                        tagsExclude = filterHolder.tagsExclude.value,
                        contentRating = filterHolder.contentRating.value,
                    )
                }
                val raw = localMangaRepository.getList(0, settings.localListOrder, filter)
                // Store local manga in the DB so Details/Reader can resolve them by id.
                raw.forEach { runCatching { mangaDataRepository.storeManga(it, replaceExisting = false) } }
                // Apply user overrides (custom title/cover) for display (Doki MangaListMapper.getOverrides()).
                val list = mangaDataRepository.applyOverrides(raw)
                if (list.isEmpty()) {
                    _uiState.value = LocalListUiState.Empty
                } else {
                    _uiState.value = LocalListUiState.Success(list)
                }
            } catch (e: Exception) {
                _uiState.value = LocalListUiState.Error(e)
            }
        }
    }
}

sealed interface LocalListUiState {
    data object Loading : LocalListUiState
    data object Empty : LocalListUiState
    data class Success(val mangaList: List<org.nekosukuriputo.nekuva.parsers.model.Manga>) : LocalListUiState
    data class Error(val exception: Throwable) : LocalListUiState
}

