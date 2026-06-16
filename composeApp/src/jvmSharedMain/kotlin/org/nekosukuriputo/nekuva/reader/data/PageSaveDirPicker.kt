package org.nekosukuriputo.nekuva.reader.data

/**
 * Picks the default "save page" directory. Android returns a persisted SAF **tree URI** (`content://…`)
 * written to via DocumentsContract; Desktop returns a plain folder path. Null if cancelled/unsupported.
 */
expect suspend fun pickPageSaveDir(): String?

/** Human-readable label for a stored page-save dir value (SAF tree URI or file path). */
fun pageSaveDirLabel(value: String): String = if (value.startsWith("content://")) {
    // Show the decoded tree segment, e.g. "primary:Pictures/Nekuva" → "Pictures/Nekuva".
    val decoded = value.substringAfterLast("/").let { java.net.URLDecoder.decode(it, "UTF-8") }
    decoded.substringAfter(':').ifEmpty { decoded }
} else {
    value
}
