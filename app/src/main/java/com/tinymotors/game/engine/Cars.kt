package com.tinymotors.game.engine

/** The 11-car merge ladder. Level n is worth 2^n points. */
object Cars {
    const val MIN_LEVEL = 1
    const val MAX_LEVEL = 11
    const val SUV_LEVEL = 6

    /** Point value of a car at [level]: Kei car (1) = 2 ... Hypercar (11) = 2048. */
    fun value(level: Int): Int {
        require(level in MIN_LEVEL..MAX_LEVEL) { "level out of range: $level" }
        return 1 shl level
    }

    fun canMerge(level: Int): Boolean = level in MIN_LEVEL until MAX_LEVEL
}

/** One car on the board. [id] is stable while the car exists so the UI can animate it. */
data class Car(val id: Int, val level: Int)
