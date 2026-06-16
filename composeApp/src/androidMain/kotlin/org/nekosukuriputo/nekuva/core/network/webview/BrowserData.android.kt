package org.nekosukuriputo.nekuva.core.network.webview

import android.webkit.CookieManager
import android.webkit.WebStorage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Android WebView data clear (Doki): web storage + cookies. Run on Main as the WebView APIs require it. */
actual suspend fun clearBrowserData() = withContext(Dispatchers.Main.immediate) {
    runCatching { WebStorage.getInstance().deleteAllData() }
    runCatching {
        val cm = CookieManager.getInstance()
        cm.removeAllCookies(null)
        cm.flush()
    }
    Unit
}
