package org.nekosukuriputo.nekuva.core.extensions

import android.content.Context
import dalvik.system.DexClassLoader
import org.koin.core.context.GlobalContext
import org.nekosukuriputo.nekuva.parsers.MangaParser
import java.io.File

// Loads the bundle's classes from its DEX child-first; the shared contract + libs come from the host
// (parent). Same selective delegation as Desktop, via dalvik's DexClassLoader (mirrors Usagi).
private class AndroidExtensionClassLoader(
    dexPath: String,
    optimizedDir: String,
    parent: ClassLoader,
) : DexClassLoader(dexPath, optimizedDir, null, parent) {

    override fun loadClass(name: String, resolve: Boolean): Class<*> {
        findLoadedClass(name)?.let { return it }
        if (isExtensionPluginClass(name)) {
            runCatching { return findClass(name) } // fall through to parent if absent in the bundle
        }
        return super.loadClass(name, resolve)
    }
}

actual fun loadExtension(path: String): LoadedExtension? = runCatching {
    val jar = File(path).takeIf { it.isFile } ?: return null
    val ctx = GlobalContext.get().get<Context>()
    // The bundle is a jar containing classes.dex (built by the exts CI with d8).
    val optimizedDir = File(ctx.codeCacheDir, "ext_dex").apply { mkdirs() }
    val parent = MangaParser::class.java.classLoader ?: return null
    loadExtensionFrom(AndroidExtensionClassLoader(jar.absolutePath, optimizedDir.absolutePath, parent))
}.onFailure {
    println("[Nekuva][ext] loadExtension failed: ${it::class.simpleName}: ${it.message}")
    it.printStackTrace()
}.getOrNull()
