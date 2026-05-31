package org.nekosukuriputo.nekuva.core.model

import kotlinx.serialization.Serializable

@Serializable
enum class ZoomMode {
    FIT_CENTER, FIT_HEIGHT, FIT_WIDTH, KEEP_START
}
