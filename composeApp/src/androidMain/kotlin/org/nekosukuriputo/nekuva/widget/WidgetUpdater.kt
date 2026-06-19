package org.nekosukuriputo.nekuva.widget

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import org.nekosukuriputo.nekuva.favourites.domain.FavouritesRepository
import org.nekosukuriputo.nekuva.history.data.HistoryRepository
import org.nekosukuriputo.nekuva.widget.recent.RecentWidgetProvider
import org.nekosukuriputo.nekuva.widget.shelf.ShelfWidgetProvider

/**
 * Keeps the home-screen widgets in sync with the library (Doki WidgetUpdater): observes history /
 * favourites changes and tells the widgets to reload their list data. Android-only; started from NekuvaApp.
 */
class WidgetUpdater(
    private val context: Context,
    private val historyRepository: HistoryRepository,
    private val favouritesRepository: FavouritesRepository,
) {

    fun start(scope: CoroutineScope) {
        // drop(1): skip the initial emission — the widget already loads fresh on placement.
        scope.launch {
            historyRepository.observeAll(1).drop(1).collect {
                WidgetCoverLoader.clear()
                notify(RecentWidgetProvider::class.java)
            }
        }
        scope.launch {
            favouritesRepository.observeMangaCount().drop(1).collect {
                notify(ShelfWidgetProvider::class.java)
            }
        }
    }

    private fun notify(provider: Class<*>) {
        runCatching {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, provider))
            if (ids.isNotEmpty()) {
                manager.notifyAppWidgetViewDataChanged(ids, org.nekosukuriputo.nekuva.R.id.widget_list)
            }
        }
    }
}
