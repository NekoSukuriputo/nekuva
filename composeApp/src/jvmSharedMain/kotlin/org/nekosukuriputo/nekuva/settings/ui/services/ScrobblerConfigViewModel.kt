package org.nekosukuriputo.nekuva.settings.ui.services

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.nekosukuriputo.nekuva.parsers.util.runCatchingCancellable
import org.nekosukuriputo.nekuva.scrobbling.common.domain.ScrobblerManager
import org.nekosukuriputo.nekuva.scrobbling.common.domain.model.ScrobblerService

/** Drives the tracking section of Settings > Services: per-scrobbler login state + OAuth login/logout. */
class ScrobblerConfigViewModel(
    private val scrobblerManager: ScrobblerManager,
) : ViewModel() {

    data class Item(
        val service: ScrobblerService,
        val isConfigured: Boolean,
        val isEnabled: Boolean,
    )

    private val _items = MutableStateFlow(buildItems())
    val items: StateFlow<List<Item>> = _items.asStateFlow()

    private fun buildItems(): List<Item> = scrobblerManager.scrobblers.map {
        Item(it.scrobblerService, it.scrobblerService.isConfigured, it.isEnabled)
    }

    fun refresh() {
        _items.value = buildItems()
    }

    fun oauthUrl(service: ScrobblerService): String? = scrobblerManager[service]?.oauthUrl

    fun logout(service: ScrobblerService) {
        scrobblerManager[service]?.logout()
        refresh()
    }

    /** Finish OAuth with the code captured from the redirect; refresh login state. */
    fun completeAuth(service: ScrobblerService, code: String) {
        viewModelScope.launch {
            runCatchingCancellable { scrobblerManager[service]?.authorize(code) }
                .onFailure { it.printStackTrace() }
            refresh()
        }
    }
}
