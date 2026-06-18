package org.nekosukuriputo.nekuva.suggestions.work

/**
 * (Re)schedule the periodic suggestions refresh (Doki SuggestionsWorker.Scheduler). Android = WorkManager;
 * Desktop = no-op (suggestions stay on-demand from the screen). Idempotent — safe to call on every app start.
 */
expect fun scheduleSuggestions()
