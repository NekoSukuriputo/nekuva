package org.nekosukuriputo.nekuva.core.db

import androidx.room.Room
import androidx.room.RoomDatabase
import java.io.File

actual fun getDatabaseBuilder(): RoomDatabase.Builder<MangaDatabase> {
    val dbFile = File(System.getProperty("java.io.tmpdir"), "nekuva-db.db")
    return Room.databaseBuilder<MangaDatabase>(
        name = dbFile.absolutePath,
    )
}
