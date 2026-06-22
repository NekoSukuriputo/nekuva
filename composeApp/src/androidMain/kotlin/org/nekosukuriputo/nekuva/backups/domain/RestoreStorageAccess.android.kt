package org.nekosukuriputo.nekuva.backups.domain

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import org.koin.core.context.GlobalContext
import org.nekosukuriputo.nekuva.core.i18n.LocaleActivityHolder
import org.nekosukuriputo.nekuva.core.prefs.AppSettings

actual suspend fun ensureLocalStorageAccessAfterRestore() {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return
    if (Environment.isExternalStorageManager()) return // already granted
    val koin = GlobalContext.get()
    val ctx = koin.get<Context>()
    val settings = koin.get<AppSettings>()
    val pkg = ctx.packageName
    // Did the restore bring in any dir on shared storage (i.e. not an app-private path)?
    val restoredDirs = settings.userSpecifiedMangaDirectories + listOfNotNull(settings.mangaStorageDir)
    val hasSharedStorageDir = restoredDirs.any { it.isNotBlank() && !it.contains(pkg) }
    if (!hasSharedStorageDir) return
    val activity = LocaleActivityHolder.current?.get() ?: return
    runCatching {
        activity.startActivity(
            Intent(
                Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                Uri.parse("package:$pkg"),
            ),
        )
    }
}
