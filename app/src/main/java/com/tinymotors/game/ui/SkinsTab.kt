package com.tinymotors.game.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.tinymotors.game.R
import com.tinymotors.game.engine.GameState
import com.tinymotors.game.engine.Rarity
import com.tinymotors.game.engine.Skin
import com.tinymotors.game.engine.Skins
import com.tinymotors.game.ui.theme.Palette
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * The Skins tab of the Store: a big preview of the chosen skin with its Buy / Use button, rarity
 * filters, and a grid of every skin. Skins of cars the child has not made yet stay locked, which
 * gives them something to play towards.
 */
@Composable
fun SkinsTab(game: GameState, onClick: () -> Unit, onAction: (Int) -> Unit) {
    var selected by rememberSaveable {
        mutableIntStateOf(
            Skins.ALL.firstOrNull { game.wearsSkin(it.id) }?.id
                ?: Skins.ALL.firstOrNull { game.isUnlocked(it.level) && !game.ownsSkin(it.id) }?.id
                ?: 0,
        )
    }
    var filter by rememberSaveable { mutableIntStateOf(-1) } // -1 = all, else Rarity ordinal
    val skin = Skins[selected] ?: Skins.ALL.first()

    SkinPreview(skin, game) { onAction(skin.id) }

    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        RarityChip(stringResource(R.string.rarity_all), null, filter == -1) {
            filter = -1
            onClick()
        }
        for (r in Rarity.entries) {
            RarityChip(stringResource(rarityName(r)), r, filter == r.ordinal) {
                filter = r.ordinal
                onClick()
            }
        }
    }

    val owned = Skins.ALL.count { game.ownsSkin(it.id) }
    KidText(
        stringResource(R.string.skins_collected, owned, Skins.COUNT),
        Modifier.padding(start = 6.dp), size = 15f, color = Palette.InkSoft, align = TextAlign.Start,
    )

    val shown = Skins.ALL.filter { filter == -1 || it.rarity.ordinal == filter }
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val cols = if (maxWidth > 520.dp) 4 else 3
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            for (row in shown.chunked(cols)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    for (c in 0 until cols) {
                        Box(Modifier.weight(1f)) {
                            row.getOrNull(c)?.let { s ->
                                SkinCard(s, game, s.id == selected) {
                                    selected = s.id
                                    onClick()
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SkinPreview(skin: Skin, game: GameState, onAction: () -> Unit) {
    val art = LocalCarArt.current
    val name = stringResource(SKIN_NAMES[skin.id])
    val carName = stringResource(CarArt.NAMES[skin.level - 1])
    val unlocked = game.isUnlocked(skin.level)
    val owned = game.ownsSkin(skin.id)
    val wearing = game.wearsSkin(skin.id)
    val colours = rarityColours(skin.rarity)
    Panel(corner = 24.dp) {
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val wide = maxWidth > 520.dp
            val picture = @Composable { modifier: Modifier ->
                Box(modifier.aspectRatio(1.3f), contentAlignment = Alignment.Center) {
                    Canvas(Modifier.fillMaxSize()) { drawStage(colours.glow) }
                    Image(
                        art.skinImage(skin.id), name,
                        Modifier.fillMaxSize().padding(start = 18.dp, end = 18.dp, bottom = 22.dp),
                    )
                    if (wearing) {
                        Box(Modifier.fillMaxSize().padding(10.dp), contentAlignment = Alignment.TopEnd) {
                            UsingBadge()
                        }
                    }
                }
            }
            val info = @Composable { modifier: Modifier ->
                Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
                    OutlinedTitle(name, size = 26f, fill = Color.White, outline = Palette.HeaderShade)
                    VSpace(4.dp)
                    Box(
                        Modifier.clip(CircleShape).background(colours.chip).padding(horizontal = 14.dp, vertical = 3.dp),
                    ) { KidText(stringResource(rarityName(skin.rarity)), size = 14f, color = Color.White) }
                    VSpace(6.dp)
                    KidText(stringResource(SKIN_TAGS[skin.id]), size = 16f, color = Palette.Ink)
                    KidText(stringResource(R.string.skin_for_car, carName), size = 13f, color = Palette.InkSoft, bold = false)
                    VSpace(10.dp)
                    if (!unlocked) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Art(R.drawable.ui_lock, Modifier.size(22.dp))
                            HSpace(6.dp)
                            KidText(stringResource(R.string.skin_find_car, carName), size = 15f, color = Palette.OrangeShade)
                        }
                        VSpace(8.dp)
                    }
                    SkinButton(skin, unlocked, owned, wearing, game.coins >= skin.price, onAction)
                }
            }
            if (wide) {
                Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    picture(Modifier.weight(1f))
                    HSpace(12.dp)
                    info(Modifier.weight(1f))
                }
            } else {
                Column(Modifier.padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    picture(Modifier.widthIn(max = 340.dp).fillMaxWidth())
                    info(Modifier.fillMaxWidth())
                }
            }
        }
    }
}

