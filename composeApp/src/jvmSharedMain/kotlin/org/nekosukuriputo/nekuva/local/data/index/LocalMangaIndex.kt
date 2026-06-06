package org.nekosukuriputo.nekuva.local.data.index

import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.nekosukuriputo.nekuva.local.domain.model.LocalManga
import org.nekosukuriputo.nekuva.local.data.input.LocalMangaParser
import java.io.File
import java.util.concurrent.ConcurrentHashMap

class LocalMangaIndex : FlowCollector<LocalManga?> {

    private val cache = ConcurrentHashMap<Long, LocalManga>()
    private val mutex = Mutex()

    override suspend fun emit(value: LocalManga?) {
        if (value != null) {
            put(value)
        }
    }

    suspend fun updateIfRequired() {}

    suspend fun get(mangaId: Long, withDetails: Boolean): LocalManga? {
        return cache[mangaId]
    }

    suspend operator fun contains(mangaId: Long): Boolean {
        return cache.containsKey(mangaId)
    }

    suspend fun put(manga: LocalManga) = mutex.withLock {
        cache[manga.manga.id] = manga
    }

    suspend fun delete(mangaId: Long) {
        cache.remove(mangaId)
    }

    suspend fun getAvailableTags(skipNsfw: Boolean): List<String> {
        return cache.values.flatMap { it.manga.tags?.map { it.title } ?: emptyList() }.distinct()
    }
}


