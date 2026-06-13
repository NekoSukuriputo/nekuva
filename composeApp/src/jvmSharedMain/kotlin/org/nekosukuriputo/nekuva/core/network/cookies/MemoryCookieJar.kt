package org.nekosukuriputo.nekuva.core.network.cookies

import okhttp3.Cookie
import okhttp3.HttpUrl

/**
 * Simple in-memory cookie jar (Desktop default). Deduplicates by name+domain+path so repeated syncs
 * from the browser engine (e.g. CloudFlare polling) replace rather than accumulate cookies.
 */
class MemoryCookieJar : MutableCookieJar {
    private val cookies = mutableListOf<Cookie>()

    @Synchronized
    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
        for (c in cookies) {
            this.cookies.removeAll { it.name == c.name && it.domain == c.domain && it.path == c.path }
            this.cookies.add(c)
        }
    }

    @Synchronized
    override fun loadForRequest(url: HttpUrl): List<Cookie> = cookies.filter { it.matches(url) }

    @Synchronized
    override fun removeCookies(url: HttpUrl, predicate: ((Cookie) -> Boolean)?) {
        cookies.removeAll { it.matches(url) && (predicate?.invoke(it) ?: true) }
    }

    override suspend fun clear(): Boolean {
        synchronized(cookies) { cookies.clear() }
        return true
    }
}
