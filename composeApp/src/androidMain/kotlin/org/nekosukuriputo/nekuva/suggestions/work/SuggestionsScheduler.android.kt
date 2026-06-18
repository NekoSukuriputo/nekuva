package org.nekosukuriputo.nekuva.suggestions.work

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

private const val SUGGESTIONS_WORK = "nekuva_suggestions"

/**
 * Android periodic suggestions refresh (Doki SuggestionsWorker.Scheduler): every 6h, network constraint from
 * `suggestions_wifi`. Cancels the work when suggestions are disabled. Idempotent.
 */
actual fun scheduleSuggestions() {
    val koin = GlobalContext.getOrNull() ?: return
    val context = koin.get<Context>()
    val settings = koin.get<AppSettings>()
    val wm = WorkManager.getInstance(context)

    if (!settings.isSuggestionsEnabled) {
        wm.cancelUniqueWork(SUGGESTIONS_WORK)
        return
    }
    val constraints = Constraints.Builder()
        .setRequiredNetworkType(if (settings.isSuggestionsWiFiOnly) NetworkType.UNMETERED else NetworkType.CONNECTED)
        .setRequiresBatteryNotLow(true)
        .build()
    val request = PeriodicWorkRequestBuilder<SuggestionsWorker>(6, TimeUnit.HOURS)
        .setConstraints(constraints)
        .setBackoffCriteria(BackoffPolicy.LINEAR, 1, TimeUnit.HOURS)
        .build()
    wm.enqueueUniquePeriodicWork(SUGGESTIONS_WORK, ExistingPeriodicWorkPolicy.UPDATE, request)
}
