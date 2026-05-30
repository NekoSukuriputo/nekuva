package org.nekosukuriputo.nekuva.core.model

import org.nekosukuriputo.nekuva.parsers.model.SortOrder
import kotlinx.serialization.Serializable

@Serializable
@Deprecated("")
enum class GenericSortOrder(
    val ascending: SortOrder,
    val descending: SortOrder,
) {
    UPDATED(SortOrder.UPDATED_ASC, SortOrder.UPDATED),
    RATING(SortOrder.RATING_ASC, SortOrder.RATING),
    POPULARITY(SortOrder.POPULARITY_ASC, SortOrder.POPULARITY),
    DATE(SortOrder.NEWEST_ASC, SortOrder.NEWEST),
    NAME(SortOrder.ALPHABETICAL, SortOrder.ALPHABETICAL_DESC);

    operator fun get(direction: SortDirection): SortOrder = when (direction) {
        SortDirection.ASC -> ascending
        SortDirection.DESC -> descending
    }

    companion object {
        fun of(order: SortOrder): GenericSortOrder = entries.first { e ->
            e.ascending == order || e.descending == order
        }
    }
}
