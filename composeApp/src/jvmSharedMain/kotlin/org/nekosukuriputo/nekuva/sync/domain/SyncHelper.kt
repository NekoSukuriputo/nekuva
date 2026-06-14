package org.nekosukuriputo.nekuva.sync.domain

import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.nekosukuriputo.nekuva.core.db.MangaDatabase
import org.nekosukuriputo.nekuva.core.db.TABLE_FAVOURITES
import org.nekosukuriputo.nekuva.core.db.TABLE_HISTORY
import org.nekosukuriputo.nekuva.core.db.withTransactionKmp
import org.nekosukuriputo.nekuva.parsers.util.await
import org.nekosukuriputo.nekuva.sync.data.SyncSettings
import org.nekosukuriputo.nekuva.sync.data.model.FavouriteCategorySyncDto
import org.nekosukuriputo.nekuva.sync.data.model.FavouriteSyncDto
import org.nekosukuriputo.nekuva.sync.data.model.HistorySyncDto
import org.nekosukuriputo.nekuva.sync.data.model.MangaSyncDto
import org.nekosukuriputo.nekuva.sync.data.model.SyncDto
import org.nekosukuriputo.nekuva.sync.data.toEntity
import org.nekosukuriputo.nekuva.sync.data.toSyncDto
import java.net.HttpURLConnection
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

/**
 * Performs one favourites / history sync round-trip and merges the server response into Room.
 * KMP port of Doki's `SyncHelper`: instead of an Android `ContentProviderClient` it talks to the
 * Room DAOs directly, and instead of `ContentProviderOperation` batches it upserts entities.
 *
 * The [httpClient] is expected to already carry the [org.nekosukuriputo.nekuva.sync.data.SyncInterceptor]
 * (auth + version headers) and [org.nekosukuriputo.nekuva.sync.data.SyncAuthenticator] (401 refresh).
 */
@OptIn(ExperimentalTime::class)
class SyncHelper(
	private val httpClient: OkHttpClient,
	private val db: MangaDatabase,
	private val settings: SyncSettings,
) {

	suspend fun syncFavourites() {
		val now = Clock.System.now().toEpochMilliseconds()
		val payload = json.encodeToString(
			SyncDto(
				history = null,
				favourites = getFavourites(),
				categories = getFavouriteCategories(),
				timestamp = now,
			),
		)
		val response = post(TABLE_FAVOURITES, payload)
		if (response != null) {
			db.withTransactionKmp {
				response.categories?.let { upsertCategories(it) }
				response.favourites?.let { upsertFavourites(it) }
			}
		}
		gcFavourites(now)
		settings.lastSyncFavourites = now
	}

	suspend fun syncHistory() {
		val now = Clock.System.now().toEpochMilliseconds()
		val payload = json.encodeToString(
			SyncDto(
				history = getHistory(),
				favourites = null,
				categories = null,
				timestamp = now,
			),
		)
		val response = post(TABLE_HISTORY, payload)
		if (response != null) {
			db.withTransactionKmp {
				response.history?.let { upsertHistory(it) }
			}
		}
		gcHistory(now)
		settings.lastSyncHistory = now
	}

	// --- read local -> DTO -------------------------------------------------------------------

	private suspend fun getFavourites(): List<FavouriteSyncDto> {
		val mangaDao = db.getMangaDao()
		return db.getFavouritesDao().findAllForSync().mapNotNull { fav ->
			val manga = mangaDao.find(fav.mangaId)?.toSyncDto() ?: return@mapNotNull null
			fav.toSyncDto(manga)
		}
	}

	private suspend fun getFavouriteCategories(): List<FavouriteCategorySyncDto> =
		db.getFavouriteCategoriesDao().findAllForSync().map { it.toSyncDto() }

	private suspend fun getHistory(): List<HistorySyncDto> {
		val mangaDao = db.getMangaDao()
		return db.getHistoryDao().findAllForSync().mapNotNull { history ->
			val manga = mangaDao.find(history.mangaId)?.toSyncDto() ?: return@mapNotNull null
			history.toSyncDto(manga)
		}
	}

	// --- merge server -> local ---------------------------------------------------------------

	private suspend fun upsertCategories(categories: List<FavouriteCategorySyncDto>) {
		val dao = db.getFavouriteCategoriesDao()
		for (dto in categories) {
			dao.upsert(dto.toEntity())
		}
	}

	private suspend fun upsertFavourites(favourites: List<FavouriteSyncDto>) {
		val favDao = db.getFavouritesDao()
		for (dto in favourites) {
			upsertManga(dto.manga)
			favDao.upsert(dto.toEntity())
		}
	}

	private suspend fun upsertHistory(history: List<HistorySyncDto>) {
		val historyDao = db.getHistoryDao()
		for (dto in history) {
			upsertManga(dto.manga)
			// Verbatim upsert: the regular HistoryDao.upsert() forces deleted_at = 0, which would
			// resurrect a tombstone coming from the server. upsertForSync writes deleted_at as-is.
			historyDao.upsertForSync(dto.toEntity())
		}
	}

	private suspend fun upsertManga(dto: MangaSyncDto) {
		val tags = dto.tags.map { it.toEntity() }
		db.getTagsDao().upsert(tags)
		db.getMangaDao().upsert(dto.toEntity(), tags)
	}

	// --- gc ----------------------------------------------------------------------------------

	private suspend fun gcFavourites(now: Long) {
		val deadline = now - GC_PERIOD_MS
		db.getFavouritesDao().gc(deadline)
		db.getFavouriteCategoriesDao().gc(deadline)
	}

	private suspend fun gcHistory(now: Long) {
		db.getHistoryDao().gc(now - GC_PERIOD_MS)
	}

	// --- http --------------------------------------------------------------------------------

	private suspend fun post(table: String, payload: String): SyncDto? {
		val request = Request.Builder()
			.url("${settings.host}/resource/$table")
			.post(payload.toRequestBody(MEDIA_TYPE_JSON))
			.build()
		val response = httpClient.newCall(request).await()
		val raw = response.body.string()
		return when {
			!response.isSuccessful -> throw SyncApiException(raw, response.code)
			response.code == HttpURLConnection.HTTP_NO_CONTENT || raw.isBlank() -> null
			else -> json.decodeFromString<SyncDto>(raw)
		}
	}

	private companion object {
		val MEDIA_TYPE_JSON = "application/json".toMediaType()
		val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
		const val GC_PERIOD_MS = 4L * 24 * 60 * 60 * 1000 // 4 days (Doki defaultGcPeriod when sync enabled)
	}
}
