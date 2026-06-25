package org.nekosukuriputo.nekuva.local.data
import java.net.URI



import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.toCollection
import kotlinx.coroutines.launch
import kotlinx.coroutines.runInterruptible
import org.nekosukuriputo.nekuva.core.model.LocalMangaSource
import org.nekosukuriputo.nekuva.core.model.isLocal
import org.nekosukuriputo.nekuva.core.model.isNsfw
import org.nekosukuriputo.nekuva.core.parser.MangaRepository
import org.nekosukuriputo.nekuva.core.prefs.AppSettings
import org.nekosukuriputo.nekuva.core.util.AlphanumComparator
import org.nekosukuriputo.nekuva.core.util.ext.deleteAwait
import org.nekosukuriputo.nekuva.core.util.ext.printStackTraceDebug
import org.nekosukuriputo.nekuva.core.util.ext.takeIfWriteable
import org.nekosukuriputo.nekuva.core.util.ext.withChildren
import org.nekosukuriputo.nekuva.core.util.MimeTypes
import org.nekosukuriputo.nekuva.core.util.ext.toFile
import org.nekosukuriputo.nekuva.local.data.index.LocalMangaIndex
import org.nekosukuriputo.nekuva.local.data.output.LocalMangaOutput.Companion.ENTRY_NAME_INDEX
import org.nekosukuriputo.nekuva.local.data.input.LocalMangaParser
import org.nekosukuriputo.nekuva.local.data.output.LocalMangaOutput
import org.nekosukuriputo.nekuva.local.data.output.LocalMangaUtil
import org.nekosukuriputo.nekuva.local.domain.MangaLock
import org.nekosukuriputo.nekuva.local.domain.model.LocalManga
import org.nekosukuriputo.nekuva.parsers.model.ContentRating
import org.nekosukuriputo.nekuva.parsers.model.Manga
import org.nekosukuriputo.nekuva.parsers.model.MangaChapter
import org.nekosukuriputo.nekuva.parsers.model.MangaListFilter
import org.nekosukuriputo.nekuva.parsers.model.MangaListFilterCapabilities
import org.nekosukuriputo.nekuva.parsers.model.MangaListFilterOptions
import org.nekosukuriputo.nekuva.parsers.model.MangaPage
import org.nekosukuriputo.nekuva.parsers.model.MangaTag
import org.nekosukuriputo.nekuva.parsers.model.SortOrder
import org.nekosukuriputo.nekuva.parsers.util.levenshteinDistance
import org.nekosukuriputo.nekuva.parsers.util.mapToSet
import org.nekosukuriputo.nekuva.parsers.util.runCatchingCancellable
import java.io.File
import java.util.EnumSet



private const val MAX_PARALLELISM = 4
private const val FILENAME_SKIP = ".notamanga"

