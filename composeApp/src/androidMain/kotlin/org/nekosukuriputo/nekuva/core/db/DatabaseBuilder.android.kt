package org.nekosukuriputo.nekuva.core.db

import android.content.Context
import androidx.room.Room
import androidx.room.RoomDatabase
import org.koin.core.context.GlobalContext

actual fun getDatabaseBuilder(): RoomDatabase.Builder<MangaDatabase> {
    val appContext = GlobalContext.get().get<Context>()
    val dbFile = appContext.getDatabasePath("nekuva-db.db")
    return Room.databaseBuilder<MangaDatabase>(
        context = appContext,
        name = dbFile.absolutePath
    )
}

