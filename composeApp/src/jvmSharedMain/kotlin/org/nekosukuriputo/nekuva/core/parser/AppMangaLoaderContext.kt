package org.nekosukuriputo.nekuva.core.parser

import kotlinx.coroutines.withTimeout
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Response
import okhttp3.ResponseBody.Companion.asResponseBody
import okio.Buffer
import org.nekosukuriputo.nekuva.core.exceptions.InteractiveActionRequiredException
import org.nekosukuriputo.nekuva.core.network.MangaHttpClient
import org.nekosukuriputo.nekuva.core.network.cookies.MutableCookieJar
import org.nekosukuriputo.nekuva.core.prefs.SourceSettings
import org.nekosukuriputo.nekuva.parsers.MangaLoaderContext
import org.nekosukuriputo.nekuva.parsers.MangaParser
import org.nekosukuriputo.nekuva.parsers.bitmap.Bitmap
import org.nekosukuriputo.nekuva.parsers.config.MangaSourceConfig
import org.nekosukuriputo.nekuva.parsers.model.MangaSource
import org.nekosukuriputo.nekuva.parsers.network.UserAgents
import java.util.Base64
import java.util.Locale
import java.util.concurrent.TimeUnit
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

expect suspend fun evaluateJsPlatform(baseUrl: String, script: String): String?
expect fun createBitmapPlatform(width: Int, height: Int): Bitmap
expect fun redrawImageResponsePlatform(response: Response, redraw: (image: Bitmap) -> Bitmap): Response

class AppMangaLoaderContext(
    override val httpClient: OkHttpClient,
    override val cookieJar: MutableCookieJar
) : MangaLoaderContext(), KoinComponent {

    private val jsTimeout = TimeUnit.SECONDS.toMillis(4)

    @Deprecated("Provide a base url")
    override suspend fun evaluateJs(script: String): String? = evaluateJs("", script)

    override suspend fun evaluateJs(baseUrl: String, script: String): String? = withTimeout(jsTimeout) {
        evaluateJsPlatform(baseUrl, script)
    }

    override fun getDefaultUserAgent(): String = UserAgents.FIREFOX_MOBILE

    override fun getConfig(source: MangaSource): MangaSourceConfig {
        // Stub for now. We will wire this properly when SourceSettings is fully ported.
        return object : MangaSourceConfig {
            override fun <T> get(key: org.nekosukuriputo.nekuva.parsers.config.ConfigKey<T>): T = throw UnsupportedOperationException("Stub getConfig")
        }
    }

    override fun encodeBase64(data: ByteArray): String {
        return Base64.getEncoder().encodeToString(data)
    }

    override fun decodeBase64(data: String): ByteArray {
        return Base64.getDecoder().decode(data)
    }

    override fun getPreferredLocales(): List<Locale> {
        return listOf(Locale.getDefault())
    }

    override fun requestBrowserAction(
        parser: MangaParser,
        url: String,
    ): Nothing = throw InteractiveActionRequiredException(parser.source, url)

    override fun redrawImageResponse(response: Response, redraw: (image: Bitmap) -> Bitmap): Response {
        return redrawImageResponsePlatform(response, redraw)
    }

    override fun createBitmap(width: Int, height: Int): Bitmap = createBitmapPlatform(width, height)
    
    suspend fun fetchDummyData(): String = "Dummy Data"
}
