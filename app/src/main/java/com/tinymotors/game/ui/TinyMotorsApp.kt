package com.tinymotors.game.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tinymotors.game.GameViewModel
import com.tinymotors.game.Screen
import com.tinymotors.game.ui.theme.BoardThemes
import com.tinymotors.game.ui.theme.LocalBoardTheme
import com.tinymotors.game.ui.theme.LocalReduceMotion

@Composable
fun TinyMotorsApp(vm: GameViewModel, reduceMotion: Boolean) {
    val state by vm.ui.collectAsStateWithLifecycle()
    val particles = remember { ParticleSystem() }
    val theme = BoardThemes[(state.game?.theme ?: 0).coerceIn(0, BoardThemes.size - 1)]
    val worn = state.game?.wornSkins ?: 0
    val art = remember(vm.art, worn) { vm.art.withSkins(worn) }
    CompositionLocalProvider(LocalCarArt provides art, LocalReduceMotion provides reduceMotion, LocalBoardTheme provides theme) {
        // Back goes to the home screen; on the home screen it leaves the app as usual.
        BackHandler(enabled = state.screen != Screen.Home) { vm.back() }
        if (!state.loaded) {
            SkyBackground()
            return@CompositionLocalProvider
        }
        AnimatedContent(
            targetState = state.screen,
            transitionSpec = { screenTransition(reduceMotion) },
            label = "screen",
        ) { screen ->
            when (screen) {
                Screen.Home -> HomeScreen(state, vm)
                Screen.Game -> GameScreen(state, vm, particles)
                Screen.Garage -> GarageScreen(state, vm)
                Screen.Settings -> SettingsScreen(state, vm)
                Screen.Store -> StoreScreen(state, vm)
            }
        }
    }
}

/**
 * The game zooms out of the PLAY button; the other screens slide in from the right and slide back
 * out when going home. With "Remove animations" on, screens only cross-fade quickly.
 */
private fun AnimatedContentTransitionScope<Screen>.screenTransition(reduceMotion: Boolean): ContentTransform {
    if (reduceMotion) return fadeIn(tween(120)) togetherWith fadeOut(tween(120))
    val ms = 380
    val ease = FastOutSlowInEasing
    val fade = tween<Float>(ms / 2)
    return when {
        targetState == Screen.Game ->
            (scaleIn(tween(ms, easing = ease), initialScale = 0.82f) + fadeIn(fade)) togetherWith
                (scaleOut(tween(ms, easing = ease), targetScale = 1.12f) + fadeOut(fade))
        initialState == Screen.Game ->
            (scaleIn(tween(ms, easing = ease), initialScale = 1.12f) + fadeIn(fade)) togetherWith
                (scaleOut(tween(ms, easing = ease), targetScale = 0.82f) + fadeOut(fade))
        targetState == Screen.Home ->
            (slideInHorizontally(tween(ms, easing = ease)) { -it / 4 } + fadeIn(fade)) togetherWith
                (slideOutHorizontally(tween(ms, easing = ease)) { it } + fadeOut(fade))
        else ->
            (slideInHorizontally(tween(ms, easing = ease)) { it } + fadeIn(fade)) togetherWith
                (slideOutHorizontally(tween(ms, easing = ease)) { -it / 4 } + fadeOut(fade))
    }
}
