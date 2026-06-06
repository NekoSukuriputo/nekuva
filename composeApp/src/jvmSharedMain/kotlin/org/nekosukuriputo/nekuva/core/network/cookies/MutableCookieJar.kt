package org.nekosukuriputo.nekuva.core.network.cookies


import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl

interface MutableCookieJar : CookieJar {

	override fun loadForRequest(url: HttpUrl): List<Cookie>

	override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>)

	fun removeCookies(url: HttpUrl, predicate: ((Cookie) -> Boolean)? = null)

	suspend fun clear(): Boolean
}


