package org.nekosukuriputo.nekuva.alternatives.domain

import org.nekosukuriputo.nekuva.parsers.model.Manga

/**
 * Auto-fix a manga by migrating it to the best alternative found in other sources (Doki AutoFixUseCase):
 * prefer an exact (case-insensitive) title match, else the first candidate, then [MigrateUseCase] to it.
 * The periodic batch service (Doki AutoFixService / WorkManager) is deferred (recorded in MIGRATION.md).
 */
class AutoFixUseCase(
    private val migrateUseCase: MigrateUseCase,
) {

    /** Picks the best candidate, migrates [oldManga] to it, and returns the chosen manga (or null if none). */
    suspend operator fun invoke(oldManga: Manga, candidates: List<Manga>): Manga? {
        if (candidates.isEmpty()) return null
        val best = candidates.firstOrNull { it.title.equals(oldManga.title, ignoreCase = true) }
            ?: candidates.first()
        migrateUseCase(oldManga, best)
        return best
    }
}
