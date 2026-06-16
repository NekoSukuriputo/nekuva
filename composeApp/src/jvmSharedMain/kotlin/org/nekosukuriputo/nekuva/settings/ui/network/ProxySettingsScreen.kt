package org.nekosukuriputo.nekuva.settings.ui.network

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import nekuva.composeapp.generated.resources.Res
import nekuva.composeapp.generated.resources.*
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import org.nekosukuriputo.nekuva.core.prefs.AppSettings
import org.nekosukuriputo.nekuva.settings.ui.components.SettingsCategoryHeader
import org.nekosukuriputo.nekuva.settings.ui.components.SettingsEditText
import org.nekosukuriputo.nekuva.settings.ui.components.SettingsItem
import org.nekosukuriputo.nekuva.settings.ui.components.SettingsSingleChoice

/**
 * Proxy configuration (Doki `ProxySettingsFragment` / pref_proxy.xml): type (Disabled/HTTP/SOCKS),
 * address, port, optional auth, and a "Test connection" that GETs neverssl.com through the configured
 * proxy. The proxy is applied live via [org.nekosukuriputo.nekuva.core.network.ProxyProvider].
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProxySettingsScreen(onBackClick: () -> Unit) {
    val settings = koinInject<AppSettings>()
    val client = koinInject<OkHttpClient>()
    val scope = rememberCoroutineScope()

    var type by remember { mutableStateOf(settings.prefString(AppSettings.KEY_PROXY_TYPE, "DIRECT")) }
    var address by remember { mutableStateOf(settings.prefString(AppSettings.KEY_PROXY_ADDRESS, "")) }
    var port by remember { mutableStateOf(settings.prefString(AppSettings.KEY_PROXY_PORT, "")) }
    var login by remember { mutableStateOf(settings.prefString(AppSettings.KEY_PROXY_LOGIN, "")) }
    var password by remember { mutableStateOf(settings.prefString(AppSettings.KEY_PROXY_PASSWORD, "")) }
    val enabled = type != "DIRECT"

    var testing by remember { mutableStateOf(false) }
    var testResult by remember { mutableStateOf<String?>(null) }
    var showResult by remember { mutableStateOf(false) }
    val connectionOk = stringResource(Res.string.connection_ok)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(Res.string.proxy)) },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
                },
            )
        },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState())) {
            SettingsSingleChoice(
                title = stringResource(Res.string.type),
                options = listOf(
                    stringResource(Res.string.disabled) to "DIRECT",
                    "HTTP" to "HTTP",
                    "SOCKS (v4/v5)" to "SOCKS",
                ),
                selected = type,
                onSelect = { settings.setPref(AppSettings.KEY_PROXY_TYPE, it); type = it },
            )
            SettingsEditText(
                title = stringResource(Res.string.address),
                value = address,
                enabled = enabled,
                keyboardType = KeyboardType.Uri,
                onValueChange = { settings.setPref(AppSettings.KEY_PROXY_ADDRESS, it); address = it },
            )
            SettingsEditText(
                title = stringResource(Res.string.port),
                value = port,
                enabled = enabled,
                keyboardType = KeyboardType.Number,
                onValueChange = { settings.setPref(AppSettings.KEY_PROXY_PORT, it); port = it },
            )

            SettingsCategoryHeader(stringResource(Res.string.authorization_optional))
            SettingsEditText(
                title = stringResource(Res.string.username),
                value = login,
                enabled = enabled,
                onValueChange = { settings.setPref(AppSettings.KEY_PROXY_LOGIN, it); login = it },
            )
            SettingsEditText(
                title = stringResource(Res.string.password),
                value = password,
                enabled = enabled,
                isPassword = true,
                onValueChange = { settings.setPref(AppSettings.KEY_PROXY_PASSWORD, it); password = it },
            )

            HorizontalDivider()
            SettingsItem(
                title = stringResource(Res.string.test_connection),
                summary = if (testing) stringResource(Res.string.loading_) else null,
                enabled = enabled && !testing,
                onClick = {
                    testing = true
                    scope.launch {
                        val error = withContext(Dispatchers.IO) {
                            runCatching {
                                val request = Request.Builder().get().url("http://neverssl.com").build()
                                client.newCall(request).execute().use { response ->
                                    check(response.isSuccessful) { response.message }
                                }
                            }.exceptionOrNull()
                        }
                        testing = false
                        testResult = error?.message?.takeIf { it.isNotBlank() } ?: error?.toString()
                        if (error == null) testResult = null
                        showResult = true
                    }
                },
            )
        }
    }

    if (showResult) {
        AlertDialog(
            onDismissRequest = { showResult = false },
            title = { Text(stringResource(Res.string.proxy)) },
            text = { Text(testResult ?: connectionOk) },
            confirmButton = {
                TextButton(onClick = { showResult = false }) { Text(stringResource(Res.string.ok)) }
            },
        )
    }
}
