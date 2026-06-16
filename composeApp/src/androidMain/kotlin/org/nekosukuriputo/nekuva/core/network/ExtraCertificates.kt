package org.nekosukuriputo.nekuva.core.network

import android.content.Context
import android.content.res.AssetManager
import android.util.Log
import okhttp3.OkHttpClient
import okhttp3.tls.HandshakeCertificates
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate

// disableCertificateVerification() lives in jvmShared SSLUtils (pure JVM, shared android+desktop).

fun OkHttpClient.Builder.installExtraCertificates(context: Context) = also { builder ->
	val certificatesBuilder = HandshakeCertificates.Builder()
		.addPlatformTrustedCertificates()
	val assets = context.assets.list("").orEmpty()
	for (path in assets) {
		if (path.endsWith(".pem")) {
			val cert = loadCert(context, path) ?: continue
			certificatesBuilder.addTrustedCertificate(cert)
		}
	}
	val certificates = certificatesBuilder.build()
	builder.sslSocketFactory(certificates.sslSocketFactory(), certificates.trustManager)
}

private fun loadCert(context: Context, path: String): X509Certificate? = runCatching {
	val cf = CertificateFactory.getInstance("X.509")
	context.assets.open(path, AssetManager.ACCESS_STREAMING).use {
		cf.generateCertificate(it)
	} as X509Certificate
}.onFailure { e ->
	e.printStackTrace()
}.onSuccess {
	if (false) {
		Log.i("ExtraCerts", "Loaded cert $path")
	}
}.getOrNull()
