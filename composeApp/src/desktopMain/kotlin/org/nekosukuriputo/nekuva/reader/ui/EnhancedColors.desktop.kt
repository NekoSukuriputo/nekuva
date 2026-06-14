package org.nekosukuriputo.nekuva.reader.ui

import coil3.request.ImageRequest

// Desktop/Skia decodes at full quality and has no RGB_565 downgrade — nothing to apply.
actual fun ImageRequest.Builder.applyEnhancedColors(enabled: Boolean): ImageRequest.Builder = this
