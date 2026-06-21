package org.nekosukuriputo.nekuva.sync.work

/** Desktop has no background scheduler (no WorkManager) — sync runs manually via Settings → Services. */
actual fun scheduleSync() = Unit
