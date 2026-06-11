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

sealed interface BackupEvent {
    data object Created : BackupEvent
    data class Restored(val result: RestoreResult) : BackupEvent
    data class Error(val message: String?) : BackupEvent
}

class BackupViewModel(
    private val repository: BackupRepository,
) : ViewModel() {

    private val _isBusy = MutableStateFlow(false)
    val isBusy: StateFlow<Boolean> = _isBusy.asStateFlow()

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

    fun restoreBackup(io: BackupIo) {
        viewModelScope.launch {
            _isBusy.value = true
            try {
                var result: RestoreResult? = null
                val opened = io.pickAndRead { result = repository.restoreBackup(it) }
                if (opened) {
                    result?.let { events.emit(BackupEvent.Restored(it)) }
                }
            } catch (e: Exception) {
                events.emit(BackupEvent.Error(e.message ?: e.javaClass.simpleName))
            } finally {
                _isBusy.value = false
            }
        }
    }
}
