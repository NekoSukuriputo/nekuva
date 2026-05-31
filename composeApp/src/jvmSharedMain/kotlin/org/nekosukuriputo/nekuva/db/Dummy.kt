package org.nekosukuriputo.nekuva.db

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Dao
import androidx.room.Query

@Entity
data class Dummy(
    @PrimaryKey(autoGenerate = true) val id: Int = 0
)

@Dao
interface DummyDao {
    @Query("SELECT * FROM Dummy")
    suspend fun getAll(): List<Dummy>
}
