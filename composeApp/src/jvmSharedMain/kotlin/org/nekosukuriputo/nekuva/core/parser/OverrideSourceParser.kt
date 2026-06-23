package org.nekosukuriputo.nekuva.core.parser

import org.nekosukuriputo.nekuva.parsers.MangaParser
import org.nekosukuriputo.nekuva.parsers.model.MangaParserSource

/**
 * Wraps a parser loaded from a runtime extension bundle so it reports the HOST's [MangaParserSource]
 * (the baseline enum constant of the same name) for host-facing identity (repository.source → DB / nav),
 * while delegating the actual parsing to the bundle's parser.
 *
 * The bundle is loaded in a separate class loader, so its `MangaParserSource` is a *different* class than
 * the host's. Per-source request handling is resolved by NAME (see CommonHeadersInterceptor), so the
 * bundle parser's own enum on request tags is fine; this wrapper only keeps host identity consistent.
 */
internal class OverrideSourceParser(
	private val delegate: MangaParser,
	override val source: MangaParserSource,
) : MangaParser by delegate
