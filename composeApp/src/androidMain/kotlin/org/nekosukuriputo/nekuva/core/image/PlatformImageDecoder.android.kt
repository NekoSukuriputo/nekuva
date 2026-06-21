package org.nekosukuriputo.nekuva.core.image

import android.graphics.Bitmap
import androidx.core.graphics.createBitmap
import coil3.ImageLoader
import coil3.asImage
import coil3.decode.DecodeResult
import coil3.decode.Decoder
import coil3.fetch.SourceFetchResult
import coil3.request.Options
import okio.BufferedSource
import okio.ByteString.Companion.encodeUtf8
import org.aomedia.avif.android.AvifDecoder
import java.nio.ByteBuffer

actual fun platformImageDecoderFactory(): Decoder.Factory? = AvifCoilDecoder.Factory()

/**
 * Coil AVIF decoder (port of Doki BitmapDecoderCompat.decodeAvif). Android's ImageDecoder throws
 * "unimplemented" on AVIF, so this decodes via the bundled libavif (org.aomedia.avif.android.AvifDecoder)
 * into a pre-allocated Bitmap. Only claims AVIF sources (ftyp box brand) so other formats fall through.
 */
class AvifCoilDecoder(private val result: SourceFetchResult) : Decoder {

    override suspend fun decode(): DecodeResult {
        val bytes = result.source.source().use { it.readByteArray() }
        // libavif requires a direct ByteBuffer (JNI).
        val buffer = ByteBuffer.allocateDirect(bytes.size).apply {
            put(bytes)
            rewind()
        }
        val info = AvifDecoder.Info()
        check(AvifDecoder.getInfo(buffer, buffer.remaining(), info)) { "Not a valid AVIF image" }
        val config = if (info.depth == 8 || info.alphaPresent) Bitmap.Config.ARGB_8888 else Bitmap.Config.RGB_565
        val bitmap = createBitmap(info.width, info.height, config)
        if (!AvifDecoder.decode(buffer, buffer.remaining(), bitmap)) {
            bitmap.recycle()
            error("AVIF decode failed")
        }
        return DecodeResult(image = bitmap.asImage(), isSampled = false)
    }

    class Factory : Decoder.Factory {
        override fun create(result: SourceFetchResult, options: Options, imageLoader: ImageLoader): Decoder? =
            if (isAvif(result.source.source())) AvifCoilDecoder(result) else null

        // Peek the ISO-BMFF header without consuming: "ftyp" box at offset 4 + an AVIF/HEIF brand at offset 8.
        private fun isAvif(source: BufferedSource): Boolean {
            if (!source.request(12)) return false
            if (!source.rangeEquals(4, FTYP)) return false
            return BRANDS.any { source.rangeEquals(8, it) }
        }

        private companion object {
            val FTYP = "ftyp".encodeUtf8()
            val BRANDS = listOf("avif", "avis", "mif1", "msf1", "miaf").map { it.encodeUtf8() }
        }
    }
}
