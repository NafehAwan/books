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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.tinymotors.game.GameViewModel
import com.tinymotors.game.R
import com.tinymotors.game.Screen
import com.tinymotors.game.UiState
import com.tinymotors.game.engine.Cars
import com.tinymotors.game.engine.GameRules
import com.tinymotors.game.ui.theme.LocalReduceMotion
import com.tinymotors.game.ui.theme.Palette
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin

// ======================= Home =======================

/** Positions on the home "sky stage", in the design's own units (1116 wide, stage starts at y = 200). */
private const val DESIGN_W = 1116f
private const val STAGE_H = 1080f

private data class CloudCar(val level: Int, val x: Float, val y: Float, val w: Float, val phase: Float)

private val CLOUD_CARS = listOf(
    CloudCar(2, 455f, 690f, 210f, 0.0f),
    CloudCar(1, 215f, 840f, 215f, 0.35f),
    CloudCar(4, 905f, 840f, 215f, 0.6f),
    CloudCar(3, 680f, 930f, 215f, 0.15f),
)

private val SPARKLES = floatArrayOf(
    370f, 625f, 18f, 548f, 620f, 16f, 113f, 765f, 26f, 664f, 760f, 24f, 800f, 765f, 18f, 378f, 885f, 15f,
    455f, 975f, 17f, 348f, 1020f, 15f, 875f, 1050f, 20f, 1040f, 820f, 12f, 1020f, 775f, 12f, 455f, 450f, 16f,
    320f, 470f, 14f, 588f, 455f, 10f, 1063f, 385f, 12f, 1030f, 285f, 18f, 657f, -35f, 14f,
)

