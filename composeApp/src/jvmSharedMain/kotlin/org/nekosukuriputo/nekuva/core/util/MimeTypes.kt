@file:OptIn(coil3.annotation.InternalCoilApi::class)
package org.nekosukuriputo.nekuva.core.util

import coil3.Uri

object MimeTypes {
    fun getMimeTypeFromExtension(extension: String?): String? {
        return when (extension?.lowercase()) {
            "jpg", "jpeg" -> "image/jpeg"
            "png" -> "image/png"
            "webp" -> "image/webp"
            "gif" -> "image/gif"
            "avif" -> "image/avif"
            "zip", "cbz" -> "application/zip"
            "rar", "cbr" -> "application/x-rar-compressed"
            "epub" -> "application/epub+zip"
            "json" -> "application/json"
            else -> null
        }
    }
    
    fun getMimeTypeFromUri(uri: Uri): String? {
        return null
    }
    
    fun getExtensionFromMimeType(mimeType: String?): String? {
        return when (mimeType) {
            "image/jpeg" -> "jpg"
            "image/png" -> "png"
            "image/webp" -> "webp"
            "image/gif" -> "gif"
            "image/avif" -> "avif"
            "application/zip" -> "zip"
            "application/x-rar-compressed" -> "rar"
            "application/epub+zip" -> "epub"
            "application/json" -> "json"
            else -> null
        }
    }
}
