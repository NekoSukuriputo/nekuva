package org.nekosukuriputo.nekuva.core.prefs

enum class NetworkPolicy {
    NON_METERED,
    ALL,
    NONE;

    fun isNetworkAllowed(): Boolean = when (this) {
        ALL -> true
        NON_METERED -> false // TODO: check if network metered
        NONE -> false
    }

    companion object {
        fun from(name: String?, default: NetworkPolicy): NetworkPolicy {
            return NetworkPolicy.entries.find { it.name == name } ?: default
        }
    }
}
