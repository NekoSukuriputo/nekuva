package org.nekosukuriputo.nekuva.alternatives.work

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import org.koin.core.component.KoinComponent
import org.koin.core.component.get
import org.nekosukuriputo.nekuva.alternatives.domain.AutoFixAllUseCase
import org.nekosukuriputo.nekuva.core.prefs.AppSettings
import org.nekosukuriputo.nekuva.parsers.util.runCatchingCancellable

/**
 * Periodic batch auto-fix worker (Doki AutoFixService): migrates manga from removed sources to a working
 * one. Instantiated by WorkManager; dependencies resolved via Koin. No-op when the toggle is off.
 */
class AutoFixWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params), KoinComponent {

    override suspend fun doWork(): Result {
        val settings = get<AppSettings>()
        if (!settings.prefBoolean(AppSettings.KEY_AUTOFIX_ENABLED, false)) return Result.success()
        val autoFixAll = get<AutoFixAllUseCase>()
        return runCatchingCancellable { autoFixAll() }.fold(
            onSuccess = { Result.success() },
            onFailure = { Result.retry() },
        )
    }
}
