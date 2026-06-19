package org.nekosukuriputo.nekuva.widget.recent

import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import android.widget.RemoteViewsService
import kotlinx.coroutines.runBlocking
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import org.nekosukuriputo.nekuva.R
import org.nekosukuriputo.nekuva.core.shortcuts.EXTRA_MANGA_ID
import org.nekosukuriputo.nekuva.history.data.HistoryRepository
import org.nekosukuriputo.nekuva.parsers.model.Manga

/** Backing service for the Recent widget's ListView (Doki RecentWidgetService). */
class RecentWidgetService : RemoteViewsService() {
    override fun onGetViewFactory(intent: Intent): RemoteViewsFactory =
        RecentListFactory(applicationContext)
}

/**
 * Loads the most recently read manga from the history DB (synchronously, on the widget binder thread)
 * and renders text rows (Doki RecentListFactory). Cover thumbnails are deferred (need a non-Compose Coil
 * bitmap load in RemoteViews); titles + source are shown for now.
 */
private class RecentListFactory(
    private val context: Context,
) : RemoteViewsService.RemoteViewsFactory, KoinComponent {

    private val historyRepository: HistoryRepository by inject()
    private var items: List<Manga> = emptyList()

    override fun onCreate() = Unit

    override fun onDataSetChanged() {
        items = runBlocking {
            runCatching { historyRepository.getList(offset = 0, limit = MAX_ITEMS) }.getOrDefault(emptyList())
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
            // fillInIntent merges into the provider's PendingIntentTemplate -> MainActivity opens the manga.
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
