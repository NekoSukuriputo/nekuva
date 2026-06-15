package org.nekosukuriputo.nekuva.core.image

import coil3.PlatformContext
import okio.Path
import okio.Path.Companion.toPath

actual fun imageDiskCacheDir(context: PlatformContext): Path =
    (System.getProperty("user.home") + "/.nekuva/image_cache").toPath()
