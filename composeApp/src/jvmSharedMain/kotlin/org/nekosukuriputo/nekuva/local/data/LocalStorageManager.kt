package org.nekosukuriputo.nekuva.local.data

import okhttp3.Cache
import java.io.File

interface LocalStorageManager {
    suspend fun getReadableDirs(): List<File>
    suspend fun getWriteableDirs(): List<File>
    suspend fun getDefaultWriteableDir(): File?
    suspend fun getApplicationStorageDirs(): Set<File>
    fun isOnExternalStorage(file: File): Boolean
    fun hasExternalStoragePermission(isReadOnly: Boolean): Boolean
    suspend fun getDirectoryDisplayName(dir: File, isFullPath: Boolean = false): String

    /** Shared OkHttp disk cache (Doki LocalStorageManager.createHttpCache) — clearable from Data removal. */
    fun createHttpCache(): Cache

    /** Bytes used by [cache] under the app cache root (Doki data-cleanup sizes). */
    suspend fun computeCacheSize(cache: CacheDir): Long

    /** Deletes everything under [cache]'s directory (Doki data-cleanup). */
    suspend fun clearCache(cache: CacheDir)
}
