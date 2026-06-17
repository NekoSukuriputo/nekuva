package org.nekosukuriputo.nekuva.stats.domain

/** Time window for the reading-stats screen (port of Doki StatsPeriod). Labels resolved in the UI. */
enum class StatsPeriod(val days: Int) {
    DAY(1),
    WEEK(7),
    MONTH(30),
    MONTHS_3(90),
    ALL(Int.MAX_VALUE),
}
