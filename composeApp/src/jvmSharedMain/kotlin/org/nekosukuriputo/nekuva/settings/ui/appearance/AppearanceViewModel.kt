package org.nekosukuriputo.nekuva.settings.ui.appearance

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.nekosukuriputo.nekuva.core.prefs.AppSettings

class AppearanceViewModel(
    private val settings: AppSettings,
) : ViewModel() {

    private val _theme = MutableStateFlow(settings.theme)
    val theme: StateFlow<Int> = _theme.asStateFlow()

    private val _amoled = MutableStateFlow(settings.isAmoledTheme)
    val amoled: StateFlow<Boolean> = _amoled.asStateFlow()

    fun setTheme(value: Int) {
        settings.theme = value
        _theme.value = value
    }

    fun setAmoled(value: Boolean) {
        settings.isAmoledTheme = value
        _amoled.value = value
    }
}
