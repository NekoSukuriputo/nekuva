package org.nekosukuriputo.nekuva.core.image

// Desktop: no bundled AVIF decoder (ImageIO doesn't support it). AVIF pages would need a JVM AVIF lib.
actual fun platformImageDecoderFactory(): coil3.decode.Decoder.Factory? = null
