package org.nekosukuriputo.nekuva.core.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Small country flag rendered with Canvas instead of a flag emoji.
 *
 * Flag emojis are pairs of Regional Indicator characters; Windows' Segoe UI Emoji deliberately omits
 * flag glyphs, so on Desktop `🇮🇩` shows as the letters "ID". Drawing the flag ourselves makes the
 * translation indicator look identical on Android and Desktop without bundling a color-emoji font.
 *
 * [lang] is an ISO-639 code (the part before any `-`/`_`). Unknown codes draw a neutral white flag.
 */
@Composable
fun LocaleFlag(lang: String, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(2.dp)
    Box(
        modifier = modifier
            .size(width = 22.dp, height = 15.dp)
            .clip(shape)
            .border(width = (0.5).dp, color = Color(0x33000000), shape = shape),
    ) {
        Canvas(modifier = Modifier.size(width = 22.dp, height = 15.dp)) {
            clipRect { drawFlag(lang.lowercase(), size) }
        }
    }
}

private fun DrawScope.drawFlag(lang: String, size: Size) {
    val w = size.width
    val h = size.height
    when (lang) {
        // English defaults to the UK flag (Doki uses 🇬🇧).
        "en" -> unionJack(w, h)

        "id", "in" -> horizontal(w, h, listOf(RED_ID, Color.White))
        "pl" -> horizontal(w, h, listOf(Color.White, RED_PL))
        "ru" -> horizontal(w, h, listOf(Color.White, BLUE_RU, RED_RU))
        "de" -> horizontal(w, h, listOf(Color.Black, RED_DE, GOLD_DE))
        "th" -> thailand(w, h)

        "fr" -> vertical(w, h, listOf(BLUE_FR, Color.White, RED_FR))
        "it" -> vertical(w, h, listOf(GREEN_IT, Color.White, RED_IT))

        "es" -> spain(w, h)
        "pt" -> portugal(w, h)

        "ja" -> japan(w, h)
        "vi" -> vietnam(w, h)
        "cn", "zh" -> china(w, h)
        "ko" -> korea(w, h)
        "tr" -> turkey(w, h)
        "ar" -> saudi(w, h)

        else -> drawRect(Color.White, size = size) // neutral fallback
    }
}

// ---- Generic layouts ------------------------------------------------------------------------------

private fun DrawScope.horizontal(w: Float, h: Float, colors: List<Color>) {
    val band = h / colors.size
    colors.forEachIndexed { i, c ->
        drawRect(c, topLeft = Offset(0f, band * i), size = Size(w, band))
    }
}

private fun DrawScope.vertical(w: Float, h: Float, colors: List<Color>) {
    val band = w / colors.size
    colors.forEachIndexed { i, c ->
        drawRect(c, topLeft = Offset(band * i, 0f), size = Size(band, h))
    }
}

// ---- Individual flags -----------------------------------------------------------------------------

private fun DrawScope.thailand(w: Float, h: Float) {
    // red / white / blue(double) / white / red
    val u = h / 6f
    drawRect(RED_TH, topLeft = Offset(0f, 0f), size = Size(w, u))
    drawRect(Color.White, topLeft = Offset(0f, u), size = Size(w, u))
    drawRect(BLUE_TH, topLeft = Offset(0f, u * 2), size = Size(w, u * 2))
    drawRect(Color.White, topLeft = Offset(0f, u * 4), size = Size(w, u))
    drawRect(RED_TH, topLeft = Offset(0f, u * 5), size = Size(w, u))
}

private fun DrawScope.spain(w: Float, h: Float) {
    // red / yellow(double) / red
    drawRect(RED_ES, topLeft = Offset(0f, 0f), size = Size(w, h * 0.25f))
    drawRect(YELLOW_ES, topLeft = Offset(0f, h * 0.25f), size = Size(w, h * 0.5f))
    drawRect(RED_ES, topLeft = Offset(0f, h * 0.75f), size = Size(w, h * 0.25f))
}

private fun DrawScope.portugal(w: Float, h: Float) {
    drawRect(GREEN_PT, size = Size(w * 0.4f, h))
    drawRect(RED_PT, topLeft = Offset(w * 0.4f, 0f), size = Size(w * 0.6f, h))
    // emblem hint on the divide
    drawCircle(YELLOW_ES, radius = h * 0.12f, center = Offset(w * 0.4f, h * 0.5f))
}

private fun DrawScope.japan(w: Float, h: Float) {
    drawRect(Color.White, size = Size(w, h))
    drawCircle(RED_JP, radius = h * 0.3f, center = Offset(w / 2f, h / 2f))
}

private fun DrawScope.vietnam(w: Float, h: Float) {
    drawRect(RED_VN, size = Size(w, h))
    drawStar(Offset(w / 2f, h / 2f), outer = h * 0.3f, inner = h * 0.13f, color = YELLOW_STAR)
}

