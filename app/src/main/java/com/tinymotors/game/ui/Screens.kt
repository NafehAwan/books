package com.tinymotors.game.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.foundation.text.BasicText
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tinymotors.game.GameViewModel
import com.tinymotors.game.R
import com.tinymotors.game.Screen
import com.tinymotors.game.UiState
import com.tinymotors.game.engine.Cars
import com.tinymotors.game.engine.GameRules
import com.tinymotors.game.ui.theme.LocalReduceMotion
import com.tinymotors.game.ui.theme.Palette
import com.tinymotors.game.ui.theme.kidText
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin

// ======================= Home =======================

@Composable
fun HomeScreen(state: UiState, vm: GameViewModel) {
    val game = state.game
    var showGift by remember { mutableStateOf(false) }
    LaunchedEffect(state.giftAvailable, state.loaded) {
        if (state.giftAvailable && state.loaded) showGift = true
    }
    Box(Modifier.fillMaxSize()) {
        SkyBackground()
        DrivingCars(levels = (Cars.MIN_LEVEL..Cars.MAX_LEVEL).filter { game?.isUnlocked(it) == true }.ifEmpty { listOf(1, 2) })
        Column(
            Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                RoundIconButton({ vm.navigate(Screen.Settings) }, stringResource(R.string.cd_settings)) { c -> KidIcons.gear(this, c) }
            }
            VSpace(8.dp)
            Title()
            VSpace(12.dp)
            Mascot(MascotMood.Idle, 0, stringResource(R.string.cd_mascot), size = 140.dp)
            VSpace(24.dp)
            ChunkyButton(
                onClick = { vm.navigate(Screen.Game) },
                color = Palette.Green,
                shade = Palette.GreenShade,
                contentDescription = stringResource(R.string.play),
                modifier = Modifier.widthIn(max = 360.dp).fillMaxWidth().height(96.dp),
                shape = RoundedCornerShape(28.dp),
                depth = 8.dp,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Canvas(Modifier.size(38.dp)) { KidIcons.play(this, Color.White) }
                    HSpace(12.dp)
                    KidText(stringResource(R.string.play), size = 36f, color = Color.White)
                }
            }
            VSpace(18.dp)
            ChunkyButton(
                onClick = { vm.navigate(Screen.Garage) },
                color = Palette.Pink,
                shade = Palette.PinkShade,
                contentDescription = stringResource(R.string.garage),
                modifier = Modifier.widthIn(max = 360.dp).fillMaxWidth().height(80.dp),
                shape = RoundedCornerShape(24.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Canvas(Modifier.size(34.dp)) { KidIcons.garage(this, Color.White) }
                    HSpace(12.dp)
                    KidText(stringResource(R.string.garage), size = 28f, color = Color.White)
                    if (game != null) {
                        HSpace(10.dp)
                        KidText("${game.unlockedCount}/${Cars.MAX_LEVEL}", size = 20f, color = Color.White.copy(alpha = 0.85f), bold = false)
                    }
                }
            }
            if (game != null && game.best > 0) {
                VSpace(14.dp)
                KidText(stringResource(R.string.best_score, game.best), size = 18f, color = Palette.InkSoft, bold = false)
            }
        }
        if (showGift) {
            GiftDialog(
                toolsFull = (game?.tows ?: 0) >= GameRules.MAX_TOWS,
                onOpen = vm::claimGift,
                onClose = {
                    showGift = false
                    vm.click()
                },
            )
        }
    }
}

@Composable
private fun Title() {
    val colours = listOf(Palette.Orange, Palette.Pink, Palette.Blue, Palette.Green, Palette.Purple)
    val text = stringResource(R.string.app_name)
    val reduce = LocalReduceMotion.current
    val t = rememberInfiniteTransition(label = "title")
    val wave = if (reduce) 0f else t.animateFloat(0f, 1f, infiniteRepeatable(tween(2400, easing = LinearEasing)), label = "w").value
    val styled = buildAnnotatedString {
        var k = 0
        text.forEach { ch ->
            if (ch == ' ') {
                append(ch)
            } else {
                withStyle(SpanStyle(color = colours[k % colours.size])) { append(ch) }
                k++
            }
        }
    }
    BasicText(
        styled,
        Modifier.graphicsLayer {
            rotationZ = sin(wave * 2 * PI).toFloat() * 2f
            translationY = sin(wave * 2 * PI).toFloat() * 4.dp.toPx()
        },
        style = kidText(52.sp).copy(shadow = androidx.compose.ui.graphics.Shadow(Color(0x332B3A55), Offset(0f, 6f), 4f)),
    )
}

