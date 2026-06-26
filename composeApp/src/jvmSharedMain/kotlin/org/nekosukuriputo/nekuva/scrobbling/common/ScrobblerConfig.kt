package org.nekosukuriputo.nekuva.scrobbling.common

/**
 * OAuth credentials for the external tracking services.
 *
 * AniList + MyAnimeList read their real client id/secret from [ScrobblerSecrets], generated at build time
 * from `local.properties` / `-D` / env (GitHub Actions secrets for release) — so the real keys are NEVER
 * committed. Empty (no key provided) → that scrobbler is "unconfigured" (login disabled, [isConfigured]).
 * Register each app with the provider and set its redirect/callback URL to [REDIRECT_URI] (`nekuva://oauth`).
 *
 * Shikimori / Kitsu / Discord keep Doki/Kotatsu's PUBLIC dev credentials inline: they are hidden from the
 * UI (Shikimori/Kitsu via ScrobblerConfigViewModel, Discord via the menu gate) but the code stays compiled.
 */
object ScrobblerConfig {

    const val REDIRECT_URI: String = "nekuva://oauth"

    // Hidden from the menu (kept compiled) — Doki public dev creds left inline.
    const val SHIKIMORI_CLIENT_ID: String = "Mw6F0tPEOgyV7F9U9Twg50Q8SndMY7hzIOfXg0AX_XU"
    const val SHIKIMORI_CLIENT_SECRET: String = "euBMt1GGRSDpVIFQVPxZrO7Kh6X4gWyv0dABuj4B-M8"

    // AniList / MyAnimeList: real creds injected at build time (kept out of source control).
    val ANILIST_CLIENT_ID: String get() = ScrobblerSecrets.ANILIST_CLIENT_ID
    val ANILIST_CLIENT_SECRET: String get() = ScrobblerSecrets.ANILIST_CLIENT_SECRET
    val MAL_CLIENT_ID: String get() = ScrobblerSecrets.MAL_CLIENT_ID

    // Hidden from the menu (kept compiled) — Doki public dev creds left inline.
    const val KITSU_CLIENT_ID: String = "dd031b32d2f56c990b1425efe6c42ad847e7fe3ab46bf1299f05ecd856bdb7dd"
    const val KITSU_CLIENT_SECRET: String = "54d7307928f63414defd96399fc31ba847961ceaecef3a5fd93144e960c0e151"

    const val DISCORD_APP_ID: String = "1397246659754459136"
}
