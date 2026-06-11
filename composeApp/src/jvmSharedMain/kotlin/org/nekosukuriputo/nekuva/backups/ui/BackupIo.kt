package org.nekosukuriputo.nekuva.backups.ui

import androidx.compose.runtime.Composable
import java.io.InputStream
import java.io.OutputStream

/**
 * Cross-platform "pick a file + give me its stream" bridge for backup/restore.
 * Desktop = JFileChooser; Android = Storage Access Framework.
 */
interface BackupIo {
    /** Opens a "save file" dialog, then calls [writer] with the output stream. Returns false if cancelled. */
    suspend fun pickAndWrite(defaultName: String, writer: suspend (OutputStream) -> Unit): Boolean

    /** Opens an "open file" dialog, then calls [reader] with the input stream. Returns false if cancelled. */
    suspend fun pickAndRead(reader: suspend (InputStream) -> Unit): Boolean
}

@Composable
expect fun rememberBackupIo(): BackupIo
