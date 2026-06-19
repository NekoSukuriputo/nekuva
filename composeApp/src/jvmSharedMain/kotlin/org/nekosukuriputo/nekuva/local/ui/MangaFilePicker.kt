package org.nekosukuriputo.nekuva.local.ui

import androidx.compose.runtime.Composable
import java.io.InputStream

/**
 * Cross-platform "pick a .cbz file + give me its name + stream" bridge for local import (Doki action_import).
 * Desktop = JFileChooser; Android = Storage Access Framework (OpenDocument).
 */
interface MangaFilePicker {
    /** Opens an "open file" dialog; calls [onPicked] with the file name + a fresh InputStream. Returns false if cancelled. */
    suspend fun pickCbz(onPicked: suspend (name: String, input: InputStream) -> Unit): Boolean
}

@Composable
expect fun rememberMangaFilePicker(): MangaFilePicker
