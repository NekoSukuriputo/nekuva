package org.nekosukuriputo.nekuva.local.data.output

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.nekosukuriputo.nekuva.core.model.isLocal
import org.nekosukuriputo.nekuva.core.util.MimeTypes
import org.nekosukuriputo.nekuva.core.util.ext.deleteAwait
import org.nekosukuriputo.nekuva.core.zip.ZipOutput
import org.nekosukuriputo.nekuva.local.data.MangaIndex
import org.nekosukuriputo.nekuva.parsers.model.Manga
import org.nekosukuriputo.nekuva.parsers.model.MangaChapter
import java.io.File
import java.util.zip.ZipFile

/** SINGLE_CBZ: one `<title>.cbz` with all pages (flat [FILENAME_PATTERN] names) + an index.json. */
class LocalMangaZipOutput(
	rootFile: File,
	manga: Manga,
) : LocalMangaOutput(rootFile) {

	private val output = ZipOutput(File(rootFile.path + SUFFIX_TMP))
	private val index = MangaIndex(null)
	private val mutex = Mutex()

	init {
		if (!manga.isLocal) {
			index.setMangaInfo(manga)
		}
	}

	override suspend fun mergeWithExisting() = mutex.withLock {
		if (rootFile.exists()) {
			runInterruptible(Dispatchers.IO) {
				mergeWith(rootFile)
			}
		}
	}

	override suspend fun addCover(file: File, type: String?) = mutex.withLock {
		val name = buildString {
			append(FILENAME_PATTERN.format(0, 0, 0))
			MimeTypes.getExtensionFromMimeType(type)?.let { ext ->
				append('.')
				append(ext)
			}
		}
		runInterruptible(Dispatchers.IO) {
			output.put(name, file)
		}
		index.setCoverEntry(name)
	}

	override suspend fun addPage(chapter: IndexedValue<MangaChapter>, file: File, pageNumber: Int, type: String?) =
		mutex.withLock {
			val name = buildString {
				append(FILENAME_PATTERN.format(chapter.value.branch.hashCode(), chapter.index + 1, pageNumber))
				MimeTypes.getExtensionFromMimeType(type)?.let { ext ->
					append('.')
					append(ext)
				}
			}
			runInterruptible(Dispatchers.IO) {
				output.put(name, file)
			}
			index.addChapter(chapter, null)
		}

	override suspend fun flushChapter(chapter: MangaChapter): Boolean = false

	override suspend fun finish() = mutex.withLock {
		runInterruptible(Dispatchers.IO) {
			output.use { output ->
				output.put(ENTRY_NAME_INDEX, index.toString())
				output.finish()
			}
		}
		rootFile.deleteAwait()
		output.file.renameTo(rootFile)
		Unit
	}

	override suspend fun cleanup() = mutex.withLock {
		output.file.deleteAwait()
		Unit
	}

	override fun close() {
		output.close()
	}

	private fun mergeWith(other: File) {
		var otherIndex: MangaIndex? = null
		ZipFile(other).use { zip ->
			for (entry in zip.entries()) {
				if (entry.name == ENTRY_NAME_INDEX) {
					otherIndex = MangaIndex(
						zip.getInputStream(entry).use { it.reader().readText() },
					)
				} else {
					output.copyEntryFrom(zip, entry)
				}
			}
		}
		otherIndex?.getMangaInfo()?.chapters?.withIndex()?.let { chapters ->
			for (chapter in chapters) {
				index.addChapter(chapter, null)
			}
		}
	}

	private companion object {
		const val FILENAME_PATTERN = "%08d_%04d%04d"
	}
}
