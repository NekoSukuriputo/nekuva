package org.nekosukuriputo.nekuva.favourites.domain

import org.nekosukuriputo.nekuva.core.db.withTransactionKmp
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import org.nekosukuriputo.nekuva.core.db.MangaDatabase
import org.nekosukuriputo.nekuva.core.db.TABLE_FAVOURITES
import org.nekosukuriputo.nekuva.core.db.TABLE_FAVOURITE_CATEGORIES
import org.nekosukuriputo.nekuva.core.db.entity.toEntities
import org.nekosukuriputo.nekuva.core.db.entity.toEntity
import org.nekosukuriputo.nekuva.core.db.entity.toMangaList as toMangaListWithTags
import org.nekosukuriputo.nekuva.core.model.FavouriteCategory
import org.nekosukuriputo.nekuva.list.domain.ListSortOrder
import org.nekosukuriputo.nekuva.favourites.data.FavouriteCategoryEntity
import org.nekosukuriputo.nekuva.favourites.data.FavouriteEntity
import org.nekosukuriputo.nekuva.favourites.data.toFavouriteCategory
import org.nekosukuriputo.nekuva.favourites.data.toMangaList
import org.nekosukuriputo.nekuva.list.domain.ListFilterOption
import org.nekosukuriputo.nekuva.parsers.model.Manga
import kotlin.time.Clock

