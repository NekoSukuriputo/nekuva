package org.nekosukuriputo.nekuva.core.util.ext

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runInterruptible
import java.io.BufferedReader
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipFile

suspend fun File.deleteAwait() = runInterruptible(Dispatchers.IO) {
    delete() || deleteRecursively()
}

fun ZipFile.readText(entry: ZipEntry) = getInputStream(entry).use { output ->
    output.bufferedReader().use(BufferedReader::readText)
}

inline fun <R> File.withChildren(block: (children: Sequence<File>) -> R): R {
    val children = listFiles()?.asSequence() ?: emptySequence()
    return block(children)
}

fun File.takeIfWriteable() = takeIf { canWrite() }
fun File.takeIfReadable() = takeIf { canRead() }
fun File.isWriteable() = canWrite()
fun File.isReadable() = canRead()

suspend fun File.computeSize(): Long = runInterruptible(Dispatchers.IO) {
    walk().filter { it.isFile }.sumOf { it.length() }
}
