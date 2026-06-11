package org.nekosukuriputo.nekuva.core.image

import coil3.ImageLoader
import coil3.Uri
import coil3.decode.DataSource
import coil3.decode.ImageSource
import coil3.fetch.FetchResult
import coil3.fetch.Fetcher
import coil3.fetch.SourceFetchResult
import coil3.request.Options
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runInterruptible
import okio.Buffer
import okio.FileSystem
import okio.Path.Companion.toOkioPath
import okio.Path.Companion.toPath
import okio.buffer
import okio.openZip
import org.nekosukuriputo.nekuva.core.util.ext.toFile

/**
 * Loads local manga page/cover images that Coil can't fetch by default:
 * - `zip:/path/Manga.cbz#chapter/page.jpg` — an entry inside a .cbz.
 * - `file:/path/Manga/chapter/page.jpg` — a plain file.
 *
 * Mirrors how [org.nekosukuriputo.nekuva.local.data.input.LocalMangaParser] builds the URIs.
 * Uses [toFile] so Windows drive-letter paths (`/D:/...`) resolve correctly — see CLAUDE.md §4.6.
 */
class LocalImageFetcher(
    private val uri: Uri,
) : Fetcher {

    override suspend fun fetch(): FetchResult? = runInterruptible(Dispatchers.IO) {
        // java.net.URI parses the scheme reliably (coil3.Uri does not for single-slash file: URIs).
        val u = java.net.URI(uri.toString())
        when (u.scheme) {
            "zip" -> {
                val entry = u.fragment ?: return@runInterruptible null
                val zipPath = u.toFile().toOkioPath()
                val entryPath = (if (entry.startsWith("/")) entry else "/$entry").toPath()
                val buffer = Buffer()
                FileSystem.SYSTEM.openZip(zipPath).source(entryPath).buffer().use { it.readAll(buffer) }
                SourceFetchResult(
                    source = ImageSource(source = buffer, fileSystem = FileSystem.SYSTEM),
                    mimeType = null,
                    dataSource = DataSource.DISK,
                )
            }
            "file", null -> {
                SourceFetchResult(
                    source = ImageSource(file = u.toFile().toOkioPath(), fileSystem = FileSystem.SYSTEM),
                    mimeType = null,
                    dataSource = DataSource.DISK,
                )
            }
            else -> null
        }
    }

    class Factory : Fetcher.Factory<Uri> {
        override fun create(data: Uri, options: Options, imageLoader: ImageLoader): Fetcher? {
            // Match on the raw string: coil3.Uri.scheme is unreliable for "file:/D:/..." (single slash).
            val s = data.toString()
            return if (s.startsWith("zip:") || s.startsWith("file:")) LocalImageFetcher(data) else null
        }
    }
}
