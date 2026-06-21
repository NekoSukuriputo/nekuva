package org.nekosukuriputo.nekuva.tracker.ui.feed

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.nekosukuriputo.nekuva.core.prefs.AppSettings
import org.nekosukuriputo.nekuva.favourites.domain.FavouritesRepository
import org.nekosukuriputo.nekuva.list.domain.ListFilterOption
import org.nekosukuriputo.nekuva.parsers.util.runCatchingCancellable
import org.nekosukuriputo.nekuva.tracker.domain.TrackerUpdateUseCase
import org.nekosukuriputo.nekuva.tracker.domain.TrackingRepository
import org.nekosukuriputo.nekuva.tracker.domain.model.FeedLogItem
import org.nekosukuriputo.nekuva.tracker.domain.model.MangaTracking

private const val PAGE_SIZE = 20

/**
 * Feed/Updates tab (Doki FeedViewModel parity): the main list is the date-ordered tracking LOG (one entry
 * per "new chapters found" event), with an optional "Updated manga" header row (Doki show_updated toggle)
 * and a **quick-filter chip row** (Doki UpdatesListQuickFilter — favourite categories with the most updates).
 * Refresh + isRunning are shared with the shell "Update" overflow via [TrackerUpdateUseCase].
 */
@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class FeedViewModel(
    private val trackingRepository: TrackingRepository,
    private val trackerUpdate: TrackerUpdateUseCase,
    private val favouritesRepository: FavouritesRepository,
    private val settings: AppSettings,
) : ViewModel() {

    private val limit = MutableStateFlow(PAGE_SIZE)
    private val firstEmission = MutableStateFlow(false)

    /** Applied quick-filter chips (Doki appliedOptions): favourite categories the user tapped. */
    private val _appliedFilter = MutableStateFlow<Set<ListFilterOption>>(emptySet())
    val appliedFilter: StateFlow<Set<ListFilterOption>> = _appliedFilter.asStateFlow()

    /** Available quick-filter chips (Doki UpdatesListQuickFilter): top favourite categories by updates. */
    val quickFilterOptions: StateFlow<List<ListFilterOption>> = flow {
        emit(
            if (settings.isQuickFilterEnabled) {
                runCatchingCancellable {
                    favouritesRepository.getMostUpdatedCategories(limit = 4).map { ListFilterOption.Favorite(it) }
                }.getOrDefault(emptyList())
            } else {
                emptyList()
            },
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    /** The applied chips + an implicit SFW filter when NSFW content is disabled (Doki combineWithSettings). */
    private val effectiveFilter: StateFlow<Set<ListFilterOption>> =
        _appliedFilter.map { applied ->
            if (settings.isNsfwContentDisabled) applied + ListFilterOption.SFW else applied
        }.stateIn(viewModelScope, SharingStarted.Eagerly, emptySet())

    /** Whether the "Updated manga" header row is shown (Doki show_updated / KEY_FEED_HEADER), live. */
    val isHeaderEnabled: StateFlow<Boolean> =
        settings.observeBoolean(AppSettings.KEY_FEED_HEADER, true)
            .stateIn(viewModelScope, SharingStarted.Eagerly, settings.isFeedHeaderVisible)

    /** Shared with the shell "Update" overflow: a single check at a time (Doki single worker). */
    val isRefreshing: StateFlow<Boolean> = trackerUpdate.isRunning

    /** Header row content: recently-updated manga (respecting the quick filter), only when the header is on. */
    val updatedManga: StateFlow<List<MangaTracking>> =
        combine(isHeaderEnabled, effectiveFilter) { enabled, filter -> enabled to filter }
            .flatMapLatest { (enabled, filter) ->
                if (enabled) trackingRepository.observeUpdatedManga(limit = 10, filterOptions = filter) else flowOf(emptyList())
            }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** The feed log itself (date-ordered, quick-filtered), paginated via [requestMoreItems]. */
    val logItems: StateFlow<List<FeedLogItem>> =
        combine(limit, effectiveFilter) { lim, filter -> lim to filter }
            .flatMapLatest { (lim, filter) -> trackingRepository.observeTrackingLog(lim, filter) }
            .onEach { firstEmission.value = true }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** True until the first DB emission so the screen can show a loading state (Doki LoadingState). */
    val isLoading: StateFlow<Boolean> =
        firstEmission.map { !it }.stateIn(viewModelScope, SharingStarted.Eagerly, true)

    init {
        // Keep the tracked set in sync with favourites/history + trim old logs whenever the Feed opens.
        viewModelScope.launch { runCatchingCancellable { trackingRepository.updateTracks() } }
        viewModelScope.launch { runCatchingCancellable { trackingRepository.gc() } }
    }

    /** Manual refresh (Doki action_update): check every tracked manga now. */
    fun refresh() = trackerUpdate.updateNow()

    /** Toggle a quick-filter chip (Doki toggleFilterOption). */
    fun toggleFilter(option: ListFilterOption) {
        _appliedFilter.value = _appliedFilter.value.toMutableSet().also {
            if (!it.remove(option)) it.add(option)
        }
    }

    fun requestMoreItems() {
        if (firstEmission.value) limit.value += PAGE_SIZE
    }

    /** Tapping a feed entry marks it read (clears its "new" highlight + the tab badge). */
    fun onItemClick(logId: Long) {
        viewModelScope.launch { runCatchingCancellable { trackingRepository.markLogAsRead(logId) } }
    }
}
