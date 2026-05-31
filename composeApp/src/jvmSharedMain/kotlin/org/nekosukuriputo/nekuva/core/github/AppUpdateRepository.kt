package org.nekosukuriputo.nekuva.core.github

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class AppUpdateRepository {
    private val availableUpdate = MutableStateFlow<Any?>(null)
    
    suspend fun getAvailableUpdate(bypassCache: Boolean = false): Any? {
        return null
    }

    suspend fun isUpdateSupported(): Boolean {
        return false
    }
}