@Composable
private fun SkinButton(skin: Skin, unlocked: Boolean, owned: Boolean, wearing: Boolean, affordable: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(22.dp)
    val modifier = Modifier.widthIn(min = 200.dp).height(64.dp)
    when {
        wearing -> ChunkyButton(onClick, Color.White, Palette.ButtonShade, stringResource(R.string.skin_take_off), modifier, shape, depth = 5.dp) {
            KidText(stringResource(R.string.skin_take_off), Modifier.padding(horizontal = 18.dp), size = 19f, color = Palette.HeaderShade)
        }
        owned -> ChunkyButton(onClick, Palette.HeaderBlue, Palette.HeaderShade, stringResource(R.string.skin_use), modifier, shape, depth = 5.dp) {
            KidText(stringResource(R.string.skin_use), Modifier.padding(horizontal = 18.dp), size = 22f, color = Color.White)
        }
        else -> {
            val label = stringResource(R.string.skin_buy)
            ChunkyButton(
                onClick, Palette.Green, Palette.GreenShade, "$label ${skin.price}", modifier, shape, depth = 5.dp,
                enabled = unlocked,
            ) {
                Row(Modifier.padding(horizontal = 18.dp), verticalAlignment = Alignment.CenterVertically) {
                    Art(R.drawable.ui_coin, Modifier.size(32.dp).alpha(if (affordable) 1f else 0.7f))
                    HSpace(6.dp)
                    KidText(skin.price.toString(), size = 22f, color = Color.White)
                    HSpace(14.dp)
                    KidText(label, size = 22f, color = Color.White)
                }
            }
        }
    }
}

