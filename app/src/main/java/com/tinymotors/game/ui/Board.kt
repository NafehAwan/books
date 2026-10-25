package com.tinymotors.game.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.unit.IntOffset
import com.tinymotors.game.engine.Car
import com.tinymotors.game.engine.Cars
import com.tinymotors.game.engine.GameRules
import com.tinymotors.game.ui.theme.LocalReduceMotion
import com.tinymotors.game.ui.theme.Palette
import kotlinx.coroutines.launch
import kotlin.math.floor
import kotlin.math.roundToInt

/** Pixel geometry of the 5 x 6 board. */
class BoardGeo(val tile: Float, val gap: Float, val pad: Float) {
    val width = pad * 2 + tile * GameRules.COLUMNS + gap * (GameRules.COLUMNS - 1)
    val height = pad * 2 + tile * GameRules.ROWS + gap * (GameRules.ROWS - 1)

    fun slotTopLeft(i: Int) = Offset(
        pad + (i % GameRules.COLUMNS) * (tile + gap),
        pad + (i / GameRules.COLUMNS) * (tile + gap),
    )

    fun slotCenter(i: Int) = slotTopLeft(i) + Offset(tile / 2, tile / 2)

    /** Slot under a point in board coordinates, or null when outside the board. */
    fun slotAt(p: Offset): Int? {
        if (p.x < 0 || p.y < 0 || p.x > width || p.y > height) return null
        val c = floor((p.x - pad + gap / 2) / (tile + gap)).toInt().coerceIn(0, GameRules.COLUMNS - 1)
        val r = floor((p.y - pad + gap / 2) / (tile + gap)).toInt().coerceIn(0, GameRules.ROWS - 1)
        return r * GameRules.COLUMNS + c
    }
}

enum class FxKind { Pop, Squash, Wiggle, DriveIn }

/** One-shot animation request for a car, identified by an increasing token. */
data class CarFx(val kind: FxKind, val token: Int)

/** What the board needs to know about an ongoing drag. */
data class DragInfo(val fromSlot: Int, val car: Car, val hoverSlot: Int?, val overTow: Boolean)

@Composable
fun Board(
    board: List<Car?>,
    geo: BoardGeo,
    drag: DragInfo?,
    hint: Pair<Int, Int>?,
    fx: Map<Int, CarFx>,
    snapIds: Set<Int>,
    measurer: TextMeasurer,
    onPlaced: (LayoutCoordinates) -> Unit,
) {
    val art = LocalCarArt.current
    val density = LocalDensity.current
    val wDp = with(density) { geo.width.toDp() }
    val hDp = with(density) { geo.height.toDp() }
    Box(Modifier.size(wDp, hDp).onGloballyPositioned(onPlaced)) {
        Canvas(Modifier.size(wDp, hDp)) {
            val r = CornerRadius(geo.tile * 0.2f)
            drawRoundRect(Palette.PanelShade, Offset(0f, 4f * density.density), Size(geo.width, geo.height), CornerRadius(20f * density.density))
            drawRoundRect(Palette.Panel, Offset.Zero, Size(geo.width, geo.height), CornerRadius(20f * density.density))
            val dragLevel = drag?.car?.level
            for (i in 0 until GameRules.SLOTS) {
                val tl = geo.slotTopLeft(i)
                drawRoundRect(Palette.SlotEdge, tl + Offset(0f, 2f * density.density), Size(geo.tile, geo.tile), r)
                drawRoundRect(Palette.Slot, tl, Size(geo.tile, geo.tile), r)
                if (drag != null && i != drag.fromSlot) {
                    val here = board[i]
                    val isMatch = here != null && here.level == dragLevel && Cars.canMerge(here.level)
                    val grow = 4f * density.density
                    if (isMatch) {
                        val strong = drag.hoverSlot == i
                        drawRoundRect(
                            Palette.Glow.copy(alpha = if (strong) 0.55f else 0.28f),
                            tl - Offset(grow, grow), Size(geo.tile + grow * 2, geo.tile + grow * 2), CornerRadius(geo.tile * 0.24f),
                        )
                        drawRoundRect(
                            Palette.Glow, tl - Offset(grow / 2, grow / 2), Size(geo.tile + grow, geo.tile + grow), CornerRadius(geo.tile * 0.22f),
                            style = Stroke(if (strong) 4f * density.density else 2.5f * density.density),
                        )
                    } else if (drag.hoverSlot == i) {
                        drawDashedSlot(Palette.InkSoft.copy(alpha = 0.6f), tl, Size(geo.tile, geo.tile), 2f * density.density)
                    }
                }
            }
        }
        for (i in 0 until GameRules.SLOTS) {
            val car = board[i] ?: continue
            key(car.id) {
                BoardCar(
                    car = car,
                    slot = i,
                    geo = geo,
                    hidden = drag?.car?.id == car.id,
                    hinted = hint != null && (hint.first == i || hint.second == i),
                    fx = fx[car.id],
                    snap = car.id in snapIds,
                    look = art[car.level],
                    measurer = measurer,
                )
            }
        }
    }
}

