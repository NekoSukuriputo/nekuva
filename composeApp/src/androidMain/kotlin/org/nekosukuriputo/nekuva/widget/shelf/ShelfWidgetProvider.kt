package org.nekosukuriputo.nekuva.widget.shelf

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import org.nekosukuriputo.nekuva.MainActivity
import org.nekosukuriputo.nekuva.R

/**
 * Favourites "shelf" home-screen widget (Doki ShelfWidgetProvider). Android-only platform surface.
 * Rows are backed by [ShelfWidgetService]; tapping a row opens the manga via MainActivity (EXTRA_MANGA_ID).
 * Per-category config (Doki ShelfWidgetConfigActivity) is deferred — this shows all favourites.
 */
class ShelfWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        for (id in appWidgetIds) {
            val views = RemoteViews(context.packageName, R.layout.widget_shelf)
            views.setRemoteAdapter(R.id.widget_list, Intent(context, ShelfWidgetService::class.java))
            views.setEmptyView(R.id.widget_list, R.id.widget_empty)

            val openApp = PendingIntent.getActivity(
                context, 0,
                Intent(context, MainActivity::class.java),
                PendingIntent.FLAG_IMMUTABLE,
            )
            views.setOnClickPendingIntent(R.id.widget_title, openApp)

            val template = PendingIntent.getActivity(
                context, 2,
                Intent(context, MainActivity::class.java).setAction(Intent.ACTION_VIEW),
                PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
            views.setPendingIntentTemplate(R.id.widget_list, template)

            appWidgetManager.updateAppWidget(id, views)
        }
        appWidgetManager.notifyAppWidgetViewDataChanged(appWidgetIds, R.id.widget_list)
    }
}
