package org.nekosukuriputo.nekuva.core.image

import coil3.ImageLoader
import coil3.Uri
import coil3.decode.DataSource
import coil3.decode.ImageSource
import coil3.fetch.FetchResult
import coil3.fetch.Fetcher
import coil3.fetch.SourceFetchResult
import coil3.request.Options
import okio.FileSystem

/**
 * Resolves `favicon://<source>` to a cached favicon file via [FaviconCache] (fetched once, persisted),
 * returning it as a disk source so Coil never re-downloads it. The slow resolution happens on the
 * cache's app scope, so a recycled composition scope can't cancel it (Doki parity).
 */
class FaviconFetcher(
    private val uri: Uri,
    private val faviconCache: FaviconCache,
) : Fetcher {
    override suspend fun fetch(): FetchResult? {
        val sourceName = uri.path ?: uri.authority ?: return null
        val file = faviconCache.resolve(sourceName) ?: return null
        return SourceFetchResult(
            source = ImageSource(file, FileSystem.SYSTEM),
            mimeType = null,
            dataSource = DataSource.DISK,
        )
    }

    class Factory(
        private val faviconCache: FaviconCache,
    ) : Fetcher.Factory<Uri> {
        override fun create(data: Uri, options: Options, imageLoader: ImageLoader): Fetcher? {
            return if (data.scheme == "favicon") FaviconFetcher(data, faviconCache) else null
        }
    }
}
