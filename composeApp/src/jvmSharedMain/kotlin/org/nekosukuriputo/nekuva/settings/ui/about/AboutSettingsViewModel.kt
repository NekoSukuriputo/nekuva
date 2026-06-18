package org.nekosukuriputo.nekuva.settings.ui.about

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.nekosukuriputo.nekuva.core.github.AppUpdateRepository
import org.nekosukuriputo.nekuva.core.github.AppVersion
import org.nekosukuriputo.nekuva.parsers.util.runCatchingCancellable

/** Result of a manual "check for updates" (Doki AboutSettingsViewModel). */
sealed interface UpdateCheckResult {
    data class Available(val version: AppVersion) : UpdateCheckResult
    data object UpToDate : UpdateCheckResult
}

class AboutSettingsViewModel(
    private val appUpdateRepository: AppUpdateRepository,
) : ViewModel() {

    private val _isChecking = MutableStateFlow(false)
    val isChecking: StateFlow<Boolean> = _isChecking.asStateFlow()

    val events = MutableSharedFlow<UpdateCheckResult>(extraBufferCapacity = 1)

    fun checkForUpdates(currentVersionName: String) {
        if (_isChecking.value) return
        viewModelScope.launch {
            _isChecking.value = true
            try {
                val update = runCatchingCancellable { appUpdateRepository.fetchUpdate(currentVersionName) }.getOrNull()
                events.emit(if (update != null) UpdateCheckResult.Available(update) else UpdateCheckResult.UpToDate)
            } finally {
                _isChecking.value = false
            }
        }
    }
}
