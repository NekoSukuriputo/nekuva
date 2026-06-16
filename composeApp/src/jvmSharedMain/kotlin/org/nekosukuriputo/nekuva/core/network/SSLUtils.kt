package org.nekosukuriputo.nekuva.core.network

import okhttp3.OkHttpClient
import java.security.SecureRandom
import java.security.cert.X509Certificate
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSocketFactory
import javax.net.ssl.X509TrustManager

/**
 * Trusts ALL certificates / disables hostname verification (Doki `ssl_bypass`). Pure JVM so it works on
 * Android + Desktop. Applied at client-build time, so toggling the pref takes effect after an app restart
 * (same as Doki). Use only when a source's TLS chain can't be validated — it removes MITM protection.
 */
fun OkHttpClient.Builder.disableCertificateVerification() = also { builder ->
	runCatching {
		val trustAllCerts = object : X509TrustManager {
			override fun checkClientTrusted(chain: Array<X509Certificate>, authType: String) = Unit

			override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String) = Unit

			override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
		}
		val sslContext = SSLContext.getInstance("SSL")
		sslContext.init(null, arrayOf(trustAllCerts), SecureRandom())
		val sslSocketFactory: SSLSocketFactory = sslContext.socketFactory
		builder.sslSocketFactory(sslSocketFactory, trustAllCerts)
		builder.hostnameVerifier { _, _ -> true }
	}.onFailure {
		it.printStackTrace()
	}
}
