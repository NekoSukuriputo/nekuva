package org.nekosukuriputo.nekuva.settings.ui.reader

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.nekosukuriputo.nekuva.reader.data.TapGridSettings
import org.nekosukuriputo.nekuva.reader.domain.TapAction
import org.nekosukuriputo.nekuva.reader.domain.TapGridArea

/** Drives the tap-actions config screen (Doki's ReaderTapGridConfigViewModel). */
class TapGridConfigViewModel(
    private val settings: TapGridSettings,
) : ViewModel() {

    data class Cell(val area: TapGridArea, val tap: TapAction?, val longTap: TapAction?)

    private val _cells = MutableStateFlow(read())
    val cells: StateFlow<List<Cell>> = _cells.asStateFlow()

    private fun read(): List<Cell> = TapGridArea.entries.map {
        Cell(it, settings.getTapAction(it, isLongTap = false), settings.getTapAction(it, isLongTap = true))
    }

    fun setAction(area: TapGridArea, isLongTap: Boolean, action: TapAction?) {
        settings.setTapAction(area, isLongTap, action)
        _cells.value = read()
    }

    fun reset() {
        settings.reset()
        _cells.value = read()
    }

    fun disableAll() {
        settings.disableAll()
        _cells.value = read()
    }
}
