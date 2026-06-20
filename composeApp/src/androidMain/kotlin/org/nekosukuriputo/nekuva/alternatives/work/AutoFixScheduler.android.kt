package org.nekosukuriputo.nekuva.alternatives.work

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import org.koin.core.context.GlobalContext
import org.nekosukuriputo.nekuva.core.prefs.AppSettings
import java.util.concurrent.TimeUnit

private const val AUTOFIX_WORK = "nekuva_autofix"

/**
 * Android periodic batch auto-fix (Doki AutoFixService): when `auto_fix_broken` is on, runs [AutoFixWorker]
 * once a day on any network; cancels the work when the toggle is off. Idempotent.
 */
actual fun scheduleAutoFix() {
    val koin = GlobalContext.getOrNull() ?: return
    val context = koin.get<Context>()
    val settings = koin.get<AppSettings>()
    val wm = WorkManager.getInstance(context)

    if (!settings.prefBoolean(AppSettings.KEY_AUTOFIX_ENABLED, false)) {
        wm.cancelUniqueWork(AUTOFIX_WORK)
        return
    }
    val constraints = Constraints.Builder()
        .setRequiredNetworkType(NetworkType.CONNECTED)
        .build()
    val request = PeriodicWorkRequestBuilder<AutoFixWorker>(24, TimeUnit.HOURS)
        .setConstraints(constraints)
        .setBackoffCriteria(BackoffPolicy.LINEAR, 1, TimeUnit.HOURS)
        .build()
    wm.enqueueUniquePeriodicWork(AUTOFIX_WORK, ExistingPeriodicWorkPolicy.UPDATE, request)
}
