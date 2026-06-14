package org.nekosukuriputo.nekuva

import android.os.Bundle
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import org.nekosukuriputo.nekuva.reader.ui.ReaderKeyEvents

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
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
