package org.nekosukuriputo.nekuva.core.extensions

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Integration check for the Desktop extension loader: loads a locally-built plugin jar and verifies its
 * sources enumerate through the selective-delegation class loader. Self-skips unless `-PextJar=<path>` is
 * passed (forwarded to the `nekuva.ext.jar` system property by the build), so normal/CI runs are no-ops.
 */
class ExtensionLoaderTest {

    @Test
    fun loadsBundleAndListsSources() {
        val jarPath = System.getProperty("nekuva.ext.jar")
        if (jarPath.isNullOrBlank()) {
            println("[ExtensionLoaderTest] skipped — pass -PextJar=<plugin jar> to run")
            return
        }
        val ext = loadExtension(jarPath)
        assertNotNull(ext, "loadExtension returned null for $jarPath")
        assertEquals(HOST_EXT_ABI_VERSION, ext.abiVersion, "ABI mismatch")
        assertTrue(ext.sources.isNotEmpty(), "bundle exposed no sources")
        println(
            "[ExtensionLoaderTest] abi=${ext.abiVersion} sources=${ext.sources.size} " +
                "sample=${ext.sources.take(5).map { it.name }}",
        )
    }
}
