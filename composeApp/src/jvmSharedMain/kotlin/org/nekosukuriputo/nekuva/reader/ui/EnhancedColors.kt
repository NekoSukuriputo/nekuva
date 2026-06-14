package org.nekosukuriputo.nekuva.reader.ui

import coil3.request.ImageRequest

/**
 * Apply Doki's "32-bit color mode": when enabled, decode pages at full ARGB_8888; otherwise the
 * memory-saving RGB_565 (Doki's default). Android-only (Coil's `bitmapConfig`); a no-op on Desktop,
 * where Skia decodes at full quality and has no equivalent downgrade knob.
 */
expect fun ImageRequest.Builder.applyEnhancedColors(enabled: Boolean): ImageRequest.Builder
