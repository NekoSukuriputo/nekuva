package org.nekosukuriputo.nekuva.tracker.domain.model

import org.nekosukuriputo.nekuva.parsers.model.Manga

/**
 * One entry in the updates Feed (Doki FeedItem / TrackingLogItem): the new chapters found for a manga at
 * a point in time. [isUnread] drives the "new" highlight; tapping marks it read.
 */
data class FeedLogItem(
    val id: Long,
    val manga: Manga,
    val chapters: List<String>,
    val createdAt: Long,
    val isUnread: Boolean,
) {
    val count: Int get() = chapters.size
}
