package org.nekosukuriputo.nekuva.core.util.ext

import java.text.SimpleDateFormat
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import java.util.Date
import java.util.Locale

actual fun currentTimeMillis(): Long = System.currentTimeMillis()

// "d MMMM yyyy" in the app locale → "24 Mei 2026" (id) / "24 May 2026" (en). Doki's medium-date equivalent.
actual fun formatEpochToDateString(epochMillis: Long): String =
    SimpleDateFormat("d MMMM yyyy", Locale.getDefault()).format(Date(epochMillis))

// Calendar-day diff in the system timezone (Doki uses LocalDate.until / ChronoUnit.DAYS).
actual fun daysAgo(epochMillis: Long): Long {
    val date = Instant.ofEpochMilli(epochMillis).atZone(ZoneId.systemDefault()).toLocalDate()
    return date.until(LocalDate.now(), ChronoUnit.DAYS)
}
