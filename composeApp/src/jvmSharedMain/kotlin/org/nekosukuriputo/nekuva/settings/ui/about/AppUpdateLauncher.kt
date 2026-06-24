package org.nekosukuriputo.nekuva.settings.ui.about

import androidx.compose.runtime.Composable
import org.nekosukuriputo.nekuva.core.github.AppVersion

/**
 * Platform bridge that applies an available app update (Doki `AppUpdateActivity`, split for KMP):
 * - **Android:** download the APK via the system `DownloadManager` (the download progress shows in the
 *   notification shade), then launch the package installer once it finishes.
 * - **Desktop:** open the release page in the default browser (no in-app APK install on Desktop).
 *
 * One method, two platform behaviours, so the [AppUpdateDialog] has a single code path; [canInstallInApp]
 * only tweaks the dialog's helper text/labels.
 */
interface AppUpdateLauncher {
    /** True where the APK is downloaded + installed in-app (Android); false → [startUpdate] opens the browser. */
    val canInstallInApp: Boolean

    /** Begin the update (Android: download + install; Desktop: open the release page in the browser). */
    fun startUpdate(version: AppVersion)
}

@Composable
expect fun rememberAppUpdateLauncher(): AppUpdateLauncher
