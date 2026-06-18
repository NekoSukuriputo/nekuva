package org.nekosukuriputo.nekuva.core.shortcuts

import android.content.Context
import android.content.Intent
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import org.nekosukuriputo.nekuva.MainActivity

/** Intent extra carrying the manga id a launcher shortcut opens (read by MainActivity). */
const val EXTRA_MANGA_ID = "nekuva.manga_id"

private const val MAX_SHORTCUTS = 4

private object ShortcutContext : KoinComponent {
    val context: Context by inject()
}

actual fun updateDynamicShortcuts(shortcuts: List<MangaShortcut>) {
    val context = ShortcutContext.context
    runCatching {
        if (shortcuts.isEmpty()) {
            ShortcutManagerCompat.removeAllDynamicShortcuts(context)
            return
        }
        val icon = IconCompat.createWithResource(context, context.applicationInfo.icon)
        val infos = shortcuts.take(MAX_SHORTCUTS).map { s ->
            val label = s.title.ifBlank { "Manga" }
            val intent = Intent(context, MainActivity::class.java).apply {
                action = Intent.ACTION_VIEW
                putExtra(EXTRA_MANGA_ID, s.id)
            }
            ShortcutInfoCompat.Builder(context, "manga_${s.id}")
                .setShortLabel(label.take(24))
                .setLongLabel(label)
                .setIcon(icon)
                .setIntent(intent)
                .build()
        }
        ShortcutManagerCompat.setDynamicShortcuts(context, infos)
    }
}

actual fun pinMangaShortcut(id: Long, title: String) {
    val context = ShortcutContext.context
    runCatching {
        if (!ShortcutManagerCompat.isRequestPinShortcutSupported(context)) return
        val label = title.ifBlank { "Manga" }
        val intent = Intent(context, MainActivity::class.java).apply {
            action = Intent.ACTION_VIEW
            putExtra(EXTRA_MANGA_ID, id)
        }
        val info = ShortcutInfoCompat.Builder(context, "manga_pin_$id")
            .setShortLabel(label.take(24))
            .setLongLabel(label)
            .setIcon(IconCompat.createWithResource(context, context.applicationInfo.icon))
            .setIntent(intent)
            .build()
        ShortcutManagerCompat.requestPinShortcut(context, info, null)
    }
}
