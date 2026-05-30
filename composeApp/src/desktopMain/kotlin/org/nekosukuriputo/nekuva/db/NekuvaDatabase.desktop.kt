package org.nekosukuriputo.nekuva.db

import androidx.room.Room
import androidx.room.RoomDatabase
import java.io.File

actual class AppContext

actual fun getDatabaseBuilder(ctx: AppContext): RoomDatabase.Builder<NekuvaDatabase> {
    val dbFile = File(System.getProperty("java.io.tmpdir"), "nekuva.db")
    return Room.databaseBuilder<NekuvaDatabase>(name = dbFile.absolutePath)
}
