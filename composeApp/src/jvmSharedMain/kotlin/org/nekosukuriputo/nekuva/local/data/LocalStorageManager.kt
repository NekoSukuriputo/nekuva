package org.nekosukuriputo.nekuva.local.data

import java.io.File

interface LocalStorageManager {
    suspend fun getReadableDirs(): List<File>
    suspend fun getWriteableDirs(): List<File>
    suspend fun getDefaultWriteableDir(): File?
    suspend fun getApplicationStorageDirs(): Set<File>
    fun isOnExternalStorage(file: File): Boolean
    fun hasExternalStoragePermission(isReadOnly: Boolean): Boolean
    suspend fun getDirectoryDisplayName(dir: File, isFullPath: Boolean = false): String
}
