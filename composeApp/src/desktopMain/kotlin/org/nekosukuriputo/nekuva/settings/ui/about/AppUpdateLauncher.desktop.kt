package org.nekosukuriputo.nekuva.settings.ui.about

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import org.nekosukuriputo.nekuva.core.github.AppVersion
import java.awt.Desktop
import java.net.URI

@Composable
actual fun rememberAppUpdateLauncher(): AppUpdateLauncher = remember { DesktopAppUpdateLauncher }

/** Desktop has no in-app APK install — "Update" opens the release page in the default browser. */
private object DesktopAppUpdateLauncher : AppUpdateLauncher {

    override val canInstallInApp: Boolean = false

    override fun startUpdate(version: AppVersion) {
        val url = version.url.ifEmpty { version.apkUrl }
        if (url.isEmpty()) return
        runCatching {
            if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
                Desktop.getDesktop().browse(URI(url))
            }
        }
    }
}
