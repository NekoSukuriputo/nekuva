package org.nekosukuriputo.nekuva.widget.recent

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import org.nekosukuriputo.nekuva.MainActivity
import org.nekosukuriputo.nekuva.R

/**
 * Recently-read manga home-screen widget (Doki RecentWidgetProvider). Android-only platform surface
 * (Desktop/iOS have no home-screen widget concept — platform-appropriate N/A, not a dropped feature).
 * Rows are backed by [RecentWidgetService]; tapping a row opens the manga via MainActivity (EXTRA_MANGA_ID).
 */
class RecentWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        for (id in appWidgetIds) {
            val views = RemoteViews(context.packageName, R.layout.widget_recent)
            views.setRemoteAdapter(R.id.widget_list, Intent(context, RecentWidgetService::class.java))
            views.setEmptyView(R.id.widget_list, R.id.widget_empty)

            // Header tap -> open the app.
            val openApp = PendingIntent.getActivity(
                context, 0,
                Intent(context, MainActivity::class.java),
                PendingIntent.FLAG_IMMUTABLE,
            )
            views.setOnClickPendingIntent(R.id.widget_title, openApp)

            // Per-row tap template: each row's fillInIntent carries EXTRA_MANGA_ID (set in the factory).
            val template = PendingIntent.getActivity(
                context, 1,
                Intent(context, MainActivity::class.java).setAction(Intent.ACTION_VIEW),
                PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
            views.setPendingIntentTemplate(R.id.widget_list, template)

            appWidgetManager.updateAppWidget(id, views)
        }
        // Force the list factory to (re)load the latest history.
        appWidgetManager.notifyAppWidgetViewDataChanged(appWidgetIds, R.id.widget_list)
    }
}
