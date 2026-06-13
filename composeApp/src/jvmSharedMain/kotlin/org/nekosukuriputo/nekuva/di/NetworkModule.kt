package org.nekosukuriputo.nekuva.di

import okhttp3.OkHttpClient
import org.koin.dsl.module
import org.nekosukuriputo.nekuva.core.network.CloudFlareInterceptor
import org.nekosukuriputo.nekuva.core.network.GZipInterceptor
import org.nekosukuriputo.nekuva.core.network.RateLimitInterceptor
import org.nekosukuriputo.nekuva.core.network.cookies.MutableCookieJar
import org.nekosukuriputo.nekuva.core.network.cookies.createCookieJar
import java.util.concurrent.TimeUnit

val networkModule = module {
    // Platform-specific so WebView/KCEF and OkHttp share cookies (CloudFlare clearance).
    single<MutableCookieJar> { createCookieJar() }
    
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
