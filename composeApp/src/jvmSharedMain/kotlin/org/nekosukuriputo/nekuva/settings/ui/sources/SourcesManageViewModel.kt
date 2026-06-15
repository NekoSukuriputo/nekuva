package org.nekosukuriputo.nekuva.settings.ui.sources

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.nekosukuriputo.nekuva.core.model.MangaSourceInfo
import org.nekosukuriputo.nekuva.core.prefs.AppSettings
import org.nekosukuriputo.nekuva.explore.data.SourcesSortOrder
import org.nekosukuriputo.nekuva.explore.data.MangaSourcesRepository
import org.nekosukuriputo.nekuva.parsers.model.MangaSource

/** Manage enabled sources (Doki SourcesManageFragment): enable/disable, pin, reorder. */
class SourcesManageViewModel(
    private val repository: MangaSourcesRepository,
    private val settings: AppSettings,
) : ViewModel() {

    val sources: StateFlow<List<MangaSourceInfo>> = repository.observeEnabledSources()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun setEnabled(source: MangaSource, enabled: Boolean) {
        viewModelScope.launch { repository.setSourcesEnabled(setOf(source), enabled) }
    }

    /** Bulk actions for multi-select (Doki mode_source): disable / pin / unpin the selected sources. */
    fun setEnabledBulk(sources: Collection<MangaSource>, enabled: Boolean) {
        viewModelScope.launch { repository.setSourcesEnabled(sources, enabled) }
    }

    fun setPinnedBulk(sources: Collection<MangaSource>, pinned: Boolean) {
        viewModelScope.launch { repository.setSourcesPinned(sources, pinned) }
    }

    fun setPinned(source: MangaSource, pinned: Boolean) {
        viewModelScope.launch { repository.setSourcesPinned(setOf(source), pinned) }
    }

    /** Disable every source at once (Doki "Disable all"). */
    fun disableAll() {
        viewModelScope.launch { repository.disableAllSources() }
    }

    var isNsfwDisabled: Boolean
        get() = settings.isNsfwContentDisabled
        set(value) { settings.isNsfwContentDisabled = value }

    /** Move an item and persist the manual order (switches sort to MANUAL like Doki). */
    fun move(fromIndex: Int, toIndex: Int) {
        val current = sources.value.map { it.mangaSource }
        if (fromIndex !in current.indices || toIndex !in current.indices) return
        val reordered = current.toMutableList().apply { add(toIndex, removeAt(fromIndex)) }
        viewModelScope.launch {
            settings.sourcesSortOrder = SourcesSortOrder.MANUAL
            repository.setPositions(reordered)
        }
    }
}
