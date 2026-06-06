package org.nekosukuriputo.nekuva.di

import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import org.koin.dsl.module
import org.nekosukuriputo.nekuva.core.network.CloudFlareInterceptor
import org.nekosukuriputo.nekuva.core.network.GZipInterceptor
import org.nekosukuriputo.nekuva.core.network.RateLimitInterceptor
import org.nekosukuriputo.nekuva.core.network.cookies.MutableCookieJar
import java.util.concurrent.TimeUnit
import java.util.Collections

class MemoryCookieJar : MutableCookieJar {
    private val cookies = mutableListOf<Cookie>()

    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
        this.cookies.addAll(cookies)
    }

    override fun loadForRequest(url: HttpUrl): List<Cookie> {
        return cookies.filter { it.matches(url) }
    }

    override fun removeCookies(url: HttpUrl, predicate: ((Cookie) -> Boolean)?) {
        cookies.removeAll { it.matches(url) && (predicate?.invoke(it) ?: true) }
    }

    override suspend fun clear(): Boolean {
        cookies.clear()
        return true
    }
}

val networkModule = module {
    single<MutableCookieJar> { MemoryCookieJar() }
    
    single<OkHttpClient> {
        OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(20, TimeUnit.SECONDS)
            .cookieJar(get<MutableCookieJar>())
            .addInterceptor(GZipInterceptor())
            .addInterceptor(CloudFlareInterceptor())
            .addInterceptor(RateLimitInterceptor())
            .build()
    }
}
