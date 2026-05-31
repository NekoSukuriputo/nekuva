package org.nekosukuriputo.nekuva.core.db

import androidx.room.RoomDatabase
import androidx.sqlite.driver.bundled.BundledSQLiteDriver

expect fun getDatabaseBuilder(): RoomDatabase.Builder<MangaDatabase>

fun getRoomDatabase(
    builder: RoomDatabase.Builder<MangaDatabase>
): MangaDatabase {
    return builder
        .setDriver(BundledSQLiteDriver())
        .addCallback(DatabasePrePopulateCallback())
        .build()
}

