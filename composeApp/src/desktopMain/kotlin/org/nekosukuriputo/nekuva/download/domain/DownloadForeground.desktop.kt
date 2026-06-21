package org.nekosukuriputo.nekuva.download.domain

/** Desktop has no foreground-service / kill model — the engine lives with the app process. No-op. */
actual fun ensureDownloadForeground() = Unit
