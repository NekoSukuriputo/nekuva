package org.nekosukuriputo.nekuva.core.parser

import android.graphics.BitmapFactory
import androidx.core.graphics.createBitmap
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import org.aomedia.avif.android.AvifDecoder
import org.nekosukuriputo.nekuva.core.network.webview.WebViewExecutor
import org.nekosukuriputo.nekuva.parsers.bitmap.Bitmap
import java.nio.ByteBuffer
import android.graphics.Bitmap as AndroidBitmap

actual suspend fun evaluateJsPlatform(baseUrl: String, script: String): String? =
    WebViewExecutor.evaluateJs(baseUrl.ifEmpty { null }, script)

actual fun createBitmapPlatform(width: Int, height: Int): Bitmap = BitmapWrapper.create(width, height)

/**
 * Decode the image, hand it to the parser's [redraw] (image descrambling — the parser reassembles shuffled
 * tiles via createBitmap + drawBitmap), and re-encode to PNG (port of Doki MangaLoaderContextImpl). Sources
 * like DoujinDesu scramble pages; without this the reassembled bitmap is blank → black. AVIF-aware decode.
 */
actual fun redrawImageResponsePlatform(response: Response, redraw: (image: Bitmap) -> Bitmap): Response {
    val body = response.body ?: return response
    val bytes = body.bytes()
    val decoded = decodeAndroidBitmap(bytes) ?: return response // can't decode → leave the response untouched
    val source = BitmapWrapper.create(decoded)
    val result = redraw(source) as BitmapWrapper
    val buffer = Buffer()
    result.compressTo(buffer.outputStream())
    if (result !== source) source.close()
    result.close()
    return response.newBuilder()
        .body(buffer.readByteArray().toResponseBody("image/png".toMediaType()))
        .build()
}

/** AVIF-aware decode to a software bitmap usable as a Canvas source for descrambling. */
private fun decodeAndroidBitmap(bytes: ByteArray): AndroidBitmap? {
    val buffer = ByteBuffer.allocateDirect(bytes.size).apply {
        put(bytes)
        rewind()
    }
    if (AvifDecoder.isAvifImage(buffer)) {
        val info = AvifDecoder.Info()
        if (!AvifDecoder.getInfo(buffer, buffer.remaining(), info)) return null
        val bmp = createBitmap(info.width, info.height, AndroidBitmap.Config.ARGB_8888)
        return if (AvifDecoder.decode(buffer, buffer.remaining(), bmp)) bmp else { bmp.recycle(); null }
    }
    val opts = BitmapFactory.Options().apply {
        inMutable = true
        inPreferredConfig = AndroidBitmap.Config.ARGB_8888
    }
    return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts)
}
