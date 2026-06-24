package org.nekosukuriputo.nekuva.settings.ui.about

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.SystemUpdate
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import nekuva.composeapp.generated.resources.Res
import nekuva.composeapp.generated.resources.app_update_available
import nekuva.composeapp.generated.resources.cancel
import nekuva.composeapp.generated.resources.new_version_s
import nekuva.composeapp.generated.resources.size_s
import nekuva.composeapp.generated.resources.update
import org.jetbrains.compose.resources.stringResource
import org.nekosukuriputo.nekuva.core.github.AppVersion
import kotlin.math.roundToInt

/**
 * "App update available" dialog (Doki `AppUpdateActivity`, gambar 5): shows the new version, APK size
 * (Android only — Desktop just opens the browser), and the release notes, with an Update / Cancel action.
 *
 * "Update" delegates to [rememberAppUpdateLauncher] → Android downloads + installs the APK (progress in the
 * notification shade); Desktop opens the release page in the default browser.
 */
@Composable
fun AppUpdateDialog(version: AppVersion, onDismiss: () -> Unit) {
    val launcher = rememberAppUpdateLauncher()
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Outlined.SystemUpdate, contentDescription = null) },
        title = { Text(stringResource(Res.string.app_update_available)) },
        text = {
            Column(modifier = Modifier.fillMaxWidth().heightIn(max = 360.dp).verticalScroll(rememberScrollState())) {
                Text(
                    text = stringResource(Res.string.new_version_s, version.name),
                    style = MaterialTheme.typography.titleMedium,
                )
                if (launcher.canInstallInApp && version.apkSize > 0L) {
                    Text(
                        text = stringResource(Res.string.size_s, formatBytes(version.apkSize)),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (version.description.isNotBlank()) {
                    Spacer(Modifier.height(12.dp))
                    // Release notes (GitHub markdown shown as plain text — no markdown renderer in commonMain).
                    Text(text = version.description, style = MaterialTheme.typography.bodyMedium)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                launcher.startUpdate(version)
                onDismiss()
            }) { Text(stringResource(Res.string.update)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(Res.string.cancel)) }
        },
    )
}

/** Human-readable byte size (Doki FileSize), e.g. 12.3 MB. */
private fun formatBytes(bytes: Long): String {
    if (bytes <= 0L) return "0 B"
    val units = arrayOf("B", "KB", "MB", "GB")
    var value = bytes.toDouble()
    var i = 0
    while (value >= 1024.0 && i < units.lastIndex) {
        value /= 1024.0
        i++
    }
    return if (i == 0) "$bytes B" else "${(value * 10).roundToInt() / 10.0} ${units[i]}"
}
