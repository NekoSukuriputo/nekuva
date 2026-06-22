package org.nekosukuriputo.nekuva.core.network

// Desktop: don't persist HTTP responses to disk — avoids Defender flagging cached source-site JS.
// (Coil still caches images in its own disk cache; only the OkHttp response cache is disabled.)
actual val httpDiskCacheEnabled: Boolean = false
