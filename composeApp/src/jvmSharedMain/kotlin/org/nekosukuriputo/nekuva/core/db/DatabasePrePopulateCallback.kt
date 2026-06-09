package org.nekosukuriputo.nekuva.core.db

import androidx.room.RoomDatabase
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL
import org.nekosukuriputo.nekuva.parsers.model.SortOrder

class DatabasePrePopulateCallback : RoomDatabase.Callback() {

	override fun onCreate(connection: SQLiteConnection) {
		super.onCreate(connection)
		// Seed the special "Default" category as a REAL row with the stable id 0, so favourites added
		// to "Default" satisfy the category_id foreign key. Explicit id 0 (not @Insert) because Room's
		// autoGenerate PK would otherwise reassign it. It is hidden from the category list queries.
		connection.prepare(
			"INSERT OR IGNORE INTO favourite_categories (category_id, created_at, sort_key, title, `order`, track, show_in_lib, `deleted_at`) VALUES (0,?,0,?,?,0,1,0)"
		).use { stmt ->
			stmt.bindLong(1, System.currentTimeMillis())
			stmt.bindText(2, "Default")
			stmt.bindText(3, SortOrder.NEWEST.name)
			stmt.step()
		}
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
