package org.nekosukuriputo.nekuva.parser

import okhttp3.CookieJar
import okhttp3.OkHttpClient
import okhttp3.Response
import org.nekosukuriputo.nekuva.parsers.MangaLoaderContext
import org.nekosukuriputo.nekuva.parsers.MangaParser
import org.nekosukuriputo.nekuva.parsers.bitmap.Bitmap
import org.nekosukuriputo.nekuva.parsers.config.MangaSourceConfig
import org.nekosukuriputo.nekuva.parsers.model.MangaSource
import java.util.Base64
import java.util.Locale
import java.util.concurrent.TimeUnit

actual class AppMangaLoaderContext : MangaLoaderContext() {
    override val httpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    override val cookieJar: CookieJar = CookieJar.NO_COOKIES

    override suspend fun evaluateJs(script: String): String? {
        return null
    }

    override suspend fun evaluateJs(baseUrl: String, script: String): String? {
        // Minimal implementation (Nashorn/JVM) for Phase 0
        return null
    }

    override fun getDefaultUserAgent(): String = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) Nekuva/Phase0"

    override fun getConfig(source: MangaSource): MangaSourceConfig {
        return object : MangaSourceConfig {
            override fun <T> get(key: org.nekosukuriputo.nekuva.parsers.config.ConfigKey<T>): T = throw UnsupportedOperationException("Stub")
        }
    }

    override fun encodeBase64(data: ByteArray): String = Base64.getEncoder().encodeToString(data)

    override fun decodeBase64(data: String): ByteArray = Base64.getDecoder().decode(data)

    override fun getPreferredLocales(): List<Locale> = listOf(Locale.getDefault())

    override fun requestBrowserAction(parser: MangaParser, url: String): Nothing {
        throw RuntimeException("Browser action required for $url")
    }

    override fun redrawImageResponse(response: Response, redraw: (image: Bitmap) -> Bitmap): Response {
        return response // Minimal stub
    }

    override fun createBitmap(width: Int, height: Int): Bitmap {
        return object : Bitmap {
            override val width = width
            override val height = height
            override fun drawBitmap(sourceBitmap: Bitmap, src: org.nekosukuriputo.nekuva.parsers.bitmap.Rect, dst: org.nekosukuriputo.nekuva.parsers.bitmap.Rect) {
                // stub
            }
        }
    }

    actual suspend fun fetchDummyData(): String {
        return "Dummy Data from Desktop Parser Bridge"
    }
}
