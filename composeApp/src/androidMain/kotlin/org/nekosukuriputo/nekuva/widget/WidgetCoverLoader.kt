package org.nekosukuriputo.nekuva.widget

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import okhttp3.OkHttpClient
import okhttp3.Request
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.util.concurrent.ConcurrentHashMap

/**
 * Loads + downscales manga covers for home-screen widgets (Doki widget covers). Uses the app OkHttp client
 * (source interceptors applied) and decodes off the binder thread inside the RemoteViewsFactory. Small
 * in-memory cache keyed by URL so repeated getViewAt calls don't re-download. RemoteViews can't use Coil.
 */
object WidgetCoverLoader : KoinComponent {

    private val client: OkHttpClient by inject()
    private val cache = ConcurrentHashMap<String, Bitmap>()

    fun load(url: String?, targetWidthPx: Int = 144): Bitmap? {
        if (url.isNullOrEmpty()) return null
        cache[url]?.let { return it }
        val bitmap = runCatching {
            val bytes = client.newCall(Request.Builder().url(url).build()).execute().use { resp ->
                if (!resp.isSuccessful) return@runCatching null
                resp.body?.bytes()
            } ?: return@runCatching null
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
            val opts = BitmapFactory.Options().apply { inSampleSize = sampleSize(bounds.outWidth, targetWidthPx) }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts)
        }.getOrNull()
        if (bitmap != null) cache[url] = bitmap
        return bitmap
    }

    fun clear() = cache.clear()

    private fun sampleSize(srcWidth: Int, targetWidth: Int): Int {
        if (srcWidth <= 0 || targetWidth <= 0) return 1
        var sample = 1
        while (srcWidth / (sample * 2) >= targetWidth) sample *= 2
        return sample
    }
}
