package org.nekosukuriputo.nekuva.reader.ui

import androidx.compose.runtime.Composable

/**
 * Platform window controls for the reader. KMP port of Doki's `ScreenOrientationHelper` + the
 * Activity window flags (keep-screen-on, immersive fullscreen, orientation). Android implements it
 * against the host Activity; Desktop is a no-op (these concepts don't exist on desktop).
 */
interface ReaderWindowController {

	/** False on platforms without screen orientation (Desktop) — used to hide the rotate control. */
	val supportsOrientation: Boolean

	/**
	 * Apply the reader's window preferences. [orientationIndex] is the `reader_orientation` pref
	 * (-1/0 = system, 1 = automatic/sensor, 2 = portrait, 3 = landscape).
	 */
	fun apply(keepScreenOn: Boolean, fullscreen: Boolean, orientationIndex: Int)

	/** Rotate/lock the screen orientation (Doki's "rotate screen" button). */
	fun toggleOrientationLock()

	/** Restore the defaults when leaving the reader. */
	fun reset()
}

@Composable
expect fun rememberReaderWindowController(): ReaderWindowController

/**
 * Bridges hardware volume keys (delivered to the Android Activity, not to Compose) to the reader.
 * The reader installs [volumeKeyHandler] while open; `MainActivity.onKeyDown` consults it. Desktop
 * never sets it.
 */
object ReaderKeyEvents {
	@Volatile
	var volumeKeyHandler: ((volumeUp: Boolean) -> Boolean)? = null
}
