package org.nekosukuriputo.nekuva.settings.ui.sources

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.nekosukuriputo.nekuva.core.nav.SourceSettingsRoute
import org.nekosukuriputo.nekuva.core.network.cookies.MutableCookieJar
import org.nekosukuriputo.nekuva.core.parser.MangaRepository
import org.nekosukuriputo.nekuva.core.parser.ParserMangaRepository
import org.nekosukuriputo.nekuva.core.prefs.AppSettings
import org.nekosukuriputo.nekuva.explore.data.MangaSourcesRepository
import org.nekosukuriputo.nekuva.parsers.config.ConfigKey
import org.nekosukuriputo.nekuva.parsers.model.MangaParserSource
import org.nekosukuriputo.nekuva.parsers.network.UserAgents

data class SourceSettingsUiState(
    val title: String = "",
    val isValidSource: Boolean = false,
    // Enable/disable (Doki "enable" header) — hidden when "all sources enabled" mode is on.
    val isEnableVisible: Boolean = false,
    val isEnabled: Boolean = true,
    // Domain / "Ranah web" (ConfigKey.Domain).
    val domain: String = "",
    val domainDefault: String = "",
    val domainPresets: List<String> = emptyList(),
    // User agent / "Tajuk Agen Pengguna" (ConfigKey.UserAgent).
    val userAgentSupported: Boolean = false,
    val userAgent: String = "",
    val userAgentDefault: String = "",
    val userAgentPresets: List<String> = emptyList(),
    // Optional parser toggles.
    val showSuspiciousSupported: Boolean = false,
    val showSuspicious: Boolean = false,
    val splitByTranslationsSupported: Boolean = false,
    val splitByTranslations: Boolean = false,
    // Preferred image server (ConfigKey.PreferredImageServer): pairs of (value, label); empty label = automatic.
    val imageServerOptions: List<Pair<String, String>> = emptyList(),
    val imageServer: String? = null,
    // Auth (sign-in).
    val authUrl: String? = null,
    val username: String? = null,
    // Static per-source toggles.
    val captchaDisabled: Boolean = false,
    val slowdownEnabled: Boolean = false,
    val browserUrl: String? = null,
)

/**
 * Per-source settings (Doki SourceSettingsFragment): enable/disable, domain/mirror, user agent, sign-in,
 * clear cookies, captcha-notification + slowdown toggles, optional parser toggles, and open-in-browser.
 */
