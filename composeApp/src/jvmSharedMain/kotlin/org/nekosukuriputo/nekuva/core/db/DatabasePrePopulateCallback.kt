package org.nekosukuriputo.nekuva.core.db

import androidx.room.RoomDatabase
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL
import org.nekosukuriputo.nekuva.parsers.model.SortOrder

class DatabasePrePopulateCallback : RoomDatabase.Callback() {

	override fun onCreate(connection: SQLiteConnection) {
		super.onCreate(connection)
		connection.prepare(
			"INSERT INTO favourite_categories (created_at, sort_key, title, `order`, track, show_in_lib, `deleted_at`) VALUES (?,?,?,?,?,?,?)"
		).use { stmt ->
			stmt.bindLong(1, System.currentTimeMillis())
			stmt.bindLong(2, 1L)
			stmt.bindText(3, "Read Later")
			stmt.bindText(4, SortOrder.NEWEST.name)
			stmt.bindLong(5, 1L)
			stmt.bindLong(6, 1L)
			stmt.bindLong(7, 0L)
			stmt.step()
		}
	}
}
