package org.nekosukuriputo.nekuva.core.exceptions

import okhttp3.Headers
import org.nekosukuriputo.nekuva.core.model.UnknownMangaSource
import org.nekosukuriputo.nekuva.parsers.model.MangaSource
import org.nekosukuriputo.nekuva.parsers.network.CloudFlareHelper

class CloudFlareProtectedException(
	override val url: String,
	source: MangaSource?,
	@Transient val headers: Headers,
) : CloudFlareException("Protected by CloudFlare", CloudFlareHelper.PROTECTION_CAPTCHA) {

	override val source: MangaSource = source ?: UnknownMangaSource
}

/**
 * The User-Agent OkHttp sent for the request that hit the CAPTCHA wall. The challenge must be solved with the
 * SAME UA (cf_clearance is UA-bound). Null when unknown → the webview uses the engine's native UA, which
 * equals OkHttp's default anyway.
 */
fun CloudFlareException.requestUserAgent(): String? =
	(this as? CloudFlareProtectedException)?.headers?.get("User-Agent")

