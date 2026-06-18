package org.nekosukuriputo.nekuva.core.util.ext

import androidx.compose.runtime.Composable
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.resources.pluralStringResource
import nekuva.composeapp.generated.resources.*

expect fun currentTimeMillis(): Long

/** Localized absolute date, e.g. "24 Mei 2026" (d MMMM yyyy, app locale) — Doki's FORMAT_SHOW_DATE equivalent. */
expect fun formatEpochToDateString(epochMillis: Long): String

/** Calendar-day difference (local timezone) between [epochMillis] and today; negative if in the future. */
expect fun daysAgo(epochMillis: Long): Long

/**
 * Stable, non-composable grouping key so lists (History/etc.) bucket entries like Doki's `DateTimeAgo`:
 * today / yesterday / 2..5 days ago / per-day for older. Same buckets as [calculateTimeAgo].
 */
fun relativeDateKey(epochMillis: Long): String {
    if (epochMillis <= 0L) return "unknown"
    val d = daysAgo(epochMillis)
    return when {
        d < 0L -> "unknown"
        d == 0L -> "today"
        d == 1L -> "yesterday"
        d < 6L -> "days_$d"
        else -> "abs_${epochMillis / 86_400_000L}"
    }
}

/**
 * Doki-style relative date (port of `calculateTimeAgo` / `DateTimeAgo`): "Today" / "Yesterday" /
 * "N days ago" for the last week, then the absolute localized date ("24 Mei 2026"). Used by History
 * group headers and the chapter list (upload date).
 */
@Composable
fun calculateTimeAgo(epochMillis: Long): String {
    if (epochMillis <= 0L) return stringResource(Res.string.unknown)
    val d = daysAgo(epochMillis)
    return when {
        d < 0L -> stringResource(Res.string.unknown)
        d == 0L -> stringResource(Res.string.today)
        d == 1L -> stringResource(Res.string.yesterday)
        d < 6L -> pluralStringResource(Res.plurals.days_ago, d.toInt(), d.toInt())
        else -> formatEpochToDateString(epochMillis)
    }
}
