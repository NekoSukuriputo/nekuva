package org.nekosukuriputo.nekuva.favourites.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.RoomWarnings
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
abstract class FavouriteCategoriesDao {

	@Query("SELECT * FROM favourite_categories WHERE category_id = :id AND deleted_at = 0")
	abstract suspend fun find(id: Int): FavouriteCategoryEntity

	// category_id = 0 is the special "Default" category. It is a real row (so favourites can
	// reference it via FK) but is represented synthetically in the UI, so it is excluded from the
	// category LIST queries to avoid a duplicate "Default" tab / a deletable Default in Manage.
	@Query("SELECT * FROM favourite_categories WHERE deleted_at = 0 AND category_id != 0 ORDER BY sort_key")
	abstract suspend fun findAll(): List<FavouriteCategoryEntity>

	@Query("SELECT * FROM favourite_categories WHERE deleted_at = 0 AND category_id != 0 ORDER BY sort_key")
	abstract fun observeAll(): Flow<List<FavouriteCategoryEntity>>

	@Query("SELECT * FROM favourite_categories WHERE deleted_at = 0 AND show_in_lib = 1 AND category_id != 0 ORDER BY sort_key")
	abstract fun observeAllVisible(): Flow<List<FavouriteCategoryEntity>>

	@Query("SELECT * FROM favourite_categories WHERE category_id = :id AND deleted_at = 0")
	abstract fun observe(id: Long): Flow<FavouriteCategoryEntity?>

	@Insert(onConflict = OnConflictStrategy.ABORT)
	abstract suspend fun insert(category: FavouriteCategoryEntity): Long

	/**
	 * Ensure the special "Default" category (category_id = 0) exists so favourites can reference it.
	 * Uses an explicit category_id = 0 because Room's @Insert treats the autoGenerate PK value 0 as
	 * "unset" and lets SQLite assign a new id instead. INSERT OR IGNORE is a no-op if it already exists.
	 */
	@Query("INSERT OR IGNORE INTO favourite_categories (category_id, created_at, sort_key, title, `order`, track, show_in_lib, deleted_at) VALUES (0, :createdAt, 0, :title, :order, 0, 1, 0)")
	abstract suspend fun ensureDefaultCategory(createdAt: Long, title: String, order: String)

	suspend fun delete(id: Long) = setDeletedAt(id, System.currentTimeMillis())

	@Query("UPDATE favourite_categories SET title = :title, `order` = :order, `track` = :tracker, `show_in_lib` = :onShelf WHERE category_id = :id")
	abstract suspend fun update(id: Long, title: String, order: String, tracker: Boolean, onShelf: Boolean)

	@Query("UPDATE favourite_categories SET title = :title WHERE category_id = :id")
	abstract suspend fun updateTitle(id: Long, title: String)

	@Query("UPDATE favourite_categories SET `order` = :order WHERE category_id = :id")
	abstract suspend fun updateOrder(id: Long, order: String)

	@Query("UPDATE favourite_categories SET `track` = :isEnabled WHERE category_id = :id")
	abstract suspend fun updateTracking(id: Long, isEnabled: Boolean)

	@Query("UPDATE favourite_categories SET `show_in_lib` = :isEnabled WHERE category_id = :id")
	abstract suspend fun updateVisibility(id: Long, isEnabled: Boolean)

	@Query("UPDATE favourite_categories SET sort_key = :sortKey WHERE category_id = :id")
	abstract suspend fun updateSortKey(id: Long, sortKey: Int)

	/** Rename a category matched by its exact (literal) title — used to localise seeded names. */
	@Query("UPDATE favourite_categories SET title = :newTitle WHERE title = :oldTitle AND deleted_at = 0")
	abstract suspend fun renameByTitle(oldTitle: String, newTitle: String)

	@Query("DELETE FROM favourite_categories WHERE deleted_at != 0 AND deleted_at < :maxDeletionTime")
	abstract suspend fun gc(maxDeletionTime: Long)

	// Sync push: every category except the implicit local default (id 0), including soft-deleted ones.
	@Query("SELECT * FROM favourite_categories WHERE category_id != 0")
	abstract suspend fun findAllForSync(): List<FavouriteCategoryEntity>

	@Query("SELECT MAX(sort_key) FROM favourite_categories WHERE deleted_at = 0")
	protected abstract suspend fun getMaxSortKey(): Int?

	@SuppressWarnings(RoomWarnings.QUERY_MISMATCH) // for the new_chapters column
	@Query("SELECT favourite_categories.*, (SELECT SUM(chapters_new) FROM tracks WHERE tracks.manga_id IN (SELECT manga_id FROM favourites WHERE favourites.category_id = favourite_categories.category_id)) AS new_chapters FROM favourite_categories WHERE track = 1 AND show_in_lib = 1 AND deleted_at = 0 AND new_chapters > 0 ORDER BY new_chapters DESC LIMIT :limit")
	abstract suspend fun getMostUpdatedCategories(limit: Int): List<FavouriteCategoryEntity>

	suspend fun getNextSortKey(): Int {
		return (getMaxSortKey() ?: 0) + 1
	}

	@Upsert
	abstract suspend fun upsert(entity: FavouriteCategoryEntity)

	@Query("UPDATE favourite_categories SET deleted_at = :deletedAt WHERE category_id = :id")
	protected abstract suspend fun setDeletedAt(id: Long, deletedAt: Long)
}