/** Small cars driving along the bottom of the home screen. */
@Composable
private fun DrivingCars(levels: List<Int>) {
    val art = LocalCarArt.current
    val reduce = LocalReduceMotion.current
    val t = rememberInfiniteTransition(label = "road")
    val p = if (reduce) 0.3f else t.animateFloat(0f, 1f, infiniteRepeatable(tween(14_000, easing = LinearEasing)), label = "p").value
    val road = Color(0xFFB9D7A8)
    Canvas(Modifier.fillMaxSize()) {
        val carSize = minOf(size.width * 0.22f, 120.dp.toPx())
        val ground = size.height - carSize * 0.35f
        drawRect(road, Offset(0f, ground), Size(size.width, size.height - ground))
        drawRect(Color(0xFF9CC48A), Offset(0f, ground), Size(size.width, 4.dp.toPx()))
        val n = levels.size.coerceAtMost(5)
        val span = size.width + carSize * 2
        for (i in 0 until n) {
            val phase = (p + i.toFloat() / n) % 1f
            val x = -carSize + phase * span
            val hop = abs(sin(phase * PI * 24)).toFloat() * carSize * 0.02f
            drawCar(art[levels[(levels.size - 1 - i).coerceAtLeast(0)]], Offset(x, ground - carSize * 0.78f - hop), Size(carSize, carSize))
        }
    }
}

@Composable
private fun GiftDialog(toolsFull: Boolean, onOpen: () -> Unit, onClose: () -> Unit) {
    val wasFull = remember { toolsFull }
    val reduce = LocalReduceMotion.current
    var opened by remember { mutableStateOf(false) }
    val lid = remember { Animatable(0f) }
    val t = rememberInfiniteTransition(label = "gift")
    val wob = if (reduce || opened) 0f else t.animateFloat(-1f, 1f, infiniteRepeatable(tween(500), RepeatMode.Reverse), label = "wob").value
    val scope = rememberCoroutineScope()
    val giftLabel = stringResource(R.string.gift_tap)
    FullScrim {
        DialogCard {
            Column(Modifier.fillMaxWidth().padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                KidText(stringResource(if (opened) R.string.gift_opened else R.string.gift_title), size = 26f)
                VSpace(12.dp)
                Box(
                    Modifier
                        .size(170.dp)
                        .semantics {
                            role = Role.Button
                            contentDescription = giftLabel
                        }
                        .clickable(enabled = !opened) {
                            opened = true
                            onOpen()
                            scope.launch { lid.animateTo(1f, spring(dampingRatio = 0.5f)) }
                        }
                        .graphicsLayer { rotationZ = wob * 5f },
                    contentAlignment = Alignment.Center,
                ) {
                    Canvas(Modifier.fillMaxSize()) { KidIcons.gift(this, Palette.Pink, Palette.Gold, lid.value) }
                    if (opened) {
                        Canvas(Modifier.size(64.dp).graphicsLayer { translationY = -lid.value * 40.dp.toPx(); alpha = lid.value.coerceIn(0f, 1f) }) {
                            drawCircle(Color.White)
                            KidIcons.hook(this, Palette.BlueShade)
                        }
                    }
                }
                VSpace(8.dp)
                if (opened) {
                    KidText(stringResource(if (wasFull) R.string.gift_full else R.string.gift_tow), size = 20f, color = Palette.BlueShade)
                    VSpace(16.dp)
                    ChunkyButton(onClose, Palette.Green, Palette.GreenShade, stringResource(R.string.ok), Modifier.size(120.dp, 72.dp)) {
                        Canvas(Modifier.size(36.dp)) { KidIcons.check(this, Color.White) }
                    }
                } else {
                    KidText(stringResource(R.string.gift_tap), size = 18f, color = Palette.InkSoft, bold = false)
                }
            }
        }
    }
}

// ======================= Garage =======================

