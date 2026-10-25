package com.tinymotors.game.ui

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tinymotors.game.GameViewModel
import com.tinymotors.game.Screen
import com.tinymotors.game.ui.theme.LocalReduceMotion

@Composable
fun TinyMotorsApp(vm: GameViewModel, reduceMotion: Boolean) {
    val state by vm.ui.collectAsStateWithLifecycle()
    val particles = remember { ParticleSystem() }
    CompositionLocalProvider(LocalCarArt provides vm.art, LocalReduceMotion provides reduceMotion) {
        // Back goes to the home screen; on the home screen it leaves the app as usual.
        BackHandler(enabled = state.screen != Screen.Home) { vm.back() }
        if (!state.loaded) {
            SkyBackground()
            return@CompositionLocalProvider
        }
        when (state.screen) {
            Screen.Home -> HomeScreen(state, vm)
            Screen.Game -> GameScreen(state, vm, particles)
            Screen.Garage -> GarageScreen(state, vm)
            Screen.Settings -> SettingsScreen(state, vm)
        }
    }
}
