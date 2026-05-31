package org.nekosukuriputo.nekuva.core.db

import androidx.room.ConstructedBy
import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.RoomDatabaseConstructor
import org.nekosukuriputo.nekuva.bookmarks.data.BookmarkEntity
import org.nekosukuriputo.nekuva.bookmarks.data.BookmarksDao
import org.nekosukuriputo.nekuva.core.db.dao.ChaptersDao
import org.nekosukuriputo.nekuva.core.db.dao.MangaDao
import org.nekosukuriputo.nekuva.core.db.dao.MangaSourcesDao
import org.nekosukuriputo.nekuva.core.db.dao.PreferencesDao
import org.nekosukuriputo.nekuva.core.db.dao.TagsDao
import org.nekosukuriputo.nekuva.core.db.dao.TrackLogsDao
import org.nekosukuriputo.nekuva.core.db.entity.ChapterEntity
import org.nekosukuriputo.nekuva.core.db.entity.MangaEntity
import org.nekosukuriputo.nekuva.core.db.entity.MangaPrefsEntity
import org.nekosukuriputo.nekuva.core.db.entity.MangaSourceEntity
import org.nekosukuriputo.nekuva.core.db.entity.MangaTagsEntity
import org.nekosukuriputo.nekuva.core.db.entity.TagEntity
import org.nekosukuriputo.nekuva.favourites.data.FavouriteCategoriesDao
import org.nekosukuriputo.nekuva.favourites.data.FavouriteCategoryEntity
import org.nekosukuriputo.nekuva.favourites.data.FavouriteEntity
import org.nekosukuriputo.nekuva.favourites.data.FavouritesDao
import org.nekosukuriputo.nekuva.history.data.HistoryDao
import org.nekosukuriputo.nekuva.history.data.HistoryEntity
import org.nekosukuriputo.nekuva.local.data.index.LocalMangaIndexDao
import org.nekosukuriputo.nekuva.local.data.index.LocalMangaIndexEntity
import org.nekosukuriputo.nekuva.scrobbling.common.data.ScrobblingDao
import org.nekosukuriputo.nekuva.scrobbling.common.data.ScrobblingEntity
import org.nekosukuriputo.nekuva.stats.data.StatsDao
import org.nekosukuriputo.nekuva.stats.data.StatsEntity
import org.nekosukuriputo.nekuva.suggestions.data.SuggestionDao
import org.nekosukuriputo.nekuva.suggestions.data.SuggestionEntity
import org.nekosukuriputo.nekuva.tracker.data.TrackEntity
import org.nekosukuriputo.nekuva.tracker.data.TrackLogEntity
import org.nekosukuriputo.nekuva.tracker.data.TracksDao

const val DATABASE_VERSION = 1

@Database(
	entities = [
		MangaEntity::class, TagEntity::class, HistoryEntity::class, MangaTagsEntity::class, ChapterEntity::class,
		FavouriteCategoryEntity::class, FavouriteEntity::class, MangaPrefsEntity::class, TrackEntity::class,
		TrackLogEntity::class, SuggestionEntity::class, BookmarkEntity::class, ScrobblingEntity::class,
		MangaSourceEntity::class, StatsEntity::class, LocalMangaIndexEntity::class,
	],
	version = DATABASE_VERSION,
)
@ConstructedBy(MangaDatabaseConstructor::class)
abstract class MangaDatabase : RoomDatabase() {

	abstract fun getHistoryDao(): HistoryDao

	abstract fun getTagsDao(): TagsDao

	abstract fun getMangaDao(): MangaDao

	abstract fun getFavouritesDao(): FavouritesDao

	abstract fun getPreferencesDao(): PreferencesDao

	abstract fun getFavouriteCategoriesDao(): FavouriteCategoriesDao

	abstract fun getTracksDao(): TracksDao

	abstract fun getTrackLogsDao(): TrackLogsDao

	abstract fun getSuggestionDao(): SuggestionDao

	abstract fun getBookmarksDao(): BookmarksDao

	abstract fun getScrobblingDao(): ScrobblingDao

	abstract fun getSourcesDao(): MangaSourcesDao

	abstract fun getStatsDao(): StatsDao

	abstract fun getLocalMangaIndexDao(): LocalMangaIndexDao

	abstract fun getChaptersDao(): ChaptersDao
}

@Suppress("NO_ACTUAL_FOR_EXPECT")
expect object MangaDatabaseConstructor : RoomDatabaseConstructor<MangaDatabase>

