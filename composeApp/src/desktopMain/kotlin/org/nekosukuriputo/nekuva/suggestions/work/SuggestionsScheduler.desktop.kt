package org.nekosukuriputo.nekuva.suggestions.work

/** Desktop has no background worker — suggestions are generated on-demand from the screen. */
actual fun scheduleSuggestions() = Unit
