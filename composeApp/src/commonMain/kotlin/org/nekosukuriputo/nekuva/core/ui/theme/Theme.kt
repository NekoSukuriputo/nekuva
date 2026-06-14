package org.nekosukuriputo.nekuva.core.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable

@Composable
fun NekuvaTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    amoled: Boolean = false,
    colorSchemeName: String = "DEFAULT",
    content: @Composable () -> Unit
) {
    // Named color theme (Doki parity) -> static Material3 palette (see ColorSchemes.kt), then the
    // AMOLED pure-black override on dark.
    val base = appColorScheme(colorSchemeName, darkTheme)
    val colorScheme = if (darkTheme && amoled) base.toAmoled() else base

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
