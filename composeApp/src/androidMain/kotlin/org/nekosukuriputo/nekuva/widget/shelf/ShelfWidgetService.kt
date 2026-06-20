package org.nekosukuriputo.nekuva.widget.shelf

import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import android.widget.RemoteViewsService
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import org.nekosukuriputo.nekuva.R
import org.nekosukuriputo.nekuva.core.shortcuts.EXTRA_MANGA_ID
import org.nekosukuriputo.nekuva.favourites.domain.FavouritesRepository
import org.nekosukuriputo.nekuva.list.domain.ListSortOrder
import org.nekosukuriputo.nekuva.parsers.model.Manga
import org.nekosukuriputo.nekuva.widget.WidgetCoverLoader

/** Backing service for the Shelf (favourites) widget's ListView (Doki ShelfWidgetService). */
class ShelfWidgetService : RemoteViewsService() {
    override fun onGetViewFactory(intent: Intent): RemoteViewsFactory {
        val appWidgetId = intent.getIntExtra(
            AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID,
        )
        return ShelfListFactory(applicationContext, appWidgetId)
    }
}

private class ShelfListFactory(
    private val context: Context,
    private val appWidgetId: Int,
) : RemoteViewsService.RemoteViewsFactory, KoinComponent {

    private val favouritesRepository: FavouritesRepository by inject()
    private var items: List<Manga> = emptyList()

    override fun onCreate() = Unit

    override fun onDataSetChanged() {
        // Per-widget configured category (Doki ShelfWidgetConfig); ALL_FAVOURITES = every favourite.
        val categoryId = ShelfWidgetConfig.getCategory(context, appWidgetId)
        items = runBlocking {
            runCatching {
                if (categoryId == ShelfWidgetConfig.ALL_FAVOURITES) {
                    favouritesRepository.observeAll(ListSortOrder.NEWEST, emptySet(), MAX_ITEMS).first()
                } else {
                    favouritesRepository.observeAll(categoryId, ListSortOrder.NEWEST, emptySet(), MAX_ITEMS).first()
                }
            }.getOrDefault(emptyList())
        }
    }

    override fun onDestroy() {
        items = emptyList()
    }

    override fun getCount(): Int = items.size

    override fun getViewAt(position: Int): RemoteViews {
        val manga = items[position]
        return RemoteViews(context.packageName, R.layout.widget_recent_item).apply {
            setTextViewText(R.id.item_title, manga.title)
            setTextViewText(R.id.item_subtitle, manga.source.name)
            setImageViewBitmap(R.id.item_cover, WidgetCoverLoader.load(manga.coverUrl))
            setOnClickFillInIntent(R.id.item_root, Intent().putExtra(EXTRA_MANGA_ID, manga.id))
        }
    }

    override fun getLoadingView(): RemoteViews? = null
    override fun getViewTypeCount(): Int = 1
    override fun getItemId(position: Int): Long = items.getOrNull(position)?.id ?: position.toLong()
    override fun hasStableIds(): Boolean = true

    private companion object {
        const val MAX_ITEMS = 20
    }
}
