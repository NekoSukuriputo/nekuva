package org.nekosukuriputo.nekuva.core.ui

import org.nekosukuriputo.nekuva.core.i18n.LocaleActivityHolder

actual fun exitApp() {
    LocaleActivityHolder.current?.get()?.finish()
}
