package com.tinymotors.game.ui

import android.os.SystemClock
import android.view.HapticFeedbackConstants
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.zIndex
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import com.tinymotors.game.Effects
import com.tinymotors.game.GameViewModel
import com.tinymotors.game.R
import com.tinymotors.game.Screen
import com.tinymotors.game.UiState
import com.tinymotors.game.engine.Car
import com.tinymotors.game.engine.Cars
import com.tinymotors.game.engine.GameEngine
import com.tinymotors.game.engine.GameEvent
import com.tinymotors.game.engine.GameState
import com.tinymotors.game.ui.theme.LocalReduceMotion
import com.tinymotors.game.ui.theme.Palette
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.sin

/** Where things are on screen, in the game screen's own coordinates. Not state: read on demand. */
private class ScreenLayout {
    var root: LayoutCoordinates? = null
    var board: LayoutCoordinates? = null
    var tow: LayoutCoordinates? = null
    var add: LayoutCoordinates? = null

    private fun rectOf(c: LayoutCoordinates?): Rect? {
        val r = root ?: return null
        val n = c ?: return null
        if (!r.isAttached || !n.isAttached) return null
        return r.localBoundingBoxOf(n, clipBounds = false)
    }

    fun boardOrigin(): Offset = rectOf(board)?.topLeft ?: Offset.Zero
    fun towRect(): Rect? = rectOf(tow)
    fun addRect(): Rect? = rectOf(add)
    fun size(): Size = root?.size?.let { Size(it.width.toFloat(), it.height.toFloat()) } ?: Size.Zero
}

/** Transient on-screen messages and flourishes. Tokens restart their animations. */
data class Bubble(val text: String, val at: Offset, val token: Int)
data class ComboPop(val count: Int, val at: Offset, val token: Int)
data class Banner(val level: Int, val tow: Boolean, val token: Int)

