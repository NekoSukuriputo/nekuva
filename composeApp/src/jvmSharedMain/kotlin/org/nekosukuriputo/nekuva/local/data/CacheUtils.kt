package org.nekosukuriputo.nekuva.local.data

import java.io.File

/** Total bytes of all files under this directory (0 if it doesn't exist). */
internal fun File.dirSizeBytes(): Long =
	if (!exists()) 0L else walkTopDown().filter { it.isFile }.sumOf { it.length() }

/** Deletes everything inside this directory but keeps the directory itself. */
internal fun File.deleteContentsRecursively() {
	if (exists()) listFiles()?.forEach { it.deleteRecursively() }
}

/** Size budget for the shared OkHttp disk cache (Doki uses a similar bound). */
internal const val HTTP_CACHE_SIZE_BYTES = 64L * 1024 * 1024
