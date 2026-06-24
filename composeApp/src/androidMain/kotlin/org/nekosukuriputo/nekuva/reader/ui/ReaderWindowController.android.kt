package org.nekosukuriputo.nekuva.reader.ui

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.graphics.Color
import android.view.WindowManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat

@Composable
actual fun rememberReaderWindowController(): ReaderWindowController {
	val context = LocalContext.current
	return remember(context) { AndroidReaderWindowController(context.findActivity()) }
}

private class AndroidReaderWindowController(
	private val activity: Activity?,
) : ReaderWindowController {

	override val supportsOrientation: Boolean = true

	// Original bar colours, captured once, so reset() restores the rest of the app's appearance.
	private val originalStatusBar: Int? = activity?.window?.statusBarColor
	private val originalNavBar: Int? = activity?.window?.navigationBarColor

	override fun apply(keepScreenOn: Boolean, fullscreen: Boolean, orientationIndex: Int) {
		val a = activity ?: return
		if (keepScreenOn) {
			a.window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
		} else {
			a.window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
		}
		// Edge-to-edge with transparent bars so the page fills the whole screen (no white strip under the
		// status bar). When fullscreen is on the bars are hidden entirely; otherwise they float transparent
		// over the page. Light (white) bar icons so they stay visible over dark/varied page images.
		WindowCompat.setDecorFitsSystemWindows(a.window, false)
		a.window.statusBarColor = Color.TRANSPARENT
		a.window.navigationBarColor = Color.TRANSPARENT
		insetsController(a)?.let { controller ->
			controller.isAppearanceLightStatusBars = false
			controller.isAppearanceLightNavigationBars = false
			if (fullscreen) {
				controller.hide(WindowInsetsCompat.Type.systemBars())
				controller.systemBarsBehavior =
					WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
			} else {
				controller.show(WindowInsetsCompat.Type.systemBars())
			}
		}
		a.requestedOrientation = orientationIndex.toActivityOrientation()
	}

	override fun toggleOrientationLock() {
		val a = activity ?: return
		val isLandscape = a.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
		a.requestedOrientation = if (isLandscape) {
			ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
		} else {
			ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
		}
	}

	override fun reset() {
		val a = activity ?: return
		a.window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
		// Undo the reader's edge-to-edge so the rest of the app keeps its normal layout/bar colours.
		WindowCompat.setDecorFitsSystemWindows(a.window, true)
		originalStatusBar?.let { a.window.statusBarColor = it }
		originalNavBar?.let { a.window.navigationBarColor = it }
		insetsController(a)?.show(WindowInsetsCompat.Type.systemBars())
		a.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
	}

	private fun insetsController(a: Activity): WindowInsetsControllerCompat? =
		WindowInsetsControllerCompat(a.window, a.window.decorView)

	private fun Int.toActivityOrientation(): Int = when (this) {
		1 -> ActivityInfo.SCREEN_ORIENTATION_SENSOR
		2 -> ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
		3 -> ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
		else -> ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
	}
}

private fun Context.findActivity(): Activity? {
	var ctx: Context? = this
	while (ctx is ContextWrapper) {
		if (ctx is Activity) return ctx
		ctx = ctx.baseContext
	}
	return null
}
