package org.nekosukuriputo.nekuva.reader.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import coil3.PlatformContext
import coil3.compose.LocalPlatformContext
import coil3.request.ImageRequest
import coil3.request.transformations
import org.nekosukuriputo.nekuva.reader.domain.CropBordersTransformation

/**
 * Reader image-pipeline options derived from settings: border crop (per active mode) + 32-bit color
 * + memory optimization (Doki parity). Provided once via [LocalReaderImageOptions] so the page
 * renderers don't each have to thread the flags through their signatures.
 */
data class ReaderImageOptions(
	val crop: Boolean = false,
	val enhancedColors: Boolean = false,
	val optimize: Boolean = false,
)

val LocalReaderImageOptions = staticCompositionLocalOf { ReaderImageOptions() }

/**
 * Build the Coil model for a reader page, applying the active [ReaderImageOptions]. Returns null for a
 * null url (placeholder); otherwise an [ImageRequest] carrying the crop transformation + color config.
 *
 * [foreground] mirrors Doki's `applyDownSampling`: when memory optimization (`reader_optimize`) is on,
 * pages that are NOT in the foreground decode at a reduced target size to save memory; the page reloads
 * at full resolution once it becomes foreground (same trade-off as Doki's RecyclerView holders).
 */
@Composable
fun rememberReaderPageModel(url: String?, foreground: Boolean = true): ImageRequest? {
	val options = LocalReaderImageOptions.current
	val context = LocalPlatformContext.current
	return remember(url, options, foreground) {
		if (url == null) return@remember null
		buildReaderPageRequest(context, url, options, foreground)
	}
}

/**
 * Non-composable variant of [rememberReaderPageModel] used by the page preloader (Doki's prefetch), so a
 * preloaded request carries the SAME crop/colour/size flags as the on-screen one and hits the cache.
 */
fun buildReaderPageRequest(
	context: PlatformContext,
	url: String,
	options: ReaderImageOptions,
	foreground: Boolean,
): ImageRequest = ImageRequest.Builder(context)
	.data(url)
	.apply {
		if (options.crop) transformations(CropBordersTransformation)
		if (options.optimize && !foreground) size(OPTIMIZED_OFFSCREEN_SIZE)
	}
	.applyEnhancedColors(options.enhancedColors)
	.build()

/** Reduced decode size for off-screen pages under `reader_optimize` (Doki down-samples off-screen ~4×). */
private const val OPTIMIZED_OFFSCREEN_SIZE = 720
