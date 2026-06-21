package org.nekosukuriputo.nekuva.download.domain

import java.io.File

actual fun downloadQueueFile(): File =
    File(System.getProperty("user.home"), ".nekuva/downloads_queue.json")
