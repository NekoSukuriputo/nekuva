package org.nekosukuriputo.nekuva.tracker.ui.updates

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.nekosukuriputo.nekuva.core.model.FavouriteCategory
import org.nekosukuriputo.nekuva.download.domain.DownloadManager
import org.nekosukuriputo.nekuva.download.domain.DownloadTask
import org.nekosukuriputo.nekuva.favourites.domain.FavouritesRepository
import org.nekosukuriputo.nekuva.parsers.model.Manga
import org.nekosukuriputo.nekuva.parsers.util.runCatchingCancellable
import org.nekosukuriputo.nekuva.tracker.domain.TrackingRepository

/**
 * Dedicated "Updates" screen (Doki UpdatesViewModel/UpdatesFragment): a full grid of every manga that has
 * unread new chapters, with long-press multi-select (Doki mode_updates: Share / Add to favourites / Download).
 * The Feed tab shows the same manga as a header strip + the per-event log; this is the flat grid view.
 */
class UpdatesViewModel(
    trackingRepository: TrackingRepository,
    private val downloadManager: DownloadManager,
    private val favouritesRepository: FavouritesRepository,
) : ViewModel() {

    private val firstEmission = MutableStateFlow(false)

    val updatedManga: StateFlow<List<Manga>> =
        trackingRepository.observeUpdatedManga(limit = 500, filterOptions = emptySet())
            .map { list -> list.map { it.manga } }
            .onEach { firstEmission.value = true }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** True until the first DB emission so the screen can show a loading state. */
    val isLoading: StateFlow<Boolean> =
        firstEmission.map { !it }.stateIn(viewModelScope, SharingStarted.Eagerly, true)

    val favouriteCategories: StateFlow<List<FavouriteCategory>> =
        favouritesRepository.observeCategories()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Download the whole of each selected manga (Doki mode_updates action_save). */
    fun downloadManga(mangas: Collection<Manga>) {
        if (mangas.isEmpty()) return
        viewModelScope.launch {
            runCatchingCancellable {
                downloadManager.schedule(
                    mangas.map { DownloadTask(manga = it, chaptersIds = null, destination = null, format = null, startPaused = false) },
                )
            }
        }
    }

    /** Add several updated manga to a favourite category (Doki mode_updates action_favourite). */
    fun addToFavourites(categoryId: Long, mangas: Collection<Manga>) {
        if (mangas.isEmpty()) return
        viewModelScope.launch { runCatchingCancellable { favouritesRepository.addToCategory(categoryId, mangas.toList()) } }
    }
}
