package com.tinymotors.game.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.drawText
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tinymotors.game.ui.theme.LocalReduceMotion
import com.tinymotors.game.ui.theme.Palette
import com.tinymotors.game.ui.theme.kidText
import kotlin.math.roundToInt

/** Big chunky 3D button: the face sits on a darker base and sinks when pressed. */
@Composable
fun ChunkyButton(
    onClick: () -> Unit,
    color: Color,
    shade: Color,
    contentDescription: String,
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(20.dp),
    depth: Dp = 6.dp,
    enabled: Boolean = true,
    content: @Composable BoxScope.() -> Unit,
) {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val sink by animateDpAsState(if (pressed) depth - 2.dp else 0.dp, spring(stiffness = 900f), label = "sink")
    val face = if (enabled) color else Palette.Grey
    val base = if (enabled) shade else Palette.GreyShade
    Box(
        modifier
            .defaultMinSize(56.dp, 56.dp)
            .semantics {
                this.contentDescription = contentDescription
                role = Role.Button
            }
            .clickable(source, indication = null, enabled = enabled, onClick = onClick),
    ) {
        Box(
            Modifier
                .matchParentSize()
                .padding(top = depth)
                .clip(shape)
                .background(base),
        )
        Box(
            Modifier
                .matchParentSize()
                .padding(bottom = depth)
                .offset { androidx.compose.ui.unit.IntOffset(0, sink.roundToPx()) }
                .clip(shape)
                .background(face)
                .drawBehind {
                    // soft top highlight for a toy-like look
                    drawRoundRect(
                        Color.White.copy(alpha = 0.22f),
                        topLeft = Offset(size.width * 0.06f, size.height * 0.08f),
                        size = Size(size.width * 0.88f, size.height * 0.3f),
                        cornerRadius = CornerRadius(size.height * 0.15f),
                    )
                },
            contentAlignment = Alignment.Center,
            content = content,
        )
    }
}

/** Round icon button (home, settings, close). */
@Composable
fun RoundIconButton(
    onClick: () -> Unit,
    contentDescription: String,
    modifier: Modifier = Modifier,
    color: Color = Palette.Panel,
    shade: Color = Palette.PanelShade,
    iconColor: Color = Palette.Ink,
    icon: DrawScope.(Color) -> Unit,
) {
    ChunkyButton(onClick, color, shade, contentDescription, modifier.size(60.dp), CircleShape, depth = 5.dp) {
        Canvas(Modifier.size(28.dp)) { icon(iconColor) }
    }
}

@Composable
fun KidText(
    text: String,
    modifier: Modifier = Modifier,
    size: Float = 18f,
    color: Color = Palette.Ink,
    bold: Boolean = true,
) {
    BasicText(text, modifier, style = kidText(size.sp, color, bold))
}

/** White rounded panel. */
@Composable
fun Panel(modifier: Modifier = Modifier, corner: Dp = 20.dp, content: @Composable BoxScope.() -> Unit) {
    Box(
        modifier
            .drawBehind {
                drawRoundRect(Palette.PanelShade, Offset(0f, 4.dp.toPx()), size, CornerRadius(corner.toPx()))
            }
            .padding(bottom = 4.dp)
            .clip(RoundedCornerShape(corner))
            .background(Palette.Panel),
        content = content,
    )
}

/** Small round number bubble (tows left, undos left). */
@Composable
fun CountBadge(count: Int, modifier: Modifier = Modifier, color: Color = Palette.Ink) {
    Box(
        modifier
            .size(28.dp)
            .clip(CircleShape)
            .background(Color.White)
            .padding(2.dp)
            .clip(CircleShape)
            .background(color),
        contentAlignment = Alignment.Center,
    ) {
        BasicText(count.toString(), style = kidText(15.sp, Color.White))
    }
}

/** Light sky with soft drifting clouds. */
@Composable
fun SkyBackground(modifier: Modifier = Modifier) {
    val reduce = LocalReduceMotion.current
    val drift = if (reduce) {
        0f
    } else {
        val t = rememberInfiniteTransition(label = "clouds")
        t.animateFloat(0f, 1f, infiniteRepeatable(tween(90_000, easing = LinearEasing)), label = "drift").value
    }
    val brush = remember { Brush.verticalGradient(listOf(Palette.SkyTop, Palette.SkyBottom)) }
    Canvas(modifier.fillMaxSize()) {
        drawRect(brush)
        val w = size.width
        val clouds = CLOUDS
        for (i in clouds.indices step 3) {
            val speed = 0.5f + (i % 2) * 0.5f
            val x = ((clouds[i] + drift * speed) % 1.2f - 0.1f) * w
            val y = clouds[i + 1] * size.height
            val s = clouds[i + 2] * w
            drawCloud(Offset(x, y), s)
        }
    }
}

private val CLOUDS = floatArrayOf(0.1f, 0.08f, 0.22f, 0.7f, 0.18f, 0.18f, 0.4f, 0.42f, 0.16f, 0.95f, 0.66f, 0.2f, 0.25f, 0.86f, 0.15f)

private fun DrawScope.drawCloud(c: Offset, s: Float) {
    val col = Palette.Cloud.copy(alpha = 0.75f)
    drawCircle(col, s * 0.32f, Offset(c.x, c.y))
    drawCircle(col, s * 0.24f, Offset(c.x - s * 0.32f, c.y + s * 0.08f))
    drawCircle(col, s * 0.26f, Offset(c.x + s * 0.32f, c.y + s * 0.06f))
    drawRoundRect(col, Offset(c.x - s * 0.55f, c.y + s * 0.02f), Size(s * 1.1f, s * 0.28f), CornerRadius(s * 0.14f))
}

