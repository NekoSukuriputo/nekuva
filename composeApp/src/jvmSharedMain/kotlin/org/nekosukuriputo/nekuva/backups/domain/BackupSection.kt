package org.nekosukuriputo.nekuva.backups.domain

import java.util.Locale
import java.util.zip.ZipEntry

/** Sections currently supported by Nekuva's backup (the migrated library data). Entry names match Doki. */
enum class BackupSection(val entryName: String) {
    INDEX("index"),
    HISTORY("history"),
    CATEGORIES("categories"),
    FAVOURITES("favourites"),
    BOOKMARKS("bookmarks"),
    SCROBBLING("scrobbling"),
    STATS("statistics"),
    ;

    companion object {
        fun of(entry: ZipEntry): BackupSection? {
            val name = entry.name.lowercase(Locale.ROOT)
            return entries.find { it.entryName == name }
        }
    }
}
