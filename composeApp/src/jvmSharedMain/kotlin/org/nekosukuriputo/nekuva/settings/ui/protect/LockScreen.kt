package org.nekosukuriputo.nekuva.settings.ui.protect

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import nekuva.composeapp.generated.resources.*
import org.nekosukuriputo.nekuva.core.prefs.AppSettings
import org.nekosukuriputo.nekuva.core.security.AppLockController
import org.nekosukuriputo.nekuva.core.security.authenticateBiometric
import org.nekosukuriputo.nekuva.core.security.isBiometricAvailable
import org.nekosukuriputo.nekuva.core.util.md5

/** Full-screen app-lock gate (Doki ProtectActivity): password entry + optional biometric. */
@Composable
fun LockScreen() {
    val settings = koinInject<AppSettings>()
    var password by remember { mutableStateOf("") }
    var wrong by remember { mutableStateOf(false) }
    val numeric = remember { settings.isAppPasswordNumeric }
    val biometric = remember { settings.isBiometricProtectionEnabled && isBiometricAvailable() }
    val scope = rememberCoroutineScope()
    val title = stringResource(Res.string.protect_application)
    val subtitle = stringResource(Res.string.enter_password)

    fun tryUnlock() {
        if (password.md5() == settings.appPassword) {
            AppLockController.unlock()
        } else {
            wrong = true
            password = ""
        }
    }

    // Offer biometric immediately if enabled & available.
    LaunchedEffect(Unit) {
        if (biometric && authenticateBiometric(title, subtitle)) AppLockController.unlock()
    }

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            modifier = Modifier.fillMaxSize().padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Icon(
                Icons.Outlined.Lock,
                contentDescription = null,
                modifier = Modifier.size(56.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
            Text(
                text = stringResource(Res.string.enter_password),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(top = 16.dp, bottom = 16.dp),
            )
            OutlinedTextField(
                value = password,
                onValueChange = { password = it; wrong = false },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(
                    keyboardType = if (numeric) KeyboardType.NumberPassword else KeyboardType.Password,
                ),
                isError = wrong,
                supportingText = if (wrong) {
                    { Text(stringResource(Res.string.wrong_password), color = MaterialTheme.colorScheme.error) }
                } else null,
            )
            Button(
                onClick = { tryUnlock() },
                enabled = password.isNotEmpty(),
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
            ) {
                Text(stringResource(Res.string.confirm))
            }
            if (biometric) {
                OutlinedButton(
                    onClick = { scope.launch { if (authenticateBiometric(title, subtitle)) AppLockController.unlock() } },
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                ) {
                    Icon(Icons.Filled.Fingerprint, contentDescription = null, modifier = Modifier.size(18.dp))
                    Text(stringResource(Res.string.use_fingerprint), modifier = Modifier.padding(start = 8.dp))
                }
            }
        }
    }
}
