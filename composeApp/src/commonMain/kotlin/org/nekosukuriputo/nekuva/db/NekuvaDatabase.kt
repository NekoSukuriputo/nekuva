package org.nekosukuriputo.nekuva.db

import androidx.room.ConstructedBy
import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.RoomDatabaseConstructor

@Database(entities = [Dummy::class], version = 1)
@ConstructedBy(NekuvaDatabaseConstructor::class)
abstract class NekuvaDatabase : RoomDatabase() {
    abstract fun dummyDao(): DummyDao
}

@Suppress("NO_ACTUAL_FOR_EXPECT", "EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")
expect object NekuvaDatabaseConstructor : RoomDatabaseConstructor<NekuvaDatabase> {
    override fun initialize(): NekuvaDatabase
}

expect class AppContext

expect fun getDatabaseBuilder(ctx: AppContext): RoomDatabase.Builder<NekuvaDatabase>