@Composable
fun HomeScreen(state: UiState, vm: GameViewModel) {
    val game = state.game
    var showGift by remember { mutableStateOf(false) }
    LaunchedEffect(state.giftAvailable, state.loaded) {
        if (state.giftAvailable && state.loaded) showGift = true
    }
    Box(Modifier.fillMaxSize()) {
        SkyBackground(scenery = true, groundFraction = 0.79f)
        Column(
            Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(horizontal = 14.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // top: best score, daily gift, settings
            Row(Modifier.fillMaxWidth().widthIn(max = 560.dp), verticalAlignment = Alignment.CenterVertically) {
                StatPill(R.drawable.ui_star_small, null, (game?.best ?: 0L).toString(), Modifier.widthIn(min = 150.dp))
                Box(Modifier.weight(1f))
                WhiteIconButton(
                    R.drawable.ui_gift, stringResource(R.string.cd_gift),
                    onClick = {
                        vm.click()
                        if (state.giftAvailable) showGift = true
                    },
                    size = 64.dp,
                    iconSize = 44.dp,
                    dot = state.giftAvailable,
                )
                HSpace(10.dp)
                WhiteIconButton(R.drawable.ui_gear, stringResource(R.string.cd_settings), { vm.navigate(Screen.Settings) }, size = 64.dp, iconSize = 40.dp)
            }
            // the sky stage: logo, Towy waving from his cloud, cars floating on clouds
            HomeStage(Modifier.weight(1f).fillMaxWidth().widthIn(max = 560.dp))
            // play
            PlayButton(Modifier.widthIn(max = 380.dp).fillMaxWidth(0.82f).height(100.dp)) { vm.play() }
            VSpace(6.dp)
            OutlinedTitle(stringResource(R.string.lets_merge), size = 26f, fill = Palette.HeaderShade, outline = Color.White)
            VSpace(12.dp)
            // bottom menu
            Row(Modifier.widthIn(max = 520.dp).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                MenuTile(stringResource(R.string.garage), R.drawable.ui_sticker_book, Modifier.weight(1f), badge = game?.let { "${it.unlockedCount}/${Cars.MAX_LEVEL}" }) {
                    vm.navigate(Screen.Garage)
                }
                MenuTile(stringResource(R.string.store), R.drawable.ui_shop, Modifier.weight(1f)) { vm.navigate(Screen.Store) }
                MenuTile(stringResource(R.string.settings), R.drawable.ui_gear, Modifier.weight(1f)) { vm.navigate(Screen.Settings) }
            }
            BannerAd(vm, state, Modifier.padding(top = 8.dp))
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

/** Logo, waving Towy on a cloud and four cars bobbing on clouds, laid out like the design. */
@Composable
private fun HomeStage(modifier: Modifier) {
    val art = LocalCarArt.current
    val reduce = LocalReduceMotion.current
    val t = rememberInfiniteTransition(label = "stage")
    val p = if (reduce) 0f else t.animateFloat(0f, 1f, infiniteRepeatable(tween(3600, easing = LinearEasing)), label = "p").value
    val twinkle = if (reduce) 0.5f else t.animateFloat(0f, 1f, infiniteRepeatable(tween(2400, easing = LinearEasing)), label = "tw").value
    val appName = stringResource(R.string.app_name)
    val towyName = stringResource(R.string.cd_mascot)
    BoxWithConstraints(modifier, contentAlignment = Alignment.TopCenter) {
        val density = androidx.compose.ui.platform.LocalDensity.current
        val wPx = constraints.maxWidth.toFloat()
        val hPx = constraints.maxHeight.toFloat()
        val u = minOf(wPx / DESIGN_W, hPx / STAGE_H) // pixels per design unit
        val left = (wPx - DESIGN_W * u) / 2
        fun dp(units: Float) = with(density) { (units * u).toDp() }
        fun x(units: Float) = with(density) { (left + units * u).toDp() }
        // cars on clouds and sparkles (behind Towy and the logo)
        Canvas(Modifier.fillMaxSize()) {
            for (c in CLOUD_CARS) {
                val bob = sin((p + c.phase) * 2 * PI).toFloat() * 10f * u
                drawSoftCloud(Offset(left + c.x * u, (c.y + 70f) * u + bob * 0.4f), c.w * 1.45f * u)
                val side = c.w * u
                drawCar(art[c.level], Offset(left + c.x * u - side / 2, c.y * u - side / 2 + bob), Size(side, side))
            }
            for (i in SPARKLES.indices step 3) {
                val a = 0.45f + 0.55f * kotlin.math.abs(sin((twinkle + i * 0.137f) * PI).toFloat())
                drawSparkle(Offset(left + SPARKLES[i] * u, SPARKLES[i + 1] * u), SPARKLES[i + 2] * u, a)
            }
        }
        Art(
            R.drawable.ui_logo_big,
            Modifier
                .align(Alignment.TopStart)
                .padding(start = x(112f))
                .size(dp(760f), dp(760f * 511f / 900f))
                .graphicsLayer {
                    rotationZ = sin(p * 2 * PI).toFloat() * 1.2f
                    translationY = sin(p * 2 * PI).toFloat() * 3.dp.toPx()
                },
            description = appName,
        )
        Art(
            R.drawable.ui_towy_wave,
            Modifier
                .align(Alignment.TopStart)
                .padding(start = x(588f), top = dp(250f))
                .size(dp(528f), dp(528f * 527f / 600f))
                .graphicsLayer {
                    translationY = sin((p + 0.5f) * 2 * PI).toFloat() * 6.dp.toPx()
                    rotationZ = sin(p * 4 * PI).toFloat() * 1.5f
                },
            description = towyName,
        )
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawSoftCloud(c: Offset, w: Float) {
    val shade = Color(0xFFDDEBFA)
    val white = Color.White
    drawOval(shade, Offset(c.x - w / 2, c.y - w * 0.06f), Size(w, w * 0.3f))
    drawCircle(white, w * 0.2f, Offset(c.x - w * 0.22f, c.y - w * 0.02f))
    drawCircle(white, w * 0.26f, Offset(c.x + w * 0.02f, c.y - w * 0.08f))
    drawCircle(white, w * 0.19f, Offset(c.x + w * 0.26f, c.y))
    drawOval(white, Offset(c.x - w * 0.46f, c.y - w * 0.06f), Size(w * 0.92f, w * 0.24f))
}

/** Four-point twinkle star; alpha changes slowly (well under 3 flashes per second). */
private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawSparkle(c: Offset, r: Float, alpha: Float) {
    val path = Path().apply {
        moveTo(c.x, c.y - r)
        quadraticTo(c.x + r * 0.15f, c.y - r * 0.15f, c.x + r, c.y)
        quadraticTo(c.x + r * 0.15f, c.y + r * 0.15f, c.x, c.y + r)
        quadraticTo(c.x - r * 0.15f, c.y + r * 0.15f, c.x - r, c.y)
        quadraticTo(c.x - r * 0.15f, c.y - r * 0.15f, c.x, c.y - r)
        close()
    }
    drawPath(path, Color(0xFFFFD84A), alpha = alpha)
    drawCircle(Color.White, r * 0.18f, c, alpha = alpha)
}

/** Big glossy orange PLAY pill from the design. */
@Composable
private fun PlayButton(modifier: Modifier, onClick: () -> Unit) {
    val reduce = LocalReduceMotion.current
    val t = rememberInfiniteTransition(label = "play")
    val pulse = if (reduce) 0f else t.animateFloat(0f, 1f, infiniteRepeatable(tween(1600), RepeatMode.Reverse), label = "pulse").value
    val label = stringResource(R.string.play)
    ChunkyButton(
        onClick = onClick,
        color = Color(0xFFFFB627),
        shade = Color(0xFFE08A00),
        contentDescription = label,
        modifier = modifier.graphicsLayer {
            val s = 1f + pulse * 0.035f
            scaleX = s
            scaleY = s
        },
        shape = CircleShape,
        depth = 9.dp,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(contentAlignment = Alignment.Center) {
                Canvas(Modifier.size(52.dp)) { KidIcons.play(this, Color(0xFFC86F00)) }
                Canvas(Modifier.size(40.dp)) { KidIcons.play(this, Color.White) }
            }
            HSpace(12.dp)
            OutlinedTitle(label.uppercase(), size = 44f, outline = Color(0xFFC86F00))
        }
    }
}

/** White rounded tile with a big picture and a label (Garage, Store, Settings). */
@Composable
private fun MenuTile(label: String, icon: Int, modifier: Modifier, badge: String? = null, onClick: () -> Unit) {
    Box(modifier) {
        ChunkyButton(
            onClick, Color.White, Palette.ButtonShade, label,
            Modifier.fillMaxWidth().height(118.dp), RoundedCornerShape(28.dp), depth = 6.dp,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Art(icon, Modifier.size(64.dp))
                VSpace(2.dp)
                KidText(label.uppercase(), size = 17f, color = Palette.HeaderShade)
            }
        }
        if (badge != null) {
            Box(
                Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 4.dp, end = 4.dp)
                    .clip(CircleShape)
                    .background(Palette.HeaderBlue)
                    .padding(horizontal = 7.dp, vertical = 1.dp),
            ) { KidText(badge, size = 12f, color = Color.White) }
        }
    }
}

@Composable
private fun GiftDialog(toolsFull: Boolean, onOpen: () -> Unit, onClose: () -> Unit) {
    val reduce = LocalReduceMotion.current
    val wasFull = remember { toolsFull }
    var opened by remember { mutableStateOf(false) }
    val pop = remember { Animatable(0f) }
    val t = rememberInfiniteTransition(label = "gift")
    val wob = if (reduce || opened) 0f else t.animateFloat(-1f, 1f, infiniteRepeatable(tween(500), RepeatMode.Reverse), label = "wob").value
    val scope = rememberCoroutineScope()
    val giftLabel = stringResource(R.string.gift_tap)
    FullScrim {
        DialogCard {
            Column(Modifier.fillMaxWidth().padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                OutlinedTitle(stringResource(if (opened) R.string.gift_opened else R.string.gift_title), size = 26f, fill = Palette.HeaderBlue, outline = Color.White)
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
                            scope.launch { pop.animateTo(1f, spring(dampingRatio = 0.45f)) }
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Art(
                        R.drawable.ui_gift,
                        Modifier.size(150.dp).graphicsLayer {
                            rotationZ = wob * 6f
                            val s = 1f - pop.value * 0.25f
                            scaleX = s
                            scaleY = s
                            alpha = 1f - pop.value * 0.35f
                        },
                    )
                    if (opened) {
                        Box(
                            Modifier
                                .size(96.dp)
                                .graphicsLayer {
                                    translationY = -pop.value * 30.dp.toPx()
                                    scaleX = pop.value
                                    scaleY = pop.value
                                }
                                .clip(CircleShape)
                                .background(Color.White),
                            contentAlignment = Alignment.Center,
                        ) { Art(R.drawable.ui_tow, Modifier.size(70.dp)) }
                    }
                }
                VSpace(8.dp)
                if (opened) {
                    KidText(stringResource(if (wasFull) R.string.gift_full else R.string.gift_tow), size = 20f, color = Palette.HeaderShade)
                    VSpace(16.dp)
                    ChunkyButton(onClose, Palette.Green, Palette.GreenShade, stringResource(R.string.ok), Modifier.size(120.dp, 72.dp)) {
                        Canvas(Modifier.size(36.dp)) { KidIcons.check(this, Color.White) }
                    }
                } else {
                    KidText(giftLabel, size = 18f, color = Palette.InkSoft, bold = false)
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
    val all = game.unlockedCount == Cars.MAX_LEVEL
    Box(Modifier.fillMaxSize()) {
        SkyBackground(scenery = true, groundFraction = 0.9f)
        Column(
            Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(horizontal = 14.dp, vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row(Modifier.fillMaxWidth().widthIn(max = 640.dp), verticalAlignment = Alignment.CenterVertically) {
                WhiteIconButton(R.drawable.ui_back, stringResource(R.string.cd_home), { vm.navigate(Screen.Home) })
                HSpace(10.dp)
                HeaderPill(stringResource(R.string.garage), R.drawable.ui_home)
            }
            VSpace(12.dp)
            BoxWithConstraints(Modifier.weight(1f).fillMaxWidth().widthIn(max = 640.dp)) {
                val cols = if (maxWidth > 520.dp) 4 else 3
                Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                    CollectionCard(game.unlockedCount)
                    VSpace(10.dp)
                    for (row in 0 until (Cars.MAX_LEVEL + cols - 1) / cols) {
                        Row(Modifier.fillMaxWidth()) {
                            for (c in 0 until cols) {
                                val level = row * cols + c + 1
                                Box(Modifier.weight(1f).padding(5.dp)) {
                                    if (level <= Cars.MAX_LEVEL) {
                                        Sticker(level, game.isUnlocked(level), art) {
                                            if (game.isUnlocked(level)) vm.sayName(res.getString(art[level].nameRes), honk = true) else vm.tapLocked()
                                        }
                                    }
                                }
                            }
                        }
                    }
                    VSpace(8.dp)
                    GlassRow {
                        Mascot(if (all) MascotMood.Cheer else MascotMood.Idle, 0, stringResource(R.string.cd_mascot), size = 64.dp, pose = MascotPose.Peek)
                        HSpace(10.dp)
                        Column(Modifier.weight(1f)) {
                            KidText(stringResource(if (all) R.string.garage_all_title else R.string.garage_more_title), size = 18f, color = Palette.HeaderShade, align = TextAlign.Start)
                            KidText(stringResource(if (all) R.string.garage_all_body else R.string.garage_more_body), size = 14f, color = Palette.InkSoft, bold = false, align = TextAlign.Start)
                        }
                    }
                    VSpace(12.dp)
                }
            }
            BannerAd(vm, state)
        }
    }
}

/** "Car Collection" card with a crown, the book and a progress bar. */
@Composable
private fun CollectionCard(found: Int) {
    val percent = found * 100 / Cars.MAX_LEVEL
    Panel(Modifier.fillMaxWidth(), corner = 22.dp) {
        Row(Modifier.fillMaxWidth().background(Palette.Cream).padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(contentAlignment = Alignment.TopCenter) {
                Art(R.drawable.ui_book, Modifier.padding(top = 18.dp).size(76.dp))
                Art(R.drawable.ui_crown, Modifier.size(40.dp))
            }
            HSpace(12.dp)
            Column(Modifier.weight(1f)) {
                KidText(stringResource(R.string.car_collection), size = 22f, color = Palette.HeaderShade, align = TextAlign.Start)
                KidText(pluralStringResource(R.plurals.garage_count, Cars.MAX_LEVEL, found, Cars.MAX_LEVEL), size = 15f, color = Palette.InkSoft, bold = false, align = TextAlign.Start)
                VSpace(8.dp)
                Box(Modifier.fillMaxWidth().height(22.dp).clip(CircleShape).background(Color.White)) {
                    Box(
                        Modifier
                            .fillMaxWidth(found / Cars.MAX_LEVEL.toFloat())
                            .height(22.dp)
                            .clip(CircleShape)
                            .background(Palette.Orange),
                    )
                    KidText(stringResource(R.string.percent, percent), Modifier.align(Alignment.Center), size = 14f, color = Palette.Ink)
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
                .padding(7.dp),
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
                if (!unlocked) OutlinedTitle("?", size = 34f, outline = Palette.Locked)
            }
            VSpace(4.dp)
            if (unlocked) {
                KidText(Cars.value(level).toString(), size = 18f, color = Palette.HeaderShade)
                Box(Modifier.clip(CircleShape).background(Palette.Glass).padding(horizontal = 10.dp, vertical = 2.dp)) {
                    KidText(name, size = 13f, color = Palette.Ink)
                }
            } else {
                KidText("?", size = 18f, color = Palette.InkSoft)
                VSpace(20.dp)
            }
        }
    }
}

// ======================= Settings =======================

private enum class SettingsTab { General, Audio, About }

@Composable
fun SettingsScreen(state: UiState, vm: GameViewModel) {
    val s = state.settings
    var tab by rememberSaveable { mutableStateOf(SettingsTab.General) }
    var resetGameDone by remember { mutableStateOf(false) }
    var startOverDone by remember { mutableStateOf(false) }
    Box(Modifier.fillMaxSize()) {
        SkyBackground(scenery = true, groundFraction = 0.9f)
        Column(
            Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 14.dp, vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row(Modifier.fillMaxWidth().widthIn(max = 560.dp), verticalAlignment = Alignment.CenterVertically) {
                HeaderPill(stringResource(R.string.settings), R.drawable.ui_gear)
                Box(Modifier.weight(1f))
                WhiteIconButton(R.drawable.ui_close, stringResource(R.string.cd_close), { vm.navigate(Screen.Home) })
            }
            VSpace(14.dp)
            Row(Modifier.widthIn(max = 560.dp).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TabPill(stringResource(R.string.tab_general), R.drawable.ui_gear, tab == SettingsTab.General, Modifier.weight(1f)) { tab = SettingsTab.General; vm.click() }
                TabPill(stringResource(R.string.tab_audio), R.drawable.ui_music, tab == SettingsTab.Audio, Modifier.weight(1f)) { tab = SettingsTab.Audio; vm.click() }
                TabPill(stringResource(R.string.tab_about), null, tab == SettingsTab.About, Modifier.weight(1f)) { tab = SettingsTab.About; vm.click() }
            }
            VSpace(12.dp)
            Column(Modifier.widthIn(max = 560.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                when (tab) {
                    SettingsTab.General -> {
                        SettingToggle(R.drawable.ui_vibrate, stringResource(R.string.set_vibration), stringResource(R.string.set_vibration_sub), s.vibration) { vm.updateSettings(s.copy(vibration = it)) }
                        SettingToggle(R.drawable.ui_star_small, stringResource(R.string.set_hints), stringResource(R.string.set_hints_sub), s.hints) { vm.updateSettings(s.copy(hints = it)) }
                        HoldRow(
                            icon = R.drawable.ui_refresh,
                            title = stringResource(R.string.reset_game),
                            subtitle = stringResource(if (resetGameDone) R.string.reset_game_done else R.string.hold_3s),
                            locked = false,
                        ) {
                            vm.newGame()
                            resetGameDone = true
                        }
                        HoldRow(
                            icon = R.drawable.ui_shield,
                            title = stringResource(R.string.parent_reset),
                            subtitle = stringResource(if (startOverDone) R.string.start_over_done else R.string.parent_reset_sub),
                            locked = true,
                        ) {
                            vm.startOver()
                            startOverDone = true
                        }
                    }
                    SettingsTab.Audio -> {
                        SettingToggle(R.drawable.ui_music, stringResource(R.string.set_music), stringResource(R.string.set_music_sub), s.music) { vm.updateSettings(s.copy(music = it)) }
                        SettingToggle(R.drawable.ui_sound, stringResource(R.string.set_sound), stringResource(R.string.set_sound_sub), s.sound) { vm.updateSettings(s.copy(sound = it)) }
                        SettingToggle(R.drawable.ui_towy_happy, stringResource(R.string.set_voice), stringResource(R.string.set_voice_sub), s.voice) { vm.updateSettings(s.copy(voice = it)) }
                    }
                    SettingsTab.About -> {
                        GlassRow {
                            Column(Modifier.weight(1f)) {
                                KidText(stringResource(R.string.app_name), size = 20f, color = Palette.HeaderShade, align = TextAlign.Start)
                                KidText(stringResource(R.string.about_badges), size = 15f, color = Palette.Ink, bold = false, align = TextAlign.Start)
                                VSpace(6.dp)
                                KidText(stringResource(R.string.privacy_note), size = 14f, color = Palette.InkSoft, bold = false, align = TextAlign.Start)
                                VSpace(6.dp)
                                KidText(stringResource(R.string.credits), size = 12f, color = Palette.InkSoft, bold = false, align = TextAlign.Start)
                            }
                        }
                    }
                }
            }
            VSpace(14.dp)
            Row(Modifier.widthIn(max = 560.dp).fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
                Art(R.drawable.ui_towy_wink, Modifier.size(150.dp, 122.dp), stringResource(R.string.cd_mascot))
                Panel(Modifier.padding(bottom = 30.dp), corner = 26.dp) {
                    KidText(stringResource(R.string.have_fun), Modifier.padding(horizontal = 18.dp, vertical = 12.dp), size = 20f, color = Palette.HeaderShade)
                }
            }
        }
    }
}

@Composable
private fun TabPill(label: String, icon: Int?, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    ChunkyButton(
        onClick,
        if (selected) Palette.HeaderBlue else Color.White,
        if (selected) Palette.HeaderShade else Palette.ButtonShade,
        label,
        modifier.height(58.dp),
        RoundedCornerShape(18.dp),
        depth = 4.dp,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 6.dp)) {
            if (icon != null) {
                Box(Modifier.size(28.dp).clip(CircleShape).background(Color.White), contentAlignment = Alignment.Center) {
                    Art(icon, Modifier.size(22.dp))
                }
            } else {
                Box(Modifier.size(26.dp).clip(CircleShape).background(if (selected) Color.White else Palette.HeaderBlue), contentAlignment = Alignment.Center) {
                    KidText("i", size = 16f, color = if (selected) Palette.HeaderBlue else Color.White)
                }
            }
            HSpace(6.dp)
            KidText(label, size = 16f, color = if (selected) Color.White else Palette.HeaderShade)
        }
    }
}

/** Press and hold for three seconds. Letting go early resets the ring. */
@Composable
private fun HoldRow(icon: Int, title: String, subtitle: String, locked: Boolean, onConfirmed: () -> Unit) {
    val progress = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    GlassRow(
        Modifier
            .semantics(mergeDescendants = true) {
                role = Role.Button
                contentDescription = "$title. $subtitle"
            }
            .pointerInput(Unit) {
                awaitEachGesture {
                    awaitFirstDown()
                    val job = scope.launch {
                        progress.animateTo(1f, tween(HOLD_MS, easing = LinearEasing))
                        onConfirmed()
                        progress.animateTo(0f, tween(400))
                    }
                    waitForUpOrCancellation()
                    if (progress.value < 1f) {
                        job.cancel()
                        scope.launch { progress.animateTo(0f, tween(250)) }
                    }
                }
            },
    ) {
        Art(icon, Modifier.size(40.dp))
        HSpace(12.dp)
        Column(Modifier.weight(1f)) {
            KidText(title, size = 19f, color = Palette.HeaderShade, align = TextAlign.Start)
            KidText(subtitle, size = 13f, color = Palette.InkSoft, bold = false, align = TextAlign.Start)
        }
        Box(Modifier.size(40.dp), contentAlignment = Alignment.Center) {
            Canvas(Modifier.size(38.dp)) { drawHoldRing(progress.value, if (locked) Palette.PinkShade else Palette.HeaderBlue) }
            if (locked) Art(R.drawable.ui_lock, Modifier.size(20.dp)) else Canvas(Modifier.size(14.dp)) { KidIcons.play(this, Palette.HeaderBlue) }
        }
    }
}

private const val HOLD_MS = 3000
