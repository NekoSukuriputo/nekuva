package org.nekosukuriputo.nekuva.tracker.work

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
import org.jetbrains.compose.resources.getString
import org.koin.core.component.KoinComponent
import org.koin.core.component.get
import org.nekosukuriputo.nekuva.MainActivity
import org.nekosukuriputo.nekuva.core.model.isNsfw
import org.nekosukuriputo.nekuva.core.prefs.AppSettings
import org.nekosukuriputo.nekuva.core.prefs.TrackerDownloadStrategy
import org.nekosukuriputo.nekuva.core.shortcuts.EXTRA_MANGA_ID
import org.nekosukuriputo.nekuva.download.domain.DownloadManager
import org.nekosukuriputo.nekuva.download.domain.DownloadTask
import org.nekosukuriputo.nekuva.local.data.LocalMangaRepository
import org.nekosukuriputo.nekuva.parsers.model.Manga
import org.nekosukuriputo.nekuva.parsers.util.runCatchingCancellable
import org.nekosukuriputo.nekuva.tracker.domain.CheckNewChaptersUseCase
import org.nekosukuriputo.nekuva.tracker.domain.TrackingRepository
import org.nekosukuriputo.nekuva.tracker.domain.model.MangaUpdates
import nekuva.composeapp.generated.resources.Res
import nekuva.composeapp.generated.resources.check_for_new_chapters
import nekuva.composeapp.generated.resources.new_chapters

private const val CHANNEL_ID = "new_chapters"

/**
 * Background new-chapter check (Doki TrackWorker, leaner): syncs the tracked set, checks each manga, saves
 * updates, optionally auto-downloads (tracker_download), and posts a per-manga notification (skipping NSFW
 * when tracker_no_nsfw). Tapping a notification opens that manga. Instantiated by WorkManager; deps via Koin.
 */
class TrackerWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params), KoinComponent {

    override suspend fun doWork(): Result {
        val settings = get<AppSettings>()
        if (!settings.prefBoolean(AppSettings.KEY_TRACKER_ENABLED, true)) return Result.success()
        val tracking = get<TrackingRepository>()
        val check = get<CheckNewChaptersUseCase>()
        val downloadManager = get<DownloadManager>()
        val localRepo = get<LocalMangaRepository>()
        val noNsfw = settings.prefBoolean(AppSettings.KEY_TRACKER_NO_NSFW, false)
        val newChaptersLabel = getString(Res.string.new_chapters)

        ensureChannel(getString(Res.string.check_for_new_chapters))
        runCatchingCancellable { tracking.updateTracks() }
        val tracks = runCatchingCancellable { tracking.getTracks() }.getOrNull().orEmpty()
        for (track in tracks) {
            val updates = runCatchingCancellable { check.check(track.manga) }.getOrNull() ?: continue
            runCatchingCancellable { tracking.saveUpdates(updates) }
            if (updates !is MangaUpdates.Success || !updates.isValid || updates.newChapters.isEmpty()) continue
            // Auto-download (tracker_download = DOWNLOADED): only manga already saved locally.
            if (settings.trackerDownloadStrategy == TrackerDownloadStrategy.DOWNLOADED &&
                updates.manga.id in localRepo.observeSavedIds().value
            ) {
                runCatchingCancellable {
                    downloadManager.schedule(
                        listOf(
                            DownloadTask(
                                manga = updates.manga,
                                chaptersIds = updates.newChapters.mapTo(HashSet()) { it.id },
                                destination = null,
                                format = null,
                                startPaused = false,
                            ),
                        ),
                    )
                }
            }
            if (!(noNsfw && updates.manga.isNsfw())) {
                notifyNewChapters(updates.manga, updates.newChapters.size, newChaptersLabel)
            }
        }
        return Result.success()
    }

    private fun ensureChannel(name: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(CHANNEL_ID, name, NotificationManager.IMPORTANCE_DEFAULT)
            NotificationManagerCompat.from(applicationContext).createNotificationChannel(channel)
        }
    }

    private fun notifyNewChapters(manga: Manga, count: Int, label: String) {
        val context = applicationContext
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        val intent = Intent(context, MainActivity::class.java).apply {
            putExtra(EXTRA_MANGA_ID, manga.id)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
        val pending = PendingIntent.getActivity(
            context,
            manga.id.toInt(),
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_notify_sync)
            .setContentTitle(manga.title)
            .setContentText("$count $label")
            .setAutoCancel(true)
            .setContentIntent(pending)
            .build()
        runCatching { NotificationManagerCompat.from(context).notify(manga.id.toInt(), notification) }
    }
}
