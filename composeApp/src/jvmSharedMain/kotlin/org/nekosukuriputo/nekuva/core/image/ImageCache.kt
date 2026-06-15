package org.nekosukuriputo.nekuva.core.image

import coil3.PlatformContext
import okio.Path

/**
 * Persistent on-disk cache directory for Coil. Enables source favicons (and cover thumbnails) to be
 * fetched once and reused across app launches (Doki behaviour) instead of being re-downloaded each time.
 */
expect fun imageDiskCacheDir(context: PlatformContext): Path
