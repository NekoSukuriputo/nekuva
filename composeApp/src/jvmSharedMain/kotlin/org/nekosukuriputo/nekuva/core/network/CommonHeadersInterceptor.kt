package org.nekosukuriputo.nekuva.core.network

import okhttp3.Headers
import okhttp3.Interceptor
import okhttp3.Interceptor.Chain
import okhttp3.Request
import okhttp3.Response
import okio.IOException
import org.nekosukuriputo.nekuva.parsers.MangaLoaderContext
import org.nekosukuriputo.nekuva.core.parser.MangaRepository
import org.nekosukuriputo.nekuva.core.parser.ParserMangaRepository
import org.nekosukuriputo.nekuva.parsers.model.MangaParserSource
import org.nekosukuriputo.nekuva.parsers.model.MangaSource
import org.nekosukuriputo.nekuva.parsers.util.mergeWith
import org.nekosukuriputo.nekuva.parsers.util.runCatchingCancellable
import java.net.IDN

class CommonHeadersInterceptor (
	private val mangaRepositoryFactoryLazy: kotlin.Lazy<MangaRepository.Factory>,
	private val mangaLoaderContextLazy: kotlin.Lazy<MangaLoaderContext>,
) : Interceptor {

	override fun intercept(chain: Chain): Response {
		val request = chain.request()
		// Image requests carry the source via the X-Manga-Source header (set by the Coil
		// MangaSourceHeaderInterceptor from the request's source extra); parser requests use the okhttp tag.
		val source = request.tag(MangaSource::class.java)
			?: request.headers[CommonHeaders.MANGA_SOURCE]?.let { org.nekosukuriputo.nekuva.core.model.MangaSource(it) }
		val repository = if (source is MangaParserSource) {
			mangaRepositoryFactoryLazy.value.create(source) as? ParserMangaRepository
		} else {
			null
		}
		val headersBuilder = request.headers.newBuilder()
			.removeAll(CommonHeaders.MANGA_SOURCE)
		repository?.getRequestHeaders()?.let {
			headersBuilder.mergeWith(it, replaceExisting = false)
		}
		if (headersBuilder[CommonHeaders.USER_AGENT] == null) {
			headersBuilder[CommonHeaders.USER_AGENT] = mangaLoaderContextLazy.value.getDefaultUserAgent()
		}
		if (headersBuilder[CommonHeaders.REFERER] == null && repository != null) {
			val idn = IDN.toASCII(repository.domain)
			headersBuilder.trySet(CommonHeaders.REFERER, "https://$idn/")
		}
		val newRequest = request.newBuilder().headers(headersBuilder.build()).build()
		return repository?.interceptSafe(ProxyChain(chain, newRequest)) ?: chain.proceed(newRequest)
	}

	private fun Headers.Builder.trySet(name: String, value: String) = try {
		set(name, value)
	} catch (e: IllegalArgumentException) {
		e.printStackTrace()
	}

	private fun Interceptor.interceptSafe(chain: Chain): Response = runCatchingCancellable {
		intercept(chain)
	}.getOrElse { e ->
		if (e is IOException || e is Error) {
			throw e
		} else {
			// only IOException can be safely thrown from an Interceptor
			throw IOException("Error in interceptor: ${e.message}", e)
		}
	}

	private class ProxyChain(
		private val delegate: Chain,
		private val request: Request,
	) : Chain by delegate {

		override fun request(): Request = request
	}
}



