package org.nekosukuriputo.nekuva.reader.ui

import androidx.compose.runtime.mutableStateListOf

/** One open standalone reader window (Desktop `reader_multitask`). [id] keeps windows distinct in Recents. */
data class ReaderWindowEntry(val id: Long, val args: ReaderArgs)

/**
 * Desktop registry of open reader windows. The launcher adds entries; `Main.kt` renders one `Window` per
 * entry at the application scope (Doki `reader_multitask` — each manga in its own window).
 */
object DesktopReaderWindows {
    private var counter = 0L
    val windows = mutableStateListOf<ReaderWindowEntry>()

    fun open(args: ReaderArgs) {
        windows.add(ReaderWindowEntry(counter++, args))
    }

    fun close(entry: ReaderWindowEntry) {
        windows.remove(entry)
    }
}
