package org.nekosukuriputo.nekuva.core.extensions

import org.nekosukuriputo.nekuva.parsers.MangaLoaderContext
import org.nekosukuriputo.nekuva.parsers.MangaParser
import java.io.File
import java.net.URLClassLoader

// Classes the PLUGIN owns (loaded child-first from the bundle): parser implementations, the generated
// factory, the source enum, and the extension entry point. Everything else — the shared contract
// (MangaParser, MangaLoaderContext, models, config, exception, network, util) plus okhttp/jsoup/kotlin —
// is delegated to the host loader so the ABI is identical on both sides (mirrors Usagi's PluginClassLoader).
private val PLUGIN_OWNED_EXACT = setOf(
    "org.nekosukuriputo.nekuva.parsers.NekuvaExtensions",
    "org.nekosukuriputo.nekuva.parsers.SourceDescriptor",
    "org.nekosukuriputo.nekuva.parsers.model.MangaParserSource",
)
private val PLUGIN_OWNED_PREFIX = listOf(
    "org.nekosukuriputo.nekuva.parsers.MangaParserFactory", // generated newParser factory
    "org.nekosukuriputo.nekuva.parsers.site.",              // parser implementations
    "org.nekosukuriputo.nekuva.parsers.core.",              // base parser abstractions they extend
)

private class ExtensionClassLoader(jar: File, parent: ClassLoader) :
    URLClassLoader(arrayOf(jar.toURI().toURL()), parent) {

    private fun isPluginOwned(name: String): Boolean =
        name in PLUGIN_OWNED_EXACT || PLUGIN_OWNED_PREFIX.any { name.startsWith(it) }

    override fun loadClass(name: String, resolve: Boolean): Class<*> {
        synchronized(getClassLoadingLock(name)) {
            findLoadedClass(name)?.let {
                if (resolve) resolveClass(it)
                return it
            }
            if (isPluginOwned(name)) {
                runCatching {
                    val c = findClass(name)
                    if (resolve) resolveClass(c)
                    return c
                }
                // fall through to parent if not present in the bundle
            }
            return super.loadClass(name, resolve)
        }
    }
}

actual fun loadExtension(path: String): LoadedExtension? = runCatching {
    val jar = File(path).takeIf { it.isFile } ?: return null
    // Parent = the host loader that already has the shared contract (the bundled baseline exts).
    val loader = ExtensionClassLoader(jar, MangaParser::class.java.classLoader)
    val entry = loader.loadClass("org.nekosukuriputo.nekuva.parsers.NekuvaExtensions")
    val abi = entry.getField("ABI_VERSION").getInt(null)
    if (abi != HOST_EXT_ABI_VERSION) return null
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

    object : LoadedExtension {
        override val abiVersion: Int = abi
        override val sources: List<ExtSource> = sources
        override fun createParser(sourceName: String, context: MangaLoaderContext): MangaParser =
            createParserMethod.invoke(instance, sourceName, context) as MangaParser
    }
}.getOrNull()
