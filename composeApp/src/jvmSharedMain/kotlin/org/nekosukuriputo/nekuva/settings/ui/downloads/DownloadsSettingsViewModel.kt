package org.nekosukuriputo.nekuva.settings.ui.downloads

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.nekosukuriputo.nekuva.core.prefs.AppSettings
import org.nekosukuriputo.nekuva.core.prefs.DownloadFormat
import org.nekosukuriputo.nekuva.local.data.LocalStorageManager

data class DirItem(
    val path: String,
    val name: String,
    val isDefault: Boolean,
    val isCustom: Boolean,
)

class DownloadsSettingsViewModel(
    private val settings: AppSettings,
    private val storageManager: LocalStorageManager,
) : ViewModel() {

    private val _directories = MutableStateFlow<List<DirItem>>(emptyList())
    val directories: StateFlow<List<DirItem>> = _directories.asStateFlow()

    private val _format = MutableStateFlow(settings.preferredDownloadFormat)
    val format: StateFlow<DownloadFormat> = _format.asStateFlow()

    // Default "save page" directory (Doki pages_dir); null = platform default.
    private val _pageSaveDir = MutableStateFlow(settings.getPagesSaveDirUri())
    val pageSaveDir: StateFlow<String?> = _pageSaveDir.asStateFlow()

    fun setPageSaveDir(path: String) {
        settings.setPagesSaveDir(path)
        _pageSaveDir.value = path
    }

    init {
        loadDirectories()
    }

    fun loadDirectories() {
        viewModelScope.launch {
            val dirs = storageManager.getWriteableDirs()
            val default = storageManager.getDefaultWriteableDir()?.absolutePath
            val custom = settings.userSpecifiedMangaDirectories
            _directories.value = dirs.map { d ->
                DirItem(
                    path = d.absolutePath,
                    name = storageManager.getDirectoryDisplayName(d, isFullPath = true),
                    isDefault = d.absolutePath == default,
                    isCustom = d.absolutePath in custom,
                )
            }
        }
    }

    fun addDirectory(path: String) {
        settings.userSpecifiedMangaDirectories = settings.userSpecifiedMangaDirectories + path
        loadDirectories()
    }

    fun removeDirectory(path: String) {
        settings.userSpecifiedMangaDirectories = settings.userSpecifiedMangaDirectories - path
        if (settings.mangaStorageDir == path) settings.mangaStorageDir = null
        loadDirectories()
    }

    fun setDefault(path: String) {
        settings.mangaStorageDir = path
        loadDirectories()
    }

    fun setFormat(value: DownloadFormat) {
        settings.preferredDownloadFormat = value
        _format.value = value
    }
}
