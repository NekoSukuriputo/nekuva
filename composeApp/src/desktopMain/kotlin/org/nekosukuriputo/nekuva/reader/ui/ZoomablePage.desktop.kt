package org.nekosukuriputo.nekuva.reader.ui

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize
import coil3.compose.SubcomposeAsyncImage
import kotlinx.coroutines.flow.SharedFlow
import org.jetbrains.compose.resources.stringResource
import nekuva.composeapp.generated.resources.Res
import nekuva.composeapp.generated.resources.error

// Desktop: manual pinch/double-tap zoom + pan over a Coil image (no SSIV — telephoto's Coil3 module has no
// JVM target; Coil already downsamples to the view size so huge pages don't OOM).
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
    var scale by remember { mutableStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    var size by remember { mutableStateOf(IntSize.Zero) }
    val zoomed = scale > 1f
    LaunchedEffect(zoomed) { onZoomChanged(zoomed) }
    LaunchedEffect(active) {
        if (!active) return@LaunchedEffect
        zoomCommands.collect { factor ->
            scale = (scale * factor).coerceIn(1f, 5f)
            val maxX = (size.width * (scale - 1f)).coerceAtLeast(0f) / 2f
            val maxY = (size.height * (scale - 1f)).coerceAtLeast(0f) / 2f
            offset = Offset(offset.x.coerceIn(-maxX, maxX), offset.y.coerceIn(-maxY, maxY))
        }
    }
    val transformState = rememberTransformableState { zoomChange, panChange, _ ->
        scale = (scale * zoomChange).coerceIn(1f, 5f)
        offset = if (scale > 1f) {
            val maxX = (size.width * (scale - 1f)) / 2f
            val maxY = (size.height * (scale - 1f)) / 2f
            Offset(
                (offset.x + panChange.x).coerceIn(-maxX, maxX),
                (offset.y + panChange.y).coerceIn(-maxY, maxY),
            )
        } else {
            Offset.Zero
        }
    }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .onSizeChanged { size = it }
            .transformable(state = transformState, enabled = zoomed)
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = { onTap(it, size, false) },
                    onDoubleTap = {
                        if (scale > 1f) {
                            scale = 1f; offset = Offset.Zero
                        } else {
                            scale = 2.5f
                        }
                    },
                    onLongPress = { onTap(it, size, true) },
                )
            },
        contentAlignment = pageAlignment,
    ) {
        SubcomposeAsyncImage(
            model = rememberReaderPageModel(url, foreground = active),
            contentDescription = null,
            contentScale = contentScale,
            colorFilter = colorFilter,
            modifier = Modifier.fillMaxSize().graphicsLayer(
                scaleX = scale,
                scaleY = scale,
                translationX = offset.x,
                translationY = offset.y,
            ),
            loading = { Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() } },
            error = { Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text(stringResource(Res.string.error), color = MaterialTheme.colorScheme.error) } },
        )
    }
}