@Composable
fun GarageScreen(state: UiState, vm: GameViewModel) {
    val art = LocalCarArt.current
    val game = state.game ?: return
    val res = LocalResources.current
    Box(Modifier.fillMaxSize()) {
        SkyBackground()
        Column(
            Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row(Modifier.fillMaxWidth().widthIn(max = 640.dp), verticalAlignment = Alignment.CenterVertically) {
                RoundIconButton({ vm.navigate(Screen.Home) }, stringResource(R.string.cd_home)) { c -> KidIcons.home(this, c) }
                HSpace(12.dp)
                Column(Modifier.weight(1f)) {
                    KidText(stringResource(R.string.garage), size = 30f)
                    KidText(pluralStringResource(R.plurals.garage_count, Cars.MAX_LEVEL, game.unlockedCount, Cars.MAX_LEVEL), size = 16f, color = Palette.InkSoft, bold = false)
                }
            }
            VSpace(12.dp)
            BoxWithConstraints(Modifier.weight(1f).fillMaxWidth().widthIn(max = 640.dp)) {
                val cols = if (maxWidth > 520.dp) 4 else 3
                Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                    for (row in 0 until (Cars.MAX_LEVEL + cols - 1) / cols) {
                        Row(Modifier.fillMaxWidth()) {
                            for (c in 0 until cols) {
                                val level = row * cols + c + 1
                                Box(Modifier.weight(1f).padding(6.dp)) {
                                    if (level <= Cars.MAX_LEVEL) {
                                        Sticker(level, game.isUnlocked(level), art) {
                                            if (game.isUnlocked(level)) vm.sayName(res.getString(art[level].nameRes), honk = true) else vm.tapLocked()
                                        }
                                    }
                                }
                            }
                        }
                    }
                    VSpace(12.dp)
                }
            }
        }
    }
}

@Composable
private fun Sticker(level: Int, unlocked: Boolean, art: CarArt, onTap: () -> Unit) {
    val look = art[level]
    val rot = remember { Animatable(0f) }
    var taps by remember { mutableIntStateOf(0) }
    val reduce = LocalReduceMotion.current
    LaunchedEffect(taps) { if (taps > 0 && !reduce) wiggle(rot, 8f) }
    val name = if (unlocked) stringResource(look.nameRes) else stringResource(R.string.locked_car)
    Panel(corner = 20.dp) {
        Column(
            Modifier
                .fillMaxWidth()
                .semantics(mergeDescendants = true) {
                    role = Role.Button
                    contentDescription = name
                }
                .clickable {
                    taps++
                    onTap()
                }
                .padding(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .clip(RoundedCornerShape(16.dp))
                    .background(if (unlocked) look.tile else Palette.Slot)
                    .graphicsLayer { rotationZ = rot.value },
                contentAlignment = Alignment.Center,
            ) {
                Canvas(Modifier.fillMaxSize()) {
                    drawCar(look, Offset.Zero, size, filter = if (unlocked) null else SilhouetteFilter, shadow = unlocked)
                }
                if (!unlocked) KidText("?", size = 40f, color = Color.White)
            }
            VSpace(6.dp)
            KidText(if (unlocked) name else "?", size = 16f, color = if (unlocked) Palette.Ink else Palette.InkSoft)
        }
    }
}

// ======================= Settings =======================

