package org.nekosukuriputo.nekuva.list.domain





enum class ListSortOrder(
	
) {

	NEWEST,
	OLDEST,
	PROGRESS,
	UNREAD,
	ALPHABETIC,
	ALPHABETIC_REVERSE,
	RATING,
	RELEVANCE,
	NEW_CHAPTERS,
	LAST_READ,
	LONG_AGO_READ,
	UPDATED,
	;

	fun isGroupingSupported() = this == LAST_READ || this == NEWEST || this == PROGRESS

	companion object {

		val HISTORY: Set<ListSortOrder> = setOf(
			LAST_READ,
			LONG_AGO_READ,
			NEWEST,
			OLDEST,
			PROGRESS,
			UNREAD,
			ALPHABETIC,
			ALPHABETIC_REVERSE,
			NEW_CHAPTERS,
			UPDATED,
		)
		val FAVORITES: Set<ListSortOrder> = setOf(
			ALPHABETIC,
			ALPHABETIC_REVERSE,
			NEWEST,
			OLDEST,
			RATING,
			NEW_CHAPTERS,
			PROGRESS,
			UNREAD,
			LAST_READ,
			LONG_AGO_READ,
			UPDATED,
		)
		val SUGGESTIONS: Set<ListSortOrder> = setOf(RELEVANCE)

		operator fun invoke(value: String, fallback: ListSortOrder) = entries.find { it.name == value } ?: fallback
	}
}

