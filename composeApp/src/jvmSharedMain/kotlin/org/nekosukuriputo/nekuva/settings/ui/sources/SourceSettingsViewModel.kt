package org.nekosukuriputo.nekuva.settings.ui.sources

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.nekosukuriputo.nekuva.core.nav.SourceSettingsRoute
import org.nekosukuriputo.nekuva.core.network.cookies.MutableCookieJar
import org.nekosukuriputo.nekuva.core.parser.MangaRepository
import org.nekosukuriputo.nekuva.core.parser.ParserMangaRepository
import org.nekosukuriputo.nekuva.parsers.model.MangaParserSource

data class SourceSettingsUiState(
    val title: String = "",
    val domains: List<String> = emptyList(),
    val currentDomain: String = "",
    val authUrl: String? = null,
    val username: String? = null,
)

/** Per-source settings (Doki SourceSettingsFragment): domain/mirror, sign-in, clear cookies. */
class SourceSettingsViewModel(
    savedStateHandle: SavedStateHandle,
    private val repositoryFactory: MangaRepository.Factory,
    private val cookieJar: MutableCookieJar,
) : ViewModel() {

    private val sourceName = savedStateHandle.toRoute<SourceSettingsRoute>().sourceName
    private val source: MangaParserSource? = MangaParserSource.entries.find { it.name == sourceName }
    private val repository: ParserMangaRepository? =
        source?.let { repositoryFactory.create(it) as? ParserMangaRepository }

    private val _uiState = MutableStateFlow(SourceSettingsUiState(title = source?.title ?: sourceName))
    val uiState: StateFlow<SourceSettingsUiState> = _uiState.asStateFlow()

    init {
        refresh()
    }

    private fun refresh() {
        val repo = repository ?: return
        viewModelScope.launch {
            val auth = repo.getAuthProvider()
            val username = if (auth != null) runCatching { auth.getUsername() }.getOrNull() else null
            _uiState.value = SourceSettingsUiState(
                title = source?.title ?: sourceName,
                domains = withContext(Dispatchers.Default) { repo.getAvailableMirrors() },
                currentDomain = withContext(Dispatchers.Default) { repo.domain },
                authUrl = auth?.authUrl,
                username = username,
            )
        }
    }

    fun setDomain(domain: String) {
        val repo = repository ?: return
        viewModelScope.launch {
            withContext(Dispatchers.Default) {
                repo.domain = domain
                repo.invalidateCache()
            }
            refresh()
        }
    }

    fun clearCookies(onDone: () -> Unit) {
        viewModelScope.launch {
            runCatching { cookieJar.clear() }
            onDone()
        }
    }
}
