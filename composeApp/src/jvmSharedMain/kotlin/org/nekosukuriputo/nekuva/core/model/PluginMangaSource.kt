package org.nekosukuriputo.nekuva.core.model

import org.nekosukuriputo.nekuva.parsers.model.ContentType
import org.nekosukuriputo.nekuva.parsers.model.MangaSource

/**
 * A source provided ONLY by a loaded runtime extension bundle — i.e. one the app does NOT ship a baseline
 * `MangaParserSource` enum constant for. It implements [MangaSource] (identified by [name]) so the rest of
 * the app can list/store/open it by name, while the actual parser comes from the bundle.
 */
data class PluginMangaSource(
    override val name: String,
    val title: String,
    val locale: String,
    val contentType: ContentType,
    val isBroken: Boolean,
) : MangaSource

/**
 * In-memory registry of plugin-provided sources, populated by `ExtensionManager` when a bundle loads.
 * Read by [MangaSource] (name → source resolution) and `MangaSourcesRepository` (enumeration). Empty when
 * no bundle is loaded, so it adds nothing to baseline behaviour.
 */
object PluginSourceRegistry {

    @Volatile
    var sources: List<PluginMangaSource> = emptyList()
        private set

    fun set(list: List<PluginMangaSource>) {
        sources = list
    }

    fun byName(name: String): PluginMangaSource? = sources.firstOrNull { it.name == name }
}