@OptIn(kotlin.time.ExperimentalTime::class)
class FavouritesRepository(
	private val db: MangaDatabase,
	private val mangaDataRepository: org.nekosukuriputo.nekuva.core.parser.MangaDataRepository,
) {

	fun observeAll(order: ListSortOrder, filterOptions: Set<ListFilterOption>, limit: Int): Flow<List<Manga>> {
		return db.getFavouritesDao().observeAll(order, filterOptions, limit)
			// Apply user overrides (custom title/cover) for display (Doki MangaListMapper.getOverrides()).
			.map { mangaDataRepository.applyOverrides(it.toMangaList()) }
	}

	fun observeAll(
		categoryId: Long,
		order: ListSortOrder,
		filterOptions: Set<ListFilterOption>,
		limit: Int
	): Flow<List<Manga>> {
		return db.getFavouritesDao().observeAll(categoryId, order, filterOptions, limit)
			.map { mangaDataRepository.applyOverrides(it.toMangaList()) }
	}

	fun observeAll(categoryId: Long, filterOptions: Set<ListFilterOption>, limit: Int): Flow<List<Manga>> {
		return observeOrder(categoryId)
			.flatMapLatest { order -> observeAll(categoryId, order, filterOptions, limit) }
	}

	fun observeMangaCount(): Flow<Int> {
		return db.getFavouritesDao().observeMangaCount()
			.distinctUntilChanged()
	}

	fun observeCategories(): Flow<List<FavouriteCategory>> {
		return db.getFavouriteCategoriesDao().observeAll().map { list ->
			list.map { it.toFavouriteCategory() }
		}
	}

	fun observeCategoriesForLibrary(): Flow<List<FavouriteCategory>> {
		return db.getFavouriteCategoriesDao().observeAllVisible().map { list ->
			list.map { it.toFavouriteCategory() }
		}
	}

	fun observeCategory(id: Long): Flow<FavouriteCategory?> {
		return db.getFavouriteCategoriesDao().observe(id)
			.map { it?.toFavouriteCategory() }
	}

	fun observeCategoriesIds(mangaId: Long): Flow<Set<Long>> {
		return db.getFavouritesDao().observeIds(mangaId).map { list ->
			list.toSet()
		}
	}

	fun observeCategories(mangaId: Long): Flow<Set<FavouriteCategory>> {
		return db.getFavouritesDao().observeCategories(mangaId).map {
			it.mapTo(LinkedHashSet(it.size)) { x -> x.toFavouriteCategory() }
		}
	}

	suspend fun getCategory(id: Long): FavouriteCategory {
		return db.getFavouriteCategoriesDao().find(id.toInt()).toFavouriteCategory()
	}

	suspend fun isFavorite(mangaId: Long): Boolean {
		return db.getFavouritesDao().findCategoriesCount(mangaId) != 0
	}

	/** All favourited manga ids — for the favourite badge on any manga list (Doki parity). */
	fun observeFavouriteIds(): Flow<Set<Long>> =
		db.getFavouritesDao().observeFavouriteIds().map { it.toSet() }

	/** Read-only title search over favourites (for global search). */
	suspend fun search(query: String, limit: Int): List<Manga> {
		return db.getFavouritesDao().searchByTitle("%$query%", limit).toMangaListWithTags()
	}

	suspend fun getCategoriesIds(mangaId: Long): Set<Long> {
		return db.getFavouritesDao().findCategoriesIds(mangaId).toSet()
	}

	suspend fun createCategory(
		title: String,
		sortOrder: ListSortOrder,
		isTrackerEnabled: Boolean,
		isVisibleOnShelf: Boolean,
	): FavouriteCategory {
		val entity = FavouriteCategoryEntity(
			title = title,
			createdAt = Clock.System.now().toEpochMilliseconds(),
			sortKey = db.getFavouriteCategoriesDao().getNextSortKey(),
			categoryId = 0,
			order = sortOrder.name,
			track = isTrackerEnabled,
			deletedAt = 0L,
			isVisibleInLibrary = isVisibleOnShelf,
		)
		val id = db.getFavouriteCategoriesDao().insert(entity)
		return entity.toFavouriteCategory(id)
	}

	suspend fun updateCategory(
		id: Long,
		title: String,
		sortOrder: ListSortOrder,
		isTrackerEnabled: Boolean,
		isVisibleOnShelf: Boolean,
	) {
		db.getFavouriteCategoriesDao().update(id, title, sortOrder.name, isTrackerEnabled, isVisibleOnShelf)
	}

	/** Toggle a category's "show in library" flag without touching its other fields (Doki tab Hide). */
	suspend fun setCategoryVisibility(id: Long, isVisible: Boolean) {
		db.getFavouriteCategoriesDao().updateVisibility(id, isVisible)
	}

	/** Rename a category (Doki Edit category) without touching its sort/tracking/visibility. */
	suspend fun renameCategory(id: Long, title: String) {
		db.getFavouriteCategoriesDao().updateTitle(id, title)
	}

	suspend fun removeCategories(ids: Collection<Long>) {
		db.withTransactionKmp {
			for (id in ids) {
				db.getFavouritesDao().deleteAll(id)
				db.getFavouriteCategoriesDao().delete(id)
			}
			db.getChaptersDao().gc()
		}
	}

	suspend fun setCategoryOrder(id: Long, order: ListSortOrder) {
		db.getFavouriteCategoriesDao().updateOrder(id, order.name)
	}

	suspend fun reorderCategories(orderedIds: List<Long>) {
		val dao = db.getFavouriteCategoriesDao()
		db.withTransactionKmp {
			for ((i, id) in orderedIds.withIndex()) {
				dao.updateSortKey(id, i)
			}
		}
	}

	suspend fun addToCategory(categoryId: Long, mangas: Collection<Manga>) {
		db.withTransactionKmp {
			// The "Default" category (id 0) is synthetic in the UI and has no seeded row on older DBs;
			// make sure it exists before referencing it, or the favourite insert fails the FK (id 0).
			if (categoryId == DEFAULT_CATEGORY_ID) {
				db.getFavouriteCategoriesDao().ensureDefaultCategory(
					createdAt = Clock.System.now().toEpochMilliseconds(),
					title = "Default",
					order = ListSortOrder.NEWEST.name,
				)
			}
			for (manga in mangas) {
				val tags = manga.tags.toEntities()
				db.getTagsDao().upsert(tags)
				db.getMangaDao().upsert(manga.toEntity(), tags)
				val entity = FavouriteEntity(
					mangaId = manga.id,
					categoryId = categoryId,
					createdAt = Clock.System.now().toEpochMilliseconds(),
					sortKey = 0,
					deletedAt = 0L,
					isPinned = false,
				)
				db.getFavouritesDao().insert(entity)
			}
		}
	}

	suspend fun removeFromFavourites(ids: Collection<Long>) {
		db.withTransactionKmp {
			for (id in ids) {
				db.getFavouritesDao().delete(mangaId = id)
			}
			db.getChaptersDao().gc()
		}
	}

	suspend fun removeFromCategory(categoryId: Long, ids: Collection<Long>) {
		db.withTransactionKmp {
			for (id in ids) {
				db.getFavouritesDao().delete(categoryId = categoryId, mangaId = id)
			}
			db.getChaptersDao().gc()
		}
	}

	/**
	 * Localise the seeded "Read Later" category to the device language: rename the original
	 * English seed title to [localizedTitle]. Idempotent — once renamed, the old title no longer
	 * matches, so it is safe to call on every app start. Handles both new and existing databases.
	 */
	suspend fun localizeSeededReadLater(localizedTitle: String) {
		if (localizedTitle.isNotBlank() && localizedTitle != SEED_READ_LATER_TITLE) {
			db.getFavouriteCategoriesDao().renameByTitle(SEED_READ_LATER_TITLE, localizedTitle)
		}
	}

	private fun observeOrder(categoryId: Long): Flow<ListSortOrder> {
		return db.getFavouriteCategoriesDao().observe(categoryId)
			.filterNotNull()
			.map { x -> ListSortOrder(x.order, ListSortOrder.NEWEST) }
			.distinctUntilChanged()
	}

	companion object {
		/** The special "Default" favourites category. Real row, synthetic in the UI. */
		const val DEFAULT_CATEGORY_ID = 0L

		/** Literal title used when seeding the "Read Later" category (localised on first run). */
		const val SEED_READ_LATER_TITLE = "Read Later"
	}
}
