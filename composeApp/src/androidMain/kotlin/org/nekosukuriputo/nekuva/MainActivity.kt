package org.nekosukuriputo.nekuva

import android.content.Context
import android.os.Bundle
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import java.lang.ref.WeakReference
import org.nekosukuriputo.nekuva.core.i18n.LocaleActivityHolder
import org.nekosukuriputo.nekuva.core.i18n.localeWrap
import org.nekosukuriputo.nekuva.core.i18n.storedLocaleTag
import org.nekosukuriputo.nekuva.reader.ui.ReaderKeyEvents

class MainActivity : ComponentActivity() {
    // Apply the chosen in-app language (Doki's app_locale) to the Activity config on every API level.
    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(localeWrap(newBase, storedLocaleTag(newBase)))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        LocaleActivityHolder.current = WeakReference(this)
        setContent {
            App()
        }
    }

    // Route hardware volume keys to the reader when it has installed a handler (Doki's volume-button
    // page navigation). Otherwise they fall through to the system volume UI.
    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (isVolumeKey(keyCode)) {
            val handler = ReaderKeyEvents.volumeKeyHandler
            if (handler != null && handler(keyCode == KeyEvent.KEYCODE_VOLUME_UP)) return true
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent?): Boolean {
        // Swallow the matching key-up so the system volume panel doesn't appear while the reader uses it.
        if (isVolumeKey(keyCode) && ReaderKeyEvents.volumeKeyHandler != null) return true
        return super.onKeyUp(keyCode, event)
    }

    private fun isVolumeKey(keyCode: Int) =
        keyCode == KeyEvent.KEYCODE_VOLUME_UP || keyCode == KeyEvent.KEYCODE_VOLUME_DOWN
}