@Composable
private fun BoardCar(
    car: Car,
    slot: Int,
    geo: BoardGeo,
    hidden: Boolean,
    hinted: Boolean,
    fx: CarFx?,
    snap: Boolean,
    look: CarLook,
    measurer: TextMeasurer,
) {
    val reduce = LocalReduceMotion.current
    val density = LocalDensity.current
    val target = geo.slotTopLeft(slot).let { IntOffset(it.x.roundToInt(), it.y.roundToInt()) }
    val drivingIn = fx?.kind == FxKind.DriveIn && !reduce
    val pos = remember {
        val start = if (drivingIn) IntOffset(-(target.x + geo.tile * 2.2f).roundToInt(), target.y) else target
        Animatable(start, IntOffset.VectorConverter)
    }
    val sx = remember { Animatable(1f) }
    val sy = remember { Animatable(1f) }
    val rot = remember { Animatable(0f) }
    val alpha = remember { Animatable(if (fx?.kind == FxKind.DriveIn && reduce) 0f else 1f) }

    LaunchedEffect(target) {
        if (pos.value == target) return@LaunchedEffect
        if (snap || reduce) {
            pos.snapTo(target)
        } else {
            pos.animateTo(target, spring(dampingRatio = 0.72f, stiffness = 420f))
        }
    }
    LaunchedEffect(fx?.token) {
        val kind = fx?.kind ?: return@LaunchedEffect
        if (reduce) {
            if (kind == FxKind.DriveIn) alpha.animateTo(1f, tween(200))
            return@LaunchedEffect
        }
        when (kind) {
            FxKind.DriveIn -> {
                launch { rot.snapTo(-4f); rot.animateTo(0f, spring(dampingRatio = 0.4f, stiffness = 120f)) }
                pos.animateTo(target, spring(dampingRatio = 0.55f, stiffness = 170f))
                squash(sx, sy)
            }
            FxKind.Pop -> {
                sx.snapTo(0.35f); sy.snapTo(0.35f)
                launch { sx.animateTo(1f, spring(dampingRatio = 0.42f, stiffness = Spring.StiffnessMediumLow)) }
                sy.animateTo(1f, spring(dampingRatio = 0.42f, stiffness = Spring.StiffnessMediumLow))
            }
            FxKind.Squash -> squash(sx, sy)
            FxKind.Wiggle -> wiggle(rot)
        }
    }
    LaunchedEffect(hinted) {
        if (!hinted || reduce) return@LaunchedEffect
        while (true) {
            wiggle(rot, 6f)
            kotlinx.coroutines.delay(900)
        }
    }

    val tileDp = with(density) { geo.tile.toDp() }
    val name = stringResource(look.nameRes)
    Canvas(
        Modifier
            .offset { pos.value }
            .size(tileDp)
            .semantics { contentDescription = name }
            .graphicsLayer {
                scaleX = sx.value
                scaleY = sy.value
                rotationZ = rot.value
                this.alpha = if (hidden) 0f else alpha.value
                transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0.5f, 0.85f)
            },
    ) {
        drawCarTile(look, size, measurer, Cars.value(car.level))
    }
}

suspend fun squash(sx: Animatable<Float, *>, sy: Animatable<Float, *>) {
    sx.snapTo(1.16f)
    sy.snapTo(0.84f)
    kotlinx.coroutines.coroutineScope {
        launch { sx.animateTo(1f, spring(dampingRatio = 0.35f, stiffness = 500f)) }
        sy.animateTo(1f, spring(dampingRatio = 0.35f, stiffness = 500f))
    }
}

suspend fun wiggle(rot: Animatable<Float, *>, amount: Float = 9f) {
    for (a in floatArrayOf(amount, -amount, amount * 0.6f, -amount * 0.4f, 0f)) {
        rot.animateTo(a, tween(70))
    }
}

/** Glow colour for the tow button while a car hovers it. */
val TowGlow = Color(0xFF7FD3FF)
