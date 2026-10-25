package com.tinymotors.game.ui

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tinymotors.game.ui.theme.Palette
import com.tinymotors.game.ui.theme.kidText

/** One of the cut-out design pictures (icons, Towy, logo). Decorative unless [description] is given. */
@Composable
fun Art(res: Int, modifier: Modifier = Modifier, description: String? = null) {
    Image(painterResource(res), description, modifier, contentScale = ContentScale.Fit)
}

/** Text with a thick outline, like the titles in the design. */
@Composable
fun OutlinedTitle(
    text: String,
    modifier: Modifier = Modifier,
    size: Float = 32f,
    fill: Color = Color.White,
    outline: Color = Palette.HeaderShade,
    align: TextAlign = TextAlign.Center,
) {
    Box(modifier) {
        BasicText(
            text,
            style = kidText(size.sp, outline, align = align).copy(
                drawStyle = Stroke(width = size * 0.32f, join = androidx.compose.ui.graphics.StrokeJoin.Round),
            ),
        )
        BasicText(text, style = kidText(size.sp, fill, align = align))
    }
}

/** Blue rounded header with a big icon, e.g. "Settings" and "Garage". */
@Composable
fun HeaderPill(title: String, icon: Int, modifier: Modifier = Modifier) {
    Box(
        modifier
            .drawBehind {
                drawRoundRect(Palette.HeaderShade, Offset(0f, 5.dp.toPx()), size, CornerRadius(size.height / 2))
            }
            .padding(bottom = 5.dp)
            .clip(CircleShape)
            .background(Palette.HeaderBlue)
            .drawBehind {
                drawRoundRect(
                    Color.White.copy(alpha = 0.18f), Offset(size.height * 0.4f, size.height * 0.1f),
                    Size(size.width - size.height * 0.8f, size.height * 0.3f), CornerRadius(size.height * 0.15f),
                )
            }
            .padding(start = 10.dp, end = 22.dp, top = 6.dp, bottom = 6.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(48.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.95f)), contentAlignment = Alignment.Center) {
                Art(icon, Modifier.size(34.dp))
            }
            HSpace(10.dp)
            OutlinedTitle(title, size = 30f)
        }
    }
}

/** White pill with an icon and a number (score, best, cars found). */
@Composable
fun StatPill(icon: Int, label: String?, value: String, modifier: Modifier = Modifier) {
    Box(
        modifier
            .drawBehind { drawRoundRect(Palette.ButtonShade, Offset(0f, 4.dp.toPx()), size, CornerRadius(size.height / 2)) }
            .padding(bottom = 4.dp)
            .clip(CircleShape)
            .background(Color.White)
            .padding(start = 6.dp, end = 14.dp)
            .height(54.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Art(icon, Modifier.size(40.dp))
            HSpace(6.dp)
            Column {
                if (label != null) KidText(label, size = 12f, color = Palette.InkSoft, bold = false)
                KidText(value, size = 22f, color = Palette.HeaderShade)
            }
        }
    }
}

/** White rounded button with a picture icon and an optional red notification dot. */
@Composable
fun WhiteIconButton(
    icon: Int,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 60.dp,
    iconSize: Dp = 36.dp,
    dot: Boolean = false,
    enabled: Boolean = true,
) {
    Box(modifier.size(size)) {
        ChunkyButton(
            onClick, Color.White, Palette.ButtonShade, contentDescription, Modifier.size(size),
            RoundedCornerShape(size * 0.34f), depth = 5.dp, enabled = enabled,
        ) {
            Art(icon, Modifier.size(iconSize))
        }
        if (dot) {
            Box(
                Modifier
                    .align(Alignment.TopEnd)
                    .offset(x = 4.dp, y = (-4).dp)
                    .size(16.dp)
                    .clip(CircleShape)
                    .background(Color.White)
                    .padding(2.dp)
                    .clip(CircleShape)
                    .background(Color(0xFFFF4D5E)),
            )
        }
    }
}

/** Settings row: icon, title, small subtitle and a blue toggle. */
@Composable
fun SettingToggle(icon: Int, title: String, subtitle: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    val knob by animateDpAsState(if (checked) 30.dp else 0.dp, spring(dampingRatio = 0.6f), label = "knob")
    GlassRow(
        Modifier
            .semantics(mergeDescendants = true) {
                role = Role.Switch
                contentDescription = title
            }
            .clickable { onChange(!checked) },
    ) {
        Art(icon, Modifier.size(40.dp))
        HSpace(12.dp)
        Column(Modifier.weight(1f)) {
            KidText(title, size = 19f, color = Palette.HeaderShade, align = TextAlign.Start)
            KidText(subtitle, size = 13f, color = Palette.InkSoft, bold = false, align = TextAlign.Start)
        }
        Box(
            Modifier
                .width(66.dp)
                .height(36.dp)
                .clip(CircleShape)
                .background(if (checked) Palette.Toggle else Palette.Grey)
                .padding(3.dp),
        ) {
            Box(
                Modifier
                    .offset { IntOffset(knob.roundToPx(), 0) }
                    .size(30.dp)
                    .clip(CircleShape)
                    .background(Color.White),
            )
        }
    }
}

