package org.nekosukuriputo.nekuva.reader.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember

/** Desktop has no orientation / system bars / keep-screen-on — everything is a no-op. */
@Composable
actual fun rememberReaderWindowController(): ReaderWindowController = remember { DesktopReaderWindowController }

private object DesktopReaderWindowController : ReaderWindowController {
	override val supportsOrientation: Boolean = false
	override fun apply(keepScreenOn: Boolean, fullscreen: Boolean, orientationIndex: Int) {}
	override fun toggleOrientationLock() {}
	override fun reset() {}
}
