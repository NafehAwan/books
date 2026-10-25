package com.tinymotors.game.ui.theme

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import com.tinymotors.game.R

/** Colours for one board theme sold in the Store. Index = theme id in the engine's Shop. */
data class BoardTheme(
    val nameRes: Int,
    val skyTop: Color,
    val skyBottom: Color,
    val cloud: Color,
    val hillFar: Color,
    val hill: Color,
    val tree: Color,
    val treeDark: Color,
    val trunk: Color,
    val flower: Color,
    val flowerCenter: Color,
    val panel: Color,
    val panelShade: Color,
    val slot: Color,
    val slotEdge: Color,
    val stars: Boolean = false,
)

val BoardThemes = listOf(
    BoardTheme(
        R.string.theme_park, Palette.SkyTop, Palette.SkyBottom, Color.White, Palette.HillFar, Palette.Hill,
        Palette.Tree, Palette.TreeDark, Palette.Trunk, Color.White, Palette.Gold,
        Palette.Panel, Palette.PanelShade, Palette.Slot, Palette.SlotEdge,
    ),
    BoardTheme(
        R.string.theme_beach, Color(0xFF9FE3F5), Color(0xFFE9F9FC), Color.White, Color(0xFF5CC8E0), Color(0xFFF6DFA4),
        Color(0xFF3FBF8F), Color(0xFF2E9C73), Color(0xFFB98A5A), Color(0xFFFF9EB5), Color(0xFFFFF1A8),
        Color(0xFFFFFDF6), Color(0xFFEBDDB8), Color(0xFFFFF3D6), Color(0xFFEFDDAE),
    ),
    BoardTheme(
        R.string.theme_snow, Color(0xFFC9DDF2), Color(0xFFF4F8FC), Color.White, Color(0xFFE3EEF9), Color(0xFFFFFFFF),
        Color(0xFF3E9A6E), Color(0xFF2F7D57), Color(0xFF8A6A4E), Color(0xFFBFD9F2), Color(0xFFFFFFFF),
        Color(0xFFFFFFFF), Color(0xFFCBDDEE), Color(0xFFE9F2FB), Color(0xFFD2E2F2),
    ),
    BoardTheme(
        R.string.theme_candy, Color(0xFFFFC9E3), Color(0xFFFFF0F7), Color.White, Color(0xFFC7F2DF), Color(0xFFA6EBCB),
        Color(0xFFFF8CC6), Color(0xFFB98CF0), Color(0xFFFFFFFF), Color(0xFFFFE066), Color(0xFFFF6FA8),
        Color(0xFFFFFFFF), Color(0xFFF3CDE2), Color(0xFFFFEFF7), Color(0xFFF6D6E8),
    ),
    BoardTheme(
        R.string.theme_night, Color(0xFF1E2A5A), Color(0xFF4A5C9A), Color(0x66FFFFFF), Color(0xFF2D5B4A), Color(0xFF3D7558),
        Color(0xFF2F6B4D), Color(0xFF24543C), Color(0xFF6E5444), Color(0xFFFFF3A8), Color(0xFFFFC83D),
        Color(0xFFF2F5FF), Color(0xFF9AA8D6), Color(0xFFE1E7FA), Color(0xFFC9D2F0),
        stars = true,
    ),
)

val LocalBoardTheme = staticCompositionLocalOf { BoardThemes[0] }
