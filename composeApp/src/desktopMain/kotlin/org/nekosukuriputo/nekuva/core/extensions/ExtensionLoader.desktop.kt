package org.nekosukuriputo.nekuva.core.extensions

import org.nekosukuriputo.nekuva.parsers.MangaParser
import java.io.File
import java.net.URLClassLoader

private class ExtensionClassLoader(jar: File, parent: ClassLoader) :
    URLClassLoader(arrayOf(jar.toURI().toURL()), parent) {

    override fun loadClass(name: String, resolve: Boolean): Class<*> {
        synchronized(getClassLoadingLock(name)) {
            findLoadedClass(name)?.let {
                if (resolve) resolveClass(it)
                return it
            }
            if (isExtensionPluginClass(name)) {
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
    loadExtensionFrom(ExtensionClassLoader(jar, MangaParser::class.java.classLoader))
}.getOrNull()
