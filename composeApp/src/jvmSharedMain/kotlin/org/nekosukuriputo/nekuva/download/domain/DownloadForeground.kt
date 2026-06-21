package org.nekosukuriputo.nekuva.download.domain

/**
 * Ensure the platform's download foreground affordance is running while downloads are active
 * (Doki's WorkManager foreground notification, re-implemented for the in-process engine).
 *
 * - Android: starts a foreground [service][org.nekosukuriputo.nekuva.download.service] that posts an
 *   ongoing progress notification and keeps the process alive while downloads run, then stops itself
 *   when the queue drains. Safe to call repeatedly (idempotent).
 * - Desktop: no-op (no OS process-kill / notification model to guard against).
 */
expect fun ensureDownloadForeground()
