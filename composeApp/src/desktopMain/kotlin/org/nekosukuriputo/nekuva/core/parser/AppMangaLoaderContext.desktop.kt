package org.nekosukuriputo.nekuva.core.parser

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.nekosukuriputo.nekuva.core.network.webview.KcefManager
import org.nekosukuriputo.nekuva.parsers.bitmap.Bitmap
import org.nekosukuriputo.nekuva.parsers.bitmap.Rect
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import javax.imageio.ImageIO

actual suspend fun evaluateJsPlatform(baseUrl: String, script: String): String? =
    KcefManager.evaluateJs(baseUrl.ifEmpty { null }, script)

actual fun createBitmapPlatform(width: Int, height: Int): Bitmap =
    DesktopBitmap(BufferedImage(width.coerceAtLeast(1), height.coerceAtLeast(1), BufferedImage.TYPE_INT_ARGB))

/** Image descramble on Desktop (ImageIO): decode → parser redraw (reassemble tiles) → re-encode PNG.
 *  ImageIO can't decode AVIF/WebP, so those return unchanged (and stay blank until a JVM decoder exists). */
actual fun redrawImageResponsePlatform(response: Response, redraw: (image: Bitmap) -> Bitmap): Response {
    val body = response.body ?: return response
    val bytes = body.bytes()
    val decoded = runCatching { ImageIO.read(bytes.inputStream()) }.getOrNull() ?: return response
    val result = redraw(DesktopBitmap(decoded)) as DesktopBitmap
    val out = ByteArrayOutputStream()
    ImageIO.write(result.image, "png", out)
    return response.newBuilder()
        .body(out.toByteArray().toResponseBody("image/png".toMediaType()))
        .build()
}

/** exts [Bitmap] backed by a [BufferedImage] (Desktop equivalent of the Android BitmapWrapper). */
private class DesktopBitmap(val image: BufferedImage) : Bitmap {
    override val width: Int get() = image.width
    override val height: Int get() = image.height

    override fun drawBitmap(sourceBitmap: Bitmap, src: Rect, dst: Rect) {
        val source = (sourceBitmap as DesktopBitmap).image
        val g = image.createGraphics()
        // dst (dx1,dy1)-(dx2,dy2) <- src (sx1,sy1)-(sx2,sy2)
        g.drawImage(source, dst.left, dst.top, dst.right, dst.bottom, src.left, src.top, src.right, src.bottom, null)
        g.dispose()
    }
}
