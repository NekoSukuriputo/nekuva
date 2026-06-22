package org.nekosukuriputo.nekuva.backups.domain

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.nekosukuriputo.nekuva.backups.data.BackupRepository
import org.nekosukuriputo.nekuva.backups.data.RestoreResult
import org.nekosukuriputo.nekuva.backups.ui.BackupEvent
import org.nekosukuriputo.nekuva.backups.ui.BackupIo
import org.nekosukuriputo.nekuva.backups.ui.RestorePrompt

/**
 * Runs backup/restore in an app-scoped coroutine (NOT a screen's ViewModel scope) so the work survives
 * leaving the settings screen — the user can keep reading/exploring while it runs (Doki's background
 * BackupService/RestoreService). Shows a system notification (Android) / tray balloon (Desktop) for the
 * running + finished state. A singleton so its state/result outlive the screen.
 */
class BackupRestoreManager(
    private val repository: BackupRepository,
) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val _isBusy = MutableStateFlow(false)
    val isBusy: StateFlow<Boolean> = _isBusy.asStateFlow()

    private val _restorePrompt = MutableStateFlow<RestorePrompt?>(null)
    val restorePrompt: StateFlow<RestorePrompt?> = _restorePrompt.asStateFlow()

    val events: MutableSharedFlow<BackupEvent> = MutableSharedFlow(extraBufferCapacity = 4)
    val resultEvents: SharedFlow<BackupEvent> get() = events

    fun createBackup(io: BackupIo, defaultName: String) {
        if (_isBusy.value) return
        scope.launch {
            _isBusy.value = true
            var started = false
            try {
                val saved = io.pickAndWrite(defaultName) { out ->
                    // Only flag the notification once the user actually picked a destination.
                    started = true
                    notifyBackupStart(isRestore = false)
                    repository.createBackup(out)
                }
                if (saved) {
                    events.emit(BackupEvent.Created)
                    if (started) notifyBackupFinish(isRestore = false, success = true)
                }
            } catch (e: Exception) {
                events.emit(BackupEvent.Error(e.message ?: e.javaClass.simpleName))
                if (started) notifyBackupFinish(isRestore = false, success = false)
            } finally {
                _isBusy.value = false
            }
        }
    }

    /** Pick + read a backup, then prompt for which sections to restore (Doki section picker). */
    fun pickRestore(io: BackupIo) {
        if (_isBusy.value) return
        scope.launch {
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
        scope.launch {
            _isBusy.value = true
            notifyBackupStart(isRestore = true)
            var result: RestoreResult? = null
            try {
                result = repository.restoreBackup(java.io.ByteArrayInputStream(prompt.bytes), selected)
                // A restored backup can add external local-manga dirs; make sure they're actually
                // readable (Android: prompt for All-files access) so the manga show up in Local.
                runCatching { ensureLocalStorageAccessAfterRestore() }
                events.emit(BackupEvent.Restored(result))
            } catch (e: Exception) {
                events.emit(BackupEvent.Error(e.message ?: e.javaClass.simpleName))
            } finally {
                notifyBackupFinish(isRestore = true, success = result != null && result.failed == 0)
                _isBusy.value = false
            }
        }
    }
}
