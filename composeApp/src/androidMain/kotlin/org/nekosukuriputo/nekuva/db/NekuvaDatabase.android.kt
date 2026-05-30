package org.nekosukuriputo.nekuva.db

import android.content.Context
import androidx.room.Room
import androidx.room.RoomDatabase

actual class AppContext(val context: Context)

actual fun getDatabaseBuilder(ctx: AppContext): RoomDatabase.Builder<NekuvaDatabase> {
    val dbFile = ctx.context.getDatabasePath("nekuva.db")
    return Room.databaseBuilder<NekuvaDatabase>(
        context = ctx.context.applicationContext,
        name = dbFile.absolutePath
    )
}
