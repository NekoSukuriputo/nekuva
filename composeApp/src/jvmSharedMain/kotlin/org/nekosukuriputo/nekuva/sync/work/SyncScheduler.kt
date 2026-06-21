package org.nekosukuriputo.nekuva.sync.work

/**
 * (Re)schedules periodic background sync (Doki's SyncAdapter periodic sync, re-architected for KMP):
 * Android uses WorkManager (periodic, ~12h, network-constrained) and only while logged in; Desktop has
 * no background scheduler → no-op (sync stays manual via Settings → Services → Synchronization).
 * Idempotent — safe to call on every app start. Reads deps from Koin.
 */
expect fun scheduleSync()
