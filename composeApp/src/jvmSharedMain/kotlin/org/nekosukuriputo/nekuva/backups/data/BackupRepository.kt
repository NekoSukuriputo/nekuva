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
import kotlinx.serialization.json.decodeToSequence
import kotlinx.serialization.json.encodeToStream
import kotlinx.serialization.serializer
import org.nekosukuriputo.nekuva.backups.data.model.BackupIndex
import org.nekosukuriputo.nekuva.backups.data.model.BookmarkBackup
import org.nekosukuriputo.nekuva.backups.data.model.CategoryBackup
import org.nekosukuriputo.nekuva.backups.data.model.FavouriteBackup
import org.nekosukuriputo.nekuva.backups.data.model.HistoryBackup
import org.nekosukuriputo.nekuva.backups.data.model.MangaBackup
import org.nekosukuriputo.nekuva.backups.domain.BackupSection
import org.nekosukuriputo.nekuva.core.db.MangaDatabase
import org.nekosukuriputo.nekuva.core.db.withTransactionKmp
import org.nekosukuriputo.nekuva.core.util.ext.printStackTraceDebug
import java.io.InputStream
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/** Number of restored entries per category (for the result message). */
data class RestoreResult(val restored: Int, val failed: Int)

@OptIn(ExperimentalSerializationApi::class)
class BackupRepository(
    private val database: MangaDatabase,
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
            output.writeJsonArray(
                BackupSection.FAVOURITES,
                database.getFavouritesDao().findAll().asFlow().map { FavouriteBackup(it) },
                serializer(),
            )
            output.writeJsonArray(
                BackupSection.BOOKMARKS,
                database.getBookmarksDao().dump().map { BookmarkBackup(it.first, it.second) },
                serializer(),
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
        // Trim old backups beyond maxCount (newest kept; filenames sort by their timestamp).
        if (maxCount != Int.MAX_VALUE) {
            dir.listFiles { f -> f.isFile && f.name.startsWith("nekuva_backup_") && f.name.endsWith(".zip") }
                ?.sortedByDescending { it.name }
                ?.drop(maxCount)
                ?.forEach { runCatching { it.delete() } }
        }
        return file
    }

    suspend fun restoreBackup(inputStream: InputStream): RestoreResult {
        var restored = 0
        var failed = 0
        ZipInputStream(inputStream).use { input ->
            var entry: ZipEntry? = input.nextEntry
            while (entry != null) {
                when (BackupSection.of(entry)) {
                    BackupSection.HISTORY -> input.readJsonArray<HistoryBackup>(serializer()).forEach {
                        if (restore { upsertManga(it.manga); getHistoryDao().upsert(it.toEntity()) }) restored++ else failed++
                    }
                    BackupSection.CATEGORIES -> input.readJsonArray<CategoryBackup>(serializer()).forEach {
                        if (restore { getFavouriteCategoriesDao().upsert(it.toEntity()) }) restored++ else failed++
                    }
                    BackupSection.FAVOURITES -> input.readJsonArray<FavouriteBackup>(serializer()).forEach {
                        if (restore { upsertManga(it.manga); getFavouritesDao().upsert(it.toEntity()) }) restored++ else failed++
                    }
                    BackupSection.BOOKMARKS -> input.readJsonArray<BookmarkBackup>(serializer()).forEach { bk ->
                        if (restore { upsertManga(bk.manga); getBookmarksDao().upsert(bk.bookmarks.map { it.toEntity() }) }) restored++ else failed++
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

    private fun <T> InputStream.readJsonArray(serializer: DeserializationStrategy<T>): Sequence<T> =
        json.decodeToSequence(this, serializer, DecodeSequenceMode.ARRAY_WRAPPED)
}
