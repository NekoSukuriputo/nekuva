package org.nekosukuriputo.nekuva.local.data.output

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okio.Closeable
import org.nekosukuriputo.nekuva.core.prefs.DownloadFormat
import org.nekosukuriputo.nekuva.core.util.ext.printStackTraceDebug
import org.nekosukuriputo.nekuva.core.util.ext.toFileNameSafe
import org.nekosukuriputo.nekuva.local.data.input.LocalMangaParser
import org.nekosukuriputo.nekuva.parsers.model.Manga
import org.nekosukuriputo.nekuva.parsers.model.MangaChapter
import org.nekosukuriputo.nekuva.parsers.util.runCatchingCancellable
import java.io.File

/**
 * Writer that produces an offline manga readable by [LocalMangaParser]. Ported from Doki: each output
 * embeds an `index.json` ([org.nekosukuriputo.nekuva.local.data.MangaIndex]) holding the manga metadata
 * and the original remote chapter ids, and pages are stored under a flat [FILENAME_PATTERN] name so a
 * downloaded chapter resolves to the exact same chapter id as its remote counterpart.
 *
 * - [LocalMangaZipOutput] = SINGLE_CBZ: one `<title>.cbz` with all pages + index.json.
 * - [LocalMangaDirOutput] = MULTIPLE_CBZ: a `<title>/` dir with one `.cbz` per chapter + index.json.
 */
sealed class LocalMangaOutput(
	val rootFile: File,
) : Closeable {

	/** Copy chapters from an already-downloaded archive so re-downloading more chapters merges. */
	abstract suspend fun mergeWithExisting()

	/** Store the manga cover. */
	abstract suspend fun addCover(file: File, type: String?)

	/** Store one page image. [pageNumber] is the page's position within [chapter]. */
	abstract suspend fun addPage(chapter: IndexedValue<MangaChapter>, file: File, pageNumber: Int, type: String?)

	/** Finalize a single chapter (DIR output writes its .cbz here); returns false if nothing buffered. */
	abstract suspend fun flushChapter(chapter: MangaChapter): Boolean

	/** Finalize the whole archive on disk. */
	abstract suspend fun finish()

	/** Discard any partial output (called on error/cancel). */
	abstract suspend fun cleanup()

	companion object {

		const val ENTRY_NAME_INDEX = "index.json"
		const val SUFFIX_TMP = ".tmp"

		private val mutex = Mutex()

		suspend fun getOrCreate(
			root: File,
			manga: Manga,
			format: DownloadFormat,
		): LocalMangaOutput = withContext(Dispatchers.IO) {
			val targetFormat = if (format == DownloadFormat.AUTOMATIC) {
				if (manga.chapters.let { it != null && it.size <= 3 }) {
					DownloadFormat.SINGLE_CBZ
				} else {
					DownloadFormat.MULTIPLE_CBZ
				}
			} else {
				format
			}
			checkNotNull(getImpl(root, manga, onlyIfExists = false, format = targetFormat))
		}

		suspend fun get(root: File, manga: Manga): LocalMangaOutput? = withContext(Dispatchers.IO) {
			getImpl(root, manga, onlyIfExists = true, format = DownloadFormat.AUTOMATIC)
		}

		private suspend fun getImpl(
			root: File,
			manga: Manga,
			onlyIfExists: Boolean,
			format: DownloadFormat,
		): LocalMangaOutput? {
			mutex.withLock {
				var i = 0
				val baseName = manga.title.toFileNameSafe()
				while (true) {
					val fileName = if (i == 0) baseName else baseName + "_$i"
					val dir = File(root, fileName)
					val zip = File(root, "$fileName.cbz")
					i++
					return when {
						dir.isDirectory -> if (canWriteTo(dir, manga)) LocalMangaDirOutput(dir, manga) else continue
						zip.isFile -> if (canWriteTo(zip, manga)) LocalMangaZipOutput(zip, manga) else continue
						!onlyIfExists -> when (format) {
							DownloadFormat.AUTOMATIC -> null
							DownloadFormat.SINGLE_CBZ -> LocalMangaZipOutput(zip, manga)
							DownloadFormat.MULTIPLE_CBZ -> LocalMangaDirOutput(dir, manga)
						}

						else -> null
					}
				}
			}
		}

		/** An existing file is writable for [manga] only if its stored index has the same manga id. */
		private suspend fun canWriteTo(file: File, manga: Manga): Boolean {
			val info = runCatchingCancellable {
				LocalMangaParser(file).getMangaInfo()
			}.onFailure {
				it.printStackTraceDebug()
			}.getOrNull() ?: return false
			return info.id == manga.id
		}
	}
}
