package org.nekosukuriputo.nekuva.core.network

import okhttp3.Authenticator
import okhttp3.Credentials
import okhttp3.Request
import okhttp3.Response
import okhttp3.Route
import org.nekosukuriputo.nekuva.core.prefs.AppSettings
import java.io.IOException
import java.net.InetSocketAddress
import java.net.PasswordAuthentication
import java.net.Proxy
import java.net.ProxySelector
import java.net.SocketAddress
import java.net.URI
import java.net.Authenticator as JavaAuthenticator

/**
 * User-configurable HTTP/SOCKS proxy (Doki `ProxyProvider`). Exposes a [ProxySelector] + OkHttp
 * [Authenticator] that read [AppSettings] live, so changing the proxy takes effect for new connections
 * without rebuilding the client. Also installs JVM-global defaults so other stacks (KCEF/WebView, JDK
 * HTTP) honour the same proxy. Invalid/incomplete config degrades to a direct connection (logged) rather
 * than failing every request.
 */
class ProxyProvider(
	private val settings: AppSettings,
) {

	private var cachedProxy: Proxy? = null

	val selector = object : ProxySelector() {
		override fun select(uri: URI?): List<Proxy> = listOf(getProxy())

		override fun connectFailed(uri: URI?, sa: SocketAddress?, ioe: IOException?) {
			ioe?.printStackTrace()
		}
	}

	val authenticator = ProxyAuthenticator()

	init {
		runCatching {
			ProxySelector.setDefault(selector)
			JavaAuthenticator.setDefault(authenticator)
		}
	}

	private fun isProxyEnabled() = settings.proxyType != Proxy.Type.DIRECT

	private fun getProxy(): Proxy {
		val type = settings.proxyType
		if (type == Proxy.Type.DIRECT) return Proxy.NO_PROXY
		val address = settings.proxyAddress
		val port = settings.proxyPort
		if (address.isNullOrEmpty() || port < 0 || port > 0xFFFF) {
			// Incomplete config: don't break every request — fall back to direct.
			return Proxy.NO_PROXY
		}
		cachedProxy?.let {
			val addr = it.address() as? InetSocketAddress
			if (addr != null && it.type() == type && addr.port == port && addr.hostString == address) {
				return it
			}
		}
		return Proxy(type, InetSocketAddress(address, port)).also { cachedProxy = it }
	}

	inner class ProxyAuthenticator : Authenticator, JavaAuthenticator() {

		override fun authenticate(route: Route?, response: Response): Request? {
			if (!isProxyEnabled()) return null
			if (response.request.header(PROXY_AUTHORIZATION) != null) return null
			val login = settings.proxyLogin ?: return null
			val password = settings.proxyPassword ?: return null
			return response.request.newBuilder()
				.header(PROXY_AUTHORIZATION, Credentials.basic(login, password))
				.build()
		}

		public override fun getPasswordAuthentication(): PasswordAuthentication? {
			if (!isProxyEnabled()) return null
			val login = settings.proxyLogin ?: return null
			val password = settings.proxyPassword ?: return null
			return PasswordAuthentication(login, password.toCharArray())
		}
	}

	private companion object {
		const val PROXY_AUTHORIZATION = "Proxy-Authorization"
	}
}
