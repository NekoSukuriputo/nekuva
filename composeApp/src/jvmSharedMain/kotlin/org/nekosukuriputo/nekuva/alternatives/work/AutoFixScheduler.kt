package org.nekosukuriputo.nekuva.alternatives.work

/**
 * Schedules the periodic batch auto-fix (Doki AutoFixService). Android = WorkManager periodic work, gated
 * by `auto_fix_broken`; Desktop/iOS = no-op (no background scheduler). Idempotent — safe on every app start.
 */
expect fun scheduleAutoFix()
