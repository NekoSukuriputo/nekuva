package org.nekosukuriputo.nekuva.remotelist.ui

import androidx.compose.runtime.Composable
import org.jetbrains.compose.resources.stringResource
import org.nekosukuriputo.nekuva.parsers.model.ContentRating
import org.nekosukuriputo.nekuva.parsers.model.ContentType
import org.nekosukuriputo.nekuva.parsers.model.Demographic
import org.nekosukuriputo.nekuva.parsers.model.MangaState
import org.nekosukuriputo.nekuva.parsers.model.SortOrder
import org.nekosukuriputo.nekuva.parsers.util.toTitleCase
import java.util.Locale
import nekuva.composeapp.generated.resources.*

/**
 * Localized label for a [SortOrder] — mirrors Doki's per-value `SortOrder.titleRes` mapping
 * (meaningful named pairs, e.g. Popular/Unpopular), reusing the exact Doki string keys (§4.4).
 */
@Composable
fun sortOrderTitle(order: SortOrder): String = when (order) {
    SortOrder.UPDATED -> stringResource(Res.string.updated)
    SortOrder.UPDATED_ASC -> stringResource(Res.string.updated_long_ago)
    SortOrder.POPULARITY -> stringResource(Res.string.popular)
    SortOrder.POPULARITY_ASC -> stringResource(Res.string.unpopular)
    SortOrder.RATING -> stringResource(Res.string.by_rating)
    SortOrder.RATING_ASC -> stringResource(Res.string.low_rating)
    SortOrder.NEWEST -> stringResource(Res.string.newest)
    SortOrder.NEWEST_ASC -> stringResource(Res.string.order_oldest)
    SortOrder.ALPHABETICAL -> stringResource(Res.string.by_name)
    SortOrder.ALPHABETICAL_DESC -> stringResource(Res.string.by_name_reverse)
    SortOrder.ADDED -> stringResource(Res.string.recently_added)
    SortOrder.ADDED_ASC -> stringResource(Res.string.added_long_ago)
    SortOrder.RELEVANCE -> stringResource(Res.string.by_relevance)
    SortOrder.POPULARITY_HOUR -> stringResource(Res.string.popular_in_hour)
    SortOrder.POPULARITY_TODAY -> stringResource(Res.string.popular_today)
    SortOrder.POPULARITY_WEEK -> stringResource(Res.string.popular_in_week)
    SortOrder.POPULARITY_MONTH -> stringResource(Res.string.popular_in_month)
    SortOrder.POPULARITY_YEAR -> stringResource(Res.string.popular_in_year)
}

/** Localized label for a [MangaState]. */
@Composable
fun mangaStateTitle(state: MangaState): String = when (state) {
    MangaState.ONGOING -> stringResource(Res.string.state_ongoing)
    MangaState.FINISHED -> stringResource(Res.string.state_finished)
    MangaState.ABANDONED -> stringResource(Res.string.state_abandoned)
    MangaState.PAUSED -> stringResource(Res.string.state_paused)
    MangaState.UPCOMING -> stringResource(Res.string.state_upcoming)
    MangaState.RESTRICTED -> stringResource(Res.string.unavailable)
}

/** Localized label for a [ContentRating]. */
@Composable
fun contentRatingTitle(rating: ContentRating): String = when (rating) {
    ContentRating.SAFE -> stringResource(Res.string.rating_safe)
    ContentRating.SUGGESTIVE -> stringResource(Res.string.rating_suggestive)
    ContentRating.ADULT -> stringResource(Res.string.rating_adult)
}

/** Localized label for a [Demographic] (Doki "Demografi"). */
@Composable
fun demographicTitle(demographic: Demographic): String = when (demographic) {
    Demographic.SHOUNEN -> stringResource(Res.string.demographic_shounen)
    Demographic.SHOUJO -> stringResource(Res.string.demographic_shoujo)
    Demographic.SEINEN -> stringResource(Res.string.demographic_seinen)
    Demographic.JOSEI -> stringResource(Res.string.demographic_josei)
    Demographic.KODOMO -> stringResource(Res.string.demographic_kodomo)
    Demographic.NONE -> stringResource(Res.string.none)
}

/** Display name of a locale in its own language ("Bahasa Indonesia"), or "Any" for null — like Doki. */
@Composable
fun localeTitle(locale: Locale?): String =
    locale?.getDisplayName(locale)?.toTitleCase(locale) ?: stringResource(Res.string.any)

/** Localized label for a [ContentType] (Doki "Tipe"). */
@Composable
fun contentTypeTitle(type: ContentType): String = when (type) {
    ContentType.MANGA -> stringResource(Res.string.content_type_manga)
    ContentType.MANHWA -> stringResource(Res.string.content_type_manhwa)
    ContentType.MANHUA -> stringResource(Res.string.content_type_manhua)
    ContentType.HENTAI -> stringResource(Res.string.content_type_hentai)
    ContentType.COMICS -> stringResource(Res.string.content_type_comics)
    ContentType.NOVEL -> stringResource(Res.string.content_type_novel)
    ContentType.ONE_SHOT -> stringResource(Res.string.content_type_one_shot)
    ContentType.DOUJINSHI -> stringResource(Res.string.content_type_doujinshi)
    ContentType.IMAGE_SET -> stringResource(Res.string.content_type_image_set)
    ContentType.ARTIST_CG -> stringResource(Res.string.content_type_artist_cg)
    ContentType.GAME_CG -> stringResource(Res.string.content_type_game_cg)
    ContentType.OTHER -> stringResource(Res.string.content_type_other)
}
