package org.nekosukuriputo.nekuva.local.data

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Environment
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runInterruptible
import okhttp3.Cache
import org.nekosukuriputo.nekuva.core.prefs.AppSettings

import org.nekosukuriputo.nekuva.core.util.ext.isReadable
import java.io.File

private const val DIR_NAME = "manga"

class AndroidLocalStorageManager(
    private val context: Context,
    private val settings: AppSettings
) : LocalStorageManager {

    override suspend fun getReadableDirs(): List<File> = runInterruptible(Dispatchers.IO) {
        getConfiguredStorageDirs().filter { it.isReadable() }
    }

    override suspend fun getWriteableDirs(): List<File> = runInterruptible(Dispatchers.IO) {
        getConfiguredStorageDirs().filter { it.canWrite() }
    }

    override suspend fun getDefaultWriteableDir(): File? = runInterruptible(Dispatchers.IO) {
        val preferredDir = null
        preferredDir ?: getFallbackStorageDir()?.takeIf { it.canWrite() }
    }

    override suspend fun getApplicationStorageDirs(): Set<File> = runInterruptible(Dispatchers.IO) {
        getAvailableStorageDirs()
    }

    override fun isOnExternalStorage(file: File): Boolean {
        return !file.absolutePath.contains(context.packageName)
    }

    override fun hasExternalStoragePermission(isReadOnly: Boolean): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Environment.isExternalStorageManager()
        } else {
            val permission = if (isReadOnly) {
                Manifest.permission.READ_EXTERNAL_STORAGE
            } else {
                Manifest.permission.WRITE_EXTERNAL_STORAGE
            }
            ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
        }
    }

    override suspend fun getDirectoryDisplayName(dir: File, isFullPath: Boolean): String = runInterruptible(Dispatchers.IO) {
        val packageName = context.packageName
        if (dir.absolutePath.contains(packageName)) {
            dir.name
        } else if (isFullPath) {
            dir.path
        } else {
            dir.name
        }
    }

    override fun createHttpCache(): Cache = Cache(File(context.cacheDir, "http_cache"), HTTP_CACHE_SIZE_BYTES)

    override suspend fun computeCacheSize(cache: CacheDir): Long = runInterruptible(Dispatchers.IO) {
        File(context.cacheDir, cache.dir).dirSizeBytes()
    }

    override suspend fun clearCache(cache: CacheDir): Unit = runInterruptible(Dispatchers.IO) {
        File(context.cacheDir, cache.dir).deleteContentsRecursively()
    }

    override fun adblockListFile(): File = File(File(context.cacheDir, "adblock"), "easylist.txt")

    private fun getConfiguredStorageDirs(): MutableSet<File> {
        val set = getAvailableStorageDirs()
        settings.userSpecifiedMangaDirectories.forEach { set += File(it) }
        return set
    }

    private fun getAvailableStorageDirs(): MutableSet<File> {
        val result = LinkedHashSet<File>()
        result += File(context.filesDir, DIR_NAME)
        context.getExternalFilesDirs(DIR_NAME).filterNotNullTo(result)
        result.retainAll { it.exists() || it.mkdirs() }
        return result
    }

    private fun getFallbackStorageDir(): File? {
        return context.getExternalFilesDir(DIR_NAME) ?: File(context.filesDir, DIR_NAME).takeIf {
            it.exists() || it.mkdirs()
        }
    }
}



