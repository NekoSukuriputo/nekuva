package org.nekosukuriputo.nekuva.scrobbling.kitsu.data

import okhttp3.Interceptor
import okhttp3.Response
import org.nekosukuriputo.nekuva.scrobbling.common.data.ScrobblerStorage
import org.nekosukuriputo.nekuva.scrobbling.common.domain.ScrobblerAuthRequiredException
import org.nekosukuriputo.nekuva.scrobbling.common.domain.model.ScrobblerService
import java.io.IOException
import java.net.HttpURLConnection

/** Adds Kitsu's JSON:API headers + the Bearer token, mapping 401 to re-login (port of Doki). */
class KitsuInterceptor(private val storage: ScrobblerStorage) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val sourceRequest = chain.request()
        val request = sourceRequest.newBuilder()
        request.header("Content-Type", VND_JSON)
        request.header("Accept", VND_JSON)
        val isAuthRequest = sourceRequest.url.pathSegments.contains("oauth")
        if (!isAuthRequest) {
            storage.accessToken?.let { request.header("Authorization", "Bearer $it") }
        }
        val response = chain.proceed(request.build())
        if (!isAuthRequest && response.code == HttpURLConnection.HTTP_UNAUTHORIZED) {
            response.close()
            throw ScrobblerAuthRequiredException(ScrobblerService.KITSU)
        }
        if (response.header("content-type")?.startsWith("text/html") == true) {
            val code = response.code
            response.close()
            throw IOException("Kitsu: unexpected HTML response ($code)")
        }
        return response
    }

    companion object {
        const val VND_JSON = "application/vnd.api+json"
    }
}
