package org.nekosukuriputo.nekuva.core.image

import okio.Path
import okio.Path.Companion.toPath

actual fun faviconCacheDir(): Path =
    (System.getProperty("user.home") + "/.nekuva/favicons").toPath()
