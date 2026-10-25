package com.tinymotors.game.ui

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import kotlin.math.cos
import kotlin.math.sin

/** Simple chunky icons drawn with shapes (no icon library needed). All fit the DrawScope size. */
object KidIcons {

    fun play(d: DrawScope, color: Color) = with(d) {
        val w = size.width
        val h = size.height
        val p = Path().apply { moveTo(w * 0.2f, h * 0.1f); lineTo(w * 0.9f, h * 0.5f); lineTo(w * 0.2f, h * 0.9f); close() }
        drawPath(p, color, style = Stroke(w * 0.12f, join = StrokeJoin.Round))
        drawPath(p, color)
    }

    fun undo(d: DrawScope, color: Color) = with(d) {
        val w = size.width
        val h = size.height
        val sw = w * 0.14f
        drawArc(color, 200f, 250f, false, Offset(w * 0.18f, h * 0.18f), Size(w * 0.64f, h * 0.64f),
            style = Stroke(sw, cap = StrokeCap.Round))
        val tip = Offset(w * 0.2f, h * 0.38f)
        val p = Path().apply {
            moveTo(tip.x - w * 0.16f, tip.y - h * 0.12f); lineTo(tip.x + w * 0.16f, tip.y - h * 0.06f); lineTo(tip.x - w * 0.02f, tip.y + h * 0.18f); close()
        }
        drawPath(p, color, style = Stroke(w * 0.06f, join = StrokeJoin.Round))
        drawPath(p, color)
    }

    fun check(d: DrawScope, color: Color) = with(d) {
        val w = size.width
        val h = size.height
        val p = Path().apply { moveTo(w * 0.15f, h * 0.55f); lineTo(w * 0.42f, h * 0.8f); lineTo(w * 0.88f, h * 0.22f) }
        drawPath(p, color, style = Stroke(w * 0.16f, cap = StrokeCap.Round, join = StrokeJoin.Round))
    }

    fun plus(d: DrawScope, color: Color) = with(d) {
        val w = size.width
        val h = size.height
        drawLine(color, Offset(w * 0.5f, h * 0.15f), Offset(w * 0.5f, h * 0.85f), w * 0.2f, StrokeCap.Round)
        drawLine(color, Offset(w * 0.15f, h * 0.5f), Offset(w * 0.85f, h * 0.5f), w * 0.2f, StrokeCap.Round)
    }

    /** Pointing hand used by the tutorial. */
    fun finger(d: DrawScope, skin: Color, outline: Color) = with(d) {
        val w = size.width
        val h = size.height
        val sw = w * 0.06f
        // index finger
        drawRoundRect(skin, Offset(w * 0.36f, h * 0.02f), Size(w * 0.22f, h * 0.55f), CornerRadius(w * 0.11f))
        drawRoundRect(outline, Offset(w * 0.36f, h * 0.02f), Size(w * 0.22f, h * 0.55f), CornerRadius(w * 0.11f), style = Stroke(sw))
        // palm
        drawRoundRect(skin, Offset(w * 0.2f, h * 0.4f), Size(w * 0.66f, h * 0.56f), CornerRadius(w * 0.24f))
        drawRoundRect(outline, Offset(w * 0.2f, h * 0.4f), Size(w * 0.66f, h * 0.56f), CornerRadius(w * 0.24f), style = Stroke(sw))
        drawRoundRect(skin, Offset(w * 0.39f, h * 0.36f), Size(w * 0.16f, h * 0.12f))
        // knuckle lines
        drawLine(outline, Offset(w * 0.6f, h * 0.48f), Offset(w * 0.6f, h * 0.6f), sw * 0.8f, StrokeCap.Round)
        drawLine(outline, Offset(w * 0.73f, h * 0.5f), Offset(w * 0.73f, h * 0.62f), sw * 0.8f, StrokeCap.Round)
    }

}
