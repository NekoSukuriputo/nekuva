package org.nekosukuriputo.nekuva.core.image

import okio.Path

/** Persistent directory for cached source favicons (one file per source) — Doki's FaviconCache. */
expect fun faviconCacheDir(): Path
