package org.nekosukuriputo.nekuva.core.diagnostics

import java.io.File

actual fun crashLogDir(): File =
    File(System.getProperty("user.home"), ".nekuva/crash")
