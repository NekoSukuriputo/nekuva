package org.nekosukuriputo.nekuva.suggestions.work

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import kotlinx.coroutines.flow.first
import org.jetbrains.compose.resources.getString
import org.koin.core.component.KoinComponent
import org.koin.core.component.get
import org.nekosukuriputo.nekuva.MainActivity
import org.nekosukuriputo.nekuva.core.model.isNsfw
import org.nekosukuriputo.nekuva.core.prefs.AppSettings
import org.nekosukuriputo.nekuva.core.shortcuts.EXTRA_MANGA_ID
import org.nekosukuriputo.nekuva.parsers.util.runCatchingCancellable
import org.nekosukuriputo.nekuva.suggestions.domain.GenerateSuggestionsUseCase
import org.nekosukuriputo.nekuva.suggestions.domain.SuggestionRepository
import nekuva.composeapp.generated.resources.Res
import nekuva.composeapp.generated.resources.suggestions

private const val CHANNEL_ID = "suggestions"

/**
 * Background suggestions refresh (Doki SuggestionsWorker, leaner): regenerates the list, then posts one
 * notification for a random suggested manga when suggestion notifications are enabled. Deps via Koin.
 */
class SuggestionsWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params), KoinComponent {

    override suspend fun doWork(): Result {
        val settings = get<AppSettings>()
        if (!settings.isSuggestionsEnabled) return Result.success()
        runCatchingCancellable { get<GenerateSuggestionsUseCase>().invoke() }
        if (settings.isSuggestionsNotificationAvailable) {
            val suggestions = runCatchingCancellable {
                get<SuggestionRepository>().observeAll().first()
            }.getOrNull().orEmpty()
            val pick = suggestions.filterNot { it.isNsfw() }.randomOrNull()
            if (pick != null) {
                notifySuggestion(pick.id, pick.title, getString(Res.string.suggestions))
            }
        }
        return Result.success()
    }

    private fun notifySuggestion(mangaId: Long, title: String, channelName: String) {
        val context = applicationContext
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(CHANNEL_ID, channelName, NotificationManager.IMPORTANCE_LOW)
            NotificationManagerCompat.from(context).createNotificationChannel(channel)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        val intent = Intent(context, MainActivity::class.java).apply {
            putExtra(EXTRA_MANGA_ID, mangaId)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
        val pending = PendingIntent.getActivity(
            context,
            mangaId.toInt(),
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_menu_search)
            .setContentTitle(channelName)
            .setContentText(title)
            .setAutoCancel(true)
            .setContentIntent(pending)
            .build()
        runCatchingCancellable { NotificationManagerCompat.from(context).notify(mangaId.toInt(), notification) }
    }
}
