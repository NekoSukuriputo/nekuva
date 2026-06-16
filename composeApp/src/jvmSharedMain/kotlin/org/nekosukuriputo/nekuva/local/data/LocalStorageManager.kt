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

    /** Total bytes of ALL app caches (thumbnails + favicons + pages + http + …) — Doki storage usage. */
    suspend fun computeCacheSize(): Long

    /** Total bytes of saved/downloaded manga (Doki "Saved manga" segment). */
    suspend fun computeStorageSize(): Long

    /** Free bytes available on the storage volume (Doki "Available" segment). */
    suspend fun computeAvailableSize(): Long

    /** Deletes everything under [cache]'s directory (Doki data-cleanup). */
    suspend fun clearCache(cache: CacheDir)

    /** File where the downloaded EasyList lives (Doki AdBlock). */
    fun adblockListFile(): File
}
