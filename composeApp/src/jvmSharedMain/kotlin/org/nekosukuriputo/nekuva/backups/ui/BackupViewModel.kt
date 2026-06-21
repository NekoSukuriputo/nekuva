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

/**
 * Thin UI delegate to the app-scoped [BackupRestoreManager] — the actual work runs in the manager so it
 * survives leaving this screen (background backup/restore with a notification).
 */
class BackupViewModel(
    private val manager: org.nekosukuriputo.nekuva.backups.domain.BackupRestoreManager,
) : ViewModel() {

    val isBusy: StateFlow<Boolean> get() = manager.isBusy
    val restorePrompt: StateFlow<RestorePrompt?> get() = manager.restorePrompt
    val events get() = manager.resultEvents

    fun createBackup(io: BackupIo, defaultName: String) = manager.createBackup(io, defaultName)

    /** Pick a backup, read it, and prompt the user for which sections to restore (Doki section picker). */
    fun restoreBackup(io: BackupIo) = manager.pickRestore(io)

    fun cancelRestore() = manager.cancelRestore()

    fun confirmRestore(selected: Set<BackupSection>) = manager.confirmRestore(selected)
}
