package org.nekosukuriputo.nekuva.scrobbling.common.data

import com.russhwolf.settings.ObservableSettings
import org.nekosukuriputo.nekuva.scrobbling.common.domain.model.ScrobblerService
import org.nekosukuriputo.nekuva.scrobbling.common.domain.model.ScrobblerUser

/**
 * Persists a scrobbler's OAuth tokens + cached user, per service. KMP port of Doki's ScrobblerStorage
 * (Android SharedPreferences) onto the shared [ObservableSettings], with keys namespaced per service.
 */
class ScrobblerStorage(
    private val prefs: ObservableSettings,
    private val service: ScrobblerService,
) {

    private fun key(name: String) = "scrobbler_${service.name}_$name"

    var accessToken: String?
        get() = prefs.getStringOrNull(key(KEY_ACCESS_TOKEN))
        set(value) = put(key(KEY_ACCESS_TOKEN), value)

    var refreshToken: String?
        get() = prefs.getStringOrNull(key(KEY_REFRESH_TOKEN))
        set(value) = put(key(KEY_REFRESH_TOKEN), value)

    var user: ScrobblerUser?
        get() = prefs.getStringOrNull(key(KEY_USER))?.let { raw ->
            val lines = raw.lines()
            if (lines.size != 4) return@let null
            ScrobblerUser(
                id = lines[0].toLongOrNull() ?: return@let null,
                nickname = lines[1],
                avatar = lines[2].ifEmpty { null },
                service = runCatching { ScrobblerService.valueOf(lines[3]) }.getOrNull() ?: return@let null,
            )
        }
        set(value) {
            if (value == null) {
                prefs.remove(key(KEY_USER))
            } else {
                prefs.putString(
                    key(KEY_USER),
                    listOf(value.id.toString(), value.nickname, value.avatar.orEmpty(), value.service.name)
                        .joinToString("\n"),
                )
            }
        }

    operator fun get(name: String): String? = prefs.getStringOrNull(key(name))

    operator fun set(name: String, value: String?) = put(key(name), value)

    fun clear() {
        accessToken = null
        refreshToken = null
        user = null
    }

    private fun put(fullKey: String, value: String?) {
        if (value == null) prefs.remove(fullKey) else prefs.putString(fullKey, value)
    }

    private companion object {
        const val KEY_ACCESS_TOKEN = "access_token"
        const val KEY_REFRESH_TOKEN = "refresh_token"
        const val KEY_USER = "user"
    }
}
