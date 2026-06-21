package org.nekosukuriputo.nekuva.core.image

import coil3.Extras
import coil3.request.ImageRequest
import org.nekosukuriputo.nekuva.parsers.model.MangaSource

val mangaSourceKey = Extras.Key<MangaSource?>(null)

/**
 * Tag an image request with its manga source (Doki mangaSourceExtra). [MangaSourceHeaderInterceptor]
 * copies it to the `X-Manga-Source` header; the OkHttp [CommonHeadersInterceptor] then applies the
 * source's Referer/User-Agent + per-source CloudFlare handling so protected covers/pages load.
 */
fun ImageRequest.Builder.mangaSourceExtra(source: MangaSource?): ImageRequest.Builder {
    extras[mangaSourceKey] = source
    return this
}
