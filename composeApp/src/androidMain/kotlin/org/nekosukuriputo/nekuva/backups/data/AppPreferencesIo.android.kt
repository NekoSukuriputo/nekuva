package org.nekosukuriputo.nekuva.backups.data

import android.content.Context
import org.koin.core.context.GlobalContext

actual fun dumpAppPreferences(): Map<String, Any?> =
    GlobalContext.get().get<Context>().getSharedPreferences("nekuva_prefs", Context.MODE_PRIVATE).all
