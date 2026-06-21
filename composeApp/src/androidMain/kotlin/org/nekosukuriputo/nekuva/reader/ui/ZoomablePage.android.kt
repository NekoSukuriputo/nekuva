package org.nekosukuriputo.nekuva.reader.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize
import kotlinx.coroutines.flow.SharedFlow
import me.saket.telephoto.zoomable.coil3.ZoomableAsyncImage
import me.saket.telephoto.zoomable.rememberZoomableImageState
import me.saket.telephoto.zoomable.rememberZoomableState

// Android: telephoto's subsampling zoomable image (Doki RegionDecoder/SSIV) — huge pages tile-decode at
// full resolution when zoomed. Telephoto owns pinch/double-tap zoom + pan; at min-zoom it leaves swipes to
// the pager, and while zoomed we disable pager swipe via onZoomChanged so panning never flips the page.
@Composable
actual fun ZoomablePage(
    url: String?,
    colorFilter: ColorFilter?,
    contentScale: ContentScale,
    pageAlignment: Alignment,
    active: Boolean,
    zoomCommands: SharedFlow<Float>,
    onZoomChanged: (Boolean) -> Unit,
    onTap: (Offset, IntSize, Boolean) -> Unit,
) {
    val zoomableState = rememberZoomableState()
    val imageState = rememberZoomableImageState(zoomableState)
    var size by remember { mutableStateOf(IntSize.Zero) }
    // zoomFraction is null before layout, 0 at min zoom, →1 at max; >0 means zoomed in.
    val zoomed = (zoomableState.zoomFraction ?: 0f) > 0.01f
    LaunchedEffect(zoomed) { onZoomChanged(zoomed) }
    LaunchedEffect(active) {
        if (active) zoomCommands.collect { factor -> zoomableState.zoomBy(factor) }
    }
    ZoomableAsyncImage(
        model = rememberReaderPageModel(url, foreground = active),
        contentDescription = null,
        modifier = Modifier.fillMaxSize().onSizeChanged { size = it },
        state = imageState,
        colorFilter = colorFilter,
        alignment = pageAlignment,
        contentScale = contentScale,
        onClick = { offset -> onTap(offset, size, false) },
        onLongClick = { offset -> onTap(offset, size, true) },
    )
}
