package org.nekosukuriputo.nekuva.widget.shelf

import android.content.Context

/**
 * Per-widget category selection for the Shelf widget (Doki ShelfWidgetConfig). Stores, keyed by
 * appWidgetId, which favourite category the widget shows ([ALL_FAVOURITES] = every favourite).
 */
object ShelfWidgetConfig {

    const val ALL_FAVOURITES = -1L
    private const val PREFS = "shelf_widget_config"

    fun getCategory(context: Context, appWidgetId: Int): Long =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getLong(key(appWidgetId), ALL_FAVOURITES)

    fun setCategory(context: Context, appWidgetId: Int, categoryId: Long) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putLong(key(appWidgetId), categoryId).apply()
    }

    fun remove(context: Context, appWidgetId: Int) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().remove(key(appWidgetId)).apply()
    }

    private fun key(appWidgetId: Int) = "cat_$appWidgetId"
}