@Composable
fun GameScreen(state: UiState, vm: GameViewModel, particles: ParticleSystem) {
    val game = state.game ?: return
    val art = LocalCarArt.current
    val reduce = LocalReduceMotion.current
    val density = LocalDensity.current
    val view = LocalView.current
    val res = LocalResources.current
    val scope = rememberCoroutineScope()
    val measurer = rememberTextMeasurer(cacheSize = 24)
    val layout = remember { ScreenLayout() }
    val latest by rememberUpdatedState(state)

    var geo by remember { mutableStateOf(BoardGeo(1f, 1f, 1f)) }
    var drag by remember { mutableStateOf<DragInfo?>(null) }
    val dragPos = remember { Animatable(Offset.Zero, Offset.VectorConverter) }
    var flying by remember { mutableStateOf<Car?>(null) } // car returning or being towed away
    val towAway = remember { Animatable(0f) }
    val fx = remember { mutableStateMapOf<Int, CarFx>() }
    var fxToken by remember { mutableIntStateOf(0) }
    val snapIds = remember { mutableStateOf(setOf<Int>()) }
    var bubble by remember { mutableStateOf<Bubble?>(null) }
    var comboPop by remember { mutableStateOf<ComboPop?>(null) }
    var banner by remember { mutableStateOf<Banner?>(null) }
    var driveAcross by remember { mutableStateOf<Pair<Int, Int>?>(null) }
    var mood by remember { mutableStateOf(MascotMood.Idle) }
    var moodKey by remember { mutableIntStateOf(0) }
    var mascotSays by remember { mutableStateOf<String?>(null) }
    var hint by remember { mutableStateOf<Pair<Int, Int>?>(null) }
    var addPulse by remember { mutableStateOf(false) }
    var inputTick by remember { mutableIntStateOf(0) }
    var tutorialStep by remember { mutableIntStateOf(0) }
    var addShake by remember { mutableIntStateOf(0) }

    fun nextToken(): Int = ++fxToken
    fun slotCenterRoot(slot: Int): Offset = layout.boardOrigin() + geo.slotCenter(slot)
    fun say(text: String?, m: MascotMood) {
        mascotSays = text
        mood = m
        moodKey++
    }
    fun tick() {
        if (latest.settings.vibration) view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
    }

    // ---------- effects from the game ----------
    LaunchedEffect(Unit) {
        vm.effects.collect { e: Effects ->
            for (ev in e.events) when (ev) {
                is GameEvent.CarAdded -> {
                    fx[ev.car.id] = CarFx(FxKind.DriveIn, nextToken())
                    if (tutorialStep == 0) tutorialStep = 1
                }
                GameEvent.BoardFull -> {
                    addShake++
                    say(res.getString(R.string.hint_board_full), MascotMood.Hint)
                }
                is GameEvent.Moved -> fx[ev.car.id] = CarFx(FxKind.Squash, nextToken())
                is GameEvent.Swapped -> {
                    fx[ev.first.id] = CarFx(FxKind.Squash, nextToken())
                    fx[ev.second.id] = CarFx(FxKind.Squash, nextToken())
                }
                is GameEvent.Merged -> {
                    fx[ev.car.id] = CarFx(FxKind.Pop, nextToken())
                    tick()
                    val at = slotCenterRoot(ev.slot)
                    if (!reduce) {
                        val c = e.combo.coerceAtMost(5)
                        particles.burst(at.x, at.y, art[ev.car.level].main, 14 + c * 6, geo.tile * (1.6f + c * 0.25f))
                    }
                    if (e.combo >= 2) comboPop = ComboPop(e.combo, at, nextToken())
                    say(null, MascotMood.Cheer)
                    if (tutorialStep == 1) tutorialStep = 2
                }
                is GameEvent.NewCarUnlocked -> {
                    banner = Banner(ev.level, ev.towGranted, nextToken())
                    driveAcross = ev.level to nextToken()
                    if (!reduce) {
                        val sz = layout.size()
                        particles.confetti(sz.width, sz.height, 90)
                    }
                    say(null, MascotMood.Cheer)
                }
                GameEvent.HypercarBuilt -> say(null, MascotMood.Dance)
                is GameEvent.Towed, is GameEvent.HelperTowed -> say(null, MascotMood.Cheer)
                GameEvent.NoTowsLeft -> say(res.getString(R.string.hint_no_tows), MascotMood.Shrug)
                GameEvent.NoUndo -> say(res.getString(R.string.hint_no_undo), MascotMood.Shrug)
                GameEvent.Stuck -> say(null, MascotMood.Shrug)
                GameEvent.NewGame -> {
                    fx.clear()
                    particles.clear()
                    say(null, MascotMood.Cheer)
                }
                is GameEvent.TowGifted, GameEvent.Undone -> Unit
            }
            snapIds.value = emptySet()
        }
    }

    // mascot returns to idle and its speech bubble fades
    LaunchedEffect(moodKey) {
        delay(2600)
        mascotSays = null
        if (mood != MascotMood.Dance && !(mood == MascotMood.Shrug && latest.stuck)) mood = MascotMood.Idle
    }

    // ---------- idle hint ----------
    val dialogOpen = state.stuck || state.showWin
    LaunchedEffect(inputTick, state.settings.hints, dialogOpen, state.tutorialSeen) {
        hint = null
        addPulse = false
        if (!state.settings.hints || dialogOpen || !state.tutorialSeen) return@LaunchedEffect
        delay(6000)
        val g = latest.game ?: return@LaunchedEffect
        hint = GameEngine(g).findHintPair()
        addPulse = !g.isFull
        if (hint != null) say(res.getString(R.string.hint_match), MascotMood.Hint)
        else if (g.isFull && g.tows > 0) say(res.getString(R.string.hint_use_tow), MascotMood.Hint)
    }

    // ---------- drag handling ----------
    fun endDrag(info: DragInfo, at: Offset) {
        val towRect = layout.towRect()
        val local = at - layout.boardOrigin()
        val target = geo.slotAt(local)
        when {
            towRect != null && towRect.inflate(8f * density.density).contains(at) && latest.game!!.tows > 0 -> {
                drag = null
                flying = info.car
                vm.tow(info.fromSlot)
                scope.launch {
                    dragPos.snapTo(dragPos.value - Offset(0f, geo.tile * 0.35f))
                    towAway.snapTo(0f)
                    towAway.animateTo(1f, tween(if (reduce) 1 else 650, easing = FastOutSlowInEasing))
                    flying = null
                }
            }
            target != null && target != info.fromSlot -> {
                snapIds.value = setOf(info.car.id)
                drag = null
                vm.drag(info.fromSlot, target, SystemClock.uptimeMillis())
            }
            else -> {
                if (towRect != null && towRect.contains(at)) vm.tow(info.fromSlot) // no tows: explains why
                drag = null
                flying = info.car
                scope.launch {
                    towAway.snapTo(-1f) // negative = returning home
                    dragPos.snapTo(dragPos.value - Offset(0f, geo.tile * 0.35f))
                    dragPos.animateTo(slotCenterRoot(info.fromSlot), spring(dampingRatio = 0.7f, stiffness = 500f))
                    flying = null
                }
            }
        }
    }

    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            .onGloballyPositioned { layout.root = it }
            .pointerInput(Unit) {
                // any touch resets the idle hint
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                    inputTick++
                }
            }
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = true)
                    val st = latest
                    val g = st.game ?: return@awaitEachGesture
                    if (st.stuck || st.showWin || flying != null) return@awaitEachGesture
                    val slot = geo.slotAt(down.position - layout.boardOrigin()) ?: return@awaitEachGesture
                    val car = g.board[slot] ?: return@awaitEachGesture
                    down.consume()
                    val id = down.id
                    var pos = down.position
                    var info: DragInfo? = null
                    try {
                        while (true) {
                            val ev = awaitPointerEvent()
                            // a second finger is ignored: only the first pointer moves the car
                            ev.changes.forEach { if (it.id != id) it.consume() }
                            val ch = ev.changes.firstOrNull { it.id == id } ?: break
                            pos = ch.position
                            if (!ch.pressed) break
                            if (info == null && (pos - down.position).getDistance() > viewConfiguration.touchSlop) {
                                info = DragInfo(slot, car, slot, false)
                                drag = info
                                scope.launch { dragPos.snapTo(pos) }
                                vm.click()
                            }
                            val current = info
                            if (current != null) {
                                scope.launch { dragPos.snapTo(pos) }
                                val hover = geo.slotAt(pos - layout.boardOrigin())
                                val overTow = layout.towRect()?.contains(pos) == true
                                if (hover != current.hoverSlot || overTow != current.overTow) {
                                    info = current.copy(hoverSlot = hover, overTow = overTow)
                                    drag = info
                                }
                            }
                            ch.consume()
                        }
                    } finally {
                        val done = info
                        if (done != null) {
                            endDrag(done, pos)
                        } else if (drag == null) {
                            // a tap: wiggle, show the name and say it
                            fx[car.id] = CarFx(FxKind.Wiggle, nextToken())
                            val name = res.getString(art[car.level].nameRes)
                            bubble = Bubble(name, slotCenterRoot(slot) - Offset(0f, geo.tile * 0.6f), nextToken())
                            vm.sayName(name, honk = false)
                        }
                    }
                }
            },
    ) {
        SkyBackground()
        Column(
            Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            TopBar(game, mood, moodKey, mascotSays, onHome = { vm.navigate(Screen.Home) })
            VSpace(8.dp)
            CarStrip(game, Modifier.widthIn(max = 480.dp))
            VSpace(10.dp)
            BoxWithConstraints(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                val pad = with(density) { 10.dp.toPx() }
                val gap = with(density) { 8.dp.toPx() }
                val w = minOf(constraints.maxWidth.toFloat(), with(density) { 480.dp.toPx() })
                val h = constraints.maxHeight.toFloat()
                val tile = minOf((w - pad * 2 - gap * 4) / 5f, (h - pad * 2 - gap * 5) / 6f)
                if (geo.tile != tile) geo = BoardGeo(tile, gap, pad)
                Board(game.board, geo, drag, hint, fx, snapIds.value, measurer) { layout.board = it }
            }
            VSpace(12.dp)
            Dock(
                state = state,
                dragging = drag,
                addPulse = addPulse,
                addShake = addShake,
                onTowPlaced = { layout.tow = it },
                onAddPlaced = { layout.add = it },
                onAdd = { vm.addCar(SystemClock.uptimeMillis()) },
                onUndo = vm::undo,
                onTowTap = { say(res.getString(R.string.hint_tow_how), MascotMood.Hint) },
                modifier = Modifier.widthIn(max = 480.dp),
            )
        }

        // ---------- floating layer: dragged / returning / towed car ----------
        val floatingCar = drag?.car ?: flying
        if (floatingCar != null) {
            val look = art[floatingCar.level]
            val lift = if (drag != null) geo.tile * 0.35f else 0f
            val tileDp = with(density) { geo.tile.toDp() }
            Canvas(
                Modifier
                    .size(tileDp)
                    .graphicsLayer {
                        val towing = drag == null && towAway.value > 0f
                        val p = dragPos.value
                        val tr = layout.towRect()
                        var x = p.x
                        var y = p.y - lift
                        var s = if (drag != null) 1.15f else 1f
                        if (towing && tr != null) {
                            val t = towAway.value
                            val k = (t * 2f).coerceAtMost(1f)
                            x = p.x + (tr.center.x - p.x) * k
                            y = p.y + (tr.center.y - p.y) * k - (t - 0.5f).coerceAtLeast(0f) * 2f * size.height * 6f
                            s = 1f - t * 0.6f
                            rotationZ = -25f * t
                            alpha = 1f - ((t - 0.75f) * 4f).coerceIn(0f, 1f)
                        } else if (drag != null) {
                            rotationZ = -4f
                        }
                        translationX = x - size.width / 2
                        translationY = y - size.height / 2
                        scaleX = s
                        scaleY = s
                        shadowElevation = if (drag != null) 18f else 0f
                        shape = androidx.compose.foundation.shape.RoundedCornerShape(20)
                        clip = false
                    },
            ) {
                drawCarTile(look, size, measurer, Cars.value(floatingCar.level))
            }
        }

        bubble?.let { NameBubble(it) }
        comboPop?.let { ComboText(it) }
        driveAcross?.let { (level, token) -> DriveAcross(level, token) { driveAcross = null } }
        banner?.let { NewCarBanner(it) { banner = null } }

        ParticleLayer(particles)

        if (!state.tutorialSeen && !dialogOpen) {
            Tutorial(
                step = tutorialStep,
                game = game,
                geo = geo,
                boardOrigin = layout.boardOrigin(),
                addRect = layout.addRect(),
                onSkip = {
                    vm.completeTutorial()
                    vm.click()
                },
                onDone = {
                    vm.completeTutorial()
                    vm.click()
                },
            )
        }

        if (state.stuck) {
            StuckDialog(
                canHelper = state.canHelper,
                canUndo = state.canUndo,
                score = game.score,
                onHelper = vm::useHelper,
                onUndo = vm::undo,
                onNewGame = vm::newGame,
            )
        } else if (state.showWin) {
            WinDialog(onKeepPlaying = vm::keepPlaying, onNewGame = vm::newGame, particles = particles, size = layout.size())
        }
    }
}

