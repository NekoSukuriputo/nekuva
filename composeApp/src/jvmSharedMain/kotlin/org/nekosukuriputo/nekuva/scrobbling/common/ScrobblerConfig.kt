package org.nekosukuriputo.nekuva.scrobbling.common

/**
 * OAuth credentials for the external tracking services. Doki stores these as per-service string
 * resources (`anilist_clientId`, `mal_clientId`, …) that are filled in at build time; here they live
 * in one config object. **Fill these with your own registered app credentials** — until then the
 * corresponding scrobbler is treated as unconfigured (login is disabled, [isConfigured] = false).
 *
 * When registering each app, set the redirect/callback URL to [REDIRECT_URI]; the in-app browser
 * intercepts a navigation to it and extracts the `code` query parameter to finish login.
 */
object ScrobblerConfig {

    const val REDIRECT_URI: String = "nekuva://oauth"

    const val SHIKIMORI_CLIENT_ID: String = ""
    const val SHIKIMORI_CLIENT_SECRET: String = ""

    const val ANILIST_CLIENT_ID: String = ""
    const val ANILIST_CLIENT_SECRET: String = ""

    const val MAL_CLIENT_ID: String = ""

    const val KITSU_CLIENT_ID: String = ""
    const val KITSU_CLIENT_SECRET: String = ""

    const val DISCORD_APP_ID: String = ""
}
