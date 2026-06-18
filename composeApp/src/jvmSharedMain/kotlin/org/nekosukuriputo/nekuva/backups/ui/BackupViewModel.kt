package org.nekosukuriputo.nekuva.backups.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.nekosukuriputo.nekuva.backups.data.BackupRepository
import org.nekosukuriputo.nekuva.backups.data.RestoreResult
import org.nekosukuriputo.nekuva.backups.domain.BackupSection

sealed interface BackupEvent {
    data object Created : BackupEvent
    data class Restored(val result: RestoreResult) : BackupEvent
    data class Error(val message: String?) : BackupEvent
}

/** A picked backup awaiting the user's choice of which sections to restore (Doki restore section picker). */
class RestorePrompt(val bytes: ByteArray, val sections: Set<BackupSection>)

class BackupViewModel(
    private val repository: BackupRepository,
) : ViewModel() {

    private val _isBusy = MutableStateFlow(false)
    val isBusy: StateFlow<Boolean> = _isBusy.asStateFlow()

    private val _restorePrompt = MutableStateFlow<RestorePrompt?>(null)
    val restorePrompt: StateFlow<RestorePrompt?> = _restorePrompt.asStateFlow()

    val events = MutableSharedFlow<BackupEvent>(extraBufferCapacity = 1)

    fun createBackup(io: BackupIo, defaultName: String) {
        viewModelScope.launch {
            _isBusy.value = true
            try {
                val saved = io.pickAndWrite(defaultName) { repository.createBackup(it) }
                if (saved) events.emit(BackupEvent.Created)
            } catch (e: Exception) {
                events.emit(BackupEvent.Error(e.message ?: e.javaClass.simpleName))
            } finally {
                _isBusy.value = false
            }
        }
    }

    /** Pick a backup, read it, and prompt the user for which sections to restore (Doki section picker). */
    fun restoreBackup(io: BackupIo) {
        viewModelScope.launch {
            _isBusy.value = true
            try {
                var bytes: ByteArray? = null
                val opened = io.pickAndRead { bytes = it.readBytes() }
                if (opened) {
                    val data = bytes
                    val sections = data?.let { repository.peekSections(it) }.orEmpty()
                    if (data == null || sections.isEmpty()) {
                        events.emit(BackupEvent.Error(null))
                    } else {
                        _restorePrompt.value = RestorePrompt(data, sections)
                    }
                }
            } catch (e: Exception) {
                events.emit(BackupEvent.Error(e.message ?: e.javaClass.simpleName))
            } finally {
                _isBusy.value = false
            }
        }
    }

    fun cancelRestore() {
        _restorePrompt.value = null
    }

    fun confirmRestore(selected: Set<BackupSection>) {
        val prompt = _restorePrompt.value ?: return
        _restorePrompt.value = null
        if (selected.isEmpty()) return
        viewModelScope.launch {
            _isBusy.value = true
            try {
                val result = repository.restoreBackup(java.io.ByteArrayInputStream(prompt.bytes), selected)
                events.emit(BackupEvent.Restored(result))
            } catch (e: Exception) {
                events.emit(BackupEvent.Error(e.message ?: e.javaClass.simpleName))
            } finally {
                _isBusy.value = false
            }
        }
    }
}
