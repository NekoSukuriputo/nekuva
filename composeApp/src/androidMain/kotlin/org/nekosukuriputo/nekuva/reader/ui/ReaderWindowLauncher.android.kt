package org.nekosukuriputo.nekuva.reader.ui

import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import org.nekosukuriputo.nekuva.ReaderActivity

/**
 * Android `reader_multitask`: launches [ReaderActivity] with `FLAG_ACTIVITY_NEW_DOCUMENT` so each reader
 * gets its own task / Recents entry (Doki's `AppRouter.openReader`).
 */
@Composable
actual fun rememberReaderWindowLauncher(): ReaderWindowLauncher {
    val context = LocalContext.current
    return remember(context) {
        ReaderWindowLauncher { args ->
            val intent = Intent(context, ReaderActivity::class.java).apply {
                putExtra(ReaderActivity.EXTRA_MANGA_ID, args.mangaId)
                putExtra(ReaderActivity.EXTRA_CHAPTER_ID, args.chapterId)
                putExtra(ReaderActivity.EXTRA_PAGE, args.page)
                putExtra(ReaderActivity.EXTRA_INCOGNITO, args.incognito)
                addFlags(Intent.FLAG_ACTIVITY_NEW_DOCUMENT or Intent.FLAG_ACTIVITY_MULTIPLE_TASK)
            }
            context.startActivity(intent)
            true
        }
    }
}
