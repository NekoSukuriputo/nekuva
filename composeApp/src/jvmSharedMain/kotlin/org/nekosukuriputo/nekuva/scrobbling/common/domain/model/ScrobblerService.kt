package org.nekosukuriputo.nekuva.scrobbling.common.domain.model

import org.nekosukuriputo.nekuva.scrobbling.common.ScrobblerConfig

/** The external tracking services Nekuva can link to (mirrors Doki). */
enum class ScrobblerService(val id: Int, val titleKey: String) {
    SHIKIMORI(1, "shikimori"),
    ANILIST(2, "anilist"),
    MAL(3, "mal"),
    KITSU(4, "kitsu");

    /** True only when its OAuth client id has been filled into [ScrobblerConfig]. */
    val isConfigured: Boolean
        get() = when (this) {
            SHIKIMORI -> ScrobblerConfig.SHIKIMORI_CLIENT_ID.isNotEmpty()
            ANILIST -> ScrobblerConfig.ANILIST_CLIENT_ID.isNotEmpty()
            MAL -> ScrobblerConfig.MAL_CLIENT_ID.isNotEmpty()
            KITSU -> ScrobblerConfig.KITSU_CLIENT_ID.isNotEmpty()
        }
}
