package org.nekosukuriputo.nekuva.backups.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.collectIndexed
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerializationStrategy
import kotlinx.serialization.json.DecodeSequenceMode
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.decodeToSequence
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.encodeToStream
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
import kotlinx.serialization.serializer
import org.nekosukuriputo.nekuva.backups.data.model.BackupIndex
import org.nekosukuriputo.nekuva.backups.data.model.BookmarkBackup
import org.nekosukuriputo.nekuva.backups.data.model.CategoryBackup
import org.nekosukuriputo.nekuva.backups.data.model.FavouriteBackup
import org.nekosukuriputo.nekuva.backups.data.model.HistoryBackup
import org.nekosukuriputo.nekuva.backups.data.model.MangaBackup
import org.nekosukuriputo.nekuva.backups.data.model.ScrobblingBackup
import org.nekosukuriputo.nekuva.backups.data.model.SourceBackup
import org.nekosukuriputo.nekuva.backups.data.model.StatisticBackup
import org.nekosukuriputo.nekuva.backups.domain.BackupSection
import org.nekosukuriputo.nekuva.core.db.MangaDatabase
import org.nekosukuriputo.nekuva.core.db.withTransactionKmp
import org.nekosukuriputo.nekuva.core.util.ext.printStackTraceDebug
import org.nekosukuriputo.nekuva.filter.data.PersistableFilter
import org.nekosukuriputo.nekuva.filter.data.SavedFiltersRepository
import java.io.InputStream
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/** Number of restored entries (for the result message). */
data class RestoreResult(val restored: Int, val failed: Int)

private const val TAP_GRID_PREFIX = "tap_grid_"
// Sensitive / device-local keys excluded from the settings backup (Doki parity). The manga-directory
// paths (local_manga_dirs / local_storage) are device/OS-specific — restoring Android paths onto Desktop
// (or vice versa) creates broken directories, so they must NOT cross devices.
private val EXCLUDED_SETTINGS = setOf(
    "app_password", "app_password_num", "proxy_login", "proxy_password", "incognito",
    "local_manga_dirs", "local_storage",
)

