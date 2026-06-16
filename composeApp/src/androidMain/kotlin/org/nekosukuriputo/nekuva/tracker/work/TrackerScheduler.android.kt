package org.nekosukuriputo.nekuva.tracker.work

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

private const val TRACKER_WORK = "nekuva_tracker"

/**
 * Android background tracker (Doki TrackWorker.Scheduler): schedules a periodic [TrackerWorker] whose
 * interval comes from `tracker_freq` and whose network constraint comes from `tracker_wifi`. Cancels the
 * work when the tracker is disabled or set to "Manual". Idempotent — safe to call on every app start.
 */
actual fun scheduleTracker() {
    val koin = GlobalContext.getOrNull() ?: return
    val context = koin.get<Context>()
    val settings = koin.get<AppSettings>()
    val wm = WorkManager.getInstance(context)

    val enabled = settings.prefBoolean(AppSettings.KEY_TRACKER_ENABLED, true)
    // tracker_freq is stored as the entry index: 0=Manual, 1=Less, 2=System default, 3=More frequent.
    val intervalHours = when (settings.prefString(AppSettings.KEY_TRACKER_FREQUENCY, "2").toIntOrNull() ?: 2) {
        1 -> 48L
        2 -> 24L
        3 -> 8L
        else -> 0L // Manual (0) / unknown
    }
    if (!enabled || intervalHours <= 0L) {
        wm.cancelUniqueWork(TRACKER_WORK)
        return
    }
    val wifiOnly = settings.prefBoolean(AppSettings.KEY_TRACKER_WIFI_ONLY, false)
    val constraints = Constraints.Builder()
        .setRequiredNetworkType(if (wifiOnly) NetworkType.UNMETERED else NetworkType.CONNECTED)
        .build()
    val request = PeriodicWorkRequestBuilder<TrackerWorker>(intervalHours, TimeUnit.HOURS)
        .setConstraints(constraints)
        .setBackoffCriteria(BackoffPolicy.LINEAR, 30, TimeUnit.MINUTES)
        .build()
    wm.enqueueUniquePeriodicWork(TRACKER_WORK, ExistingPeriodicWorkPolicy.UPDATE, request)
}
