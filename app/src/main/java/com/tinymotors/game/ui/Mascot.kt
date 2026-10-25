package com.tinymotors.game.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.tinymotors.game.ui.theme.LocalReduceMotion
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.sin
import kotlin.random.Random

enum class MascotMood { Idle, Cheer, Shrug, Hint, Dance }

private val Body = Color(0xFFFFB830)
private val BodyShade = Color(0xFFE8941A)
private val Dark = Color(0xFF2B3A55)
private val Hub = Color(0xFFDDE5EE)
private val Cheek = Color(0x66FF6F91)
private val Light = Color(0xFF7FD3FF)

/**
 * "Towie", the friendly round tow truck. Blinks when idle, jumps on [MascotMood.Cheer]
 * (re-triggered whenever [moodKey] changes), tilts on Shrug, leans on Hint and wiggles on Dance.
 */
@Composable
fun Mascot(mood: MascotMood, moodKey: Int, description: String, modifier: Modifier = Modifier, size: Dp = 84.dp) {
    val reduce = LocalReduceMotion.current
    val eyeOpen = remember { Animatable(1f) }
    val jump = remember { Animatable(0f) }
    val tilt = remember { Animatable(0f) }

    LaunchedEffect(Unit) {
        while (true) {
            delay(Random.nextLong(2200, 4600))
            eyeOpen.animateTo(0.08f, tween(70))
            eyeOpen.animateTo(1f, tween(110))
        }
    }
    LaunchedEffect(mood, moodKey) {
        when (mood) {
            MascotMood.Cheer -> if (!reduce) {
                launch { tilt.animateTo(0f, spring()) }
                jump.snapTo(0f)
                jump.animateTo(1f, tween(160))
                jump.animateTo(0f, spring(dampingRatio = 0.35f, stiffness = Spring.StiffnessMediumLow))
            }
            MascotMood.Shrug -> tilt.animateTo(-9f, spring(dampingRatio = 0.5f))
            MascotMood.Hint -> tilt.animateTo(7f, spring(dampingRatio = 0.5f))
            else -> tilt.animateTo(0f, spring(dampingRatio = 0.5f))
        }
    }
    val t = rememberInfiniteTransition(label = "mascot")
    val phase = if (reduce) 0f else t.animateFloat(0f, 1f, infiniteRepeatable(tween(1600, easing = LinearEasing), RepeatMode.Restart), label = "p").value

    Canvas(modifier.size(size).semantics { contentDescription = description }) {
        val s = this.size.minDimension
        val wave = sin(phase * 2 * PI).toFloat()
        val bob = if (mood == MascotMood.Dance) wave * s * 0.04f else wave * s * 0.012f
        val danceTilt = if (mood == MascotMood.Dance) sin(phase * 4 * PI).toFloat() * 10f else 0f
        val y = -jump.value * s * 0.16f + bob
        val squash = 1f + jump.value * 0.06f
        translate(top = y) {
            rotate(tilt.value + danceTilt, Offset(s * 0.5f, s * 0.85f)) {
                drawTowie(s, eyeOpen.value, mood, squash)
            }
        }
    }
}

