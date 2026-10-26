package com.tinymotors.game

import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.mutableStateOf
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.tinymotors.game.ui.TinyMotorsApp

class MainActivity : ComponentActivity() {

    private val vm: GameViewModel by viewModels()
    private val reduceMotion = mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        val splash = installSplashScreen()
        super.onCreate(savedInstanceState)
        splash.setKeepOnScreenCondition { !vm.ui.value.loaded }
        enableEdgeToEdge()
        reduceMotion.value = animationsOff()
        setContent { TinyMotorsApp(vm, reduceMotion.value) }
    }

    override fun onStart() {
        super.onStart()
        vm.onForeground()
    }

    override fun onResume() {
        super.onResume()
        reduceMotion.value = animationsOff()
    }

    override fun onStop() {
        vm.onBackground()
        super.onStop()
    }

    /** The system "Remove animations" setting sets the animator scale to 0. */
    private fun animationsOff(): Boolean =
        Settings.Global.getFloat(contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
}
