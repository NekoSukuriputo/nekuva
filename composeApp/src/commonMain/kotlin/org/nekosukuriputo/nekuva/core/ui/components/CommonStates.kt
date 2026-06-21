package org.nekosukuriputo.nekuva.core.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.stringResource
import nekuva.composeapp.generated.resources.*

@Composable
fun LoadingState(modifier: Modifier = Modifier) {
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator()
    }
}

/**
 * Generic error state. When the error is a [CloudFlareException] and [onResolveCloudFlare] is provided,
 * shows Doki's "solve captcha" affordance (message + button → embedded browser to clear the challenge)
 * instead of plain Retry — mirrors Doki's `ExceptionResolver.canResolve`/`getResolveStringId`.
 */
@Composable
fun ErrorState(
    error: Throwable?,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
    onResolveCloudFlare: ((org.nekosukuriputo.nekuva.core.exceptions.CloudFlareException) -> Unit)? = null,
) {
    val cloudFlare = error as? org.nekosukuriputo.nekuva.core.exceptions.CloudFlareException
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            if (cloudFlare != null && onResolveCloudFlare != null) {
                Text(
                    text = stringResource(Res.string.captcha_required),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(modifier = Modifier.height(12.dp))
                Button(onClick = { onResolveCloudFlare(cloudFlare) }) {
                    Text(stringResource(Res.string.captcha_solve))
                }
            } else {
                Text(text = "${stringResource(Res.string.error)}: ${error?.message ?: stringResource(Res.string.unknown)}", color = MaterialTheme.colorScheme.error)
                Spacer(modifier = Modifier.height(8.dp))
                Button(onClick = onRetry) {
                    Text(stringResource(Res.string.retry))
                }
            }
        }
    }
}

@Composable
fun EmptyState(message: String, modifier: Modifier = Modifier, secondary: String? = null) {
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        androidx.compose.foundation.layout.Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(6.dp),
        ) {
            Text(
                text = message,
                color = MaterialTheme.colorScheme.onSurface,
                style = MaterialTheme.typography.titleMedium,
            )
            if (secondary != null) {
                Text(
                    text = secondary,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }
}
