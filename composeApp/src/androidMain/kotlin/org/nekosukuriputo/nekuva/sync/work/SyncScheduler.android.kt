package org.nekosukuriputo.nekuva.sync.work

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import org.koin.core.component.KoinComponent
import org.koin.core.component.get
import org.koin.core.context.GlobalContext
import org.nekosukuriputo.nekuva.parsers.util.runCatchingCancellable
import org.nekosukuriputo.nekuva.sync.domain.SyncManager
import java.util.concurrent.TimeUnit

private const val SYNC_WORK = "nekuva_sync"

/**
 * Android periodic sync (Doki SyncAdapter periodic): runs a [SyncWorker] every ~12h while logged in,
 * cancels it when logged out. Idempotent (KEEP) so re-scheduling on each app start doesn't reset the timer.
 */
actual fun scheduleSync() {
    val koin = GlobalContext.getOrNull() ?: return
    val context = koin.get<Context>()
    val wm = WorkManager.getInstance(context)
    if (!koin.get<SyncManager>().isLoggedIn) {
        wm.cancelUniqueWork(SYNC_WORK)
        return
    }
    val constraints = Constraints.Builder()
        .setRequiredNetworkType(NetworkType.CONNECTED)
        .build()
    val request = PeriodicWorkRequestBuilder<SyncWorker>(12, TimeUnit.HOURS)
        .setConstraints(constraints)
        .setBackoffCriteria(BackoffPolicy.LINEAR, 30, TimeUnit.MINUTES)
        .build()
    wm.enqueueUniquePeriodicWork(SYNC_WORK, ExistingPeriodicWorkPolicy.KEEP, request)
}

/** Background sync job: pushes/merges favourites + history with the Kotatsu sync server. Deps via Koin. */
class SyncWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params), KoinComponent {

    override suspend fun doWork(): Result {
        val manager = get<SyncManager>()
        if (!manager.isLoggedIn) return Result.success()
        return runCatchingCancellable { manager.syncNow() }
            .fold(onSuccess = { Result.success() }, onFailure = { Result.retry() })
    }
}
