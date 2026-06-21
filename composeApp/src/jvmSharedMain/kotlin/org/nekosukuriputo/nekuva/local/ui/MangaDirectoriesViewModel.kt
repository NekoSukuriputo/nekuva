package org.nekosukuriputo.nekuva.local.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.nekosukuriputo.nekuva.core.prefs.AppSettings
import org.nekosukuriputo.nekuva.local.data.LocalStorageManager
import java.io.File

/** One row in the "Manga directories" screen (Doki DirectoryConfigModel). */
data class DirectoryConfigModel(
    val title: String,
    val path: String,
    val isDefault: Boolean,
    val isAppPrivate: Boolean,
    val availableBytes: Long,
    val totalBytes: Long,
)

/**
 * "Manga directories" CRUD (Doki MangaDirectoriesViewModel): lists the app-private storage dirs (not
 * removable) + the user's custom dirs, each with a storage meter. Adding a custom dir writes it to
 * AppSettings.userSpecifiedMangaDirectories (picked up by LocalStorageManager.getConfiguredStorageDirs,
 * so the Local list scans it); removing drops it (and clears it as the default download dir if set).
 */
class MangaDirectoriesViewModel(
    private val storageManager: LocalStorageManager,
    private val settings: AppSettings,
    // Shared storage-change signal observed by the Local list — emit so it re-scans when dirs change.
    private val localStorageChanges: kotlinx.coroutines.flow.MutableSharedFlow<org.nekosukuriputo.nekuva.local.domain.model.LocalManga?>,
) : ViewModel() {

    private val _items = MutableStateFlow<List<DirectoryConfigModel>>(emptyList())
    val items: StateFlow<List<DirectoryConfigModel>> = _items.asStateFlow()

    init { loadList() }

    fun addDirectory(path: String) {
        viewModelScope.launch {
            val appDirs = withContext(Dispatchers.IO) { storageManager.getApplicationStorageDirs() }
            // Don't duplicate an app-private dir as a "custom" one.
            if (File(path) !in appDirs) {
                settings.userSpecifiedMangaDirectories = settings.userSpecifiedMangaDirectories + path
            }
            loadList()
            localStorageChanges.emit(null) // make the Local list re-scan the new directory
        }
    }

    fun removeDirectory(model: DirectoryConfigModel) {
        settings.userSpecifiedMangaDirectories = settings.userSpecifiedMangaDirectories - model.path
        if (settings.mangaStorageDir == model.path) settings.mangaStorageDir = null
        loadList()
        viewModelScope.launch { localStorageChanges.emit(null) }
    }

    private fun loadList() {
        viewModelScope.launch {
            _items.value = withContext(Dispatchers.IO) {
                val defaultDir = storageManager.getDefaultWriteableDir()?.absolutePath
                val appPaths = storageManager.getApplicationStorageDirs().map { it.absolutePath }.toSet()
                buildList {
                    storageManager.getApplicationStorageDirs().forEach { dir ->
                        add(dir.toModel(rawPath = dir.absolutePath, isDefault = dir.absolutePath == defaultDir, isAppPrivate = true))
                    }
                    // Custom dirs: key the model on the RAW stored string so Remove matches it even when the
                    // stored value isn't a valid absolute path (e.g. a foreign path left by an old restore).
                    settings.userSpecifiedMangaDirectories
                        .filter { File(it).absolutePath !in appPaths }
                        .forEach { raw ->
                            add(File(raw).toModel(rawPath = raw, isDefault = raw == settings.mangaStorageDir, isAppPrivate = false))
                        }
                }
            }
        }
    }

    private suspend fun File.toModel(rawPath: String, isDefault: Boolean, isAppPrivate: Boolean) = DirectoryConfigModel(
        title = storageManager.getDirectoryDisplayName(this, isFullPath = false),
        path = rawPath,
        isDefault = isDefault,
        isAppPrivate = isAppPrivate,
        availableBytes = runCatching { usableSpace }.getOrDefault(0L),
        totalBytes = runCatching { totalSpace }.getOrDefault(0L),
    )
}