/** Pill switch for settings. */
@Composable
fun KidSwitch(checked: Boolean, label: String, onChange: (Boolean) -> Unit, modifier: Modifier = Modifier) {
    val knob by animateDpAsState(if (checked) 30.dp else 0.dp, spring(dampingRatio = 0.6f), label = "knob")
    Row(
        modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 64.dp)
            .semantics(mergeDescendants = true) {
                role = Role.Switch
                contentDescription = label
            }
            .clickable { onChange(!checked) }
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        KidText(label, Modifier.weight(1f), size = 20f, bold = false)
        Box(
            Modifier
                .width(66.dp)
                .height(36.dp)
                .clip(CircleShape)
                .background(if (checked) Palette.Green else Palette.Grey)
                .padding(3.dp),
        ) {
            Box(
                Modifier
                    .offset { androidx.compose.ui.unit.IntOffset(knob.roundToPx(), 0) }
                    .size(30.dp)
                    .clip(CircleShape)
                    .background(Color.White),
            )
        }
    }
}

@Composable
fun VSpace(h: Dp) = Spacer(Modifier.height(h))

@Composable
fun HSpace(w: Dp) = Spacer(Modifier.width(w))

/** Draws a car picture centred in [size] with a soft ground shadow underneath (drawn at runtime). */
fun DrawScope.drawCar(look: CarLook, topLeft: Offset, boxSize: Size, alpha: Float = 1f, filter: ColorFilter? = null, shadow: Boolean = true) {
    val w = boxSize.width
    val h = boxSize.height
    if (shadow) {
        // The car art fills ~88% of the width and sits in the middle of its square; wheels are ~70% down.
        val sw = w * 0.74f
        val sh = h * 0.12f
        drawOval(
            Brush.radialGradient(
                listOf(Color(0x552B3A55), Color(0x002B3A55)),
                center = Offset(topLeft.x + w / 2, topLeft.y + h * 0.71f),
                radius = sw / 2,
            ),
            Offset(topLeft.x + (w - sw) / 2, topLeft.y + h * 0.71f - sh / 2),
            Size(sw, sh),
            alpha = alpha,
        )
    }
    drawImage(
        look.image,
        dstOffset = androidx.compose.ui.unit.IntOffset(topLeft.x.roundToInt(), topLeft.y.roundToInt()),
        dstSize = IntSize(w.roundToInt(), h.roundToInt()),
        alpha = alpha,
        colorFilter = filter,
    )
}

/** Car tile: car-tinted rounded square with a deeper edge, the car, and a value badge. */
fun DrawScope.drawCarTile(look: CarLook, tile: Size, badge: TextMeasurer?, value: Int, alpha: Float = 1f) {
    val r = CornerRadius(tile.width * 0.2f)
    val edgeH = tile.height * 0.07f
    drawRoundRect(look.edge, Offset(0f, edgeH), Size(tile.width, tile.height - edgeH), r, alpha = alpha)
    drawRoundRect(look.tile, Offset.Zero, Size(tile.width, tile.height - edgeH), r, alpha = alpha)
    val inset = tile.width * 0.02f
    drawCar(look, Offset(inset, tile.height * 0.04f), Size(tile.width - inset * 2, tile.width - inset * 2), alpha)
    if (badge != null) {
        val text = value.toString()
        val style = kidText(if (value >= 1000) 9.sp else 11.sp, if (look.level == 11) Palette.Ink else Color.White)
        val layout = badge.measure(text, style)
        val pw = layout.size.width + 10.dp.toPx()
        val ph = layout.size.height + 2.dp.toPx()
        val pos = Offset(tile.width * 0.06f, tile.height * 0.05f)
        drawRoundRect(if (look.level == 11) Palette.Gold else look.edge, pos, Size(pw, ph), CornerRadius(ph / 2), alpha = alpha)
        drawText(layout, topLeft = Offset(pos.x + 5.dp.toPx(), pos.y + 1.dp.toPx()), alpha = alpha)
    }
}

/** Faded grey look for locked cars in the collection strip. */
val GreyFilter: ColorFilter = ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(0f) })

/** Dark silhouette for locked stickers. */
val SilhouetteFilter: ColorFilter = ColorFilter.tint(Palette.Locked, androidx.compose.ui.graphics.BlendMode.SrcIn)

/** Dashed rounded outline (non-merge drop targets). */
fun DrawScope.drawDashedSlot(color: Color, topLeft: Offset, box: Size, inset: Float) {
    drawRoundRect(
        color,
        Offset(topLeft.x + inset, topLeft.y + inset),
        Size(box.width - inset * 2, box.height - inset * 2),
        CornerRadius(box.width * 0.2f),
        style = Stroke(
            width = 3.dp.toPx(),
            pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(10.dp.toPx(), 7.dp.toPx())),
        ),
    )
}

@Composable
fun FullScrim(content: @Composable BoxScope.() -> Unit) {
    Box(
        Modifier
            .fillMaxSize()
            .background(Palette.Scrim)
            .clickable(remember { MutableInteractionSource() }, indication = null) { },
        contentAlignment = Alignment.Center,
        content = content,
    )
}

@Composable
fun DialogCard(modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit) {
    Panel(
        modifier
            .padding(24.dp)
            .widthIn(max = 420.dp)
            .fillMaxWidth(),
        corner = 24.dp,
        content = content,
    )
}
