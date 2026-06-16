package org.nekosukuriputo.nekuva.core.prefs

enum class NetworkPolicy {
    NON_METERED,
    ALL,
    NONE;

    fun isNetworkAllowed(): Boolean = isNetworkAllowed(isMetered = true)

    /** Doki parity: ALL always allows, NON_METERED only on un-metered (e.g. Wi-Fi), NONE never. */
    fun isNetworkAllowed(isMetered: Boolean): Boolean = when (this) {
        ALL -> true
        NON_METERED -> !isMetered
        NONE -> false
    }

    companion object {
        fun from(name: String?, default: NetworkPolicy): NetworkPolicy {
            return NetworkPolicy.entries.find { it.name == name } ?: default
        }
    }
}
