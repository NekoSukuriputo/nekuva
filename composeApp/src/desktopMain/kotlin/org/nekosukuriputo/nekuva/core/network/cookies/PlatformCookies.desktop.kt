package org.nekosukuriputo.nekuva.core.network.cookies

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.Cookie
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import org.cef.callback.CefCookieVisitor
import org.cef.misc.BoolRef
import org.cef.network.CefCookie
import org.cef.network.CefCookieManager

actual fun createCookieJar(): MutableCookieJar = MemoryCookieJar()

/**
 * Copy CEF's cookies for [url] into [cookieJar] so cookies the embedded browser obtained (e.g. the
 * CloudFlare `cf_clearance`) become visible to the OkHttp client. CEF visits cookies asynchronously,
 * so we collect via a visitor with a short timeout guard.
 */
actual suspend fun syncBrowserCookies(url: String, cookieJar: MutableCookieJar) {
    val httpUrl = url.toHttpUrlOrNull() ?: return
    val manager = runCatching { CefCookieManager.getGlobalManager() }.getOrNull() ?: return
    val collected = java.util.Collections.synchronizedList(ArrayList<Cookie>())
    val done = CompletableDeferred<Unit>()

    val started = runCatching {
        manager.visitUrlCookies(url, true, object : CefCookieVisitor {
            override fun visit(cookie: CefCookie, count: Int, total: Int, delete: BoolRef): Boolean {
                runCatching {
                    val domain = cookie.domain?.trim()?.removePrefix(".").orEmpty().ifEmpty { httpUrl.host }
                    val builder = Cookie.Builder()
                        .name(cookie.name)
                        .value(cookie.value)
                        .domain(domain)
                        .path(cookie.path?.ifEmpty { "/" } ?: "/")
                    if (cookie.secure) builder.secure()
                    if (cookie.httponly) builder.httpOnly()
                    collected.add(builder.build())
                }
                if (count >= total - 1) {
                    done.complete(Unit)
                }
                return true
            }
        })
    }.getOrDefault(false)

    if (started) {
        withTimeoutOrNull(2_500) { done.await() }
    }
    val snapshot = synchronized(collected) { collected.toList() }
    if (snapshot.isNotEmpty()) {
        runCatching { cookieJar.saveFromResponse(httpUrl, snapshot) }
    }
}
