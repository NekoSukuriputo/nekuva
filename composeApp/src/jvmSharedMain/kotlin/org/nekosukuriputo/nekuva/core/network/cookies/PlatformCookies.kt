package org.nekosukuriputo.nekuva.core.network.cookies

/**
 * The cookie jar backing the OkHttp client. It must be the SAME store the in-app browser engine uses,
 * so cookies set while solving a CloudFlare challenge reach OkHttp:
 * - Android: [AndroidCookieJar] over the system `CookieManager` (shared with WebView automatically).
 * - Desktop: an in-memory jar; KCEF/CEF keeps its own store, bridged on demand by [syncBrowserCookies].
 */
expect fun createCookieJar(): MutableCookieJar

/**
 * Pull cookies the browser engine holds for [url] into [cookieJar]. No-op on Android (CookieManager is
 * already shared); on Desktop it copies CEF's cookies into the OkHttp jar so CloudFlare clearance is seen.
 */
expect suspend fun syncBrowserCookies(url: String, cookieJar: MutableCookieJar)
