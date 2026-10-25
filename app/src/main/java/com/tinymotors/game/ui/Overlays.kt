package com.tinymotors.game.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import com.tinymotors.game.R
import com.tinymotors.game.engine.Cars
import com.tinymotors.game.engine.GameEngine
import com.tinymotors.game.engine.GameState
import com.tinymotors.game.ui.theme.LocalReduceMotion
import com.tinymotors.game.ui.theme.Palette
import kotlinx.coroutines.delay
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sin

/** Places the content with its centre at a point given in the parent's coordinates. */
fun Modifier.centerAt(at: () -> Offset): Modifier = layout { measurable, constraints ->
    val p = measurable.measure(constraints.copy(minWidth = 0, minHeight = 0))
    layout(p.width, p.height) {
        val o = at()
        p.place((o.x - p.width / 2f).roundToInt(), (o.y - p.height / 2f).roundToInt())
    }
}

/** Places the content with its bottom-left corner at a point given in the parent's coordinates. */
fun Modifier.bottomStartAt(at: () -> Offset): Modifier = layout { measurable, constraints ->
    val p = measurable.measure(constraints.copy(minWidth = 0, minHeight = 0))
    layout(p.width, p.height) {
        val o = at()
        p.place(o.x.roundToInt(), (o.y - p.height).roundToInt())
    }
}

@Composable
fun NameBubble(b: Bubble) {
    val a = remember(b.token) { Animatable(0f) }
    LaunchedEffect(b.token) {
        a.animateTo(1f, spring(dampingRatio = 0.5f))
        delay(1500)
        a.animateTo(0f, tween(250))
    }
    Box(
        Modifier
            .centerAt { b.at }
            .graphicsLayer {
                alpha = a.value.coerceIn(0f, 1f)
                scaleX = 0.6f + 0.4f * a.value
                scaleY = 0.6f + 0.4f * a.value
            },
    ) {
        Panel(corner = 16.dp) {
            KidText(b.text, Modifier.padding(horizontal = 14.dp, vertical = 8.dp), size = 20f)
        }
    }
}

@Composable
fun ComboText(c: ComboPop) {
    val reduce = LocalReduceMotion.current
    val t = remember(c.token) { Animatable(0f) }
    LaunchedEffect(c.token) { t.animateTo(1f, tween(1100, easing = LinearEasing)) }
    val density = LocalDensity.current
    val grow = 1f + (c.count.coerceAtMost(6) - 2) * 0.12f
    Box(
        Modifier
            .centerAt { c.at - Offset(0f, with(density) { (if (reduce) 30.dp else (30 + 60 * t.value).dp).toPx() }) }
            .graphicsLayer {
                alpha = 1f - ((t.value - 0.6f) / 0.4f).coerceIn(0f, 1f)
                scaleX = grow
                scaleY = grow
            },
    ) {
        KidText(stringResource(R.string.combo, c.count), size = 26f, color = Palette.OrangeShade)
    }
}

@Composable
fun SpeechBubble(text: String, modifier: Modifier = Modifier) {
    val density = LocalDensity.current
    Box(
        modifier.layout { m, _ ->
            val p = m.measure(Constraints(maxWidth = with(density) { 220.dp.roundToPx() }))
            layout(0, 0) { p.place(-p.width, with(density) { 2.dp.roundToPx() }) }
        },
    ) {
        Panel(corner = 14.dp) {
            KidText(text, Modifier.padding(horizontal = 12.dp, vertical = 8.dp), size = 16f)
        }
    }
}

/** The newly unlocked car drives across the screen. */
@Composable
fun DriveAcross(level: Int, token: Int, onDone: () -> Unit) {
    val reduce = LocalReduceMotion.current
    val art = LocalCarArt.current
    val t = remember(token) { Animatable(0f) }
    LaunchedEffect(token) {
        if (!reduce) t.animateTo(1f, tween(2600, easing = LinearEasing))
        onDone()
    }
    if (reduce) return
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val w = constraints.maxWidth.toFloat()
        val h = constraints.maxHeight.toFloat()
        val carPx = with(LocalDensity.current) { 160.dp.toPx() }
        Canvas(Modifier.fillMaxSize()) {
            val x = -carPx + (w + carPx) * t.value
            val y = h * 0.62f - abs(sin(t.value * PI * 8)).toFloat() * carPx * 0.04f
            drawCar(art[level], Offset(x, y), Size(carPx, carPx))
        }
    }
}

