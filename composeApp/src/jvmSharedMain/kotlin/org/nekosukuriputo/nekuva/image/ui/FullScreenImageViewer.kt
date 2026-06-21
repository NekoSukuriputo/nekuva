package org.nekosukuriputo.nekuva.image.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil3.compose.AsyncImage
import coil3.compose.LocalPlatformContext
import org.nekosukuriputo.nekuva.core.image.mangaSourceExtra
import org.jetbrains.compose.resources.stringResource
import nekuva.composeapp.generated.resources.Res
import nekuva.composeapp.generated.resources.cancel
import nekuva.composeapp.generated.resources.save
import nekuva.composeapp.generated.resources.share

/**
 * Fullscreen zoomable image viewer (Doki ImageActivity). Cross-platform: pinch / double-tap to zoom,
 * drag to pan, tap empty area or Close to dismiss. Share sends the image URL via the platform share
 * (Android sheet / Desktop clipboard). Saving the image to disk is deferred (needs platform file write).
 */
@Composable
fun FullScreenImageViewer(
    imageUrl: String?,
    onDismiss: () -> Unit,
    onShare: (String) -> Unit = {},
    onSave: (String) -> Unit = {},
    source: org.nekosukuriputo.nekuva.parsers.model.MangaSource? = null,
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        var scale by remember { mutableFloatStateOf(1f) }
        var offsetX by remember { mutableFloatStateOf(0f) }
        var offsetY by remember { mutableFloatStateOf(0f) }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
                .pointerInput(Unit) {
                    detectTapGestures(
                        onTap = { if (scale > 1f) { scale = 1f; offsetX = 0f; offsetY = 0f } else onDismiss() },
                        onDoubleTap = {
                            if (scale > 1f) { scale = 1f; offsetX = 0f; offsetY = 0f } else scale = 2.5f
                        },
                    )
                }
                .pointerInput(Unit) {
                    detectTransformGestures { _, pan, zoom, _ ->
                        scale = (scale * zoom).coerceIn(1f, 5f)
                        if (scale > 1f) {
                            offsetX += pan.x
                            offsetY += pan.y
                        } else {
                            offsetX = 0f
                            offsetY = 0f
                        }
                    }
                },
            contentAlignment = Alignment.Center,
        ) {
            val imgCtx = coil3.compose.LocalPlatformContext.current
            val imgModel = remember(imageUrl, source) {
                coil3.request.ImageRequest.Builder(imgCtx)
                    .data(imageUrl)
                    .apply { if (source != null) mangaSourceExtra(source) }
                    .build()
            }
            AsyncImage(
                model = imgModel,
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer(
                        scaleX = scale,
                        scaleY = scale,
                        translationX = offsetX,
                        translationY = offsetY,
                    ),
            )

            IconButton(
                onClick = onDismiss,
                modifier = Modifier.align(Alignment.TopStart).padding(8.dp),
                colors = IconButtonDefaults.iconButtonColors(contentColor = Color.White),
            ) {
                Icon(Icons.Filled.Close, contentDescription = stringResource(Res.string.cancel))
            }
            if (!imageUrl.isNullOrEmpty()) {
                androidx.compose.foundation.layout.Row(
                    modifier = Modifier.align(Alignment.TopEnd).padding(8.dp),
                ) {
                    IconButton(
                        onClick = { onSave(imageUrl) },
                        colors = IconButtonDefaults.iconButtonColors(contentColor = Color.White),
                    ) {
                        Icon(Icons.Filled.Download, contentDescription = stringResource(Res.string.save))
                    }
                    IconButton(
                        onClick = { onShare(imageUrl) },
                        colors = IconButtonDefaults.iconButtonColors(contentColor = Color.White),
                    ) {
                        Icon(Icons.Filled.Share, contentDescription = stringResource(Res.string.share))
                    }
                }
            }
        }
    }
}
