package org.nekosukuriputo.nekuva.scrobbling.common.domain

import org.nekosukuriputo.nekuva.parsers.model.Manga
import org.nekosukuriputo.nekuva.scrobbling.common.domain.model.ScrobblerService

/** Aggregates all available scrobblers (Doki's ScrobblerRepositoryMap equivalent). */
class ScrobblerManager(val scrobblers: List<Scrobbler>) {

    operator fun get(service: ScrobblerService): Scrobbler? = scrobblers.find { it.scrobblerService == service }

    /** Scrobblers whose OAuth client id has been configured (shown in the Services screen). */
    val configured: List<Scrobbler> get() = scrobblers.filter { it.scrobblerService.isConfigured }

    /** Push read progress to every authorized scrobbler that has this manga linked. */
    suspend fun scrobble(manga: Manga, chapterId: Long) {
        for (scrobbler in scrobblers) {
            if (scrobbler.isEnabled) scrobbler.tryScrobble(manga, chapterId)
        }
    }
}
