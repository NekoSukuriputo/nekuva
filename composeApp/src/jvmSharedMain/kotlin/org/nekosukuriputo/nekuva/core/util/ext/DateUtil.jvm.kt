package org.nekosukuriputo.nekuva.core.util.ext

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

actual fun currentTimeMillis(): Long = System.currentTimeMillis()

actual fun formatEpochToDateString(epochMillis: Long): String {
    val date = Date(epochMillis)
    val format = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
    return format.format(date)
}