@Composable
fun SettingsScreen(state: UiState, vm: GameViewModel) {
    val s = state.settings
    var confirmNew by remember { mutableStateOf(false) }
    var resetDone by remember { mutableStateOf(false) }
    Box(Modifier.fillMaxSize()) {
        SkyBackground()
        Column(
            Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row(Modifier.fillMaxWidth().widthIn(max = 520.dp), verticalAlignment = Alignment.CenterVertically) {
                RoundIconButton({ vm.navigate(Screen.Home) }, stringResource(R.string.cd_home)) { c -> KidIcons.home(this, c) }
                HSpace(12.dp)
                KidText(stringResource(R.string.settings), size = 30f)
            }
            VSpace(16.dp)
            Panel(Modifier.widthIn(max = 520.dp).fillMaxWidth()) {
                Column(Modifier.padding(vertical = 8.dp)) {
                    KidSwitch(s.sound, stringResource(R.string.set_sound), { vm.updateSettings(s.copy(sound = it)) })
                    KidSwitch(s.music, stringResource(R.string.set_music), { vm.updateSettings(s.copy(music = it)) })
                    KidSwitch(s.vibration, stringResource(R.string.set_vibration), { vm.updateSettings(s.copy(vibration = it)) })
                    KidSwitch(s.voice, stringResource(R.string.set_voice), { vm.updateSettings(s.copy(voice = it)) })
                    KidSwitch(s.hints, stringResource(R.string.set_hints), { vm.updateSettings(s.copy(hints = it)) })
                }
            }
            VSpace(16.dp)
            ChunkyButton(
                { confirmNew = true; vm.click() }, Palette.Orange, Palette.OrangeShade, stringResource(R.string.new_game),
                Modifier.widthIn(max = 520.dp).fillMaxWidth().height(64.dp),
            ) { KidText(stringResource(R.string.new_game), size = 20f, color = Color.White) }
            VSpace(16.dp)
            Panel(Modifier.widthIn(max = 520.dp).fillMaxWidth()) {
                Column(Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    KidText(stringResource(R.string.grown_ups), size = 18f)
                    VSpace(4.dp)
                    KidText(stringResource(R.string.start_over_help), size = 15f, color = Palette.InkSoft, bold = false)
                    VSpace(12.dp)
                    if (resetDone) {
                        KidText(stringResource(R.string.start_over_done), size = 18f, color = Palette.GreenShade)
                    } else {
                        HoldToConfirm(stringResource(R.string.start_over)) {
                            vm.startOver()
                            resetDone = true
                        }
                    }
                }
            }
            VSpace(16.dp)
            KidText(stringResource(R.string.privacy_note), Modifier.widthIn(max = 520.dp), size = 14f, color = Palette.InkSoft, bold = false)
            VSpace(6.dp)
            KidText(stringResource(R.string.credits), Modifier.widthIn(max = 520.dp), size = 12f, color = Palette.InkSoft, bold = false)
        }
        if (confirmNew) {
            FullScrim {
                DialogCard {
                    Column(Modifier.fillMaxWidth().padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        KidText(stringResource(R.string.new_game_q), size = 24f)
                        VSpace(6.dp)
                        KidText(stringResource(R.string.new_game_keep), size = 16f, color = Palette.InkSoft, bold = false)
                        VSpace(16.dp)
                        Row {
                            ChunkyButton({ confirmNew = false; vm.click() }, Palette.Grey, Palette.GreyShade, stringResource(R.string.cancel), Modifier.size(110.dp, 64.dp)) {
                                Canvas(Modifier.size(28.dp)) { KidIcons.close(this, Color.White) }
                            }
                            HSpace(16.dp)
                            ChunkyButton({
                                confirmNew = false
                                vm.newGame()
                                vm.navigate(Screen.Game)
                            }, Palette.Green, Palette.GreenShade, stringResource(R.string.ok), Modifier.size(110.dp, 64.dp)) {
                                Canvas(Modifier.size(32.dp)) { KidIcons.check(this, Color.White) }
                            }
                        }
                    }
                }
            }
        }
    }
}

/** Parent gate: press and hold for three seconds. Letting go early resets the ring. */
@Composable
private fun HoldToConfirm(label: String, onConfirmed: () -> Unit) {
    val progress = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    Box(
        Modifier
            .widthIn(max = 360.dp)
            .fillMaxWidth()
            .height(72.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(Palette.Slot)
            .semantics {
                role = Role.Button
                contentDescription = label
            }
            .pointerInput(Unit) {
                awaitEachGesture {
                    awaitFirstDown()
                    val job = scope.launch {
                        progress.animateTo(1f, tween(HOLD_MS, easing = LinearEasing))
                        onConfirmed()
                    }
                    waitForUpOrCancellation()
                    if (progress.value < 1f) {
                        job.cancel()
                        scope.launch { progress.animateTo(0f, tween(250)) }
                    }
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.fillMaxSize()) {
            drawRect(Palette.PinkShade.copy(alpha = 0.25f), size = Size(size.width * progress.value, size.height))
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Canvas(Modifier.size(32.dp)) {
                drawCircle(Palette.SlotEdge, style = Stroke(4.dp.toPx()))
                drawArc(Palette.PinkShade, -90f, 360f * progress.value, false, style = Stroke(4.dp.toPx(), cap = StrokeCap.Round))
            }
            HSpace(10.dp)
            KidText(label, size = 17f, color = Palette.Ink)
        }
    }
}

private const val HOLD_MS = 3000
