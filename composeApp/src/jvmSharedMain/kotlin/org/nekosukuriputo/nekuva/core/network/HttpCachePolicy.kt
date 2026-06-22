package org.nekosukuriputo.nekuva.core.network

/**
 * Whether the OkHttp client persists HTTP RESPONSES to the on-disk cache.
 *
 * Android: true — the cache lives in app-private storage (not scanned by desktop AV) and speeds up
 * browsing. Desktop: false — the cache (`~/.nekuva/http_cache`) sits in the user profile where Windows
 * Defender scans it, and manga source sites serve ad/redirect JavaScript that gets cached and flagged
 * (e.g. Trojan:JS/Redirector) even though Nekuva never executes those cached responses (HTML is parsed
 * with jsoup). Image caching is unaffected — Coil keeps its own separate disk cache.
 */
expect val httpDiskCacheEnabled: Boolean
