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
            val list = ColorScheme.entries.toMutableList()
            // TODO: dynamic color
            return list
        }

        fun safeValueOf(name: String): ColorScheme? {
            return ColorScheme.entries.find(name)
        }
    }
}
