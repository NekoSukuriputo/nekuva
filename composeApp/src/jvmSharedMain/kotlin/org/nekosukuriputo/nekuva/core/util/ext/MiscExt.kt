package org.nekosukuriputo.nekuva.core.util.ext

import org.nekosukuriputo.nekuva.core.util.MimeTypes
import java.net.URI
import java.io.File
import okio.FileSystem
import okio.Path

fun Throwable.printStackTraceDebug() {
    this.printStackTrace()
}

fun String.toFileNameSafe(): String {
    return this.replace("[\\\\/:*?\"<>|]".toRegex(), "_")
}

const val URI_SCHEME_ZIP = "zip"
fun URI.isZipUri() = scheme == URI_SCHEME_ZIP
fun URI.isFileUri() = scheme == "file" || scheme == null

/**
 * Convert a `file:` / `zip:` URI's path part to a real [File], correct on **Windows**.
 *
 * `URI.path` is already percent-decoded, but on Windows a file/zip URI's path is `"/D:/dir/file"` —
 * the leading slash before the drive letter must be stripped, or `File("/D:/...")` resolves to a
 * bogus path and **every local manga silently breaks on Desktop** (no chapters, no cover, no pages).
 * POSIX paths (`/home/...`) are left untouched. See CLAUDE.md §4.6.
 */
fun URI.toFile(): File {
    val raw = this.path ?: this.schemeSpecificPart
    val fixed = if (raw.length >= 3 && raw[0] == '/' && raw[2] == ':') raw.substring(1) else raw
    return File(fixed)
}

fun FileSystem.isDirectory(path: Path) = metadataOrNull(path)?.isDirectory == true
fun FileSystem.isRegularFile(path: Path) = metadataOrNull(path)?.isRegularFile == true

fun <T> Iterable<T>.toListSorted(comparator: java.util.Comparator<in T>): List<T> = toList().sortedWith(comparator)

fun URI.buildUpon(): URIBuilder = URIBuilder(this)

class URIBuilder(val uri: URI) {
    var scheme = uri.scheme
    var fragment = uri.fragment
    var path = uri.path
    fun scheme(s: String) = apply { scheme = s }
    fun fragment(f: String) = apply { fragment = f }
    fun appendEncodedPath(p: String) = apply { 
        val prefix = if (path?.endsWith("/") == true) path else if (path != null) "$path/" else "/"
        path = prefix + p
    }
    fun build(): URI {
        return java.net.URI(scheme, uri.userInfo, uri.host, uri.port, path, uri.query, fragment)
    }
}

fun File.getReadableDirs(): List<File> = listFiles()?.filter { it.isDirectory && it.canRead() } ?: emptyList()
fun File.subdir(name: String): File = File(this, name).apply { mkdirs() }


