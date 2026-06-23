package org.nekosukuriputo.nekuva.core.extensions

import org.nekosukuriputo.nekuva.parsers.MangaLoaderContext
import org.nekosukuriputo.nekuva.parsers.MangaParser

/** Host⇄extension ABI the loader requires; must match nekuva-exts `NekuvaExtensions.ABI_VERSION`. */
const val HOST_EXT_ABI_VERSION: Int = 1

/** Host-side view of one source provided by a loaded extension bundle (mirrors exts `SourceDescriptor`). */
data class ExtSource(
    val name: String,
    val title: String,
    val locale: String,
    val contentType: String,
    val isBroken: Boolean,
)

/**
 * A loaded extension bundle. Its parsers live in a separate class loader; only the shared contract
 * ([MangaParser], [MangaLoaderContext], models) crosses the boundary (delegated to the host loader),
 * so a parser returned here is a real host [MangaParser] usable in the normal pipeline.
 */
interface LoadedExtension {
    val abiVersion: Int
    val sources: List<ExtSource>
    fun createParser(sourceName: String, context: MangaLoaderContext): MangaParser
}

/**
 * Load a downloaded extension bundle (the nekuva-exts plugin) at runtime, reading its sources via
 * reflection over `org.nekosukuriputo.nekuva.parsers.NekuvaExtensions`. Returns null if the bundle can't
 * be loaded or its ABI is incompatible with [HOST_EXT_ABI_VERSION].
 *
 * Desktop: `URLClassLoader` with selective delegation. Android: `DexClassLoader` (Step 3 — currently a
 * stub returning null; the app uses the bundled baseline). iOS: unsupported (bundled-only).
 */
expect fun loadExtension(path: String): LoadedExtension?