@Composable
private fun TopBar(game: GameState, mood: MascotMood, moodKey: Int, says: String?, onHome: () -> Unit) {
    Row(Modifier.fillMaxWidth().widthIn(max = 520.dp).zIndex(1f), verticalAlignment = Alignment.CenterVertically) {
        RoundIconButton(onHome, stringResource(R.string.cd_home)) { c -> KidIcons.home(this, c) }
        HSpace(8.dp)
        ScorePanel(stringResource(R.string.score), game.score, Modifier.weight(1f))
        HSpace(8.dp)
        ScorePanel(stringResource(R.string.best), game.best, Modifier.weight(1f))
        HSpace(4.dp)
        Box {
            Mascot(mood, moodKey, stringResource(R.string.cd_mascot), size = 72.dp)
            if (says != null) SpeechBubble(says, Modifier.align(Alignment.BottomEnd))
        }
    }
}

@Composable
private fun ScorePanel(label: String, value: Long, modifier: Modifier) {
    val shown by animateFloatAsState(value.toFloat(), tween(450), label = "score")
    Panel(modifier.height(62.dp), corner = 18.dp) {
        Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            KidText(label, size = 13f, color = Palette.InkSoft, bold = false)
            KidText(shown.toLong().toString(), size = 22f)
        }
    }
}

