package org.nekosukuriputo.nekuva.sync.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Wire models for the Kotatsu-compatible sync server (the same JSON shapes Doki uses). KMP port of
 * Doki's sync DTOs, with the Android `Cursor` constructors and `toContentValues()` dropped — Nekuva
 * reads/writes Room entities directly, so the entity to DTO mapping lives in
 * [org.nekosukuriputo.nekuva.sync.data.SyncMapping] instead.
 */
@Serializable
data class SyncDto(
	@SerialName("history") val history: List<HistorySyncDto>? = null,
	@SerialName("categories") val categories: List<FavouriteCategorySyncDto>? = null,
	@SerialName("favourites") val favourites: List<FavouriteSyncDto>? = null,
	@SerialName("timestamp") val timestamp: Long,
)

@Serializable
data class MangaTagSyncDto(
	@SerialName("tag_id") val id: Long,
	@SerialName("title") val title: String,
	@SerialName("key") val key: String,
	@SerialName("source") val source: String,
)

@Serializable
data class MangaSyncDto(
	@SerialName("manga_id") val id: Long,
	@SerialName("title") val title: String,
	@SerialName("alt_title") val altTitle: String? = null,
	@SerialName("url") val url: String,
	@SerialName("public_url") val publicUrl: String,
	@SerialName("rating") val rating: Float,
	@SerialName("content_rating") val contentRating: String? = null,
	@SerialName("cover_url") val coverUrl: String,
	@SerialName("large_cover_url") val largeCoverUrl: String? = null,
	@SerialName("tags") val tags: Set<MangaTagSyncDto>,
	@SerialName("state") val state: String? = null,
	@SerialName("author") val author: String? = null,
	@SerialName("source") val source: String,
)

@Serializable
data class FavouriteSyncDto(
	@SerialName("manga_id") val mangaId: Long,
	@SerialName("manga") val manga: MangaSyncDto,
	@SerialName("category_id") val categoryId: Int,
	@SerialName("sort_key") val sortKey: Int,
	@SerialName("pinned") val pinned: Boolean,
	@SerialName("created_at") val createdAt: Long,
	@SerialName("deleted_at") val deletedAt: Long,
)

@Serializable
data class FavouriteCategorySyncDto(
	@SerialName("category_id") val categoryId: Int,
	@SerialName("created_at") val createdAt: Long,
	@SerialName("sort_key") val sortKey: Int,
	@SerialName("title") val title: String,
	@SerialName("order") val order: String,
	@SerialName("track") val track: Boolean,
	@SerialName("show_in_lib") val isVisibleInLibrary: Boolean,
	@SerialName("deleted_at") val deletedAt: Long,
)

@Serializable
data class HistorySyncDto(
	@SerialName("manga_id") val mangaId: Long,
	@SerialName("created_at") val createdAt: Long,
	@SerialName("updated_at") val updatedAt: Long,
	@SerialName("chapter_id") val chapterId: Long,
	@SerialName("page") val page: Int,
	@SerialName("scroll") val scroll: Float,
	@SerialName("percent") val percent: Float,
	@SerialName("deleted_at") val deletedAt: Long,
	@SerialName("chapters") val chaptersCount: Int,
	@SerialName("manga") val manga: MangaSyncDto,
)
