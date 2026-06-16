package org.nekosukuriputo.nekuva

import android.content.Context
import android.os.Bundle
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import org.nekosukuriputo.nekuva.core.i18n.localeWrap
import org.nekosukuriputo.nekuva.core.i18n.storedLocaleTag
import org.nekosukuriputo.nekuva.reader.ui.ReaderArgs
import org.nekosukuriputo.nekuva.reader.ui.ReaderKeyEvents
import org.nekosukuriputo.nekuva.reader.ui.ReaderWindowHost

/**
 * Standalone reader Activity (Doki `reader_multitask`): hosts the reader in its own task so multiple manga
 * can be read side-by-side / in Recents. Launched with `FLAG_ACTIVITY_NEW_DOCUMENT`; reuses [ReaderWindowHost]
 * (own theme + image loader + mini NavHost) so it is functionally identical to the in-app reader.
 */
class ReaderActivity : ComponentActivity() {

    // Match MainActivity: apply the chosen in-app language to this Activity's config.
    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(localeWrap(newBase, storedLocaleTag(newBase)))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val mangaId = intent.getLongExtra(EXTRA_MANGA_ID, -1L)
        if (mangaId <= 0L) {
            finish()
            return
        }
        val args = ReaderArgs(
            mangaId = mangaId,
            chapterId = intent.getLongExtra(EXTRA_CHAPTER_ID, -1L),
            page = intent.getIntExtra(EXTRA_PAGE, -1),
            incognito = intent.getBooleanExtra(EXTRA_INCOGNITO, false),
        )
        setContent {
            ReaderWindowHost(args, onClose = { finish() })
        }
    }

    // Route hardware volume keys to the reader's handler (Doki volume-button page navigation), as MainActivity does.
    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (isVolumeKey(keyCode)) {
            val handler = ReaderKeyEvents.volumeKeyHandler
            if (handler != null && handler(keyCode == KeyEvent.KEYCODE_VOLUME_UP)) return true
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent?): Boolean {
        if (isVolumeKey(keyCode) && ReaderKeyEvents.volumeKeyHandler != null) return true
        return super.onKeyUp(keyCode, event)
    }

    private fun isVolumeKey(keyCode: Int) =
        keyCode == KeyEvent.KEYCODE_VOLUME_UP || keyCode == KeyEvent.KEYCODE_VOLUME_DOWN

    companion object {
        const val EXTRA_MANGA_ID = "manga_id"
        const val EXTRA_CHAPTER_ID = "chapter_id"
        const val EXTRA_PAGE = "page"
        const val EXTRA_INCOGNITO = "incognito"
    }
}
