package org.nekosukuriputo.nekuva.core.image

import coil3.intercept.Interceptor
import coil3.network.httpHeaders
import coil3.request.ImageResult
import org.nekosukuriputo.nekuva.core.model.PluginMangaSource
import org.nekosukuriputo.nekuva.core.model.unwrap
import org.nekosukuriputo.nekuva.core.network.CommonHeaders
import org.nekosukuriputo.nekuva.parsers.model.MangaParserSource

class MangaSourceHeaderInterceptor : Interceptor {

	override suspend fun intercept(chain: Interceptor.Chain): ImageResult {
		// Tag with the source NAME for CommonHeadersInterceptor — covers bundled (MangaParserSource) and
		// extension-bundle (PluginMangaSource) sources; skips Local/Unknown (neither type).
		val mangaSource = chain.request.extras[mangaSourceKey]?.unwrap()
		if (mangaSource !is MangaParserSource && mangaSource !is PluginMangaSource) return chain.proceed()
		val request = chain.request
		val newHeaders = request.httpHeaders.newBuilder()
			.set(CommonHeaders.MANGA_SOURCE, mangaSource.name)
			.build()
		val newRequest = request.newBuilder()
			.httpHeaders(newHeaders)
			.build()
		return chain.withRequest(newRequest).proceed()
	}
}

