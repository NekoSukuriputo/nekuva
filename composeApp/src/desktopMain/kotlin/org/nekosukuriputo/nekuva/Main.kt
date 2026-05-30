package org.nekosukuriputo.nekuva

import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import org.nekosukuriputo.nekuva.di.initKoin

fun main() {
    initKoin()
    application {
        Window(
            onCloseRequest = ::exitApplication,
            title = "Nekuva",
        ) {
            App()
        }
    }
}
