package org.nekosukuriputo.nekuva.db

import androidx.room.RoomDatabaseConstructor

public actual object NekuvaDatabaseConstructor : RoomDatabaseConstructor<NekuvaDatabase> {
  actual override fun initialize(): NekuvaDatabase =
      org.nekosukuriputo.nekuva.db.NekuvaDatabase_Impl()
}
