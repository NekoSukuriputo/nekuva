package org.nekosukuriputo.nekuva.core.parser

import org.nekosukuriputo.nekuva.parsers.MangaParser
import org.nekosukuriputo.nekuva.parsers.model.MangaParserSource

/**
 * Wraps a parser loaded from a runtime extension bundle so it reports the HOST's [MangaParserSource]
 * (the baseline enum constant of the same name) instead of the bundle's own enum class.
 *
 * The bundle is loaded in a separate class loader, so its `MangaParserSource` is a *different* class
 * than the host's. When an extension overrides a source the app already ships, we keep the host enum
 * for identity/DB/navigation and only delegate the actual parsing to the bundle's parser — avoiding a
 * cross-class-loader `MangaParserSource` mismatch. All other behaviour is delegated unchanged.
 */
internal class OverrideSourceParser(
    private val delegate: MangaParser,
    override val source: MangaParserSource,
) : MangaParser by delegate