private fun DrawScope.china(w: Float, h: Float) {
    drawRect(RED_CN, size = Size(w, h))
    val big = Offset(w * 0.16f, h * 0.28f)
    drawStar(big, outer = h * 0.2f, inner = h * 0.08f, color = YELLOW_STAR)
    val small = h * 0.07f
    listOf(
        Offset(w * 0.32f, h * 0.12f),
        Offset(w * 0.40f, h * 0.24f),
        Offset(w * 0.40f, h * 0.40f),
        Offset(w * 0.32f, h * 0.52f),
    ).forEach { drawStar(it, outer = small, inner = small * 0.42f, color = YELLOW_STAR) }
}

private fun DrawScope.korea(w: Float, h: Float) {
    drawRect(Color.White, size = Size(w, h))
    val r = h * 0.22f
    val c = Offset(w / 2f, h / 2f)
    // taegeuk approximated as a red upper half + blue lower half circle
    drawArc(RED_KR, startAngle = 180f, sweepAngle = 180f, useCenter = true,
        topLeft = Offset(c.x - r, c.y - r), size = Size(r * 2, r * 2))
    drawArc(BLUE_KR, startAngle = 0f, sweepAngle = 180f, useCenter = true,
        topLeft = Offset(c.x - r, c.y - r), size = Size(r * 2, r * 2))
}

private fun DrawScope.turkey(w: Float, h: Float) {
    drawRect(RED_TR, size = Size(w, h))
    val cx = w * 0.42f
    val cy = h * 0.5f
    val outer = h * 0.22f
    // crescent: white disc minus a red disc offset to the right
    drawCircle(Color.White, radius = outer, center = Offset(cx, cy))
    drawCircle(RED_TR, radius = outer * 0.82f, center = Offset(cx + outer * 0.32f, cy))
    drawStar(Offset(w * 0.62f, cy), outer = h * 0.12f, inner = h * 0.05f, color = Color.White)
}

private fun DrawScope.saudi(w: Float, h: Float) {
    drawRect(GREEN_SA, size = Size(w, h))
    // simplified emblem hint: a thin white bar (the sword)
    drawRect(Color.White, topLeft = Offset(w * 0.18f, h * 0.62f), size = Size(w * 0.64f, h * 0.06f))
}

private fun DrawScope.unionJack(w: Float, h: Float) {
    drawRect(BLUE_UK, size = Size(w, h))
    // white diagonals
    drawLine(Color.White, Offset(0f, 0f), Offset(w, h), strokeWidth = h * 0.22f)
    drawLine(Color.White, Offset(w, 0f), Offset(0f, h), strokeWidth = h * 0.22f)
    // red diagonals (thinner)
    drawLine(RED_UK, Offset(0f, 0f), Offset(w, h), strokeWidth = h * 0.1f)
    drawLine(RED_UK, Offset(w, 0f), Offset(0f, h), strokeWidth = h * 0.1f)
    // white cross
    drawRect(Color.White, topLeft = Offset(w / 2 - h * 0.18f, 0f), size = Size(h * 0.36f, h))
    drawRect(Color.White, topLeft = Offset(0f, h / 2 - h * 0.18f), size = Size(w, h * 0.36f))
    // red cross
    drawRect(RED_UK, topLeft = Offset(w / 2 - h * 0.1f, 0f), size = Size(h * 0.2f, h))
    drawRect(RED_UK, topLeft = Offset(0f, h / 2 - h * 0.1f), size = Size(w, h * 0.2f))
}

private fun DrawScope.drawStar(center: Offset, outer: Float, inner: Float, color: Color) {
    val path = Path()
    val points = 5
    for (i in 0 until points * 2) {
        val r = if (i % 2 == 0) outer else inner
        val angle = -PI / 2 + i * PI / points
        val x = center.x + (r * cos(angle)).toFloat()
        val y = center.y + (r * sin(angle)).toFloat()
        if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
    }
    path.close()
    drawPath(path, color)
}

// ---- Colors ---------------------------------------------------------------------------------------

private val RED_ID = Color(0xFFCE1126)
private val RED_PL = Color(0xFFDC143C)
private val BLUE_RU = Color(0xFF0039A6); private val RED_RU = Color(0xFFD52B1E)
private val RED_DE = Color(0xFFDD0000); private val GOLD_DE = Color(0xFFFFCE00)
private val RED_TH = Color(0xFFA51931); private val BLUE_TH = Color(0xFF2D2A4A)
private val BLUE_FR = Color(0xFF0055A4); private val RED_FR = Color(0xFFEF4135)
private val GREEN_IT = Color(0xFF009246); private val RED_IT = Color(0xFFCE2B37)
private val RED_ES = Color(0xFFAA151B); private val YELLOW_ES = Color(0xFFF1BF00)
private val GREEN_PT = Color(0xFF006600); private val RED_PT = Color(0xFFFF0000)
private val RED_JP = Color(0xFFBC002D)
private val RED_VN = Color(0xFFDA251D); private val YELLOW_STAR = Color(0xFFFFFF00)
private val RED_CN = Color(0xFFDE2910)
private val RED_KR = Color(0xFFCD2E3A); private val BLUE_KR = Color(0xFF0047A0)
private val RED_TR = Color(0xFFE30A17)
private val GREEN_SA = Color(0xFF006C35)
private val BLUE_UK = Color(0xFF012169); private val RED_UK = Color(0xFFC8102E)