class SourceSettingsViewModel(
    savedStateHandle: SavedStateHandle,
    private val repositoryFactory: MangaRepository.Factory,
    private val cookieJar: MutableCookieJar,
    private val sourcesRepository: MangaSourcesRepository,
    private val settings: AppSettings,
) : ViewModel() {

    private val sourceName = savedStateHandle.toRoute<SourceSettingsRoute>().sourceName
    private val source: MangaParserSource? = MangaParserSource.entries.find { it.name == sourceName }
    private val repository: ParserMangaRepository? =
        source?.let { repositoryFactory.create(it) as? ParserMangaRepository }

    private val _uiState = MutableStateFlow(SourceSettingsUiState(title = source?.title ?: sourceName))
    val uiState: StateFlow<SourceSettingsUiState> = _uiState.asStateFlow()

    init {
        refresh()
        // Live enable state (Doki observeIsEnabled).
        source?.let { src ->
            viewModelScope.launch {
                sourcesRepository.observeIsEnabled(src).collect { enabled ->
                    _uiState.update { it.copy(isEnabled = enabled) }
                }
            }
        }
    }

    private fun refresh() {
        val repo = repository ?: run {
            _uiState.update { it.copy(isValidSource = false) }
            return
        }
        viewModelScope.launch {
            val data = withContext(Dispatchers.Default) {
                val configKeys = repo.getConfigKeys()
                val config = repo.getConfig()
                val domainKey = configKeys.firstNotNullOfOrNull { it as? ConfigKey.Domain }
                val uaKey = configKeys.firstNotNullOfOrNull { it as? ConfigKey.UserAgent }
                val suspiciousKey = configKeys.firstNotNullOfOrNull { it as? ConfigKey.ShowSuspiciousContent }
                val splitKey = configKeys.firstNotNullOfOrNull { it as? ConfigKey.SplitByTranslations }
                val imageServerKey = configKeys.firstNotNullOfOrNull { it as? ConfigKey.PreferredImageServer }

                val imageServerOptions = imageServerKey?.presetValues?.map { (value, label) ->
                    (value ?: "") to (label ?: "")
                }.orEmpty()

                SourceSettingsData(
                    domain = repo.domain,
                    domainDefault = domainKey?.defaultValue.orEmpty(),
                    domainPresets = repo.getAvailableMirrors(),
                    uaSupported = uaKey != null,
                    userAgent = uaKey?.let { config[it] }.orEmpty(),
                    userAgentDefault = uaKey?.defaultValue.orEmpty(),
                    suspiciousSupported = suspiciousKey != null,
                    showSuspicious = suspiciousKey?.let { config[it] } ?: false,
                    splitSupported = splitKey != null,
                    splitByTranslations = splitKey?.let { config[it] } ?: false,
                    imageServerOptions = imageServerOptions,
                    imageServer = imageServerKey?.let { config[it] },
                    captchaDisabled = config.isCaptchaNotificationsDisabled,
                    slowdownEnabled = config.isSlowdownEnabled,
                    browserUrl = "https://${repo.domain}",
                )
            }
            val auth = withContext(Dispatchers.Default) { repo.getAuthProvider() }
            val username = if (auth != null) {
                runCatching { withContext(Dispatchers.Default) { auth.getUsername() } }.getOrNull()
            } else {
                null
            }
            _uiState.update { state ->
                state.copy(
                    isValidSource = true,
                    isEnableVisible = !settings.isAllSourcesEnabled,
                    domain = data.domain,
                    domainDefault = data.domainDefault,
                    domainPresets = data.domainPresets,
                    userAgentSupported = data.uaSupported,
                    userAgent = data.userAgent,
                    userAgentDefault = data.userAgentDefault,
                    userAgentPresets = USER_AGENT_PRESETS,
                    showSuspiciousSupported = data.suspiciousSupported,
                    showSuspicious = data.showSuspicious,
                    splitByTranslationsSupported = data.splitSupported,
                    splitByTranslations = data.splitByTranslations,
                    imageServerOptions = data.imageServerOptions,
                    imageServer = data.imageServer,
                    authUrl = auth?.authUrl,
                    username = username,
                    captchaDisabled = data.captchaDisabled,
                    slowdownEnabled = data.slowdownEnabled,
                    browserUrl = data.browserUrl,
                )
            }
        }
    }

    fun setEnabled(value: Boolean) {
        val src = source ?: return
        viewModelScope.launch {
            withContext(Dispatchers.Default) { sourcesRepository.setSourcesEnabled(setOf(src), value) }
        }
    }

    fun setDomain(domain: String) {
        val repo = repository ?: return
        val value = domain.trim().ifEmpty { return }
        viewModelScope.launch {
            withContext(Dispatchers.Default) {
                repo.domain = value
                repo.invalidateCache()
            }
            refresh()
        }
    }

    fun resetDomain() {
        val default = _uiState.value.domainDefault
        if (default.isNotEmpty()) setDomain(default)
    }

    fun setUserAgent(userAgent: String) {
        val repo = repository ?: return
        viewModelScope.launch {
            withContext(Dispatchers.Default) {
                val key = repo.getConfigKeys().firstNotNullOfOrNull { it as? ConfigKey.UserAgent } ?: return@withContext
                repo.getConfig()[key] = userAgent.trim().ifEmpty { key.defaultValue }
                repo.invalidateCache()
            }
            refresh()
        }
    }

    fun setShowSuspicious(value: Boolean) = setBooleanConfig(value) { keys ->
        keys.firstNotNullOfOrNull { it as? ConfigKey.ShowSuspiciousContent }
    }

    fun setSplitByTranslations(value: Boolean) = setBooleanConfig(value) { keys ->
        keys.firstNotNullOfOrNull { it as? ConfigKey.SplitByTranslations }
    }

    fun setImageServer(value: String) {
        val repo = repository ?: return
        viewModelScope.launch {
            withContext(Dispatchers.Default) {
                val key = repo.getConfigKeys()
                    .firstNotNullOfOrNull { it as? ConfigKey.PreferredImageServer } ?: return@withContext
                repo.getConfig()[key] = value
                repo.invalidateCache()
            }
            refresh()
        }
    }

    fun setCaptchaDisabled(value: Boolean) {
        val repo = repository ?: return
        viewModelScope.launch {
            withContext(Dispatchers.Default) { repo.getConfig().isCaptchaNotificationsDisabled = value }
            _uiState.update { it.copy(captchaDisabled = value) }
        }
    }

    fun setSlowdown(value: Boolean) {
        val repo = repository ?: return
        viewModelScope.launch {
            withContext(Dispatchers.Default) { repo.getConfig().isSlowdownEnabled = value }
            _uiState.update { it.copy(slowdownEnabled = value) }
        }
    }

    fun clearCookies(onDone: () -> Unit) {
        viewModelScope.launch {
            runCatching { cookieJar.clear() }
            refresh()
            onDone()
        }
    }

    private inline fun setBooleanConfig(value: Boolean, crossinline keySelector: (List<ConfigKey<*>>) -> ConfigKey<Boolean>?) {
        val repo = repository ?: return
        viewModelScope.launch {
            withContext(Dispatchers.Default) {
                val key = keySelector(repo.getConfigKeys()) ?: return@withContext
                repo.getConfig()[key] = value
                repo.invalidateCache()
            }
            refresh()
        }
    }

    private data class SourceSettingsData(
        val domain: String,
        val domainDefault: String,
        val domainPresets: List<String>,
        val uaSupported: Boolean,
        val userAgent: String,
        val userAgentDefault: String,
        val suspiciousSupported: Boolean,
        val showSuspicious: Boolean,
        val splitSupported: Boolean,
        val splitByTranslations: Boolean,
        val imageServerOptions: List<Pair<String, String>>,
        val imageServer: String?,
        val captchaDisabled: Boolean,
        val slowdownEnabled: Boolean,
        val browserUrl: String,
    )

    private companion object {
        val USER_AGENT_PRESETS = listOf(
            UserAgents.FIREFOX_MOBILE,
            UserAgents.CHROME_MOBILE,
            UserAgents.FIREFOX_DESKTOP,
            UserAgents.CHROME_DESKTOP,
        )
    }
}
