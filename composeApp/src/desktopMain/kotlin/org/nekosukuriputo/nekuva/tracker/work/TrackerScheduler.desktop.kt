package org.nekosukuriputo.nekuva.tracker.work

/** Desktop has no background scheduler (no WorkManager) — tracker runs manually via the Feed tab. */
actual fun scheduleTracker() = Unit
