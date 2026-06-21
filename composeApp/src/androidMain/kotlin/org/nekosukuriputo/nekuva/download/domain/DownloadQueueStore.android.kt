package org.nekosukuriputo.nekuva.download.domain

import android.content.Context
import org.koin.core.context.GlobalContext
import java.io.File

actual fun downloadQueueFile(): File =
    GlobalContext.get().get<Context>().filesDir.resolve("downloads_queue.json")
