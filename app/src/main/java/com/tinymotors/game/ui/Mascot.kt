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
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.tinymotors.game.R
import com.tinymotors.game.ui.theme.LocalReduceMotion
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.sin
import kotlin.random.Random

enum class MascotMood { Idle, Cheer, Shrug, Hint, Dance }

/** Which Towy picture to use. [Peek] pictures show Towy's face and paws resting on an edge. */
enum class MascotPose { Peek, Full }

private fun peekImage(mood: MascotMood, blinking: Boolean): Int = when (mood) {
    MascotMood.Idle -> if (blinking) R.drawable.ui_towy_squint else R.drawable.ui_towy_happy
    MascotMood.Cheer -> R.drawable.ui_towy_laugh
    MascotMood.Dance -> R.drawable.ui_towy_squint
    MascotMood.Shrug -> R.drawable.ui_towy_surprised
    MascotMood.Hint -> R.drawable.ui_towy_happy
}

private fun fullImage(mood: MascotMood): Int = when (mood) {
    MascotMood.Cheer, MascotMood.Dance -> R.drawable.ui_towy_bounce
    else -> R.drawable.ui_towy_front
}

/**
 * Towy, the friendly tow truck. Bobs and blinks when idle, jumps on [MascotMood.Cheer]
 * (re-triggered whenever [moodKey] changes), tilts on Shrug, leans on Hint and wiggles on Dance.
 */
@Composable
fun Mascot(
    mood: MascotMood,
    moodKey: Int,
    description: String,
    modifier: Modifier = Modifier,
    size: Dp = 84.dp,
    pose: MascotPose = MascotPose.Full,
) {
    val reduce = LocalReduceMotion.current
    val jump = remember { Animatable(0f) }
    val tilt = remember { Animatable(0f) }
    var blinking by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        while (true) {
            delay(Random.nextLong(2400, 4800))
            blinking = true
            delay(160)
            blinking = false
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
            MascotMood.Shrug -> tilt.animateTo(-8f, spring(dampingRatio = 0.5f))
            MascotMood.Hint -> tilt.animateTo(7f, spring(dampingRatio = 0.5f))
            else -> tilt.animateTo(0f, spring(dampingRatio = 0.5f))
        }
    }
    val t = rememberInfiniteTransition(label = "mascot")
    val phase = if (reduce) 0f else t.animateFloat(0f, 1f, infiniteRepeatable(tween(1800, easing = LinearEasing), RepeatMode.Restart), label = "p").value

    val res = when (pose) {
        MascotPose.Peek -> peekImage(mood, blinking && !reduce)
        MascotPose.Full -> fullImage(mood)
    }
    Image(
        painter = painterResource(res),
        contentDescription = description,
        contentScale = ContentScale.Fit,
        modifier = modifier
            .size(size)
            .graphicsLayer {
                val wave = sin(phase * 2 * PI).toFloat()
                val px = size.toPx()
                val bob = if (mood == MascotMood.Dance) wave * px * 0.05f else wave * px * 0.015f
                translationY = -jump.value * px * 0.16f + bob
                rotationZ = tilt.value + if (mood == MascotMood.Dance) sin(phase * 4 * PI).toFloat() * 9f else 0f
                val squash = jump.value * 0.06f
                scaleX = 1f - squash
                scaleY = 1f + squash
                transformOrigin = TransformOrigin(0.5f, 1f)
            },
    )
}
