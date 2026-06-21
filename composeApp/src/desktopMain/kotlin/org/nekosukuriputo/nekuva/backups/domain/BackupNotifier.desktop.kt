package org.nekosukuriputo.nekuva.backups.domain

import nekuva.composeapp.generated.resources.Res
import nekuva.composeapp.generated.resources.create_backup
import nekuva.composeapp.generated.resources.done
import nekuva.composeapp.generated.resources.error_occurred
import nekuva.composeapp.generated.resources.loading
import nekuva.composeapp.generated.resources.restore_backup
import org.jetbrains.compose.resources.getString
import java.awt.SystemTray
import java.awt.TrayIcon
import java.awt.Toolkit

// A single reusable tray icon for backup/restore balloon notifications (added lazily, kept for the session).
private val trayIcon: TrayIcon? by lazy {
    runCatching {
        if (!SystemTray.isSupported()) return@runCatching null
        val image = Toolkit.getDefaultToolkit().createImage(
            object {}.javaClass.classLoader.getResource("nekuva_icon.png"),
        )
        val icon = TrayIcon(image, "Nekuva").apply { isImageAutoSize = true }
        SystemTray.getSystemTray().add(icon)
        icon
    }.getOrNull()
}

private suspend fun balloon(caption: String, text: String, type: TrayIcon.MessageType) {
    runCatching { trayIcon?.displayMessage(caption, text, type) }
}

actual suspend fun notifyBackupStart(isRestore: Boolean) {
    val title = getString(if (isRestore) Res.string.restore_backup else Res.string.create_backup)
    balloon(title, getString(Res.string.loading), TrayIcon.MessageType.INFO)
}

actual suspend fun notifyBackupFinish(isRestore: Boolean, success: Boolean) {
    val title = getString(if (isRestore) Res.string.restore_backup else Res.string.create_backup)
    val text = getString(if (success) Res.string.done else Res.string.error_occurred)
    balloon(title, text, if (success) TrayIcon.MessageType.INFO else TrayIcon.MessageType.ERROR)
}
