package org.nekosukuriputo.nekuva.core.share

import android.content.Intent
import org.nekosukuriputo.nekuva.core.i18n.LocaleActivityHolder

/** Android system share sheet via the current Activity (held by LocaleActivityHolder). */
actual fun shareText(text: String) {
    val activity = LocaleActivityHolder.current?.get() ?: return
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, text)
    }
    runCatching { activity.startActivity(Intent.createChooser(send, null)) }
}
