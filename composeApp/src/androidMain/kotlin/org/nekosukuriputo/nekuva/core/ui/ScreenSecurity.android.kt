package org.nekosukuriputo.nekuva.core.ui

import android.view.WindowManager
import org.nekosukuriputo.nekuva.core.i18n.LocaleActivityHolder

actual fun applySecureFlag(secure: Boolean) {
    val window = LocaleActivityHolder.current?.get()?.window ?: return
    if (secure) {
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
    } else {
        window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
    }
}