@Composable
fun NewCarBanner(b: Banner, onDone: () -> Unit) {
    val art = LocalCarArt.current
    val y = remember(b.token) { Animatable(-1f) }
    LaunchedEffect(b.token) {
        y.animateTo(0f, spring(dampingRatio = 0.55f, stiffness = 300f))
        delay(2600)
        y.animateTo(-1.4f, tween(300))
        onDone()
    }
    Box(
        Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(top = 70.dp),
        contentAlignment = Alignment.TopCenter,
    ) {
        Column(
            Modifier.graphicsLayer { translationY = y.value * 320.dp.toPx() },
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Canvas(Modifier.size(130.dp)) { drawCar(art[b.level], Offset.Zero, size) }
            Box(Modifier.offset(y = (-26).dp).size(300.dp, 92.dp), contentAlignment = Alignment.Center) {
                Canvas(Modifier.fillMaxSize()) { drawRibbon() }
                Column(Modifier.padding(bottom = 14.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    OutlinedTitle(stringResource(art[b.level].nameRes), size = 26f, outline = Palette.RibbonShade)
                    KidText(stringResource(R.string.new_car_unlocked), size = 14f, color = Color.White)
                }
            }
            if (b.tow) {
                Row(
                    Modifier
                        .offset(y = (-22).dp)
                        .clip(CircleShape)
                        .background(Color.White)
                        .padding(horizontal = 12.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Art(R.drawable.ui_tow, Modifier.size(28.dp))
                    HSpace(6.dp)
                    KidText(stringResource(R.string.plus_one_tow), size = 16f, color = Palette.HeaderShade)
                }
            }
        }
    }
}

@Composable
fun StuckDialog(
    canHelper: Boolean,
    canUndo: Boolean,
    score: Long,
    onHelper: () -> Unit,
    onUndo: () -> Unit,
    onNewGame: () -> Unit,
) {
    FullScrim {
        DialogCard {
            Column(Modifier.fillMaxWidth().padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Mascot(MascotMood.Shrug, 0, stringResource(R.string.cd_mascot), size = 110.dp)
                KidText(stringResource(R.string.garage_full), size = 28f)
                VSpace(8.dp)
                if (canHelper) {
                    KidText(stringResource(R.string.helper_offer), size = 18f, color = Palette.InkSoft, bold = false)
                    VSpace(16.dp)
                    ChunkyButton(onHelper, Palette.Blue, Palette.BlueShade, stringResource(R.string.call_tow_truck), Modifier.fillMaxWidth().height(72.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(44.dp).clip(CircleShape).background(Color.White), contentAlignment = Alignment.Center) {
                                Art(R.drawable.ui_tow, Modifier.size(34.dp))
                            }
                            HSpace(8.dp)
                            KidText(stringResource(R.string.call_tow_truck), size = 20f, color = Color.White)
                        }
                    }
                } else {
                    KidText(pluralStringResource(R.plurals.encourage, score.coerceAtMost(Int.MAX_VALUE.toLong()).toInt(), score), size = 18f, color = Palette.InkSoft, bold = false)
                    VSpace(16.dp)
                    ChunkyButton(onNewGame, Palette.Green, Palette.GreenShade, stringResource(R.string.new_game), Modifier.fillMaxWidth().height(72.dp)) {
                        KidText(stringResource(R.string.new_game), size = 22f, color = Color.White)
                    }
                }
                if (canUndo) {
                    VSpace(12.dp)
                    ChunkyButton(onUndo, Palette.Purple, Palette.PurpleShade, stringResource(R.string.undo), Modifier.fillMaxWidth().height(60.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Canvas(Modifier.size(26.dp)) { KidIcons.undo(this, Color.White) }
                            HSpace(8.dp)
                            KidText(stringResource(R.string.undo), size = 18f, color = Color.White)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun WinDialog(onKeepPlaying: () -> Unit, onNewGame: () -> Unit, particles: ParticleSystem, size: Size) {
    val reduce = LocalReduceMotion.current
    val art = LocalCarArt.current
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(1400)
        visible = true
        if (!reduce) {
            particles.confetti(size.width, size.height, 120)
            delay(1200)
            particles.confetti(size.width, size.height, 80)
        }
    }
    if (!visible) return
    FullScrim {
        DialogCard {
            Column(Modifier.fillMaxWidth().padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Art(R.drawable.ui_win_badge, Modifier.size(230.dp, 190.dp), stringResource(R.string.cd_mascot))
                Canvas(Modifier.size(150.dp, 110.dp)) {
                    val side = this.size.width
                    drawCar(art[Cars.MAX_LEVEL], Offset(0f, (this.size.height - side) / 2), Size(side, side))
                }
                OutlinedTitle(stringResource(R.string.you_won), size = 26f, fill = Palette.HeaderBlue, outline = Color.White)
                VSpace(16.dp)
                ChunkyButton(onKeepPlaying, Palette.Green, Palette.GreenShade, stringResource(R.string.keep_playing), Modifier.fillMaxWidth().height(72.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Canvas(Modifier.size(26.dp)) { KidIcons.play(this, Color.White) }
                        HSpace(8.dp)
                        KidText(stringResource(R.string.keep_playing), size = 22f, color = Color.White)
                    }
                }
                VSpace(12.dp)
                ChunkyButton(onNewGame, Palette.Orange, Palette.OrangeShade, stringResource(R.string.new_game), Modifier.fillMaxWidth().height(64.dp)) {
                    KidText(stringResource(R.string.new_game), size = 20f, color = Color.White)
                }
            }
        }
    }
}

/** Three-step first-run tutorial: tap Add car, drag two matching cars, collect them all. */
@Composable
fun Tutorial(
    step: Int,
    game: GameState,
    geo: BoardGeo,
    boardOrigin: Offset,
    addRect: Rect?,
    onSkip: () -> Unit,
    onDone: () -> Unit,
) {
    val art = LocalCarArt.current
    val reduce = LocalReduceMotion.current
    val t = rememberInfiniteTransition(label = "tutorial")
    val p = if (reduce) 0.5f else t.animateFloat(0f, 1f, infiniteRepeatable(tween(1700, easing = FastOutSlowInEasing), RepeatMode.Restart), label = "p").value
    val pair = remember(game.board) { GameEngine(game).findHintPair() }
    val density = LocalDensity.current

    Box(Modifier.fillMaxSize()) {
        when {
            step == 0 || (step == 1 && pair == null) -> if (addRect != null) {
                val press = abs(sin(p * 2 * PI)).toFloat()
                Box(Modifier.centerAt { addRect.center + Offset(addRect.width * 0.18f, addRect.height * 0.55f + press * 10f * density.density) }) {
                    Canvas(Modifier.size(64.dp)) { KidIcons.finger(this, Color(0xFFFFD9B8), Palette.Ink) }
                }
                Box(Modifier.centerAt { Offset(addRect.center.x, addRect.top - 44f * density.density) }) {
                    Panel(corner = 16.dp) {
                        Row(Modifier.padding(horizontal = 14.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Canvas(Modifier.size(26.dp)) { KidIcons.plus(this, Palette.OrangeShade) }
                            HSpace(6.dp)
                            KidText(stringResource(R.string.tut_tap_add), size = 20f)
                        }
                    }
                }
            }
            step == 1 && pair != null -> {
                val a = boardOrigin + geo.slotCenter(pair.first)
                val b = boardOrigin + geo.slotCenter(pair.second)
                val k = ((p - 0.15f) / 0.6f).coerceIn(0f, 1f)
                val at = a + (b - a) * k
                val level = game.board[pair.first]?.level ?: 1
                val look = art[level]
                val tileDp = with(density) { geo.tile.toDp() }
                Box(Modifier.centerAt { at - Offset(0f, geo.tile * 0.2f) }.graphicsLayer { alpha = 0.75f }) {
                    Canvas(Modifier.size(tileDp)) { drawCar(look, Offset.Zero, size) }
                }
                Box(Modifier.centerAt { at + Offset(geo.tile * 0.15f, geo.tile * 0.35f) }) {
                    Canvas(Modifier.size(60.dp)) { KidIcons.finger(this, Color(0xFFFFD9B8), Palette.Ink) }
                }
                // car + car = next car, just above the board's left edge (Towy sits on the right)
                Box(Modifier.bottomStartAt { Offset(boardOrigin.x + 4f * density.density, boardOrigin.y - 6f * density.density) }) {
                    Panel(corner = 16.dp) {
                        Row(Modifier.padding(horizontal = 10.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                            Canvas(Modifier.size(52.dp)) { drawCar(look, Offset.Zero, size, shadow = false) }
                            KidText("+", size = 26f)
                            Canvas(Modifier.size(52.dp)) { drawCar(look, Offset.Zero, size, shadow = false) }
                            KidText("=", size = 26f)
                            Canvas(Modifier.size(64.dp)) { drawCar(art[level + 1], Offset.Zero, size, shadow = false) }
                        }
                    }
                }
            }
        }
        if (step >= 2) {
            FullScrim {
                DialogCard {
                    Column(Modifier.fillMaxWidth().padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
                            Art(R.drawable.ui_star_small, Modifier.size(40.dp))
                            Art(R.drawable.ui_book, Modifier.size(84.dp))
                            Art(R.drawable.ui_star_small, Modifier.size(40.dp))
                        }
                        VSpace(8.dp)
                        OutlinedTitle(stringResource(R.string.tut_collect), size = 26f, fill = Palette.HeaderBlue, outline = Color.White)
                        VSpace(16.dp)
                        ChunkyButton(onDone, Palette.Green, Palette.GreenShade, stringResource(R.string.ok), Modifier.size(120.dp, 72.dp)) {
                            Canvas(Modifier.size(36.dp)) { KidIcons.check(this, Color.White) }
                        }
                    }
                }
            }
        } else {
            Box(
                Modifier
                    .fillMaxSize()
                    .windowInsetsPadding(WindowInsets.safeDrawing)
                    .padding(top = 92.dp, end = 12.dp),
                contentAlignment = Alignment.TopEnd,
            ) {
                ChunkyButton(onSkip, Palette.Panel, Palette.PanelShade, stringResource(R.string.skip), Modifier.height(56.dp), CircleShape, depth = 4.dp) {
                    Row(Modifier.padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                        KidText(stringResource(R.string.skip), size = 16f, color = Palette.InkSoft)
                        HSpace(4.dp)
                        Canvas(Modifier.size(14.dp)) { KidIcons.play(this, Palette.InkSoft) }
                    }
                }
            }
        }
    }
}
