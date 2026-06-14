package org.nekosukuriputo.nekuva.history.data

import org.nekosukuriputo.nekuva.core.db.withTransactionKmp
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import org.nekosukuriputo.nekuva.core.db.MangaDatabase
import org.nekosukuriputo.nekuva.core.db.entity.toEntity
import org.nekosukuriputo.nekuva.core.db.entity.toManga
import org.nekosukuriputo.nekuva.core.db.entity.toMangaList
import org.nekosukuriputo.nekuva.core.db.entity.toMangaTags
import org.nekosukuriputo.nekuva.core.db.entity.toMangaTagsList
import org.nekosukuriputo.nekuva.core.model.MangaHistory
import org.nekosukuriputo.nekuva.core.model.isLocal
import org.nekosukuriputo.nekuva.core.model.isNsfw
import org.nekosukuriputo.nekuva.core.model.toMangaSources
import org.nekosukuriputo.nekuva.core.parser.MangaDataRepository
import org.nekosukuriputo.nekuva.core.prefs.AppSettings
import org.nekosukuriputo.nekuva.core.prefs.ProgressIndicatorMode
import org.nekosukuriputo.nekuva.history.domain.model.MangaWithHistory
import org.nekosukuriputo.nekuva.list.domain.ListFilterOption
import org.nekosukuriputo.nekuva.list.domain.ListSortOrder
import org.nekosukuriputo.nekuva.list.domain.ReadingProgress
import org.nekosukuriputo.nekuva.parsers.model.Manga
import org.nekosukuriputo.nekuva.parsers.model.MangaSource
import org.nekosukuriputo.nekuva.parsers.model.MangaTag
import org.nekosukuriputo.nekuva.parsers.util.findById
import org.nekosukuriputo.nekuva.parsers.util.levenshteinDistance
import kotlin.time.Clock

fun HistoryEntity.toMangaHistory() = MangaHistory(
	createdAt = createdAt,
	updatedAt = updatedAt,
	chapterId = chapterId,
	page = page,
	scroll = scroll.toInt(),
	percent = percent,
	chaptersCount = chaptersCount,
)

