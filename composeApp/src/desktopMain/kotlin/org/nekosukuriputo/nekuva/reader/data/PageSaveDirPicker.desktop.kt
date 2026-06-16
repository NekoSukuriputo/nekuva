package org.nekosukuriputo.nekuva.reader.data

/** Desktop: reuse the folder chooser — page-save uses a plain path (DesktopPagePersister writes there). */
actual suspend fun pickPageSaveDir(): String? =
    org.nekosukuriputo.nekuva.download.ui.dialog.pickMangaDirectory()
