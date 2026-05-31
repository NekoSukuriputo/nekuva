package org.nekosukuriputo.nekuva.core.util.ext

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.internal.closeQuietly
import okio.IOException
import org.jsoup.HttpStatusException
import java.net.HttpURLConnection

private val TYPE_JSON = "application/json".toMediaType()

fun Response.ensureSuccess(): Response {
	if (!isSuccessful) {
		closeQuietly()
		throw IOException(message)
	}
	return this
}

fun Response.requireOk(): Response {
	if (code != HttpURLConnection.HTTP_OK) {
		closeQuietly()
		throw HttpStatusException(message, code, request.url.toString())
	}
	return this
}

