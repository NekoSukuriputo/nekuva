package org.nekosukuriputo.nekuva.download.ui.dialog

/** Whether this platform can open a native folder chooser for the download destination. */
expect val supportsDirectoryPicker: Boolean

/** Opens a native directory chooser; returns the chosen folder's absolute path, or null if cancelled. */
expect suspend fun pickMangaDirectory(): String?
