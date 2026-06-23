package org.nekosukuriputo.nekuva.core.model

import org.nekosukuriputo.nekuva.parsers.model.ContentType
import org.nekosukuriputo.nekuva.parsers.model.MangaParserSource
import org.nekosukuriputo.nekuva.parsers.model.MangaSource

data object LocalMangaSource : MangaSource {
    override val name = "LOCAL"
}

data object UnknownMangaSource : MangaSource {
    override val name = "UNKNOWN"
}

data object TestMangaSource : MangaSource {
    override val name = "TEST"
}

fun MangaSource(name: String?): MangaSource {
    when (name ?: return UnknownMangaSource) {
        UnknownMangaSource.name -> return UnknownMangaSource
        LocalMangaSource.name -> return LocalMangaSource
        TestMangaSource.name -> return TestMangaSource
    }
    // We will handle ExternalMangaSource later since it depends on Android's DocumentFile
    // For now, if it starts with content:, return UnknownMangaSource
    if (name.startsWith("content:")) {
        return UnknownMangaSource
    }
    MangaParserSource.entries.forEach {
        if (it.name == name) return it
    }
    // A source provided only by a loaded runtime extension bundle (no bundled enum constant).
    PluginSourceRegistry.byName(name)?.let { return it }
    return UnknownMangaSource
}

fun Collection<String>.toMangaSources() = map(::MangaSource)

fun MangaSource.isNsfw(): Boolean = when (this) {
    is MangaSourceInfo -> mangaSource.isNsfw()
    is MangaParserSource -> contentType == ContentType.HENTAI
    is PluginMangaSource -> contentType == ContentType.HENTAI
    else -> false
}

tailrec fun MangaSource.unwrap(): MangaSource = if (this is MangaSourceInfo) {
    mangaSource.unwrap()
} else {
    this
}
