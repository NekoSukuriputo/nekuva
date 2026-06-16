package org.nekosukuriputo.nekuva.core.network.webview.adblock

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import org.nekosukuriputo.nekuva.core.prefs.AppSettings
import org.nekosukuriputo.nekuva.parsers.util.await
import java.io.File
import java.net.HttpURLConnection
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * EasyList-based ad blocker (Doki `core/network/webview/adblock/AdBlock`). [shouldLoadUrl] is consulted by
 * the in-app browser's request interception (Android WebView `shouldInterceptRequest` / Desktop KCEF
 * `onBeforeResourceLoad`). Rules are parsed lazily from [listFile]; [updateList] downloads/refreshes it.
 * A no-op (allow-all) while `adblock` is off or the list isn't downloaded yet.
 */
class AdBlock(
	private val settings: AppSettings,
	private val listFile: File,
	private val httpClient: OkHttpClient,
) {

	private var rules: RulesList? = null

	fun shouldLoadUrl(url: String, baseUrl: String?): Boolean {
		if (!settings.isAdBlockEnabled) return true
		return shouldLoadUrl(
			url.lowercase().toHttpUrlOrNull() ?: return true,
			baseUrl?.lowercase()?.toHttpUrlOrNull(),
		)
	}

	fun shouldLoadUrl(url: HttpUrl, baseUrl: HttpUrl?): Boolean {
		if (!settings.isAdBlockEnabled) {
			return true
		}
		val rulesList = synchronized(this) {
			rules ?: parseRules().also { rules = it }
		} ?: return true
		return rulesList[url, baseUrl] == null
	}

	private fun parseRules(): RulesList? = runCatching {
		if (!listFile.exists()) return null
		listFile.useLines { lines ->
			val parsed = RulesList()
			lines.forEach { line -> parsed.add(line) }
			parsed.trimToSize()
			parsed
		}
	}.getOrNull()

	/** Downloads EasyList into [listFile] (If-Modified-Since aware), then drops the parsed cache. */
	suspend fun updateList() = withContext(Dispatchers.IO) {
		val dateFormat = SimpleDateFormat(HTTP_DATE_FORMAT, Locale.ENGLISH)
		val requestBuilder = Request.Builder().url(EASYLIST_URL).get()
		if (listFile.exists() && listFile.length() > 0) {
			requestBuilder.header("If-Modified-Since", dateFormat.format(Date(listFile.lastModified())))
		}
		httpClient.newCall(requestBuilder.build()).await().use { response ->
			if (response.code == HttpURLConnection.HTTP_NOT_MODIFIED) return@withContext
			if (!response.isSuccessful) return@withContext
			val body = response.body ?: return@withContext
			listFile.parentFile?.mkdirs()
			val lastModified = response.header("Last-Modified")
				?.let { runCatching { dateFormat.parse(it) }.getOrNull() }?.time ?: System.currentTimeMillis()
			body.byteStream().use { input -> listFile.outputStream().use { out -> input.copyTo(out) } }
			listFile.setLastModified(lastModified)
			synchronized(this@AdBlock) { rules = null }
		}
	}

	private companion object {
		const val EASYLIST_URL = "https://easylist.to/easylist/easylist.txt"
		const val HTTP_DATE_FORMAT = "EEE, dd MMM yyyy HH:mm:ss 'GMT'"
	}
}
