package org.nekosukuriputo.nekuva.core.shortcuts

/** A recent-manga entry for the launcher's dynamic shortcuts (Doki `dynamic_shortcuts`). */
data class MangaShortcut(val id: Long, val title: String)

/**
 * Publish the recent-manga launcher shortcuts (Android `ShortcutManager`). An empty list clears them.
 * Desktop has no launcher shortcuts (no-op).
 */
expect fun updateDynamicShortcuts(shortcuts: List<MangaShortcut>)

/**
 * Pin a manga to the home screen / launcher (Doki `action_shortcut`). Android = `requestPinShortcut`
 * (API 26+, if supported); Desktop has no concept (no-op).
 */
expect fun pinMangaShortcut(id: Long, title: String)
