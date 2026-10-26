package com.tinymotors.game.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.input.pointer.pointerInput
import androidx.activity.compose.LocalActivity
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView
import com.tinymotors.game.GameViewModel
import com.tinymotors.game.R
import com.tinymotors.game.REWARD_COINS
import com.tinymotors.game.Screen
import com.tinymotors.game.UiState
import com.tinymotors.game.engine.GameEvent
import com.tinymotors.game.engine.Shop
import com.tinymotors.game.monetize.Products
import com.tinymotors.game.ui.theme.BoardTheme
import com.tinymotors.game.ui.theme.BoardThemes
import com.tinymotors.game.ui.theme.Palette
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private enum class StoreTab { Themes, Coins }

@Composable
fun StoreScreen(state: UiState, vm: GameViewModel) {
    val game = state.game ?: return
    val res = LocalResources.current
    val activity = LocalActivity.current
    var tab by rememberSaveable { mutableStateOf(StoreTab.Themes) }
    var note by remember { mutableStateOf<String?>(null) }
    var gateFor by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(note) {
        if (note != null) {
            delay(2600)
            note = null
        }
    }
    Box(Modifier.fillMaxSize()) {
        SkyBackground(scenery = true, groundFraction = 0.92f)
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
                HeaderPill(stringResource(R.string.store), R.drawable.ui_store)
                Box(Modifier.weight(1f))
                StatPill(R.drawable.ui_coin, null, game.coins.toString())
            }
            VSpace(12.dp)
            Row(Modifier.widthIn(max = 640.dp).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StoreTabPill(stringResource(R.string.tab_themes), R.drawable.ui_palette, tab == StoreTab.Themes, Modifier.weight(1f)) {
                    tab = StoreTab.Themes
                    vm.click()
                }
                StoreTabPill(stringResource(R.string.tab_coins), R.drawable.ui_coin, tab == StoreTab.Coins, Modifier.weight(1f)) {
                    tab = StoreTab.Coins
                    vm.click()
                }
            }
            VSpace(10.dp)
            Column(
                Modifier
                    .weight(1f)
                    .widthIn(max = 640.dp)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                when (tab) {
                    StoreTab.Themes -> ThemesTab(game.coins, game.theme, { game.ownsTheme(it) }) { id ->
                        val events = vm.buyTheme(id)
                        note = when (val e = events.firstOrNull()) {
                            is GameEvent.NotEnoughCoins -> res.getQuantityString(R.plurals.need_more_coins, e.missing.toInt(), e.missing.toInt())
                            is GameEvent.ThemeBought -> res.getString(R.string.theme_bought)
                            else -> null
                        }
                    }
                    StoreTab.Coins -> CoinsTab(
                        state = state,
                        onWatch = { activity?.let(vm::watchAd) },
                        onBuy = { id ->
                            vm.click()
                            gateFor = id
                        },
                    )
                }
                GlassRow {
                    Mascot(MascotMood.Idle, 0, stringResource(R.string.cd_mascot), size = 60.dp, pose = MascotPose.Peek)
                    HSpace(10.dp)
                    KidText(stringResource(R.string.earn_coins), Modifier.weight(1f), size = 16f, color = Palette.HeaderShade, align = TextAlign.Start)
                }
                VSpace(8.dp)
            }
        }
        note?.let { msg ->
            Box(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing).padding(bottom = 24.dp), contentAlignment = Alignment.BottomCenter) {
                Panel(corner = 20.dp) {
                    KidText(msg, Modifier.padding(horizontal = 18.dp, vertical = 12.dp), size = 18f, color = Palette.HeaderShade)
                }
            }
        }
        gateFor?.let { id ->
            ParentGate(
                onPassed = {
                    gateFor = null
                    val ok = activity != null && vm.buyProduct(activity, id)
                    if (!ok) note = res.getString(R.string.store_unavailable)
                },
                onCancel = {
                    gateFor = null
                    vm.click()
                },
            )
        }
    }
}

