package org.nekosukuriputo.nekuva.core.share

import org.nekosukuriputo.nekuva.parsers.model.Manga

/**
 * Share plain text (Doki `action_share`). Android = system share sheet; Desktop = copy to clipboard
 * (no native share on desktop). Cross-cutting core used by Details/History/Favourites/Local/Reader.
 */
expect fun shareText(text: String)

/** Share a manga: its title + public URL. */
fun shareManga(manga: Manga) {
    val url = manga.publicUrl.ifEmpty { manga.url }
    shareText(if (url.isEmpty()) manga.title else "${manga.title}\n$url")
}
