package org.nekosukuriputo.nekuva.core.image

/**
 * A platform-specific Coil [coil3.decode.Decoder.Factory] for image formats the platform's default decoder
 * can't handle (Doki BitmapDecoderCompat) — notably AVIF, which Android's ImageDecoder fails on with
 * "unimplemented" and which sources like DoujinDesu serve. Android returns a libavif-backed decoder;
 * Desktop has none (returns null).
 */
expect fun platformImageDecoderFactory(): coil3.decode.Decoder.Factory?
