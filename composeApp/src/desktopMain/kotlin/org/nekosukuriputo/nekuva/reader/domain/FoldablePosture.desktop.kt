package org.nekosukuriputo.nekuva.reader.domain

import androidx.compose.runtime.Composable

// Desktop has no foldable hinge.
@Composable
actual fun rememberIsBookPosture(): Boolean = false
