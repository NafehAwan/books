package com.tinymotors.game.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.toArgb
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/**
 * Sparkles and confetti stored in fixed arrays (no allocation per frame).
 * Particles only fade and fall; nothing flashes.
 */
class ParticleSystem(private val capacity: Int = 320) {
    private val x = FloatArray(capacity)
    private val y = FloatArray(capacity)
    private val vx = FloatArray(capacity)
    private val vy = FloatArray(capacity)
    private val life = FloatArray(capacity)
    private val maxLife = FloatArray(capacity)
    private val sizes = FloatArray(capacity)
    private val rot = FloatArray(capacity)
    private val vrot = FloatArray(capacity)
    private val color = IntArray(capacity)
    private val confetti = BooleanArray(capacity)
    private var next = 0

    /** Bumped on every emit so the frame loop restarts. */
    var version by mutableIntStateOf(0)
        private set

    /** Redraw counter read by the canvas. */
    var frame by mutableLongStateOf(0L)
        private set

    private fun slot(): Int {
        // reuse a dead slot, otherwise overwrite the oldest
        for (k in 0 until capacity) {
            val i = (next + k) % capacity
            if (life[i] <= 0f) {
                next = (i + 1) % capacity
                return i
            }
        }
        val i = next
        next = (next + 1) % capacity
        return i
    }

    fun burst(cx: Float, cy: Float, tint: Color, count: Int, speed: Float) {
        val main = tint.toArgb()
        for (n in 0 until count) {
            val i = slot()
            val a = Random.nextFloat() * 6.283f
            val v = speed * (0.35f + Random.nextFloat() * 0.65f)
            x[i] = cx
            y[i] = cy
            vx[i] = cos(a) * v
            vy[i] = sin(a) * v - speed * 0.25f
            maxLife[i] = 0.55f + Random.nextFloat() * 0.45f
            life[i] = maxLife[i]
            sizes[i] = speed * (0.018f + Random.nextFloat() * 0.02f)
            rot[i] = 0f
            vrot[i] = 0f
            color[i] = if (n % 3 == 0) WHITE_GOLD else main
            confetti[i] = false
        }
        version++
    }

    fun confetti(width: Float, height: Float, count: Int) {
        for (n in 0 until count) {
            val i = slot()
            x[i] = Random.nextFloat() * width
            y[i] = -Random.nextFloat() * height * 0.3f
            vx[i] = (Random.nextFloat() - 0.5f) * width * 0.15f
            vy[i] = height * (0.18f + Random.nextFloat() * 0.18f)
            maxLife[i] = 2.6f + Random.nextFloat() * 1.2f
            life[i] = maxLife[i]
            sizes[i] = width * (0.014f + Random.nextFloat() * 0.012f)
            rot[i] = Random.nextFloat() * 360f
            vrot[i] = (Random.nextFloat() - 0.5f) * 360f
            color[i] = CONFETTI[n % CONFETTI.size]
            confetti[i] = true
        }
        version++
    }

    /** Advances the simulation; returns false when nothing is alive (the loop can stop). */
    fun update(dt: Float): Boolean {
        var any = false
        for (i in 0 until capacity) {
            if (life[i] <= 0f) continue
            life[i] -= dt
            if (life[i] <= 0f) continue
            any = true
            if (confetti[i]) {
                vx[i] *= 0.99f
                x[i] += vx[i] * dt + sin(life[i] * 3f + i) * 0.6f
            } else {
                vy[i] += 900f * dt * 0.5f
                vx[i] *= 0.96f
                x[i] += vx[i] * dt
            }
            y[i] += vy[i] * dt
            rot[i] += vrot[i] * dt
        }
        frame++
        return any
    }

    fun clear() {
        life.fill(0f)
        frame++
    }

    fun draw(scope: DrawScope) = with(scope) {
        for (i in 0 until capacity) {
            val l = life[i]
            if (l <= 0f) continue
            val alpha = (l / maxLife[i]).coerceIn(0f, 1f)
            val c = Color(color[i])
            if (confetti[i]) {
                val s = sizes[i]
                rotate(rot[i], Offset(x[i], y[i])) {
                    drawRect(c, Offset(x[i] - s / 2, y[i] - s / 4), Size(s, s / 2), alpha = alpha.coerceAtMost(0.95f))
                }
            } else {
                drawCircle(c, sizes[i] * (0.5f + alpha * 0.5f), Offset(x[i], y[i]), alpha = alpha)
            }
        }
    }

    private companion object {
        val WHITE_GOLD = Color(0xFFFFF2B3).toArgb()
        val CONFETTI = intArrayOf(
            Color(0xFFFF6F91).toArgb(), Color(0xFFFFC83D).toArgb(), Color(0xFF4CD07D).toArgb(),
            Color(0xFF45ADF7).toArgb(), Color(0xFFA084F0).toArgb(), Color(0xFFFFA62B).toArgb(),
        )
    }
}

/** Full-screen layer that runs the particle frame loop only while particles are alive. */
@Composable
fun ParticleLayer(system: ParticleSystem, modifier: Modifier = Modifier) {
    LaunchedEffect(system.version) {
        var last = 0L
        var alive = true
        while (alive) {
            withFrameNanos { now ->
                val dt = if (last == 0L) 0.016f else ((now - last) / 1e9f).coerceAtMost(0.05f)
                last = now
                alive = system.update(dt)
            }
        }
    }
    Canvas(modifier.fillMaxSize()) {
        // Reading the frame counter subscribes this canvas to every simulation step.
        if (system.frame >= 0L) system.draw(this)
    }
}
