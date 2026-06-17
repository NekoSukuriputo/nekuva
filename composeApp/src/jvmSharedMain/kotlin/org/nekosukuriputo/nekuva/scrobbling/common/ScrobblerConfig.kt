package org.nekosukuriputo.nekuva.scrobbling.common

/**
 * OAuth credentials for the external tracking services.
 *
 * ⚠️ PLACEHOLDER CREDENTIALS — REPLACE BEFORE RELEASE ⚠️
 * The values below are Doki/Kotatsu's PUBLIC dev credentials (from their open-source `constants.xml`),
 * filled in here only so the scrobblers appear *configured* and the full login/API code path is wired and
 * compilable. They will NOT complete a real OAuth login as-is, because each is registered against Doki's
 * own redirect URI — not Nekuva's [REDIRECT_URI]. To actually use these services, replace each below:
 *   1. Register your own app with the provider (AniList / MyAnimeList / Kitsu / Discord / Shikimori),
 *   2. Set its redirect/callback URL to [REDIRECT_URI] (`nekuva://oauth`),
 *   3. Paste your own `*_CLIENT_ID` / `*_CLIENT_SECRET` here (search "TODO(credentials)").
 * An empty value makes that scrobbler "unconfigured" (login disabled, [isConfigured] = false).
 */
object ScrobblerConfig {

    const val REDIRECT_URI: String = "nekuva://oauth"

    // TODO(credentials): replace with your own registered Shikimori app.
    const val SHIKIMORI_CLIENT_ID: String = "Mw6F0tPEOgyV7F9U9Twg50Q8SndMY7hzIOfXg0AX_XU"
    const val SHIKIMORI_CLIENT_SECRET: String = "euBMt1GGRSDpVIFQVPxZrO7Kh6X4gWyv0dABuj4B-M8"

    // TODO(credentials): replace with your own registered AniList app.
    const val ANILIST_CLIENT_ID: String = "9887"
    const val ANILIST_CLIENT_SECRET: String = "wrMqFosItQWsmB8dtAHfIFPDt15FfQi2ZGiKkJoW"

    // TODO(credentials): replace with your own registered MyAnimeList app (PKCE, no secret).
    const val MAL_CLIENT_ID: String = "6cd8e6349e9a36bc1fc1ab97703c9fd1"

    // TODO(credentials): replace with your own registered Kitsu app.
    const val KITSU_CLIENT_ID: String = "dd031b32d2f56c990b1425efe6c42ad847e7fe3ab46bf1299f05ecd856bdb7dd"
    const val KITSU_CLIENT_SECRET: String = "54d7307928f63414defd96399fc31ba847961ceaecef3a5fd93144e960c0e151"

    // TODO(credentials): replace with your own registered Discord application id.
    const val DISCORD_APP_ID: String = "1397246659754459136"
}
