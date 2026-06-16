package org.nekosukuriputo.nekuva.di

import okhttp3.Cache
import okhttp3.OkHttpClient
import org.koin.dsl.module
import org.nekosukuriputo.nekuva.core.network.CloudFlareInterceptor
import org.nekosukuriputo.nekuva.core.network.DoHManager
import org.nekosukuriputo.nekuva.core.network.GZipInterceptor
import org.nekosukuriputo.nekuva.core.network.ProxyProvider
import org.nekosukuriputo.nekuva.core.network.RateLimitInterceptor
import org.nekosukuriputo.nekuva.core.network.cookies.MutableCookieJar
import org.nekosukuriputo.nekuva.core.network.cookies.createCookieJar
import org.nekosukuriputo.nekuva.core.network.disableCertificateVerification
import org.nekosukuriputo.nekuva.core.prefs.AppSettings
import org.nekosukuriputo.nekuva.local.data.LocalStorageManager
import java.util.concurrent.TimeUnit

val networkModule = module {
    // Platform-specific so WebView/KCEF and OkHttp share cookies (CloudFlare clearance).
    single<MutableCookieJar> { createCookieJar() }
    // User-configurable proxy (Doki). selector/authenticator read settings live → no client rebuild.
    single { ProxyProvider(get<AppSettings>()) }
    // Shared OkHttp disk cache (Doki) — also clearable from Data removal ("Clear network cache").
    single<Cache> { get<LocalStorageManager>().createHttpCache() }
    // EasyList ad blocker (Doki adblock) — consulted by the in-app browser's request interception.
    single {
        org.nekosukuriputo.nekuva.core.network.webview.adblock.AdBlock(
            get<AppSettings>(),
            get<LocalStorageManager>().adblockListFile(),
            get<OkHttpClient>(),
        )
    }

    single<OkHttpClient> {
        val settings = get<AppSettings>()
        val cache = get<Cache>()
        OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(20, TimeUnit.SECONDS)
            .cookieJar(get<MutableCookieJar>())
            .cache(cache)
            // Proxy (Doki proxy_type_2/address/port + auth) — live via ProxyProvider.
            .proxySelector(get<ProxyProvider>().selector)
            .proxyAuthenticator(get<ProxyProvider>().authenticator)
            // DNS-over-HTTPS (Doki doh) — DoHManager reads the provider live, falls back to system DNS.
            .dns(DoHManager(settings, cache))
            // SSL bypass (Doki ssl_bypass): trust-all when enabled (build-time → applies after restart).
            .apply { if (settings.isSSLBypassEnabled) disableCertificateVerification() }
            .addInterceptor(GZipInterceptor())
            .addInterceptor(CloudFlareInterceptor())
            .addInterceptor(RateLimitInterceptor())
            .build()
    }
}
