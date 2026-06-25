package org.nekosukuriputo.nekuva.core.extensions

import org.nekosukuriputo.nekuva.parsers.MangaLoaderContext
import org.nekosukuriputo.nekuva.parsers.MangaParser

// Classes the BUNDLE owns (loaded child-first from the extension): parser implementations, the generated
// factory, and the entry point. Everything else — the shared contract
// (MangaParser/MangaLoaderContext/models/config/exception/network/util) + okhttp/jsoup/kotlin — is
// delegated to the host loader so the ABI is identical on both sides (mirrors Usagi's PluginClassLoader).
// Shared by the Desktop (URLClassLoader) and Android (DexClassLoader) extension class loaders.
//
// CRITICAL: `MangaParserSource` (the source enum) is NOT plugin-owned — it is part of the shared contract.
// It is the return type of `MangaParser.getSource()`, so if the bundle loaded its OWN copy, the host's
// `MangaParser` interface and the bundle's `AbstractMangaParser` would reference two different
// `MangaParserSource` classes and the JVM rejects the link with a *loader constraint violation*
// ("different Class objects for the type … MangaParserSource used in the signature"). That made EVERY
// runtime-extension parser override fail and silently fall back to the built-in parser. Keeping it
// host-loaded gives one enum on both sides. Consequence: a runtime bundle can fix/replace parsers for
// sources that exist in the host's (compile-time) enum, but cannot add a genuinely NEW source enum value —
// that still requires a host rebuild against the newer exts.
private val PLUGIN_OWNED_EXACT = setOf(
    "org.nekosukuriputo.nekuva.parsers.NekuvaExtensions",
    "org.nekosukuriputo.nekuva.parsers.SourceDescriptor",
)
private val PLUGIN_OWNED_PREFIX = listOf(
    "org.nekosukuriputo.nekuva.parsers.MangaParserFactory", // generated newParser factory
    "org.nekosukuriputo.nekuva.parsers.site.",              // parser implementations
    "org.nekosukuriputo.nekuva.parsers.core.",              // base parser abstractions they extend
)

/** Whether [name] should be loaded from the extension bundle (child-first) rather than the host. */
internal fun isExtensionPluginClass(name: String): Boolean =
    name in PLUGIN_OWNED_EXACT || PLUGIN_OWNED_PREFIX.any { name.startsWith(it) }

/**
 * Reflect a [LoadedExtension] out of a class loader that has the bundle on it (the host's contract is
 * shared via the parent loader). Returns null if the entry point is missing or the ABI is incompatible.
 */
internal fun loadExtensionFrom(loader: ClassLoader): LoadedExtension? = runCatching {
    val entry = loader.loadClass("org.nekosukuriputo.nekuva.parsers.NekuvaExtensions")
    val abi = entry.getField("ABI_VERSION").getInt(null)
    if (abi != HOST_EXT_ABI_VERSION) {
        lastExtensionError = "ABI mismatch: bundle=$abi, host=$HOST_EXT_ABI_VERSION"
        return null
    }
    val instance = entry.getField("INSTANCE").get(null)

    val descriptors = entry.getMethod("listSources").invoke(instance) as List<*>
    val sources = descriptors.mapNotNull { d ->
        d ?: return@mapNotNull null
        val c = d.javaClass
        ExtSource(
            name = c.getMethod("getName").invoke(d) as String,
            title = c.getMethod("getTitle").invoke(d) as String,
            locale = c.getMethod("getLocale").invoke(d) as String,
            contentType = (c.getMethod("getContentType").invoke(d) as Enum<*>).name,
            isBroken = c.getMethod("isBroken").invoke(d) as Boolean,
        )
    }
    val createParserMethod = entry.getMethod(
        "createParser",
        String::class.java,
        MangaLoaderContext::class.java,
    )

    lastExtensionError = null
    object : LoadedExtension {
        override val abiVersion: Int = abi
        override val sources: List<ExtSource> = sources
        override fun createParser(sourceName: String, context: MangaLoaderContext): MangaParser =
            createParserMethod.invoke(instance, sourceName, context) as MangaParser
    }
}.onFailure { e ->
    // Capture the ROOT cause (shown in the "Update extensions" error + logcat). Reflective invokes wrap the
    // real error in InvocationTargetException (message=null), so walk the cause chain to the actual throwable
    // — a release failure here usually means R8 stripped a class the bundle links against (add a keep).
    val root = generateSequence(e) { it.cause }.last()
    lastExtensionError = "${root::class.simpleName}: ${root.message ?: "(no message)"}"
    println("[Nekuva][ext] loadExtensionFrom failed: $lastExtensionError")
    e.printStackTrace()
}.getOrNull()
