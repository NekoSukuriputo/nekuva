package org.nekosukuriputo.nekuva.di

import okhttp3.Cache
import okhttp3.OkHttpClient
import org.koin.core.qualifier.named
import org.koin.dsl.module
import org.nekosukuriputo.nekuva.core.network.CacheLimitInterceptor
import org.nekosukuriputo.nekuva.core.network.CloudFlareInterceptor
import org.nekosukuriputo.nekuva.core.network.CommonHeadersInterceptor
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
            // HTTP response cache: ON for Android (app-private), OFF for Desktop — source sites serve
            // ad/redirect JS that Defender flags once OkHttp caches it to ~/.nekuva/http_cache. Images
            // are cached separately by Coil, so this only drops parser-response caching on Desktop.
            .apply { if (org.nekosukuriputo.nekuva.core.network.httpDiskCacheEnabled) cache(cache) }
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

    // Manga/parser client (Doki @MangaHttpClient) = base client + CommonHeadersInterceptor. This is the
    // client the parser engine (AppMangaLoaderContext.httpClient) uses, so per-source getRequestHeaders()
    // (e.g. DoujinDesu's "X-Requested-With: XMLHttpRequest" on its /themes/ajax/ch.php page request) and the
    // per-source interceptSafe (CloudFlare) are applied to PARSER requests, not just Coil image requests.
    // Without it the DoujinDesu ajax POST returned no <img> → getPages() = 0 pages → blank reader.
    // CommonHeadersInterceptor reads the MangaSource the WebClient tags onto each request (addTags).
    single<OkHttpClient>(named("manga")) {
        get<OkHttpClient>().newBuilder()
            .addNetworkInterceptor(CacheLimitInterceptor())
            .addInterceptor(
                CommonHeadersInterceptor(
                    lazy { get<org.nekosukuriputo.nekuva.core.parser.MangaRepository.Factory>() },
                    lazy { get<org.nekosukuriputo.nekuva.parsers.MangaLoaderContext>() },
                ),
            )
            .build()
    }
}
