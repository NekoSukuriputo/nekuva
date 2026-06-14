package org.nekosukuriputo.nekuva.settings.ui.protect

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import nekuva.composeapp.generated.resources.*
import org.nekosukuriputo.nekuva.core.prefs.AppSettings
import org.nekosukuriputo.nekuva.core.security.isBiometricAvailable
import org.nekosukuriputo.nekuva.core.util.isNumeric
import org.nekosukuriputo.nekuva.core.util.md5

private const val MIN_PASSWORD_LENGTH = 4

/** Set up the app-lock password (Doki ProtectSetupActivity): enter → repeat → confirm, + biometric opt-in. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProtectSetupScreen(onDone: () -> Unit) {
    val settings = koinInject<AppSettings>()
    var password by remember { mutableStateOf("") }
    var firstPassword by remember { mutableStateOf<String?>(null) }
    var mismatch by remember { mutableStateOf(false) }
    var useBiometric by remember { mutableStateOf(settings.isBiometricProtectionEnabled) }
    val biometricAvailable = remember { isBiometricAvailable() }
    val confirming = firstPassword != null

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(Res.string.protect_application)) },
                navigationIcon = {
                    IconButton(onClick = onDone) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(stringResource(Res.string.protect_application_summary))
            OutlinedTextField(
                value = password,
                onValueChange = { password = it; mismatch = false },
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(Res.string.password)) },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                isError = mismatch,
                supportingText = {
                    Text(
                        when {
                            mismatch -> stringResource(Res.string.passwords_mismatch)
                            confirming -> stringResource(Res.string.repeat_password)
                            else -> stringResource(Res.string.password_length_hint)
                        },
                    )
                },
            )
            if (biometricAvailable) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Switch(checked = useBiometric, onCheckedChange = { useBiometric = it })
                    Spacer(Modifier.width(12.dp))
                    Text(stringResource(Res.string.use_fingerprint))
                }
            }
            Button(
                onClick = {
                    if (!confirming) {
                        firstPassword = password
                        password = ""
                    } else if (password == firstPassword) {
                        settings.appPassword = password.md5()
                        settings.isAppPasswordNumeric = password.isNumeric()
                        settings.isBiometricProtectionEnabled = useBiometric
                        onDone()
                    } else {
                        mismatch = true
                        firstPassword = null
                        password = ""
                    }
                },
                enabled = password.length >= MIN_PASSWORD_LENGTH,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(if (confirming) Res.string.confirm else Res.string.next))
            }
        }
    }
}