@OptIn(kotlin.time.ExperimentalTime::class)
class HistoryRepository(
	private val db: MangaDatabase,
	private val settings: AppSettings,
	private val mangaRepository: MangaDataRepository,
) {

	suspend fun getList(offset: Int, limit: Int): List<Manga> {
		val entities = db.getHistoryDao().findAll(offset, limit)
		return entities.map { it.toManga() }
	}

	/** Read-only title search over read history (for global search). */
	suspend fun search(query: String, limit: Int): List<Manga> {
		return db.getHistoryDao().searchByTitle("%$query%", limit).toMangaList()
	}

	suspend fun getLastOrNull(): Manga? {
		val entity = db.getHistoryDao().findAll(0, 1).firstOrNull() ?: return null
		return entity.toManga()
	}

	fun observeLast(): Flow<Manga?> {
		return db.getHistoryDao().observeAll(1).map {
			val first = it.firstOrNull()
			first?.toManga()
		}
	}

	fun observeAll(): Flow<List<Manga>> {
		return db.getHistoryDao().observeAll().map { list -> list.map { it.toManga() } }
	}

	fun observeAll(limit: Int): Flow<List<Manga>> {
		return db.getHistoryDao().observeAll(limit).map { list -> list.map { it.toManga() } }
	}

	fun observeAllWithHistory(
		order: ListSortOrder,
		filterOptions: Set<ListFilterOption>,
		limit: Int
	): Flow<List<MangaWithHistory>> {
		return db.getHistoryDao().observeAll(order, filterOptions, limit).map { list ->
			list.map {
				MangaWithHistory(
					it.toManga(),
					it.history.toMangaHistory(),
				)
			}
		}
	}

	fun observeOne(id: Long): Flow<MangaHistory?> {
		return db.getHistoryDao().observe(id).map {
			it?.toMangaHistory()
		}
	}

	/** mangaId -> read percent, for reading indicators on any manga list (Doki parity). */
	fun observeProgressMap(): Flow<Map<Long, Float>> =
		db.getHistoryDao().observeProgress().map { rows -> rows.associate { it.mangaId to it.percent } }

	suspend fun addOrUpdate(manga: Manga, chapterId: Long, page: Int, scroll: Int, percent: Float, force: Boolean) {
		if (!force && shouldSkip(manga)) {
			return
		}
		db.withTransactionKmp {
			mangaRepository.storeManga(manga, replaceExisting = true)
			val branch = manga.chapters?.findById(chapterId)?.branch
			db.getHistoryDao().upsert(
				HistoryEntity(
					mangaId = manga.id,
					createdAt = Clock.System.now().toEpochMilliseconds(),
					updatedAt = Clock.System.now().toEpochMilliseconds(),
					chapterId = chapterId,
					page = page,
					scroll = scroll.toFloat(),
					percent = percent,
					chaptersCount = manga.chapters?.count { it.branch == branch } ?: 0,
					deletedAt = 0L,
				),
			)
			// CheckNewChaptersUseCase and Scrobbler can be added here later
		}
	}

	suspend fun getOne(manga: Manga): MangaHistory? {
		return db.getHistoryDao().find(manga.id)?.recoverIfNeeded(manga)?.toMangaHistory()
	}

	suspend fun getProgress(mangaId: Long, mode: ProgressIndicatorMode): ReadingProgress? {
		val entity = db.getHistoryDao().find(mangaId) ?: return null
		val fixedPercent = if (ReadingProgress.isCompleted(entity.percent)) 1f else entity.percent
		return ReadingProgress(
			percent = fixedPercent,
			totalChapters = entity.chaptersCount,
			mode = mode,
		).takeIf { it.isValid() }
	}

	suspend fun clear() {
		db.getHistoryDao().clear()
	}

	suspend fun delete(manga: Manga) {
		db.withTransactionKmp {
			db.getHistoryDao().delete(manga.id)
			mangaRepository.gcChaptersCache()
		}
	}

	suspend fun deleteAfter(minDate: Long) {
		db.withTransactionKmp {
			db.getHistoryDao().deleteAfter(minDate)
			mangaRepository.gcChaptersCache()
		}
	}

	suspend fun deleteNotFavorite() {
		db.withTransactionKmp {
			db.getHistoryDao().deleteNotFavorite()
			mangaRepository.gcChaptersCache()
		}
	}

	suspend fun delete(ids: Collection<Long>) {
		db.withTransactionKmp {
			for (id in ids) {
				db.getHistoryDao().delete(id)
			}
			mangaRepository.gcChaptersCache()
		}
	}

	suspend fun deleteOrSwap(manga: Manga, alternative: Manga?) {
		if (alternative == null || db.getMangaDao().update(alternative.toEntity()) <= 0) {
			delete(manga)
		}
	}

	suspend fun getPopularTags(limit: Int): List<MangaTag> {
		return db.getHistoryDao().findPopularTags(limit).toMangaTagsList()
	}

	suspend fun getPopularSources(limit: Int): List<MangaSource> {
		return db.getHistoryDao().findPopularSources(limit).toMangaSources()
	}

	fun shouldSkip(manga: Manga): Boolean = settings.isIncognitoModeEnabled(manga.isNsfw())

	fun observeShouldSkip(manga: Manga): Flow<Boolean> {
		return kotlinx.coroutines.flow.flowOf(shouldSkip(manga))
	}

	private suspend fun recover(ids: Collection<Long>) {
		db.withTransactionKmp {
			for (id in ids) {
				db.getHistoryDao().recover(id)
			}
		}
	}

	private suspend fun HistoryEntity.recoverIfNeeded(manga: Manga): HistoryEntity {
		val chapters = manga.chapters
		if (manga.isLocal || chapters.isNullOrEmpty() || chapters.findById(chapterId) != null) {
			return this
		}
		val newChapterId = chapters.getOrNull(
			(chapters.size * percent).toInt(),
		)?.id ?: return this
		val newEntity = copy(chapterId = newChapterId)
		db.getHistoryDao().update(newEntity)
		return newEntity
	}

	private fun HistoryWithManga.toManga() = manga.toManga(tags.toMangaTags(), null)
}
