package com.tinymotors.game.data

/** Parent/child settings. Everything is on by default; music plays quietly. */
data class Settings(
    val sound: Boolean = true,
    val music: Boolean = true,
    val vibration: Boolean = true,
    val voice: Boolean = true,
    val hints: Boolean = true,
)
