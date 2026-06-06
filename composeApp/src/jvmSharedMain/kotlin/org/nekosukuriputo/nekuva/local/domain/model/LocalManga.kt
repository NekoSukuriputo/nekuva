package org.nekosukuriputo.nekuva.local.domain.model

import java.net.URI


import org.nekosukuriputo.nekuva.parsers.model.Manga
import org.nekosukuriputo.nekuva.parsers.model.MangaTag
import java.io.File

data class LocalManga(
	val manga: Manga,
	val file: File = manga.url.let { URI(it) }.let { java.io.File(it) },
) {

	var createdAt: Long = -1L
		private set
		get() {
			if (field == -1L) {
				field = file.lastModified()
			}
			return field
		}

	fun toUri(): URI = manga.url.let { URI(it) }

	fun isMatchesQuery(query: String): Boolean {
		return manga.title.contains(query, ignoreCase = true) ||
			manga.altTitles.any { it.contains(query, ignoreCase = true) } ||
			manga.authors.any { it.contains(query, ignoreCase = true) }
	}

	fun containsTags(tags: Collection<String>): Boolean {
		return tags.all { tag -> tag in manga.tags }
	}

	fun containsAnyTag(tags: Collection<String>): Boolean {
		return tags.any { tag -> tag in manga.tags }
	}

	private operator fun Collection<MangaTag>.contains(title: String): Boolean {
		return any { it.title.equals(title, ignoreCase = true) }
	}

	override fun toString(): String {
		return "LocalManga(${file.path}: ${manga.title})"
	}
}