@Composable
private fun StoreTabPill(label: String, icon: Int, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    ChunkyButton(
        onClick,
        if (selected) Palette.HeaderBlue else Color.White,
        if (selected) Palette.HeaderShade else Palette.ButtonShade,
        label,
        modifier.height(58.dp),
        RoundedCornerShape(18.dp),
        depth = 4.dp,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(30.dp).clip(CircleShape).background(Color.White), contentAlignment = Alignment.Center) {
                Art(icon, Modifier.size(24.dp))
            }
            HSpace(8.dp)
            KidText(label, size = 18f, color = if (selected) Color.White else Palette.HeaderShade)
        }
    }
}

@Composable
private fun ThemesTab(coins: Long, inUse: Int, owns: (Int) -> Boolean, onPick: (Int) -> Unit) {
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val cols = if (maxWidth > 520.dp) 3 else 2
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            for (row in 0 until (Shop.THEME_COUNT + cols - 1) / cols) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    for (c in 0 until cols) {
                        val id = row * cols + c
                        Box(Modifier.weight(1f)) {
                            if (id < Shop.THEME_COUNT) {
                                ThemeCard(BoardThemes[id], Shop.THEME_PRICES[id], owns(id), id == inUse, coins >= Shop.THEME_PRICES[id]) { onPick(id) }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ThemeCard(theme: BoardTheme, price: Int, owned: Boolean, inUse: Boolean, affordable: Boolean, onClick: () -> Unit) {
    val name = stringResource(theme.nameRes)
    Panel(corner = 20.dp) {
        Column(
            Modifier
                .fillMaxWidth()
                .semantics(mergeDescendants = true) {
                    role = Role.Button
                    contentDescription = name
                }
                .clickable(onClick = onClick)
                .padding(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Canvas(
                Modifier
                    .fillMaxWidth()
                    .aspectRatio(1.3f)
                    .clip(RoundedCornerShape(14.dp)),
            ) { drawThemePreview(theme) }
            VSpace(6.dp)
            KidText(name, size = 16f, color = Palette.HeaderShade)
            VSpace(4.dp)
            val (label, colour) = when {
                inUse -> stringResource(R.string.in_use) to Palette.GreenShade
                owned -> stringResource(R.string.use_theme) to Palette.HeaderBlue
                price == 0 -> stringResource(R.string.free) to Palette.GreenShade
                else -> price.toString() to (if (affordable) Palette.OrangeShade else Palette.InkSoft)
            }
            Row(
                Modifier
                    .clip(CircleShape)
                    .background(if (inUse) Color(0xFFE4F7EA) else Palette.Glass)
                    .padding(horizontal = 12.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (!owned && price > 0) {
                    Art(R.drawable.ui_coin, Modifier.size(20.dp))
                    HSpace(4.dp)
                }
                if (inUse) {
                    Canvas(Modifier.size(16.dp)) { KidIcons.check(this, Palette.GreenShade) }
                    HSpace(4.dp)
                }
                KidText(label, size = 16f, color = colour)
            }
        }
    }
}

/** Little picture of a theme: its sky, hills and a mini board. */
private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawThemePreview(t: BoardTheme) {
    drawRect(Brush.verticalGradient(listOf(t.skyTop, t.skyBottom)))
    if (t.stars) {
        for (i in 0 until 8) drawCircle(Color.White, 1.5.dp.toPx(), Offset(size.width * (0.1f + i * 0.11f), size.height * (0.1f + (i % 3) * 0.08f)))
    }
    val hill = Path().apply {
        moveTo(0f, size.height * 0.62f)
        cubicTo(size.width * 0.3f, size.height * 0.5f, size.width * 0.7f, size.height * 0.68f, size.width, size.height * 0.56f)
        lineTo(size.width, size.height); lineTo(0f, size.height); close()
    }
    drawPath(hill, t.hill)
    drawCircle(t.treeDark, size.width * 0.07f, Offset(size.width * 0.12f, size.height * 0.52f))
    drawCircle(t.tree, size.width * 0.08f, Offset(size.width * 0.88f, size.height * 0.48f))
    val bw = size.width * 0.5f
    val bh = size.height * 0.5f
    val bx = (size.width - bw) / 2
    val by = size.height * 0.3f
    drawRoundRect(t.panel, Offset(bx, by), Size(bw, bh), CornerRadius(bw * 0.08f))
    val cell = (bw - bw * 0.1f) / 4
    for (r in 0 until 3) for (c in 0 until 4) {
        drawRoundRect(
            t.slot, Offset(bx + bw * 0.05f + c * cell + cell * 0.08f, by + bh * 0.07f + r * cell + cell * 0.08f),
            Size(cell * 0.84f, cell * 0.84f), CornerRadius(cell * 0.2f),
        )
    }
}

@Composable
private fun CoinsTab(state: UiState, onWatch: () -> Unit, onBuy: (String) -> Unit) {
    // Reward video: always the child's choice, clearly labelled as an ad.
    val left = state.adsLeftToday
    GlassRow(Modifier.semantics(mergeDescendants = true) { role = Role.Button }) {
        Box(contentAlignment = Alignment.TopEnd) {
            Art(R.drawable.ui_coin, Modifier.size(48.dp))
            Box(Modifier.clip(CircleShape).background(Palette.Ink).padding(horizontal = 5.dp)) {
                KidText(stringResource(R.string.ad_label), size = 10f, color = Color.White)
            }
        }
        HSpace(12.dp)
        Column(Modifier.weight(1f)) {
            KidText(stringResource(R.string.watch_ad), size = 18f, color = Palette.HeaderShade, align = TextAlign.Start)
            KidText(
                when {
                    left <= 0 -> stringResource(R.string.ads_none_left)
                    !state.rewardedReady -> stringResource(R.string.ad_loading)
                    else -> pluralStringResource(R.plurals.ads_left_today, left, left)
                },
                size = 13f, color = Palette.InkSoft, bold = false, align = TextAlign.Start,
            )
        }
        ChunkyButton(
            onWatch, Palette.Green, Palette.GreenShade, stringResource(R.string.watch_ad),
            Modifier.height(56.dp), RoundedCornerShape(18.dp), depth = 4.dp,
            enabled = left > 0 && state.rewardedReady,
        ) {
            KidText(pluralStringResource(R.plurals.watch_ad_reward, REWARD_COINS, REWARD_COINS), Modifier.padding(horizontal = 14.dp), size = 16f, color = Color.White)
        }
    }

    // Real-money items: every one sits behind the grown-up gate.
    KidText(stringResource(R.string.for_grown_ups), Modifier.padding(start = 6.dp, top = 6.dp), size = 16f, color = Palette.InkSoft, align = TextAlign.Start)
    val packs = listOf(
        Triple(Products.COINS_SMALL, R.string.coin_pack_small, 1),
        Triple(Products.COINS_MEDIUM, R.string.coin_pack_medium, 2),
        Triple(Products.COINS_LARGE, R.string.coin_pack_large, 3),
    )
    for ((id, nameRes, stack) in packs) {
        PurchaseRow(
            title = stringResource(nameRes),
            subtitle = pluralStringResource(R.plurals.coin_pack_amount, Products.COIN_PACKS.getValue(id), Products.COIN_PACKS.getValue(id)),
            price = state.prices[id],
            icon = {
                Box(Modifier.size(48.dp)) {
                    for (k in 0 until stack) {
                        Art(R.drawable.ui_coin, Modifier.size(34.dp).padding(start = (k * 6).dp, top = (k * 5).dp))
                    }
                }
            },
            onClick = { onBuy(id) },
        )
    }
    if (!state.adsRemoved) {
        PurchaseRow(
            title = stringResource(R.string.remove_ads),
            subtitle = stringResource(R.string.remove_ads_sub),
            price = state.prices[Products.REMOVE_ADS],
            icon = { Art(R.drawable.ui_shield, Modifier.size(44.dp)) },
            onClick = { onBuy(Products.REMOVE_ADS) },
        )
    } else {
        GlassRow {
            Art(R.drawable.ui_shield, Modifier.size(44.dp))
            HSpace(12.dp)
            KidText(stringResource(R.string.ads_removed), size = 17f, color = Palette.GreenShade, align = TextAlign.Start)
        }
    }
}

@Composable
private fun PurchaseRow(title: String, subtitle: String, price: String?, icon: @Composable () -> Unit, onClick: () -> Unit) {
    GlassRow {
        icon()
        HSpace(12.dp)
        Column(Modifier.weight(1f)) {
            KidText(title, size = 18f, color = Palette.HeaderShade, align = TextAlign.Start)
            KidText(subtitle, size = 13f, color = Palette.InkSoft, bold = false, align = TextAlign.Start)
        }
        ChunkyButton(
            onClick, Palette.Orange, Palette.OrangeShade, "$title ${price ?: ""}",
            Modifier.height(56.dp), RoundedCornerShape(18.dp), depth = 4.dp,
            enabled = price != null,
        ) {
            KidText(price ?: stringResource(R.string.price_unavailable), Modifier.padding(horizontal = 12.dp), size = 15f, color = Color.White)
        }
    }
}

/** Grown-up gate in front of every real-money purchase: press and hold for three seconds. */
@Composable
private fun ParentGate(onPassed: () -> Unit, onCancel: () -> Unit) {
    val progress = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    FullScrim {
        DialogCard {
            Column(Modifier.fillMaxWidth().padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Art(R.drawable.ui_shield, Modifier.size(64.dp))
                VSpace(6.dp)
                OutlinedTitle(stringResource(R.string.gate_title), size = 26f, fill = Palette.HeaderBlue, outline = Color.White)
                VSpace(6.dp)
                KidText(stringResource(R.string.gate_body), size = 15f, color = Palette.InkSoft, bold = false)
                VSpace(16.dp)
                val holdLabel = stringResource(R.string.gate_hold)
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(70.dp)
                        .clip(RoundedCornerShape(20.dp))
                        .background(Palette.Slot)
                        .semantics {
                            role = Role.Button
                            contentDescription = holdLabel
                        }
                        .pointerInput(Unit) {
                            awaitEachGesture {
                                awaitFirstDown()
                                val job = scope.launch {
                                    progress.animateTo(1f, tween(3000, easing = LinearEasing))
                                    onPassed()
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
                        drawRect(Palette.HeaderBlue.copy(alpha = 0.25f), size = Size(size.width * progress.value, size.height))
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Canvas(Modifier.size(30.dp)) { drawHoldRing(progress.value, Palette.HeaderBlue) }
                        HSpace(10.dp)
                        KidText(holdLabel, size = 17f, color = Palette.Ink)
                    }
                }
                VSpace(12.dp)
                WhiteIconButton(R.drawable.ui_close, stringResource(R.string.cd_close), onCancel)
            }
        }
    }
}

/** Child-directed AdMob banner. Only on the Home and Garage screens, never during play. */
@Composable
fun BannerAd(vm: GameViewModel, state: UiState, modifier: Modifier = Modifier) {
    val ready by vm.ads.ready.collectAsState()
    if (state.adsRemoved || !ready) return
    BoxWithConstraints(modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        val widthDp = maxWidth.value.toInt().coerceAtMost(728)
        AndroidView(
            factory = { ctx ->
                AdView(ctx).apply {
                    setAdSize(AdSize.getCurrentOrientationAnchoredAdaptiveBannerAdSize(ctx, widthDp))
                    adUnitId = vm.ads.bannerUnitId
                    loadAd(AdRequest.Builder().build())
                }
            },
            onRelease = { it.destroy() },
        )
    }
}
