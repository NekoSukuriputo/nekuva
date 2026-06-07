package org.nekosukuriputo.nekuva.core.util.ext

import androidx.compose.runtime.Composable
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.resources.pluralStringResource
import nekuva.composeapp.generated.resources.*

expect fun currentTimeMillis(): Long
expect fun formatEpochToDateString(epochMillis: Long): String

@Composable
fun calculateTimeAgo(epochMillis: Long): String {
    if (epochMillis <= 0L) return stringResource(Res.string.unknown)
    
    val diffMillis = currentTimeMillis() - epochMillis
    val diffDays = diffMillis / (1000 * 60 * 60 * 24)

    return when {
        diffDays < 0 -> stringResource(Res.string.unknown)
        diffDays == 0L -> stringResource(Res.string.today)
        diffDays == 1L -> stringResource(Res.string.yesterday)
        diffDays < 6L -> pluralStringResource(Res.plurals.days_ago, diffDays.toInt(), diffDays.toInt())
        else -> formatEpochToDateString(epochMillis)
    }
}
