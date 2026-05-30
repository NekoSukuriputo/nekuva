package org.nekosukuriputo.nekuva.db

import androidx.room.RoomDatabase
import androidx.room.util.getColumnIndexOrThrow
import androidx.room.util.performSuspending
import androidx.sqlite.SQLiteStatement
import javax.`annotation`.processing.Generated
import kotlin.Int
import kotlin.String
import kotlin.Suppress
import kotlin.collections.List
import kotlin.collections.MutableList
import kotlin.collections.mutableListOf
import kotlin.reflect.KClass

@Generated(value = ["androidx.room.RoomProcessor"])
@Suppress(names = ["UNCHECKED_CAST", "DEPRECATION", "REDUNDANT_PROJECTION", "REMOVAL"])
public class DummyDao_Impl(
  __db: RoomDatabase,
) : DummyDao {
  private val __db: RoomDatabase
  init {
    this.__db = __db
  }

  public override suspend fun getAll(): List<Dummy> {
    val _sql: String = "SELECT * FROM Dummy"
    return performSuspending(__db, true, false) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        val _columnIndexOfId: Int = getColumnIndexOrThrow(_stmt, "id")
        val _result: MutableList<Dummy> = mutableListOf()
        while (_stmt.step()) {
          val _item: Dummy
          val _tmpId: Int
          _tmpId = _stmt.getLong(_columnIndexOfId).toInt()
          _item = Dummy(_tmpId)
          _result.add(_item)
        }
        _result
      } finally {
        _stmt.close()
      }
    }
  }

  public companion object {
    public fun getRequiredConverters(): List<KClass<*>> = emptyList()
  }
}
