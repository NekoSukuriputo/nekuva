package org.nekosukuriputo.nekuva.core.diagnostics

import android.content.Context
import org.koin.core.context.GlobalContext
import java.io.File

actual fun crashLogDir(): File =
    GlobalContext.get().get<Context>().filesDir.resolve("crash")
