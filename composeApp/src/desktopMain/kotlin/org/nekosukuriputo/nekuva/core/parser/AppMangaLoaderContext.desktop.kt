package org.nekosukuriputo.nekuva.core.parser

import okhttp3.Response
import org.nekosukuriputo.nekuva.core.network.webview.KcefManager
import org.nekosukuriputo.nekuva.parsers.bitmap.Bitmap

actual suspend fun evaluateJsPlatform(baseUrl: String, script: String): String? =
    KcefManager.evaluateJs(baseUrl.ifEmpty { null }, script)

actual fun createBitmapPlatform(width: Int, height: Int): Bitmap {
    return object : Bitmap {
        override val width = width
        override val height = height
        override fun drawBitmap(sourceBitmap: Bitmap, src: org.nekosukuriputo.nekuva.parsers.bitmap.Rect, dst: org.nekosukuriputo.nekuva.parsers.bitmap.Rect) {}
    }
}

actual fun redrawImageResponsePlatform(response: Response, redraw: (image: Bitmap) -> Bitmap): Response = response
