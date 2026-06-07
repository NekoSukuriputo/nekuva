package org.nekosukuriputo.nekuva.core.image

import coil3.ImageLoader
import coil3.Uri
import coil3.fetch.FetchResult
import coil3.fetch.Fetcher
import coil3.request.Options
import org.nekosukuriputo.nekuva.core.parser.MangaRepository
import org.nekosukuriputo.nekuva.core.parser.ParserMangaRepository
import org.nekosukuriputo.nekuva.parsers.model.MangaParserSource

class FaviconFetcher(
    private val uri: Uri,
    private val options: Options,
    private val imageLoader: ImageLoader,
    private val mangaRepositoryFactory: MangaRepository.Factory,
) : Fetcher {
    override suspend fun fetch(): FetchResult? {
        val sourceName = uri.path ?: uri.authority ?: return null
        val mangaSource = MangaParserSource.entries.find { it.name == sourceName } ?: return null
        val repo = mangaRepositoryFactory.create(mangaSource) as? ParserMangaRepository ?: return null
        
        val favicons = try {
            repo.getFavicons()
        } catch (e: Exception) {
            return null
        }
        
        val icon = favicons.find(144) ?: favicons.firstOrNull() ?: return null
        
        val mappedData = imageLoader.components.map(icon.url, options)
        val fetcher = imageLoader.components.newFetcher(mappedData, options, imageLoader)?.first ?: return null
        return fetcher.fetch()
    }

    class Factory(
        private val mangaRepositoryFactory: MangaRepository.Factory,
    ) : Fetcher.Factory<Uri> {
        override fun create(
            data: Uri,
            options: Options,
            imageLoader: ImageLoader
        ): Fetcher? {
            if (data.scheme == "favicon") {
                return FaviconFetcher(data, options, imageLoader, mangaRepositoryFactory)
            }
            return null
        }
    }
}