/** Soft white row used in settings lists. */
@Composable
fun GlassRow(modifier: Modifier = Modifier, content: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit) {
    Row(
        modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 72.dp)
            .drawBehind { drawRoundRect(Palette.GlassEdge, Offset(0f, 3.dp.toPx()), size, CornerRadius(18.dp.toPx())) }
            .padding(bottom = 3.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(Color.White)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        content = content,
    )
}

/** Purple ribbon banner ("New car!", "2048!"). */
fun DrawScope.drawRibbon(color: Color = Palette.Ribbon, shade: Color = Palette.RibbonShade) {
    val w = size.width
    val h = size.height
    val tail = h * 0.45f
    for (side in 0..1) {
        val p = Path().apply {
            if (side == 0) {
                moveTo(0f, h * 0.3f); lineTo(tail * 1.4f, h * 0.3f); lineTo(tail * 1.4f, h); lineTo(0f, h); lineTo(tail * 0.6f, h * 0.65f); close()
            } else {
                moveTo(w, h * 0.3f); lineTo(w - tail * 1.4f, h * 0.3f); lineTo(w - tail * 1.4f, h); lineTo(w, h); lineTo(w - tail * 0.6f, h * 0.65f); close()
            }
        }
        drawPath(p, shade)
    }
    drawRoundRect(shade, Offset(tail, h * 0.08f), Size(w - tail * 2, h * 0.84f), CornerRadius(h * 0.2f))
    drawRoundRect(color, Offset(tail, 0f), Size(w - tail * 2, h * 0.84f), CornerRadius(h * 0.2f))
    drawRoundRect(Color.White.copy(alpha = 0.2f), Offset(tail + h * 0.2f, h * 0.08f), Size(w - tail * 2 - h * 0.4f, h * 0.22f), CornerRadius(h * 0.11f))
}

/** Rolling hills, round trees and little flowers along the bottom of a screen. */
fun DrawScope.drawScenery(groundTop: Float) {
    val w = size.width
    val h = size.height
    val far = Path().apply {
        moveTo(0f, groundTop)
        cubicTo(w * 0.25f, groundTop - h * 0.06f, w * 0.45f, groundTop - h * 0.02f, w * 0.6f, groundTop - h * 0.05f)
        cubicTo(w * 0.78f, groundTop - h * 0.08f, w * 0.9f, groundTop - h * 0.03f, w, groundTop - h * 0.05f)
        lineTo(w, h); lineTo(0f, h); close()
    }
    drawPath(far, Palette.HillFar)
    // trees on the far hill
    val trees = floatArrayOf(0.06f, 0.2f, 0.33f, 0.71f, 0.86f, 0.97f)
    for ((i, tx) in trees.withIndex()) {
        val x = w * tx
        val r = w * (0.045f + (i % 3) * 0.008f)
        val base = groundTop - h * 0.035f
        drawRoundRect(Palette.Trunk, Offset(x - r * 0.12f, base - r * 0.4f), Size(r * 0.24f, r * 0.9f), CornerRadius(r * 0.1f))
        drawCircle(Palette.TreeDark, r, Offset(x, base - r * 1.1f))
        drawCircle(Palette.Tree, r * 0.82f, Offset(x - r * 0.18f, base - r * 1.25f))
        drawCircle(Color.White.copy(alpha = 0.18f), r * 0.3f, Offset(x - r * 0.4f, base - r * 1.6f))
    }
    val near = Path().apply {
        moveTo(0f, groundTop + h * 0.02f)
        cubicTo(w * 0.3f, groundTop - h * 0.015f, w * 0.65f, groundTop + h * 0.03f, w, groundTop)
        lineTo(w, h); lineTo(0f, h); close()
    }
    drawPath(near, Palette.Hill)
    // bushes and flowers
    for (bx in floatArrayOf(0.12f, 0.52f, 0.9f)) {
        val x = w * bx
        val y = groundTop + h * 0.03f
        val r = w * 0.04f
        drawCircle(Palette.TreeDark, r, Offset(x - r * 0.7f, y))
        drawCircle(Palette.Tree, r * 1.15f, Offset(x, y - r * 0.3f))
        drawCircle(Palette.TreeDark, r * 0.9f, Offset(x + r * 0.8f, y))
    }
    for (fx in floatArrayOf(0.04f, 0.27f, 0.41f, 0.64f, 0.78f, 0.95f)) {
        val x = w * fx
        val y = groundTop + h * 0.06f + (fx * 37 % 1) * h * 0.02f
        val r = w * 0.011f
        for (k in 0 until 5) {
            val a = k * 1.2566f
            drawCircle(Color.White, r, Offset(x + kotlin.math.cos(a) * r * 1.2f, y + kotlin.math.sin(a) * r * 1.2f))
        }
        drawCircle(Palette.Gold, r * 0.8f, Offset(x, y))
    }
}

/** Glossy grey-blue outline ring used for the hold-to-confirm gate. */
fun DrawScope.drawHoldRing(progress: Float, color: Color) {
    drawCircle(Palette.GlassEdge, style = Stroke(4.dp.toPx()))
    drawArc(color, -90f, 360f * progress, false, style = Stroke(4.dp.toPx(), cap = androidx.compose.ui.graphics.StrokeCap.Round))
}

