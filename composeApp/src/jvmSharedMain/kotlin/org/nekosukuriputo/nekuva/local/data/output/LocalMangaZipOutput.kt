package org.nekosukuriputo.nekuva.local.data.output

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.nekosukuriputo.nekuva.core.util.ext.deleteAwait
import org.nekosukuriputo.nekuva.core.zip.ZipOutput
import java.io.File
import java.util.zip.ZipFile

/** SINGLE_CBZ: one `<title>.cbz` whose entries are `<chapterDir>/<page>` images. */
class LocalMangaZipOutput(
    rootFile: File,
) : LocalMangaOutput(rootFile) {

    private val output = ZipOutput(File(rootFile.path + SUFFIX_TMP))
    private val mutex = Mutex()

    override suspend fun mergeWithExisting() = mutex.withLock {
        if (rootFile.exists()) {
            runInterruptible(Dispatchers.IO) {
                ZipFile(rootFile).use { zip ->
                    for (entry in zip.entries()) {
                        output.copyEntryFrom(zip, entry)
                    }
                }
            }
        }
    }

    override suspend fun addPage(chapterDir: String, fileName: String, source: File) = mutex.withLock {
        runInterruptible(Dispatchers.IO) {
            output.put("$chapterDir/$fileName", source)
        }
        Unit
    }

    override suspend fun finish() = mutex.withLock {
        runInterruptible(Dispatchers.IO) {
            output.use { it.finish() }
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

    private companion object {
        const val SUFFIX_TMP = ".tmp"
    }
}
