package org.nekosukuriputo.nekuva.bookmarks.domain

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.nekosukuriputo.nekuva.bookmarks.data.BookmarkEntity
import org.nekosukuriputo.nekuva.bookmarks.data.toBookmark
import org.nekosukuriputo.nekuva.bookmarks.data.toBookmarks
import org.nekosukuriputo.nekuva.bookmarks.data.toEntity
import org.nekosukuriputo.nekuva.core.db.MangaDatabase
import org.nekosukuriputo.nekuva.core.db.entity.toEntities
import org.nekosukuriputo.nekuva.core.db.entity.toEntity
import org.nekosukuriputo.nekuva.core.db.entity.toManga
import org.nekosukuriputo.nekuva.core.db.withTransactionKmp
import org.nekosukuriputo.nekuva.parsers.model.Manga

class BookmarksRepository(
    private val db: MangaDatabase,
) {

    fun observeBookmark(manga: Manga, chapterId: Long, page: Int): Flow<Bookmark?> {
        return db.getBookmarksDao().observe(manga.id, chapterId, page).map { it?.toBookmark(manga) }
    }

    /** Bookmarks of a single manga (for the details bottom sheet). */
    fun observeBookmarks(manga: Manga): Flow<List<Bookmark>> {
        return db.getBookmarksDao().observe(manga.id).map { it.toBookmarks(manga) }
    }

    fun observeBookmarks(): Flow<Map<Manga, List<Bookmark>>> {
        return db.getBookmarksDao().observe().map { grouped ->
            val res = LinkedHashMap<Manga, List<Bookmark>>(grouped.size)
            for ((mangaWithTags, entities) in grouped) {
                val manga = mangaWithTags.toManga()
                res[manga] = entities.toBookmarks(manga)
            }
            res
        }
    }

    /** Upsert the manga first (FK), then insert the bookmark — same ordering as history/favourites. */
    suspend fun addBookmark(bookmark: Bookmark) {
        db.withTransactionKmp {
            val tags = bookmark.manga.tags.toEntities()
            db.getTagsDao().upsert(tags)
            db.getMangaDao().upsert(bookmark.manga.toEntity(), tags)
            db.getBookmarksDao().insert(bookmark.toEntity())
        }
    }

    suspend fun removeBookmark(mangaId: Long, chapterId: Long, page: Int) {
        db.getBookmarksDao().delete(mangaId, chapterId, page)
    }

    /** Remove several bookmarks by page id; returns the removed rows so the action can be undone. */
    suspend fun removeBookmarks(pageIds: Set<Long>): List<BookmarkEntity> {
        val removed = ArrayList<BookmarkEntity>(pageIds.size)
        db.withTransactionKmp {
            val dao = db.getBookmarksDao()
            for (id in pageIds) {
                dao.find(id)?.let { removed.add(it) }
                dao.delete(id)
            }
        }
        return removed
    }

    suspend fun restore(entities: List<BookmarkEntity>) {
        db.withTransactionKmp {
            val dao = db.getBookmarksDao()
            for (e in entities) {
                runCatching { dao.insert(e) }
            }
        }
    }
}
