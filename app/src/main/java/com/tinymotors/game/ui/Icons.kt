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

    fun gear(d: DrawScope, color: Color) = with(d) {
        val c = center
        val r = size.minDimension / 2
        for (i in 0 until 8) {
            rotate(i * 45f, c) {
                drawRoundRect(color, Offset(c.x - r * 0.16f, c.y - r * 0.98f), Size(r * 0.32f, r * 0.5f), CornerRadius(r * 0.08f))
            }
        }
        drawCircle(color, r * 0.68f, c)
        drawCircle(Color.White, r * 0.28f, c)
    }

    fun home(d: DrawScope, color: Color) = with(d) {
        val w = size.width
        val h = size.height
        val roof = Path().apply {
            moveTo(w * 0.5f, h * 0.08f); lineTo(w * 0.95f, h * 0.5f); lineTo(w * 0.05f, h * 0.5f); close()
        }
        drawPath(roof, color, style = Stroke(w * 0.1f, join = StrokeJoin.Round))
        drawPath(roof, color)
        drawRoundRect(color, Offset(w * 0.18f, h * 0.45f), Size(w * 0.64f, h * 0.47f), CornerRadius(w * 0.08f))
        drawRoundRect(Color.White, Offset(w * 0.4f, h * 0.62f), Size(w * 0.2f, h * 0.3f), CornerRadius(w * 0.06f))
    }

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

    /** Tow hook on a chain. */
    fun hook(d: DrawScope, color: Color) = with(d) {
        val w = size.width
        val h = size.height
        val sw = w * 0.13f
        for (i in 0..1) drawCircle(color, w * 0.08f, Offset(w * 0.5f, h * (0.1f + i * 0.14f)), style = Stroke(w * 0.06f))
        drawLine(color, Offset(w * 0.5f, h * 0.34f), Offset(w * 0.5f, h * 0.6f), sw, StrokeCap.Round)
        drawArc(color, 0f, 200f, false, Offset(w * 0.22f, h * 0.42f), Size(w * 0.56f, h * 0.48f), style = Stroke(sw, cap = StrokeCap.Round))
    }

    fun garage(d: DrawScope, color: Color) = with(d) {
        val w = size.width
        val h = size.height
        val p = Path().apply {
            moveTo(w * 0.06f, h * 0.42f); lineTo(w * 0.5f, h * 0.1f); lineTo(w * 0.94f, h * 0.42f)
            lineTo(w * 0.94f, h * 0.92f); lineTo(w * 0.06f, h * 0.92f); close()
        }
        drawPath(p, color, style = Stroke(w * 0.08f, join = StrokeJoin.Round))
        drawPath(p, color)
        for (i in 0..2) {
            drawRoundRect(Color.White, Offset(w * 0.24f, h * (0.5f + i * 0.13f)), Size(w * 0.52f, h * 0.08f), CornerRadius(w * 0.03f))
        }
    }

    fun star(d: DrawScope, color: Color, rotation: Float = 0f) = with(d) {
        val c = center
        val r = size.minDimension / 2
        val p = Path()
        for (i in 0 until 10) {
            val rr = if (i % 2 == 0) r else r * 0.45f
            val a = Math.toRadians((i * 36 - 90 + rotation).toDouble())
            val x = c.x + rr * cos(a).toFloat()
            val y = c.y + rr * sin(a).toFloat()
            if (i == 0) p.moveTo(x, y) else p.lineTo(x, y)
        }
        p.close()
        drawPath(p, color, style = Stroke(r * 0.15f, join = StrokeJoin.Round))
        drawPath(p, color)
    }

    fun check(d: DrawScope, color: Color) = with(d) {
        val w = size.width
        val h = size.height
        val p = Path().apply { moveTo(w * 0.15f, h * 0.55f); lineTo(w * 0.42f, h * 0.8f); lineTo(w * 0.88f, h * 0.22f) }
        drawPath(p, color, style = Stroke(w * 0.16f, cap = StrokeCap.Round, join = StrokeJoin.Round))
    }

    fun close(d: DrawScope, color: Color) = with(d) {
        val w = size.width
        val h = size.height
        drawLine(color, Offset(w * 0.22f, h * 0.22f), Offset(w * 0.78f, h * 0.78f), w * 0.16f, StrokeCap.Round)
        drawLine(color, Offset(w * 0.78f, h * 0.22f), Offset(w * 0.22f, h * 0.78f), w * 0.16f, StrokeCap.Round)
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

    /** Wrapped present. [lidLift] 0..1 opens the lid. */
    fun gift(d: DrawScope, box: Color, ribbon: Color, lidLift: Float) = with(d) {
        val w = size.width
        val h = size.height
        val lidY = h * 0.3f - lidLift * h * 0.35f
        drawRoundRect(box, Offset(w * 0.12f, h * 0.42f), Size(w * 0.76f, h * 0.54f), CornerRadius(w * 0.08f))
        drawRect(ribbon, Offset(w * 0.44f, h * 0.42f), Size(w * 0.12f, h * 0.54f))
        rotate(-lidLift * 25f, Offset(w * 0.5f, lidY)) {
            drawRoundRect(box, Offset(w * 0.06f, lidY), Size(w * 0.88f, h * 0.16f), CornerRadius(w * 0.06f))
            drawRect(ribbon, Offset(w * 0.44f, lidY), Size(w * 0.12f, h * 0.16f))
            drawOval(ribbon, Offset(w * 0.24f, lidY - h * 0.14f), Size(w * 0.26f, h * 0.16f))
            drawOval(ribbon, Offset(w * 0.5f, lidY - h * 0.14f), Size(w * 0.26f, h * 0.16f))
        }
    }
}
