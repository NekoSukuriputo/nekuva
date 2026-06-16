package org.nekosukuriputo.nekuva.tracker.work

/**
 * (Re)schedules the background new-chapter tracker per current settings (Doki TrackWorker.Scheduler):
 * Android uses WorkManager (periodic, interval from `tracker_freq`, Wi-Fi constraint, notifications);
 * Desktop has no background scheduler → no-op (tracker stays manual via the Feed). Reads settings from Koin.
 */
expect fun scheduleTracker()
