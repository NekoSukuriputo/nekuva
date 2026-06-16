package org.nekosukuriputo.nekuva.core.network.webview

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Desktop KCEF browser data clear (Doki): deletes the global CEF cookies (best-effort). The on-disk cache
 * under ~/.nekuva/kcef is owned by the running Chromium and is left to its own management.
 */
actual suspend fun clearBrowserData() {
    withContext(Dispatchers.IO) {
        runCatching {
            org.cef.network.CefCookieManager.getGlobalManager()?.deleteCookies("", "")
        }
    }
}
