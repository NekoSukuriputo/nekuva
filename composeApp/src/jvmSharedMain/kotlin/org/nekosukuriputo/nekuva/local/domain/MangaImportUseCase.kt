package org.nekosukuriputo.nekuva.local.domain

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.withContext
import org.nekosukuriputo.nekuva.local.data.LocalStorageManager
import org.nekosukuriputo.nekuva.local.data.hasZipExtension
import org.nekosukuriputo.nekuva.local.data.input.LocalMangaParser
import org.nekosukuriputo.nekuva.local.domain.model.LocalManga
import java.io.File
import java.io.IOException
import java.io.InputStream

/**
 * Import a `.cbz`/`.zip` into the local library (Doki SingleMangaImporter, KMP port). Copies the picked
 * file into the default writeable dir, parses it via [LocalMangaParser], and emits to [localStorageChanges]
 * so the Local list refreshes. Directory import (Doki importDirectory) is deferred — file (CBZ) only for now.
 */
class MangaImportUseCase(
    private val storageManager: LocalStorageManager,
    private val localStorageChanges: MutableSharedFlow<LocalManga?>,
) {

    suspend fun import(name: String, input: InputStream): LocalManga = withContext(Dispatchers.IO) {
        if (!hasZipExtension(name)) {
            throw IOException("Unsupported file: $name (only .cbz/.zip are supported)")
        }
        val dest = File(outputDir(), name)
        dest.outputStream().use { output -> input.copyTo(output) }
        finish(dest)
    }

    /**
     * Import a folder of images (Doki importDirectory). The platform picker supplies [copyContents], which
     * writes the picked tree into the destination dir (java.io on Desktop, DocumentFile/SAF on Android).
     */
    suspend fun importDirectory(name: String, copyContents: suspend (destDir: File) -> Unit): LocalManga =
        withContext(Dispatchers.IO) {
            val dest = File(outputDir(), name)
            dest.mkdirs()
            copyContents(dest)
            finish(dest)
        }

    private suspend fun outputDir(): File = storageManager.getDefaultWriteableDir()
        ?: throw IOException("No writeable storage directory available")

    private suspend fun finish(dest: File): LocalManga {
        val manga = LocalMangaParser(dest).getManga(withDetails = false)
        localStorageChanges.emit(manga)
        return manga
    }
}
