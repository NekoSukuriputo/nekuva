package org.nekosukuriputo.nekuva.core.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.SubcomposeAsyncImage
import coil3.compose.SubcomposeAsyncImageContent
import coil3.compose.AsyncImagePainter

/**
 * Source favicon (Doki FaviconView): loads `favicon://<name>` via Coil (disk-cached, fetched once),
 * falling back to a Doki-style letter placeholder while loading or on error.
 */
@Composable
fun SourceFaviconImage(
    sourceName: String,
    displayName: String,
    modifier: Modifier = Modifier,
    letterSize: TextUnit = 20.sp,
) {
    SubcomposeAsyncImage(
        model = "favicon://$sourceName",
        contentDescription = displayName,
        modifier = modifier.clip(RoundedCornerShape(12.dp)),
    ) {
        when (painter.state) {
            is AsyncImagePainter.State.Success -> SubcomposeAsyncImageContent()
            else -> SourceIconPlaceholder(displayName, Modifier.fillMaxSize(), letterSize)
        }
    }
}

/** Doki-style fallback: rounded square with a bold first letter, colour derived from the source name. */
@Composable
fun SourceIconPlaceholder(
    name: String,
    modifier: Modifier = Modifier,
    letterSize: TextUnit = 20.sp,
) {
    val letter = name.trim().take(1).uppercase().ifEmpty { "?" }
    val color = remember(name) { sourceLetterColor(name) }
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHighest)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(12.dp)),
        contentAlignment = Alignment.Center,
    ) {
        Text(text = letter, color = color, fontWeight = FontWeight.Bold, fontSize = letterSize)
    }
}

/** Deterministic colour from the source name (Doki KotatsuColors.random). */
private fun sourceLetterColor(name: String): Color {
    val hue = ((name.hashCode() and 0x7FFFFFFF) % 360).toFloat()
    return Color.hsv(hue, 0.55f, 0.85f)
}
