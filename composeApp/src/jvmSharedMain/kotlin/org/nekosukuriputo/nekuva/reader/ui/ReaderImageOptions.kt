package org.nekosukuriputo.nekuva.reader.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import coil3.compose.LocalPlatformContext
import coil3.request.ImageRequest
import coil3.request.transformations
import org.nekosukuriputo.nekuva.reader.domain.CropBordersTransformation

/**
 * Reader image-pipeline options derived from settings: border crop (per active mode) + 32-bit color
 * (Doki parity). Provided once via [LocalReaderImageOptions] so the page renderers don't each have to
 * thread the flags through their signatures.
 */
data class ReaderImageOptions(
	val crop: Boolean = false,
	val enhancedColors: Boolean = false,
)

val LocalReaderImageOptions = staticCompositionLocalOf { ReaderImageOptions() }

/**
 * Build the Coil model for a reader page, applying the active [ReaderImageOptions]. Returns null for a
 * null url (placeholder); otherwise an [ImageRequest] carrying the crop transformation + color config.
 */
@Composable
fun rememberReaderPageModel(url: String?): ImageRequest? {
	val options = LocalReaderImageOptions.current
	val context = LocalPlatformContext.current
	return remember(url, options) {
		if (url == null) return@remember null
		ImageRequest.Builder(context)
			.data(url)
			.apply { if (options.crop) transformations(CropBordersTransformation) }
			.applyEnhancedColors(options.enhancedColors)
			.build()
	}
}