class LocalMangaRepository constructor(
	private val storageManager: LocalStorageManager,
	private val localMangaIndex: LocalMangaIndex,
	private val localStorageChanges: MutableSharedFlow<LocalManga?>,
	private val settings: AppSettings,
	private val lock: MangaLock,
) : MangaRepository {

	override val source = LocalMangaSource

	@OptIn(org.nekosukuriputo.nekuva.parsers.InternalParsersApi::class)
	override val filterCapabilities: MangaListFilterCapabilities
		get() = MangaListFilterCapabilities(
			isMultipleTagsSupported = true,
			isTagsExclusionSupported = true,
			isSearchSupported = true,
			isSearchWithFiltersSupported = true,
		)

	override val sortOrders: Set<SortOrder> = EnumSet.of(
		SortOrder.ALPHABETICAL,
		SortOrder.RATING,
		SortOrder.NEWEST,
		SortOrder.RELEVANCE,
	)

	override var defaultSortOrder: SortOrder
		get() = settings.localListOrder
		set(value) {
			settings.localListOrder = value
		}

	@OptIn(org.nekosukuriputo.nekuva.parsers.InternalParsersApi::class)
	override suspend fun getFilterOptions(): MangaListFilterOptions {
		// Derive available genres from the ACTUAL scanned library, not localMangaIndex — that index is an
		// in-memory cache populated only via the LocalStorageChanges flow, so it is EMPTY for a library that
		// already exists on disk (nothing emitted yet). Reading it left the quick-filter chips and the filter
		// sheet's genre list blank. Scan, collect distinct tag titles, and map them to LocalMangaSource tags
		// (the local filter matches by title) — mirrors Doki's getFilterOptions.
		val list = getRawList()
		if (settings.isNsfwContentDisabled) {
			list.removeAll { it.manga.isNsfw() }
		}
		val tagTitles = list.flatMap { it.manga.tags?.map { t -> t.title } ?: emptyList() }.distinct()
		return MangaListFilterOptions(
			availableTags = tagTitles.mapToSet { MangaTag(title = it, key = it, source = source) },
			availableContentRating = if (!settings.isNsfwContentDisabled) {
				EnumSet.of(ContentRating.SAFE, ContentRating.ADULT)
			} else {
				emptySet()
			},
		)
	}

	override suspend fun getList(offset: Int, order: SortOrder?, filter: MangaListFilter?): List<Manga> {
		if (offset > 0) {
			return emptyList()
		}
		val list = getRawList()
		if (settings.isNsfwContentDisabled) {
			list.removeAll { it.manga.isNsfw() }
		}
		if (filter != null) {
			val query = filter.query
			if (!query.isNullOrEmpty()) {
				list.retainAll { x -> x.isMatchesQuery(query) }
			}
			if (filter.tags.isNotEmpty()) {
				list.retainAll { x -> x.containsTags(filter.tags.mapToSet { it.title }) }
			}
			if (filter.tagsExclude.isNotEmpty()) {
				list.removeAll { x -> x.containsAnyTag(filter.tagsExclude.mapToSet { it.title }) }
			}
			filter.contentRating.singleOrNull()?.let { contentRating ->
				val isNsfw = contentRating == ContentRating.ADULT
				list.retainAll { it.manga.isNsfw() == isNsfw }
			}
			if (!query.isNullOrEmpty() && order == SortOrder.RELEVANCE) {
				list.sortBy { it.manga.title.levenshteinDistance(query) }
			}
		}
		when (order) {
			SortOrder.ALPHABETICAL -> list.sortWith(compareBy(AlphanumComparator()) { x -> x.manga.title })
			SortOrder.RATING -> list.sortByDescending { it.manga.rating }
			SortOrder.NEWEST,
			SortOrder.UPDATED -> list.sortWith(compareBy({ -it.createdAt }, { it.manga.id }))

			else -> Unit
		}
		// Configured storage dirs can overlap (a manually-added dir nested in/equal to another), so the
		// same manga can be scanned twice. De-duplicate by id so the list has unique entries (and unique
		// LazyColumn keys downstream).
		return list.distinctBy { it.manga.id }.unwrap()
	}

	override suspend fun getDetails(manga: Manga): Manga = when {
		!manga.isLocal -> requireNotNull(findSavedManga(manga, withDetails = true)?.manga) {
			"Manga is not local or saved"
		}

		else -> LocalMangaParser(manga.url.let { URI(it) }).getManga(withDetails = true).manga
	}

	override suspend fun getPages(chapter: MangaChapter): List<MangaPage> {
		return LocalMangaParser(chapter.url.let { URI(it) }).getPages(chapter)
	}

	/**
	 * Offline-first: if [manga] has a downloaded local copy that contains [chapter] (matched by the
	 * original remote chapter id, preserved in index.json), return that chapter's pages from disk;
	 * otherwise null so the caller fetches online. Mirrors Doki reading a downloaded chapter offline.
	 */
	suspend fun getPagesIfDownloaded(manga: Manga, chapter: MangaChapter): List<MangaPage>? {
		if (manga.isLocal) return runCatchingCancellable { getPages(chapter) }.getOrNull()
		val saved = findSavedManga(manga, withDetails = true)?.manga ?: return null
		val localChapter = saved.chapters?.find { it.id == chapter.id } ?: return null
		return runCatchingCancellable { getPages(localChapter) }.getOrNull()
	}

	suspend fun delete(manga: Manga): Boolean {
		val file = URI(manga.url).toFile()
		// deleteRecursively handles both a `.cbz` file and a `<title>/` directory of chapter archives.
		val result = runInterruptible(Dispatchers.IO) { file.deleteRecursively() }
		if (result) {
			localMangaIndex.delete(manga.id)
			localStorageChanges.emit(null)
		}
		return result
	}

	suspend fun deleteChapters(manga: Manga, ids: Set<Long>) = lock.withLock(manga) {
		val subject = if (manga.isLocal) manga else checkNotNull(findSavedManga(manga, withDetails = false)) {
			"Manga is not stored on local storage"
		}.manga
		LocalMangaUtil(subject).deleteChapters(ids)
		val updated = getDetails(subject)
		localStorageChanges.emit(LocalManga(updated))
	}

	suspend fun getRemoteManga(localManga: Manga): Manga? {
		return runCatchingCancellable {
			LocalMangaParser(localManga.url.let { URI(it) }).getMangaInfo()?.takeUnless { it.isLocal }
		}.onFailure {
			it.printStackTraceDebug()
		}.getOrNull()
	}

	suspend fun findSavedManga(remoteManga: Manga, withDetails: Boolean = true): LocalManga? = runCatchingCancellable {
		// very fast path
		localMangaIndex.get(remoteManga.id, withDetails)?.let { cached ->
			return@runCatchingCancellable cached
		}
		// fast path
		LocalMangaParser.find(storageManager.getReadableDirs(), remoteManga)?.let {
			return it.getManga(withDetails)
		}
		// slow path
		val files = getAllFiles()
		return channelFlow {
			for (file in files) {
				launch {
					val mangaInput = LocalMangaParser.getOrNull(file)
					runCatchingCancellable {
						val mangaInfo = mangaInput?.getMangaInfo()
						if (mangaInfo != null && mangaInfo.id == remoteManga.id) {
							send(mangaInput)
						}
					}.onFailure {
						it.printStackTraceDebug()
					}
				}
			}
		}.firstOrNull()?.getManga(withDetails)
	}.onSuccess { x: LocalManga? ->
		if (x != null) {
			localMangaIndex.put(x)
		}
	}.onFailure {
		it.printStackTraceDebug()
	}.getOrNull()

	override suspend fun getPageUrl(page: MangaPage) = page.url

	override suspend fun getRelated(seed: Manga): List<Manga> = emptyList()

	suspend fun getOutputDir(manga: Manga, fallback: File?): File? {
		val defaultDir = fallback?.takeIfWriteable() ?: storageManager.getDefaultWriteableDir()
		if (defaultDir != null && LocalMangaOutput.get(defaultDir, manga) != null) {
			return defaultDir
		}
		return storageManager.getWriteableDirs()
			.firstOrNull {
				LocalMangaOutput.get(it, manga) != null
			} ?: defaultDir
	}

	suspend fun cleanup(): Boolean {
		if (lock.isNotEmpty()) {
			return false
		}
		val dirs = storageManager.getWriteableDirs()
		runInterruptible(Dispatchers.IO) {
			val filter = TempFileFilter()
			dirs.forEach { dir ->
				dir.withChildren { children ->
					children.forEach { child ->
						if (filter.accept(child)) {
							child.deleteRecursively()
						}
					}
				}
			}
		}
		return true
	}

	// Downloaded/local manga ids — Doki's LocalMangaIndex membership for the "saved" badge across lists.
	// Lazily scanned once, then refreshed whenever local storage changes (download finished / deleted).
	private val savedIdsScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
	private val savedIdsFlow: StateFlow<Set<Long>> by lazy {
		val state = MutableStateFlow<Set<Long>>(emptySet())
		savedIdsScope.launch {
			state.value = scanSavedIds()
			localStorageChanges.collect { state.value = scanSavedIds() }
		}
		state.asStateFlow()
	}

	/** Observe the set of downloaded manga ids (initial scan + refresh on storage changes). */
	fun observeSavedIds(): StateFlow<Set<Long>> = savedIdsFlow

	private suspend fun scanSavedIds(): Set<Long> =
		runCatchingCancellable { getRawList().mapToSet { it.manga.id } }.getOrDefault(emptySet())

	fun getRawListAsFlow(): Flow<LocalManga> = channelFlow {
		val files = getAllFiles()
		val dispatcher = Dispatchers.IO.limitedParallelism(MAX_PARALLELISM)
		for (file in files) {
			launch(dispatcher) {
				runCatchingCancellable {
					LocalMangaParser.getOrNull(file)?.getManga(withDetails = false)
				}.onFailure { e ->
					e.printStackTraceDebug()
				}.onSuccess { m ->
					if (m != null) send(m)
				}
			}
		}
	}

	private suspend fun getRawList(): ArrayList<LocalManga> = getRawListAsFlow().toCollection(ArrayList())

	private suspend fun getAllFiles() = storageManager.getReadableDirs()
		.asSequence()
		.flatMap { dir ->
			dir.withChildren { children ->
				children.filter { !it.isHidden && !it.shouldSkip() && it.looksLikeManga() }.toList()
			}
		}

	private fun Collection<LocalManga>.unwrap(): List<Manga> = map { it.manga }

	private fun File.shouldSkip(): Boolean = isDirectory && File(this, FILENAME_SKIP).exists()

	/**
	 * A scanned child is a manga only if it is a `.cbz`/`.zip`, or a directory that is a Nekuva/Doki
	 * download (`index.json`), contains chapter `.cbz` files or page images directly, or has chapter
	 * sub-folders that contain images. The shallow (≤2 level) check keeps non-manga directories — e.g. a
	 * project's `gradle/`, `composeApp/`, `build/`, `.git/` — out of the Local list when a broad folder
	 * is configured as a manga directory.
	 */
	private fun File.looksLikeManga(): Boolean = when {
		isZipArchive -> true
		isDirectory -> {
			val children = listFiles() ?: return false
			children.any { it.name == ENTRY_NAME_INDEX } ||
				children.any { it.isFile && (it.isImageFile() || it.isZipArchive) } ||
				children.any { sub ->
					sub.isDirectory && (sub.listFiles()?.any { it.isFile && it.isImageFile() } == true)
				}
		}

		else -> false
	}

	private fun File.isImageFile(): Boolean =
		MimeTypes.getMimeTypeFromExtension(name)?.startsWith("image/") == true
}



