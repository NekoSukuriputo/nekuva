package org.nekosukuriputo.nekuva.core.extensions

import java.security.KeyFactory
import java.security.Signature
import java.security.spec.X509EncodedKeySpec
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

/**
 * Base64 of the DER X.509 (SubjectPublicKeyInfo) RSA public key whose private half signs `index.json`
 * in the nekuva-exts release CI. The downloaded catalog must carry a matching `index.json.sig`, so the
 * app only trusts bundles published by the maintainer (defence-in-depth on top of HTTPS + sha256).
 *
 * BLANK = verification disabled (bootstrap). To enforce it, generate a keypair and set this:
 *   openssl genpkey -algorithm RSA -pkeyopt rsa_keygen_bits:2048 -out ext_signing.pem   (→ CI secret EXT_SIGNING_KEY)
 *   openssl rsa -in ext_signing.pem -pubout -outform DER | base64 -w0                   (→ paste below)
 */
const val EXT_PUBLIC_KEY_B64: String =
    "MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEAsvw+3iEu7zQNbLF97BODE6Iqyw2AfCLA2LqChDfdBR995UgXP8lkaJlF9p4PzhtMSxx7oojSJAukJUFeit4/dOpln0QXiyqv6wEHn2Ute/QffSxnn4vaauW/1nUzmPgyv91WiWmtI7vFqbrrRHsbDKpfRCZp7tQJ60jlc0jhaFvG1mIMQFBgN6CQNFPz/stp0RZHe2F+KEaI8y7yPXlGrwe7SHp0jxgT0Z4bwxjRVnrn4yj730+xO/mMGoNqs3PUag4ppR9iijL4JbWPIl0dpV3dr7il7U/6u3eNR55egvv/Bc7vLf/7roI/WS8df0CcSPQe3Rjk7wlDeR0nSBHVyQIDAQAB"

/**
 * Verify [data] (the raw `index.json` bytes) against [signatureB64] (base64 SHA256withRSA signature).
 * Returns true when no public key is configured yet (bootstrap); otherwise requires a valid signature.
 */
@OptIn(ExperimentalEncodingApi::class)
fun verifyExtensionSignature(data: ByteArray, signatureB64: String?): Boolean {
    if (EXT_PUBLIC_KEY_B64.isBlank()) return true // not configured — accept (HTTPS + sha256 still apply)
    val sig = signatureB64?.trim().takeUnless { it.isNullOrEmpty() } ?: return false
    return runCatching {
        val publicKey = KeyFactory.getInstance("RSA")
            .generatePublic(X509EncodedKeySpec(Base64.decode(EXT_PUBLIC_KEY_B64)))
        Signature.getInstance("SHA256withRSA").run {
            initVerify(publicKey)
            update(data)
            verify(Base64.decode(sig))
        }
    }.getOrDefault(false)
}
