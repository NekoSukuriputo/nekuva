package org.nekosukuriputo.nekuva.core.extensions

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Verifies the embedded [EXT_PUBLIC_KEY_B64] accepts a signature made by the maintainer's private key
 * (and rejects tampering). The signature below was produced with:
 *   printf 'nekuva-ext-signing-test' | openssl dgst -sha256 -sign ext_signing.pem | openssl base64 -A
 */
class ExtensionSigningTest {

    private val data = "nekuva-ext-signing-test".toByteArray()
    private val validSig =
        "Fbu9voaEFx39E7aidyWR0+klxPuOLAJK+25+MRx69IXSffn8TBdsf3AI0GsRV1eGwAS9sOE6F35tEurZdUSlO//t+BCtqFFyYMaYhwakqO6szO4ysa1oOJC2rTUWO+0cHx2ybvrszvR5b12kEMSbHlki1vvmWjmFfbtNnIDUJbylysVOpJoYfqx5h56qHrtHrfvgChIvIiCo2Up3K17ytVi53VVF8G/+n95gVi+D/8FbtQMdSrklMsw750ySwOeLuRjQxbhBxjAZENpoq5QjtANbitjIXsHb9rkuOXT14LDJyunrbnffaJzZ8xPVvjmjB4lIucmhvAD2eEC7i2wopA=="

    @Test
    fun acceptsMaintainerSignature() {
        // Skips automatically while no key is embedded (bootstrap); enforces once it is set.
        if (EXT_PUBLIC_KEY_B64.isBlank()) return
        assertTrue(verifyExtensionSignature(data, validSig), "valid signature must verify")
        assertFalse(verifyExtensionSignature("tampered".toByteArray(), validSig), "tampered data must fail")
        assertFalse(verifyExtensionSignature(data, null), "missing signature must fail when a key is set")
    }
}
