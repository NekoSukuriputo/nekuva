package org.nekosukuriputo.nekuva.reader.ui

import android.graphics.Bitmap
import coil3.request.ImageRequest
import coil3.request.bitmapConfig

actual fun ImageRequest.Builder.applyEnhancedColors(enabled: Boolean): ImageRequest.Builder =
	bitmapConfig(if (enabled) Bitmap.Config.ARGB_8888 else Bitmap.Config.RGB_565)
