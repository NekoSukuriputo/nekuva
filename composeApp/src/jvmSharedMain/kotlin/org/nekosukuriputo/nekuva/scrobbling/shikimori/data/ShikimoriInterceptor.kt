package org.nekosukuriputo.nekuva.scrobbling.shikimori.data

import okhttp3.Interceptor
import okhttp3.Response
import okio.IOException
import org.nekosukuriputo.nekuva.scrobbling.common.data.ScrobblerStorage
import org.nekosukuriputo.nekuva.scrobbling.common.domain.ScrobblerAuthRequiredException
import org.nekosukuriputo.nekuva.scrobbling.common.domain.model.ScrobblerService

/** Adds the Shikimori User-Agent (required by their API) + the Bearer token, and maps 401 to re-login. */
class ShikimoriInterceptor(private val storage: ScrobblerStorage) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val source = chain.request()
        val request = source.newBuilder().header("User-Agent", "Nekuva")
        val isAuthRequest = source.url.pathSegments.contains("oauth")
        if (!isAuthRequest) {
            storage.accessToken?.let { request.header("Authorization", "Bearer $it") }
        }
        val response = chain.proceed(request.build())
        if (!isAuthRequest && response.code == 401) {
            response.close()
            throw ScrobblerAuthRequiredException(ScrobblerService.SHIKIMORI)
        }
        if (!response.isSuccessful && !response.isRedirect) {
            val code = response.code
            val message = response.message
            response.close()
            throw IOException("$code $message")
        }
        return response
    }
}
