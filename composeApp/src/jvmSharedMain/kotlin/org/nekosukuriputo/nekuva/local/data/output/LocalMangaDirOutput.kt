package org.nekosukuriputo.nekuva.local.data.output

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runInterruptible
import java.io.File

/**
 * MULTIPLE_CBZ: a `<title>/` directory containing `<chapterDir>/<page>` images.
 *
 * Note: Doki's MULTIPLE_CBZ produces one .cbz per chapter; here we emit a folder-per-chapter tree, which
 * Nekuva's [org.nekosukuriputo.nekuva.local.data.input.LocalMangaParser] reads identically. The per-chapter
 * .cbz refinement is deferred (see MIGRATION.md).
 */
class LocalMangaDirOutput(
    rootFile: File,
) : LocalMangaOutput(rootFile) {

    override suspend fun mergeWithExisting() {
        // Directory already persists on disk; new chapters are simply added alongside existing ones.
    }

    override suspend fun addPage(chapterDir: String, fileName: String, source: File) {
        runInterruptible(Dispatchers.IO) {
            val dir = File(rootFile, chapterDir)
            dir.mkdirs()
            source.copyTo(File(dir, fileName), overwrite = true)
        }
    }

    override suspend fun finish() {
        // Files are already written in place.
    }

    override suspend fun cleanup() {
        // Keep whatever chapters finished; partial chapter folders are harmless and re-downloadable.
    }

    override fun close() = Unit
}
