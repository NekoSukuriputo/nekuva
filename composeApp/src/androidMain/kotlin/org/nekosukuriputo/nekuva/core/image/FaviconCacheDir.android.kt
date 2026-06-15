package org.nekosukuriputo.nekuva.core.image

import android.content.Context
import okio.Path
import okio.Path.Companion.toOkioPath
import org.koin.mp.KoinPlatform

actual fun faviconCacheDir(): Path =
    KoinPlatform.getKoin().get<Context>().cacheDir.resolve("favicons").toOkioPath()
