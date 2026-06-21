package org.nekosukuriputo.nekuva.reader.domain

import android.app.Activity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.window.layout.FoldingFeature
import androidx.window.layout.WindowInfoTracker
import kotlinx.coroutines.flow.map

@Composable
actual fun rememberIsBookPosture(): Boolean {
    val activity = LocalContext.current as? Activity ?: return false
    val flow = remember(activity) {
        WindowInfoTracker.getOrCreate(activity)
            .windowLayoutInfo(activity)
            .map { info ->
                info.displayFeatures.any { it is FoldingFeature && it.state == FoldingFeature.State.HALF_OPENED }
            }
    }
    val isBook by flow.collectAsState(initial = false)
    return isBook
}
