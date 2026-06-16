package org.nekosukuriputo.nekuva.local.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runInterruptible
import okhttp3.Cache
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

    override fun createHttpCache(): Cache = Cache(File(appDataDir, "http_cache"), HTTP_CACHE_SIZE_BYTES)

    override suspend fun computeCacheSize(cache: CacheDir): Long = runInterruptible(Dispatchers.IO) {
        File(appDataDir, cache.dir).dirSizeBytes()
    }

    override suspend fun clearCache(cache: CacheDir): Unit = runInterruptible(Dispatchers.IO) {
        File(appDataDir, cache.dir).deleteContentsRecursively()
    }

    // Desktop appDataDir mixes caches + saved manga + KCEF, so sum ONLY the known cache subdirs.
    override suspend fun computeCacheSize(): Long = runInterruptible(Dispatchers.IO) {
        listOf("image_cache", "favicons", CacheDir.PAGES.dir, "http_cache", "adblock")
            .sumOf { File(appDataDir, it).dirSizeBytes() }
    }

    override suspend fun computeStorageSize(): Long = runInterruptible(Dispatchers.IO) {
        getAvailableStorageDirs().sumOf { it.dirSizeBytes() }
    }

    override suspend fun computeAvailableSize(): Long = runInterruptible(Dispatchers.IO) {
        runCatching { appDataDir.usableSpace }.getOrDefault(0L)
    }

    override fun adblockListFile(): File = File(File(appDataDir, "adblock"), "easylist.txt")

    private fun getConfiguredStorageDirs(): MutableSet<File> {
        val set = getAvailableStorageDirs()
        // User-picked download folders (persisted) so they show in the picker and their manga appear in Local.
        settings.userSpecifiedMangaDirectories.forEach { set += File(it) }
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

