package org.nekosukuriputo.nekuva.reader.domain

import coil3.Bitmap
import coil3.size.Size
import coil3.transform.Transformation

/**
 * Coil transformation that trims uniform page borders (Doki's "Crop pages"). Applied per-request on
 * the reader page renderers when crop is enabled for the active mode. The actual pixel work is in the
 * platform [trimImageBorders] actuals.
 */
object CropBordersTransformation : Transformation() {

	override val cacheKey: String = "nekuva:crop-borders"

	override suspend fun transform(input: Bitmap, size: Size): Bitmap = trimImageBorders(input)
}
