package org.nekosukuriputo.nekuva.reader.ui

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext

@Composable
actual fun rememberBatteryPercent(): Int? {
	val context = LocalContext.current
	var percent by remember { mutableStateOf<Int?>(null) }
	DisposableEffect(context) {
		fun read(intent: Intent?): Int? {
			val level = intent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
			val scale = intent?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
			return if (level >= 0 && scale > 0) level * 100 / scale else null
		}
		val receiver = object : BroadcastReceiver() {
			override fun onReceive(c: Context?, intent: Intent?) {
				read(intent)?.let { percent = it }
			}
		}
		val filter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
		// registerReceiver with a sticky intent returns the current battery state immediately.
		val sticky = context.registerReceiver(receiver, filter)
		read(sticky)?.let { percent = it }
		onDispose { runCatching { context.unregisterReceiver(receiver) } }
	}
	return percent
}
