package org.nekosukuriputo.nekuva.core.util

import java.security.MessageDigest

/** MD5 hex digest — matches Doki's `String.md5()` used for the app-lock password hash. */
fun String.md5(): String =
    MessageDigest.getInstance("MD5").digest(encodeToByteArray()).joinToString("") { "%02x".format(it) }

/** True if the string is non-empty and all digits (Doki uses this to pick a numeric PIN keyboard). */
fun String.isNumeric(): Boolean = isNotEmpty() && all { it.isDigit() }