@Composable
private fun SkinCard(skin: Skin, game: GameState, isSelected: Boolean, onClick: () -> Unit) {
    val art = LocalCarArt.current
    val name = stringResource(SKIN_NAMES[skin.id])
    val colours = rarityColours(skin.rarity)
    val unlocked = game.isUnlocked(skin.level)
    val owned = game.ownsSkin(skin.id)
    val wearing = game.wearsSkin(skin.id)
    val shape = RoundedCornerShape(18.dp)
    Column(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(colours.card)
            .border(if (isSelected) 3.dp else 1.dp, if (isSelected) Palette.Gold else Palette.GlassEdge, shape)
            .semantics(mergeDescendants = true) {
                role = Role.Button
                contentDescription = name
                selected = isSelected
            }
            .clickable(onClick = onClick)
            .padding(6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(Modifier.fillMaxWidth().aspectRatio(1.15f), contentAlignment = Alignment.Center) {
            Image(art.skinImage(skin.id), null, Modifier.fillMaxSize().alpha(if (unlocked) 1f else 0.55f))
            Canvas(Modifier.fillMaxSize()) { drawStar(colours.chip, Offset(11.dp.toPx(), 11.dp.toPx()), 9.dp.toPx()) }
            if (!unlocked) Art(R.drawable.ui_lock, Modifier.size(30.dp).align(Alignment.BottomEnd))
            if (wearing) Box(Modifier.align(Alignment.TopEnd)) { UsingBadge() }
        }
        val pill = Modifier
            .clip(CircleShape)
            .background(if (wearing) Color(0xFFE4F7EA) else Color.White.copy(alpha = 0.85f))
            .padding(horizontal = 10.dp, vertical = 2.dp)
        Row(pill, verticalAlignment = Alignment.CenterVertically) {
            when {
                wearing -> KidText(stringResource(R.string.skin_using), size = 13f, color = Palette.GreenShade)
                owned -> KidText(stringResource(R.string.skin_owned), size = 13f, color = Palette.HeaderBlue)
                !unlocked -> KidText(stringResource(R.string.skin_find_car_short), size = 13f, color = Palette.InkSoft)
                else -> {
                    Art(R.drawable.ui_coin, Modifier.size(16.dp))
                    HSpace(3.dp)
                    KidText(skin.price.toString(), size = 13f, color = if (game.coins >= skin.price) Palette.OrangeShade else Palette.InkSoft)
                }
            }
        }
    }
}

@Composable
private fun UsingBadge() {
    Box(
        Modifier.size(28.dp).clip(CircleShape).background(Palette.Green).padding(6.dp),
    ) { Canvas(Modifier.fillMaxSize()) { KidIcons.check(this, Color.White) } }
}

@Composable
private fun RarityChip(label: String, rarity: Rarity?, on: Boolean, onClick: () -> Unit) {
    val colours = rarity?.let { rarityColours(it) }
    Row(
        Modifier
            .height(44.dp)
            .clip(CircleShape)
            .background(if (on) Palette.Gold else Color.White)
            .border(2.dp, if (on) Palette.OrangeShade else Palette.GlassEdge, CircleShape)
            .semantics {
                role = Role.Tab
                selected = on
            }
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (colours != null) {
            Canvas(Modifier.size(18.dp)) { drawStar(colours.chip, center, size.minDimension / 2) }
            HSpace(5.dp)
        }
        KidText(label, size = 15f, color = if (on) Palette.Ink else Palette.HeaderShade)
    }
}

private class RarityColours(val chip: Color, val card: Color, val glow: Color)

private fun rarityColours(r: Rarity) = when (r) {
    Rarity.Common -> RarityColours(Color(0xFF7F8DA6), Color(0xFFEFF3F9), Color(0xFFE1E8F2))
    Rarity.Rare -> RarityColours(Palette.Blue, Color(0xFFE3F1FF), Color(0xFFC9E6FF))
    Rarity.Epic -> RarityColours(Palette.Purple, Color(0xFFEFE8FF), Color(0xFFDCCEFF))
    Rarity.Legendary -> RarityColours(Color(0xFFF2A900), Color(0xFFFFF4D2), Color(0xFFFFE59A))
}

private fun rarityName(r: Rarity) = when (r) {
    Rarity.Common -> R.string.rarity_common
    Rarity.Rare -> R.string.rarity_rare
    Rarity.Epic -> R.string.rarity_epic
    Rarity.Legendary -> R.string.rarity_legendary
}

/** Soft glow and a round pedestal for the big preview. */
private fun DrawScope.drawStage(glow: Color) {
    drawCircle(
        Brush.radialGradient(listOf(glow, glow.copy(alpha = 0f)), center = Offset(size.width / 2, size.height * 0.45f), radius = size.width * 0.5f),
        radius = size.width * 0.5f, center = Offset(size.width / 2, size.height * 0.45f),
    )
    val w = size.width * 0.72f
    val h = size.height * 0.16f
    val top = size.height * 0.76f
    drawOval(Color(0xFFC9D6E6), Offset((size.width - w) / 2, top + h * 0.22f), Size(w, h))
    drawOval(Color(0xFFF1F5FB), Offset((size.width - w) / 2, top), Size(w, h))
    drawStar(Palette.Gold, Offset(size.width * 0.14f, size.height * 0.2f), size.minDimension * 0.05f)
    drawStar(Palette.Gold, Offset(size.width * 0.86f, size.height * 0.14f), size.minDimension * 0.04f)
}

private fun DrawScope.drawStar(color: Color, c: Offset, r: Float) {
    val path = Path()
    for (i in 0 until 10) {
        val rr = if (i % 2 == 0) r else r * 0.48f
        val a = -PI / 2 + i * PI / 5
        val p = Offset(c.x + (rr * cos(a)).toFloat(), c.y + (rr * sin(a)).toFloat())
        if (i == 0) path.moveTo(p.x, p.y) else path.lineTo(p.x, p.y)
    }
    path.close()
    drawPath(path, color)
}

/** Skin names and taglines in [Skins.ALL] order. */
private val SKIN_NAMES = intArrayOf(
    R.string.skinname_racing_red, R.string.skinname_cow, R.string.skinname_police, R.string.skinname_bunny,
    R.string.skinname_taxi, R.string.skinname_kitty, R.string.skinname_mint, R.string.skinname_dino,
    R.string.skinname_stars, R.string.skinname_flower_van, R.string.skinname_santa, R.string.skinname_crown,
    R.string.skinname_rescue, R.string.skinname_pumpkin, R.string.skinname_pink_bow, R.string.skinname_unicorn,
    R.string.skinname_rally, R.string.skinname_rainbow, R.string.skinname_night, R.string.skinname_neon,
    R.string.skinname_galaxy, R.string.skinname_champion,
)
private val SKIN_TAGS = intArrayOf(
    R.string.skintag_racing_red, R.string.skintag_cow, R.string.skintag_police, R.string.skintag_bunny,
    R.string.skintag_taxi, R.string.skintag_kitty, R.string.skintag_mint, R.string.skintag_dino,
    R.string.skintag_stars, R.string.skintag_flower_van, R.string.skintag_santa, R.string.skintag_crown,
    R.string.skintag_rescue, R.string.skintag_pumpkin, R.string.skintag_pink_bow, R.string.skintag_unicorn,
    R.string.skintag_rally, R.string.skintag_rainbow, R.string.skintag_night, R.string.skintag_neon,
    R.string.skintag_galaxy, R.string.skintag_champion,
)
