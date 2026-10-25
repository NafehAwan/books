package com.tinymotors.game.ui.theme

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import com.tinymotors.game.R

object Palette {
    val SkyTop = Color(0xFFCDEBFF)
    val SkyBottom = Color(0xFFEFF6FB)
    val Cloud = Color(0xFFFFFFFF)
    val Panel = Color(0xFFFFFFFF)
    val PanelShade = Color(0xFFD5E3F0)
    val Slot = Color(0xFFE8F0F8)
    val SlotEdge = Color(0xFFD3DFEB)
    val Ink = Color(0xFF2B3A55)
    val InkSoft = Color(0xFF6B7A99)
    val Scrim = Color(0x992B3A55)

    val Green = Color(0xFF4CD07D)
    val GreenShade = Color(0xFF2FA65C)
    val Glow = Color(0xFF3DDC84)
    val Orange = Color(0xFFFFA62B)
    val OrangeShade = Color(0xFFDB7E00)
    val Blue = Color(0xFF45ADF7)
    val BlueShade = Color(0xFF1F7FC8)
    val Purple = Color(0xFFA084F0)
    val PurpleShade = Color(0xFF7457CC)
    val Pink = Color(0xFFFF7BAC)
    val PinkShade = Color(0xFFD9507F)
    val Grey = Color(0xFFB8C4D4)
    val GreyShade = Color(0xFF8E9BB0)
    val Gold = Color(0xFFFFC83D)
    val Locked = Color(0xFF3A4660)
}

val Fredoka = FontFamily(
    Font(R.font.fredoka_medium, FontWeight.Medium),
    Font(R.font.fredoka_bold, FontWeight.Bold),
)

fun kidText(
    size: TextUnit = 18.sp,
    color: Color = Palette.Ink,
    bold: Boolean = true,
    align: TextAlign = TextAlign.Center,
) = TextStyle(
    fontFamily = Fredoka,
    fontWeight = if (bold) FontWeight.Bold else FontWeight.Medium,
    fontSize = size,
    color = color,
    textAlign = align,
)

/** True when the system "Remove animations" setting is on: the game then keeps motion to a minimum. */
val LocalReduceMotion = staticCompositionLocalOf { false }
