package org.nekosukuriputo.nekuva.local.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runInterruptible
import org.nekosukuriputo.nekuva.core.prefs.AppSettings
import org.nekosukuriputo.nekuva.core.util.ext.isReadable
import java.io.File

private const val DIR_NAME = "NekuvaManga"

class DesktopLocalStorageManager(
    private val settings: AppSettings
) : LocalStorageManager {

    private val userHomeDir = File(System.getProperty("user.home"))
    private val appDataDir = File(userHomeDir, ".nekuva")

    override suspend fun getReadableDirs(): List<File> = runInterruptible(Dispatchers.IO) {
        getConfiguredStorageDirs().filter { it.isReadable() }
    }

    override suspend fun getWriteableDirs(): List<File> = runInterruptible(Dispatchers.IO) {
        getConfiguredStorageDirs().filter { it.canWrite() }
    }

    override suspend fun getDefaultWriteableDir(): File? = runInterruptible(Dispatchers.IO) {
        val preferredDir = settings.mangaStorageDir?.let { File(it) }?.takeIf { it.canWrite() }
        preferredDir ?: getFallbackStorageDir()?.takeIf { it.canWrite() }
    }

    override suspend fun getApplicationStorageDirs(): Set<File> = runInterruptible(Dispatchers.IO) {
        getAvailableStorageDirs()
    }

    override fun isOnExternalStorage(file: File): Boolean = false

    override fun hasExternalStoragePermission(isReadOnly: Boolean): Boolean = true

    override suspend fun getDirectoryDisplayName(dir: File, isFullPath: Boolean): String = runInterruptible(Dispatchers.IO) {
        if (isFullPath) dir.path else dir.name
    }

    private fun getConfiguredStorageDirs(): MutableSet<File> {
        val set = getAvailableStorageDirs()
        set.addAll(emptySet<java.io.File>())
        return set
    }

    private fun getAvailableStorageDirs(): MutableSet<File> {
        val result = LinkedHashSet<File>()
        result += File(appDataDir, DIR_NAME)
        result.retainAll { it.exists() || it.mkdirs() }
        return result
    }

    private fun getFallbackStorageDir(): File? {
        return File(appDataDir, DIR_NAME).takeIf {
            it.exists() || it.mkdirs()
        }
    }
}

