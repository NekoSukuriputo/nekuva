package org.nekosukuriputo.nekuva.list.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import kotlinx.coroutines.flow.flowOf
import org.koin.compose.koinInject
import org.nekosukuriputo.nekuva.core.prefs.AppSettings
import org.nekosukuriputo.nekuva.core.prefs.ProgressIndicatorMode
import org.nekosukuriputo.nekuva.core.ui.components.MangaBadges
import org.nekosukuriputo.nekuva.favourites.domain.FavouritesRepository
import org.nekosukuriputo.nekuva.history.data.HistoryRepository
import org.nekosukuriputo.nekuva.parsers.model.Manga

/**
 * Per-item reading progress + favourite badge for ANY manga list, observed from the DB — Doki attaches
 * these in its list mapper. Gated by the Appearance settings (`progress_indicators`, `manga_list_badges`).
 * [includeFavouriteBadge] is turned off for the Favourites list itself (every item is already a favourite).
 */
class MangaListDecorations(
    private val progressMap: Map<Long, Float>,
    private val favouriteIds: Set<Long>,
    private val progressEnabled: Boolean,
    private val favouriteBadgeEnabled: Boolean,
) {
    fun progressOf(manga: Manga): Float? =
        if (progressEnabled) progressMap[manga.id]?.takeIf { it >= 0f } else null

    fun badgesOf(manga: Manga): MangaBadges =
        MangaBadges(favourite = favouriteBadgeEnabled && manga.id in favouriteIds)
}

@Composable
fun rememberMangaListDecorations(includeFavouriteBadge: Boolean = true): MangaListDecorations {
    val settings = koinInject<AppSettings>()
    val favourites = koinInject<FavouritesRepository>()
    val history = koinInject<HistoryRepository>()
    val progressEnabled = remember { settings.progressIndicatorMode != ProgressIndicatorMode.NONE }
    val favBadgeEnabled = remember { includeFavouriteBadge && (settings.getMangaListBadges() and 1) != 0 }

    val progressMap by (if (progressEnabled) history.observeProgressMap() else flowOf(emptyMap<Long, Float>()))
        .collectAsState(emptyMap())
    val favIds by (if (favBadgeEnabled) favourites.observeFavouriteIds() else flowOf(emptySet<Long>()))
        .collectAsState(emptySet())

    return remember(progressMap, favIds) {
        MangaListDecorations(progressMap, favIds, progressEnabled, favBadgeEnabled)
    }
}
