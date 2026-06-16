package org.nekosukuriputo.nekuva.core.os

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

/** Android: opens the system "ignore battery optimizations" dialog (Doki ignore_dose / DozeHelper). */
@Composable
actual fun rememberBatteryOptimizationRequest(): (() -> Unit)? {
    val context = LocalContext.current
    return remember(context) {
        {
            runCatching {
                val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                    data = Uri.parse("package:${context.packageName}")
                    if (context !is Activity) addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
            }
            Unit
        }
    }
}
