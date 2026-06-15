package org.nekosukuriputo.nekuva.core.image

import coil3.PlatformContext
import okio.Path
import okio.Path.Companion.toOkioPath

actual fun imageDiskCacheDir(context: PlatformContext): Path =
    context.cacheDir.resolve("coil_image_cache").toOkioPath()
