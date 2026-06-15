package org.nekosukuriputo.nekuva.core.image

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okio.FileSystem
import okio.Path
import okhttp3.OkHttpClient
import okhttp3.Request
import org.nekosukuriputo.nekuva.core.parser.MangaRepository
import org.nekosukuriputo.nekuva.core.parser.ParserMangaRepository
import org.nekosukuriputo.nekuva.parsers.model.MangaParserSource
import org.nekosukuriputo.nekuva.parsers.util.await

/**
 * Persistent source-favicon cache (Doki's FaviconCache). Each source's favicon is resolved + downloaded
 * ONCE and stored as a file; later loads read the file (no network). Resolution runs on an app-level
 * scope so it isn't cancelled when Coil's per-item composition scope is recycled — the cause of the
 * "rememberCoroutineScope left the composition" failures that left every icon on the placeholder.
 */
class FaviconCache(
    private val dir: Path,
    private val repositoryFactory: MangaRepository.Factory,
    private val httpClient: OkHttpClient,
) {
    private val fs = FileSystem.SYSTEM
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val inFlight = HashMap<String, Deferred<Path?>>()
    private val mutex = Mutex()

    /** Returns the cached favicon file path for [sourceName], fetching+storing it once if needed; null if unavailable. */
    suspend fun resolve(sourceName: String): Path? {
        val file = dir / sourceName
        if (fs.exists(file)) return file
        val deferred = mutex.withLock {
            inFlight[sourceName] ?: scope.async {
                runCatching { fetchAndStore(sourceName, file) }.getOrNull()
            }.also { inFlight[sourceName] = it }
        }
        return try {
            deferred.await()
        } finally {
            mutex.withLock { if (inFlight[sourceName] === deferred) inFlight.remove(sourceName) }
        }
    }

    private suspend fun fetchAndStore(sourceName: String, file: Path): Path? {
        val source = MangaParserSource.entries.find { it.name == sourceName } ?: return null
        val repo = repositoryFactory.create(source) as? ParserMangaRepository ?: return null
        val favicons = repo.getFavicons()
        val icon = favicons.find(FAVICON_SIZE) ?: favicons.firstOrNull() ?: return null
        val request = Request.Builder()
            .url(icon.url)
            .apply { favicons.referer?.takeIf { it.isNotEmpty() }?.let { header("Referer", it) } }
            .build()
        val bytes = httpClient.newCall(request).await().use { response ->
            if (!response.isSuccessful) return null
            response.body?.bytes() ?: return null
        }
        if (bytes.isEmpty()) return null
        fs.createDirectories(dir)
        fs.write(file) { write(bytes) }
        return file
    }

    private companion object {
        const val FAVICON_SIZE = 144
    }
}
