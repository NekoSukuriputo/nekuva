package org.nekosukuriputo.nekuva.local.ui

import androidx.compose.runtime.Composable
import java.io.File
import java.io.InputStream

/**
 * Cross-platform local-import picker (Doki action_import). Desktop = JFileChooser; Android = Storage Access
 * Framework (OpenDocument / OpenDocumentTree).
 */
interface MangaFilePicker {
    /** Opens an "open file" dialog; calls [onPicked] with the file name + a fresh InputStream. Returns false if cancelled. */
    suspend fun pickCbz(onPicked: suspend (name: String, input: InputStream) -> Unit): Boolean

    /**
     * Opens a "pick folder" dialog; calls [onPicked] with the folder name + a [copyInto] that copies the
     * picked tree into a destination dir (platform-specific: java.io on Desktop, DocumentFile on Android).
     * Returns false if cancelled.
     */
    suspend fun pickDirectory(onPicked: suspend (name: String, copyInto: suspend (destDir: File) -> Unit) -> Unit): Boolean
}

@Composable
expect fun rememberMangaFilePicker(): MangaFilePicker