private fun DrawScope.drawTowie(s: Float, eyeOpen: Float, mood: MascotMood, squash: Float) {
    // ground shadow
    drawOval(Color(0x332B3A55), Offset(s * 0.12f, s * 0.9f), Size(s * 0.76f, s * 0.08f))
    // crane arm and hook (behind the body)
    drawLine(Dark, Offset(s * 0.26f, s * 0.36f), Offset(s * 0.1f, s * 0.12f), s * 0.06f, StrokeCap.Round)
    drawLine(Dark, Offset(s * 0.1f, s * 0.12f), Offset(s * 0.1f, s * 0.3f), s * 0.025f, StrokeCap.Round)
    drawArc(Dark, 0f, 220f, false, Offset(s * 0.05f, s * 0.27f), Size(s * 0.1f, s * 0.1f), style = Stroke(s * 0.03f, cap = StrokeCap.Round))
    // roof light (steady, never flashing)
    drawRoundRect(Light, Offset(s * 0.5f, s * 0.13f), Size(s * 0.14f, s * 0.1f), CornerRadius(s * 0.05f))
    // body
    val bodyTop = s * 0.2f / squash
    drawRoundRect(BodyShade, Offset(s * 0.12f, bodyTop + s * 0.04f), Size(s * 0.8f, s * 0.64f - bodyTop + s * 0.2f), CornerRadius(s * 0.26f))
    drawRoundRect(Body, Offset(s * 0.12f, bodyTop), Size(s * 0.8f, s * 0.62f - bodyTop + s * 0.2f), CornerRadius(s * 0.26f))
    // windscreen face panel
    drawRoundRect(Color.White.copy(alpha = 0.9f), Offset(s * 0.34f, bodyTop + s * 0.08f), Size(s * 0.5f, s * 0.3f), CornerRadius(s * 0.14f))
    // eyes
    val happy = mood == MascotMood.Cheer || mood == MascotMood.Dance
    val look = if (mood == MascotMood.Hint) s * 0.025f else 0f
    val eyeY = bodyTop + s * 0.22f
    for (ex in floatArrayOf(s * 0.48f, s * 0.7f)) {
        if (happy) {
            drawArc(Dark, 200f, 140f, false, Offset(ex - s * 0.05f, eyeY - s * 0.03f), Size(s * 0.1f, s * 0.08f), style = Stroke(s * 0.03f, cap = StrokeCap.Round))
        } else {
            val h = s * 0.11f * eyeOpen
            drawOval(Dark, Offset(ex - s * 0.04f + look, eyeY - h / 2), Size(s * 0.08f, h.coerceAtLeast(s * 0.01f)))
            if (eyeOpen > 0.5f) drawCircle(Color.White, s * 0.015f, Offset(ex - s * 0.012f + look, eyeY - s * 0.02f))
        }
    }
    // eyebrows on shrug
    if (mood == MascotMood.Shrug) {
        drawLine(Dark, Offset(s * 0.43f, eyeY - s * 0.1f), Offset(s * 0.52f, eyeY - s * 0.12f), s * 0.02f, StrokeCap.Round)
        drawLine(Dark, Offset(s * 0.66f, eyeY - s * 0.12f), Offset(s * 0.75f, eyeY - s * 0.1f), s * 0.02f, StrokeCap.Round)
    }
    // cheeks
    drawCircle(Cheek, s * 0.04f, Offset(s * 0.4f, eyeY + s * 0.08f))
    drawCircle(Cheek, s * 0.04f, Offset(s * 0.79f, eyeY + s * 0.08f))
    // mouth
    val mouthY = eyeY + s * 0.07f
    when (mood) {
        MascotMood.Shrug -> drawLine(Dark, Offset(s * 0.54f, mouthY + s * 0.03f), Offset(s * 0.64f, mouthY + s * 0.03f), s * 0.025f, StrokeCap.Round)
        MascotMood.Cheer, MascotMood.Dance -> drawArc(Dark, 0f, 180f, true, Offset(s * 0.52f, mouthY - s * 0.01f), Size(s * 0.14f, s * 0.1f))
        else -> drawArc(Dark, 20f, 140f, false, Offset(s * 0.52f, mouthY - s * 0.03f), Size(s * 0.14f, s * 0.08f), style = Stroke(s * 0.025f, cap = StrokeCap.Round))
    }
    // bumper and wheels
    drawRoundRect(Dark, Offset(s * 0.78f, s * 0.7f), Size(s * 0.16f, s * 0.06f), CornerRadius(s * 0.03f))
    for (wx in floatArrayOf(s * 0.3f, s * 0.72f)) {
        drawCircle(Dark, s * 0.12f, Offset(wx, s * 0.82f))
        drawCircle(Hub, s * 0.05f, Offset(wx, s * 0.82f))
    }
}
