package org.nekosukuriputo.nekuva.scrobbling.mal.data

import okhttp3.Interceptor
import okhttp3.Response
import org.nekosukuriputo.nekuva.scrobbling.common.data.ScrobblerStorage
import org.nekosukuriputo.nekuva.scrobbling.common.domain.ScrobblerAuthRequiredException
import org.nekosukuriputo.nekuva.scrobbling.common.domain.model.ScrobblerService
import java.io.IOException
import java.net.HttpURLConnection

private const val JSON = "application/json"

/** Adds MAL's JSON headers + the Bearer token, mapping 401 to re-login (port of Doki). */
class MALInterceptor(private val storage: ScrobblerStorage) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val sourceRequest = chain.request()
        val request = sourceRequest.newBuilder()
        request.header("Content-Type", JSON)
        request.header("Accept", JSON)
        val isAuthRequest = sourceRequest.url.pathSegments.contains("oauth2")
        if (!isAuthRequest) {
            storage.accessToken?.let { request.header("Authorization", "Bearer $it") }
        }
        val response = chain.proceed(request.build())
        if (!isAuthRequest && response.code == HttpURLConnection.HTTP_UNAUTHORIZED) {
            response.close()
            throw ScrobblerAuthRequiredException(ScrobblerService.MAL)
        }
        // MAL serves an HTML error page (e.g. rate limit) instead of JSON — surface it as an error.
        if (response.header("content-type")?.startsWith("text/html") == true) {
            val code = response.code
            response.close()
            throw IOException("MAL: unexpected HTML response ($code)")
        }
        return response
    }
}