@Composable
private fun CarStrip(game: GameState, modifier: Modifier) {
    val art = LocalCarArt.current
    Panel(modifier.fillMaxWidth().height(44.dp), corner = 16.dp) {
        Row(Modifier.fillMaxSize().padding(horizontal = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            for (level in Cars.MIN_LEVEL..Cars.MAX_LEVEL) {
                val unlocked = game.isUnlocked(level)
                Canvas(Modifier.weight(1f).height(36.dp)) {
                    val side = minOf(size.width, size.height) * 1.2f
                    drawCar(
                        art[level],
                        Offset((size.width - side) / 2, (size.height - side) / 2),
                        Size(side, side),
                        alpha = if (unlocked) 1f else 0.35f,
                        filter = if (unlocked) null else GreyFilter,
                        shadow = false,
                    )
                }
            }
        }
    }
}

@Composable
private fun Dock(
    state: UiState,
    dragging: DragInfo?,
    addPulse: Boolean,
    addShake: Int,
    onTowPlaced: (LayoutCoordinates) -> Unit,
    onAddPlaced: (LayoutCoordinates) -> Unit,
    onAdd: () -> Unit,
    onUndo: () -> Unit,
    onTowTap: () -> Unit,
    modifier: Modifier,
) {
    val game = state.game ?: return
    val art = LocalCarArt.current
    val reduce = LocalReduceMotion.current
    val t = rememberInfiniteTransition(label = "dock")
    val bob = if (reduce) 0f else t.animateFloat(0f, 1f, infiniteRepeatable(tween(1800, easing = LinearEasing)), label = "bob").value
    val pulse = if (reduce || !addPulse) 0f else t.animateFloat(0f, 1f, infiniteRepeatable(tween(1200), RepeatMode.Reverse), label = "pulse").value
    val shake = remember { Animatable(0f) }
    LaunchedEffect(addShake) {
        if (addShake > 0 && !reduce) wiggle(shake, 6f)
    }
    val towHot = dragging?.overTow == true
    val towScale by animateFloatAsState(if (towHot) 1.15f else if (dragging != null) 1.06f else 1f, spring(dampingRatio = 0.5f), label = "tow")

    Row(modifier.fillMaxWidth().height(96.dp), verticalAlignment = Alignment.CenterVertically) {
        // Tow
        Box(
            Modifier
                .size(84.dp)
                .onGloballyPositioned(onTowPlaced)
                .graphicsLayer {
                    scaleX = towScale
                    scaleY = towScale
                },
        ) {
            ChunkyButton(
                onClick = onTowTap,
                color = if (towHot) TowGlow else Palette.Blue,
                shade = Palette.BlueShade,
                contentDescription = pluralStringResource(R.plurals.cd_tow, game.tows, game.tows),
                modifier = Modifier.fillMaxSize(),
                enabled = game.tows > 0 || dragging != null,
            ) {
                Canvas(Modifier.size(40.dp)) { KidIcons.hook(this, androidx.compose.ui.graphics.Color.White) }
            }
            CountBadge(game.tows, Modifier.align(Alignment.TopEnd), Palette.BlueShade)
        }
        HSpace(12.dp)
        // Add car
        Box(
            Modifier
                .weight(1f)
                .height(92.dp)
                .onGloballyPositioned(onAddPlaced)
                .graphicsLayer {
                    translationY = sin(bob * 2 * PI).toFloat() * 3.dp.toPx()
                    val s = 1f + pulse * 0.06f
                    scaleX = s
                    scaleY = s
                    rotationZ = shake.value
                },
        ) {
            ChunkyButton(
                onClick = onAdd,
                color = Palette.Orange,
                shade = Palette.OrangeShade,
                contentDescription = stringResource(R.string.cd_add_car, stringResource(art[state.nextLevel].nameRes)),
                modifier = Modifier.fillMaxSize(),
                shape = androidx.compose.foundation.shape.RoundedCornerShape(24.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
                    Box(
                        Modifier
                            .size(62.dp)
                            .graphicsLayer { clip = false },
                        contentAlignment = Alignment.Center,
                    ) {
                        Canvas(Modifier.size(56.dp)) {
                            drawCircle(androidx.compose.ui.graphics.Color.White.copy(alpha = 0.9f))
                            drawCar(art[state.nextLevel], Offset(-size.width * 0.08f, -size.height * 0.05f), Size(size.width * 1.16f, size.height * 1.16f), shadow = false)
                        }
                    }
                    HSpace(8.dp)
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Canvas(Modifier.size(20.dp)) { KidIcons.plus(this, androidx.compose.ui.graphics.Color.White) }
                        KidText(stringResource(R.string.add_car), size = 20f, color = androidx.compose.ui.graphics.Color.White)
                    }
                }
            }
        }
        HSpace(12.dp)
        // Undo
        Box(Modifier.size(84.dp)) {
            ChunkyButton(
                onClick = onUndo,
                color = Palette.Purple,
                shade = Palette.PurpleShade,
                contentDescription = pluralStringResource(R.plurals.cd_undo, game.undosLeft, game.undosLeft),
                modifier = Modifier.fillMaxSize(),
                enabled = state.canUndo,
            ) {
                Canvas(Modifier.size(40.dp)) { KidIcons.undo(this, androidx.compose.ui.graphics.Color.White) }
            }
            CountBadge(game.undosLeft, Modifier.align(Alignment.TopEnd), Palette.PurpleShade)
        }
    }
}
