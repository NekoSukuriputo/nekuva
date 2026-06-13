package org.nekosukuriputo.nekuva.core.network.cookies

// Android WebView and OkHttp share the system CookieManager via AndroidCookieJar, so solving a
// CloudFlare challenge in the in-app browser immediately makes the clearance cookie visible to OkHttp.
actual fun createCookieJar(): MutableCookieJar = AndroidCookieJar()

actual suspend fun syncBrowserCookies(url: String, cookieJar: MutableCookieJar) = Unit
