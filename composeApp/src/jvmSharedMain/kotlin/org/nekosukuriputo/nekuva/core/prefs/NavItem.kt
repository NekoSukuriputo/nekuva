package org.nekosukuriputo.nekuva.core.prefs

/**
 * Bottom-nav / navigation-rail sections (Doki `nav_main`). Limited to the tabs Nekuva actually hosts
 * as top-level destinations; the order + which are shown is user-configurable (NavConfig editor).
 */
enum class NavItem {
    HISTORY,
    FAVORITES,
    EXPLORE,
    FEED,
    LOCAL,
}
