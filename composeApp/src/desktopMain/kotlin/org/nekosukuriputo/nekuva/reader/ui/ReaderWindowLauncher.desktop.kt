package org.nekosukuriputo.nekuva.reader.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember

/** Desktop `reader_multitask`: opens the reader in a new top-level window (rendered by `Main.kt`). */
@Composable
actual fun rememberReaderWindowLauncher(): ReaderWindowLauncher = remember {
    ReaderWindowLauncher { args ->
        DesktopReaderWindows.open(args)
        true
    }
}
