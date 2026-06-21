package org.nekosukuriputo.nekuva

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import java.lang.ref.WeakReference
import org.nekosukuriputo.nekuva.core.i18n.LocaleActivityHolder
import org.nekosukuriputo.nekuva.core.i18n.localeWrap
import org.nekosukuriputo.nekuva.core.i18n.storedLocaleTag
import org.nekosukuriputo.nekuva.core.nav.DeepLinkBus
import org.nekosukuriputo.nekuva.core.shortcuts.EXTRA_MANGA_ID
import org.nekosukuriputo.nekuva.reader.ui.ReaderKeyEvents

class MainActivity : ComponentActivity() {
    // Apply the chosen in-app language (Doki's app_locale) to the Activity config on every API level.
    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(localeWrap(newBase, storedLocaleTag(newBase)))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        // Cold-start splash (Doki): show the launcher icon on a dark background, then hand off to the app.
        installSplashScreen()
        super.onCreate(savedInstanceState)
        LocaleActivityHolder.current = WeakReference(this)
        handleShortcutIntent(intent)
        setContent {
            App()
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleShortcutIntent(intent)
    }

    // Re-lock the app when it leaves the foreground (Doki protect_app) — but not on a config change
    // (rotation / locale recreate), so the user isn't asked to unlock again right away.
    override fun onStop() {
        super.onStop()
        if (!isChangingConfigurations) {
            runCatching {
                val settings = org.koin.mp.KoinPlatform.getKoin()
                    .get<org.nekosukuriputo.nekuva.core.prefs.AppSettings>()
                org.nekosukuriputo.nekuva.core.security.AppLockController.lockIfProtected(settings)
            }
        }
    }

    // Launcher dynamic shortcut tap (Doki dynamic_shortcuts) -> open that manga / source via the deep-link bus.
    private fun handleShortcutIntent(intent: Intent?) {
        val id = intent?.getLongExtra(EXTRA_MANGA_ID, -1L) ?: -1L
        if (id > 0L) DeepLinkBus.requestOpenManga(id)
        intent?.getStringExtra(org.nekosukuriputo.nekuva.core.shortcuts.EXTRA_SOURCE_NAME)
            ?.takeIf { it.isNotEmpty() }
            ?.let { DeepLinkBus.requestOpenSource(it) }
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
