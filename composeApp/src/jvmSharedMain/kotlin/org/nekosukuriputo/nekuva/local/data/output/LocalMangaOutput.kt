package org.nekosukuriputo.nekuva.local.data.output

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okio.Closeable
import org.nekosukuriputo.nekuva.core.prefs.DownloadFormat
import org.nekosukuriputo.nekuva.core.util.ext.toFileNameSafe
import org.nekosukuriputo.nekuva.parsers.model.Manga
import java.io.File

/**
 * Writer that produces an offline manga readable by [org.nekosukuriputo.nekuva.local.data.input.LocalMangaParser].
 *
 * Nekuva's parser is *structure based* (chapters = sub-folders of images inside a .cbz, or sub-folders
 * inside a plain directory) — it does NOT read an index.json, so the layout we emit is intentionally
 * simpler than Doki's. Pages/chapters are named with zero-padded prefixes so the parser's alphanumeric
 * sort keeps the right order.
 */
sealed class LocalMangaOutput(
    val rootFile: File,
) : Closeable {

    /** Copy chapters from an already-downloaded archive so re-downloading more chapters merges instead of replacing. */
    abstract suspend fun mergeWithExisting()

    /** Store one page image. [chapterDir]/[fileName] become the entry path; [source] is the downloaded temp file. */
    abstract suspend fun addPage(chapterDir: String, fileName: String, source: File)

    /** Finalize the archive on disk. */
    abstract suspend fun finish()

    /** Discard any partial output (called on error/cancel). */
    abstract suspend fun cleanup()

    companion object {

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
        ): LocalMangaOutput? = mutex.withLock {
            val baseName = manga.title.toFileNameSafe()
            val dir = File(root, baseName)
            val zip = File(root, "$baseName.cbz")
            when {
                dir.isDirectory -> LocalMangaDirOutput(dir)
                zip.isFile -> LocalMangaZipOutput(zip)
                onlyIfExists -> null
                format == DownloadFormat.MULTIPLE_CBZ -> LocalMangaDirOutput(dir)
                else -> LocalMangaZipOutput(zip) // SINGLE_CBZ / AUTOMATIC fallback
            }
        }
    }
}