@OptIn(ExperimentalSerializationApi::class)
class BackupRepository(
    private val database: MangaDatabase,
    private val savedFiltersRepository: SavedFiltersRepository,
) {

    private val json = Json {
        allowSpecialFloatingPointValues = true
        coerceInputValues = true
        encodeDefaults = true
        ignoreUnknownKeys = true
    }

    suspend fun createBackup(outputStream: OutputStream) {
        ZipOutputStream(outputStream).use { output ->
            output.writeJsonArray(BackupSection.INDEX, flowOf(BackupIndex()), serializer())
            output.writeJsonArray(
                BackupSection.HISTORY,
                database.getHistoryDao().dump().map { HistoryBackup(it) },
                serializer(),
            )
            output.writeJsonArray(
                BackupSection.CATEGORIES,
                database.getFavouriteCategoriesDao().findAll().asFlow().map { CategoryBackup(it) },
                serializer(),
            )
            // dump() (not findAll(), which GROUP BYs manga_id) so a manga in MULTIPLE categories keeps a row
            // per category — else restored favourites collapse / mix across categories.
            output.writeJsonArray(
                BackupSection.FAVOURITES,
                database.getFavouritesDao().dump().map { FavouriteBackup(it) },
                serializer(),
            )
            // Settings + reader-grid live in the same prefs store; split by the tap_grid_ prefix (Doki format).
            val prefs = dumpAppPreferences()
            output.writeSettingsEntry(
                BackupSection.SETTINGS,
                prefs.filterKeys { !it.startsWith(TAP_GRID_PREFIX) && it !in EXCLUDED_SETTINGS },
            )
            output.writeSettingsEntry(
                BackupSection.SETTINGS_READER_GRID,
                prefs.filterKeys { it.startsWith(TAP_GRID_PREFIX) },
            )
            output.writeJsonArray(
                BackupSection.BOOKMARKS,
                database.getBookmarksDao().dump().map { BookmarkBackup(it.first, it.second) },
                serializer(),
            )
            output.writeJsonArray(
                BackupSection.SOURCES,
                database.getSourcesDao().findAll().filter { it.isEnabled }.asFlow().map { SourceBackup(it) },
                serializer(),
            )
            output.writeJsonArray(
                BackupSection.SCROBBLING,
                database.getScrobblingDao().dumpEnabled().map { ScrobblingBackup(it) },
                serializer(),
            )
            output.writeJsonArray(
                BackupSection.STATS,
                database.getStatsDao().dumpEnabled().map { StatisticBackup(it) },
                serializer(),
            )
            output.writeJsonArray(
                BackupSection.SAVED_FILTERS,
                savedFiltersRepository.getAllFilters().asFlow(),
                serializer<PersistableFilter>(),
            )
            output.finish()
        }
    }

    /**
     * Write a timestamped backup zip into [dirPath] (periodic backup, Doki PeriodicalBackupWorker), then keep
     * only the newest [maxCount] `nekuva_backup_*.zip` files. Returns the created file, or null on failure.
     */
    suspend fun createBackupToDirectory(dirPath: String, maxCount: Int): java.io.File? {
        val dir = java.io.File(dirPath)
        if (!dir.exists()) dir.mkdirs()
        if (!dir.isDirectory) return null
        val file = java.io.File(dir, "nekuva_backup_${System.currentTimeMillis()}.zip")
        java.io.FileOutputStream(file).use { createBackup(it) }
        if (maxCount != Int.MAX_VALUE) {
            dir.listFiles { f -> f.isFile && f.name.startsWith("nekuva_backup_") && f.name.endsWith(".zip") }
                ?.sortedByDescending { it.name }
                ?.drop(maxCount)
                ?.forEach { runCatching { it.delete() } }
        }
        return file
    }

    /** Which restorable sections a backup contains (for the restore section picker; excludes INDEX). */
    fun peekSections(bytes: ByteArray): Set<BackupSection> {
        val result = LinkedHashSet<BackupSection>()
        ZipInputStream(java.io.ByteArrayInputStream(bytes)).use { input ->
            var entry: ZipEntry? = input.nextEntry
            while (entry != null) {
                BackupSection.of(entry)?.takeIf { it != BackupSection.INDEX }?.let { result += it }
                input.closeEntry()
                entry = input.nextEntry
            }
        }
        return result
    }

    suspend fun restoreBackup(
        inputStream: InputStream,
        sections: Set<BackupSection> = BackupSection.entries.toSet(),
    ): RestoreResult {
        var restored = 0
        var failed = 0
        ZipInputStream(inputStream).use { input ->
            var entry: ZipEntry? = input.nextEntry
            while (entry != null) {
                when (BackupSection.of(entry)?.takeIf { it in sections }) {
                    BackupSection.HISTORY -> input.readJsonArray<HistoryBackup>(serializer()).forEach {
                        if (restore { upsertManga(it.manga); getHistoryDao().upsert(it.toEntity()) }) restored++ else failed++
                    }
                    BackupSection.CATEGORIES -> input.readJsonArray<CategoryBackup>(serializer()).forEach {
                        if (restore { getFavouriteCategoriesDao().upsert(it.toEntity()) }) restored++ else failed++
                    }
                    BackupSection.FAVOURITES -> input.readJsonArray<FavouriteBackup>(serializer()).forEach {
                        if (restore { upsertManga(it.manga); getFavouritesDao().upsert(it.toEntity()) }) restored++ else failed++
                    }
                    BackupSection.SETTINGS, BackupSection.SETTINGS_READER_GRID -> {
                        if (runCatching { writeAppPreferences(input.readSettingsMap()) }.isSuccess) restored++ else failed++
                    }
                    BackupSection.BOOKMARKS -> input.readJsonArray<BookmarkBackup>(serializer()).forEach { bk ->
                        if (restore { upsertManga(bk.manga); getBookmarksDao().upsert(bk.bookmarks.map { it.toEntity() }) }) restored++ else failed++
                    }
                    BackupSection.SOURCES -> input.readJsonArray<SourceBackup>(serializer()).forEach {
                        if (restore { getSourcesDao().upsert(it.toEntity()) }) restored++ else failed++
                    }
                    BackupSection.SCROBBLING -> input.readJsonArray<ScrobblingBackup>(serializer()).forEach {
                        if (restore { getScrobblingDao().upsert(it.toEntity()) }) restored++ else failed++
                    }
                    BackupSection.STATS -> input.readJsonArray<StatisticBackup>(serializer()).forEach {
                        if (restore { getStatsDao().upsert(it.toEntity()) }) restored++ else failed++
                    }
                    BackupSection.SAVED_FILTERS -> input.readJsonArray<PersistableFilter>(serializer()).forEach {
                        if (runCatching { savedFiltersRepository.restore(it) }.isSuccess) restored++ else failed++
                    }
                    BackupSection.INDEX, null -> Unit
                }
                input.closeEntry()
                entry = input.nextEntry
            }
        }
        return RestoreResult(restored, failed)
    }

    private suspend inline fun restore(crossinline block: suspend MangaDatabase.() -> Unit): Boolean {
        return try {
            database.withTransactionKmp { database.block() }
            true
        } catch (e: Exception) {
            e.printStackTraceDebug()
            false
        }
    }

    private suspend fun MangaDatabase.upsertManga(manga: MangaBackup) {
        val tags = manga.tags.map { it.toEntity() }
        getTagsDao().upsert(tags)
        getMangaDao().upsert(manga.toEntity(), tags)
    }

    private suspend fun <T> ZipOutputStream.writeJsonArray(
        section: BackupSection,
        data: Flow<T>,
        serializer: SerializationStrategy<T>,
    ) {
        putNextEntry(ZipEntry(section.entryName))
        write("[".toByteArray())
        data.collectIndexed { index, value ->
            if (index > 0) write(",".toByteArray())
            json.encodeToStream(serializer, value, this)
        }
        write("]".toByteArray())
        closeEntry()
        flush()
    }

    /** Settings sections are an array wrapping a single key->value object (Doki format: `[{...}]`). */
    private fun ZipOutputStream.writeSettingsEntry(section: BackupSection, values: Map<String, Any?>) {
        putNextEntry(ZipEntry(section.entryName))
        val obj = buildJsonObject {
            for ((k, v) in values) put(k, v.toJsonElement())
        }
        write("[".toByteArray())
        write(obj.toString().toByteArray())
        write("]".toByteArray())
        closeEntry()
        flush()
    }

    private fun InputStream.readSettingsMap(): Map<String, Any?> {
        val text = readBytes().decodeToString().ifBlank { return emptyMap() }
        val obj = json.parseToJsonElement(text).jsonArray.firstOrNull()?.jsonObject ?: return emptyMap()
        return obj.mapValues { (_, el) -> el.toKotlin() }.filterValues { it != null }
    }

    private fun <T> InputStream.readJsonArray(serializer: DeserializationStrategy<T>): Sequence<T> =
        json.decodeToSequence(this, serializer, DecodeSequenceMode.ARRAY_WRAPPED)

    private fun Any?.toJsonElement(): JsonElement = when (this) {
        is Boolean -> JsonPrimitive(this)
        is Int -> JsonPrimitive(this)
        is Long -> JsonPrimitive(this)
        is Float -> JsonPrimitive(this)
        is Double -> JsonPrimitive(this)
        is String -> JsonPrimitive(this)
        is Collection<*> -> buildJsonArray { forEach { add(JsonPrimitive(it.toString())) } }
        else -> JsonPrimitive(this?.toString() ?: "")
    }

    private fun JsonElement.toKotlin(): Any? = when (this) {
        is JsonArray -> map { it.jsonPrimitive.content } // string set
        is JsonPrimitive -> when {
            isString -> content
            booleanOrNull != null -> booleanOrNull
            intOrNull != null -> intOrNull
            longOrNull != null -> longOrNull
            doubleOrNull != null -> doubleOrNull
            else -> content
        }
        else -> null
    }
}
