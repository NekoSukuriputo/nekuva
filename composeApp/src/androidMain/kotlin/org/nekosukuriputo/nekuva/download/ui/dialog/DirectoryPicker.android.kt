package org.nekosukuriputo.nekuva.download.ui.dialog

// Android arbitrary-folder selection needs SAF (content:// tree), which the File-based download engine
// can't write to directly — deferred (see MIGRATION.md). The dialog still lists the app's writeable
// storage dirs via LocalStorageManager.getWriteableDirs().
actual val supportsDirectoryPicker: Boolean = false

actual suspend fun pickMangaDirectory(): String? = null
