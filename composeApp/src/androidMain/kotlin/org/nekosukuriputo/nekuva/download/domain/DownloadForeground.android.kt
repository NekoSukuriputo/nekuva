package org.nekosukuriputo.nekuva.download.domain

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import org.koin.core.context.GlobalContext
import org.nekosukuriputo.nekuva.download.service.DownloadService

/** Start the download foreground service (idempotent — onStartCommand no-ops if already running). */
actual fun ensureDownloadForeground() {
    val ctx = GlobalContext.get().get<Context>()
    // May throw ForegroundServiceStartNotAllowedException if called from the background (Android 12+);
    // downloads still run in-process, so just skip the notification in that case.
    runCatching {
        ContextCompat.startForegroundService(ctx, Intent(ctx, DownloadService::class.java))
    }
}
