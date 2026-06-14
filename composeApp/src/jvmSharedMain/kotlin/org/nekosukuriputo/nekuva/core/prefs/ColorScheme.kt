package org.nekosukuriputo.nekuva.core.prefs

import org.nekosukuriputo.nekuva.parsers.util.find

enum class ColorScheme {
    DEFAULT,
    MONET,
    EXPRESSIVE,
    MIKU,
    RENA,
    FROG,
    BLUEBERRY,
    SAKURA,
    MAMIMI,
    KANADE,
    ITSUKA;

    companion object {
        val default: ColorScheme
            get() = DEFAULT // TODO: Dynamic color

        fun getAvailableList(): List<ColorScheme> {
            // Static-palette decision: MONET/EXPRESSIVE are dynamic-only (no static colors), so they
            // are not offered. The 9 named static themes map to Doki's palettes (see ColorSchemes.kt).
            return ColorScheme.entries.filter { it != MONET && it != EXPRESSIVE }
        }

        fun safeValueOf(name: String): ColorScheme? {
            return ColorScheme.entries.find(name)
        }
    }
}
