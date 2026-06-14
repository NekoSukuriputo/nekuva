package org.nekosukuriputo.nekuva.sync.data

import org.nekosukuriputo.nekuva.core.db.entity.MangaEntity
import org.nekosukuriputo.nekuva.core.db.entity.MangaWithTags
import org.nekosukuriputo.nekuva.core.db.entity.TagEntity
import org.nekosukuriputo.nekuva.favourites.data.FavouriteCategoryEntity
import org.nekosukuriputo.nekuva.favourites.data.FavouriteEntity
import org.nekosukuriputo.nekuva.history.data.HistoryEntity
import org.nekosukuriputo.nekuva.sync.data.model.FavouriteCategorySyncDto
import org.nekosukuriputo.nekuva.sync.data.model.FavouriteSyncDto
import org.nekosukuriputo.nekuva.sync.data.model.HistorySyncDto
import org.nekosukuriputo.nekuva.sync.data.model.MangaSyncDto
import org.nekosukuriputo.nekuva.sync.data.model.MangaTagSyncDto

// Row-level Room entity <-> sync DTO mapping. Equivalent of Doki's Cursor constructors /
// toContentValues(), but against Nekuva's typed Room entities.

fun TagEntity.toSyncDto() = MangaTagSyncDto(
	id = id,
	title = title,
	key = key,
	source = source,
)

fun MangaTagSyncDto.toEntity() = TagEntity(
	id = id,
	title = title,
	key = key,
	source = source,
	isPinned = false,
)

fun MangaWithTags.toSyncDto() = MangaSyncDto(
	id = manga.id,
	title = manga.title,
	altTitle = manga.altTitles,
	url = manga.url,
	publicUrl = manga.publicUrl,
	rating = manga.rating,
	contentRating = manga.contentRating,
	coverUrl = manga.coverUrl,
	largeCoverUrl = manga.largeCoverUrl,
	tags = tags.mapTo(HashSet(tags.size)) { it.toSyncDto() },
	state = manga.state,
	author = manga.authors,
	source = manga.source,
)

fun MangaSyncDto.toEntity() = MangaEntity(
	id = id,
	title = title,
	altTitles = altTitle,
	url = url,
	publicUrl = publicUrl,
	rating = rating,
	// The wire format has no nsfw flag (Doki derives it locally); approximate from content rating.
	isNsfw = contentRating?.let { it == "ADULT" || it == "PORNOGRAPHIC" } ?: false,
	contentRating = contentRating,
	coverUrl = coverUrl,
	largeCoverUrl = largeCoverUrl,
	state = state,
	authors = author,
	source = source,
)

fun FavouriteEntity.toSyncDto(manga: MangaSyncDto) = FavouriteSyncDto(
	mangaId = mangaId,
	manga = manga,
	categoryId = categoryId.toInt(),
	sortKey = sortKey,
	pinned = isPinned,
	createdAt = createdAt,
	deletedAt = deletedAt,
)

fun FavouriteSyncDto.toEntity() = FavouriteEntity(
	mangaId = mangaId,
	categoryId = categoryId.toLong(),
	sortKey = sortKey,
	isPinned = pinned,
	createdAt = createdAt,
	deletedAt = deletedAt,
)

fun FavouriteCategoryEntity.toSyncDto() = FavouriteCategorySyncDto(
	categoryId = categoryId,
	createdAt = createdAt,
	sortKey = sortKey,
	title = title,
	order = order,
	track = track,
	isVisibleInLibrary = isVisibleInLibrary,
	deletedAt = deletedAt,
)

fun FavouriteCategorySyncDto.toEntity() = FavouriteCategoryEntity(
	categoryId = categoryId,
	createdAt = createdAt,
	sortKey = sortKey,
	title = title,
	order = order,
	track = track,
	isVisibleInLibrary = isVisibleInLibrary,
	deletedAt = deletedAt,
)

fun HistoryEntity.toSyncDto(manga: MangaSyncDto) = HistorySyncDto(
	mangaId = mangaId,
	createdAt = createdAt,
	updatedAt = updatedAt,
	chapterId = chapterId,
	page = page,
	scroll = scroll,
	percent = percent,
	deletedAt = deletedAt,
	chaptersCount = chaptersCount,
	manga = manga,
)

fun HistorySyncDto.toEntity() = HistoryEntity(
	mangaId = mangaId,
	createdAt = createdAt,
	updatedAt = updatedAt,
	chapterId = chapterId,
	page = page,
	scroll = scroll,
	percent = percent,
	deletedAt = deletedAt,
	chaptersCount = chaptersCount,
)
