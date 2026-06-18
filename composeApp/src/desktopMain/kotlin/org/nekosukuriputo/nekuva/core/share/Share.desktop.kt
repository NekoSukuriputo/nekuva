package org.nekosukuriputo.nekuva.core.share

import java.awt.Toolkit
import java.awt.datatransfer.StringSelection

/** Desktop has no OS share sheet → copy the text (title + link) to the system clipboard. */
actual fun shareText(text: String) {
    runCatching {
        Toolkit.getDefaultToolkit().systemClipboard.setContents(StringSelection(text), null)
    }
}
