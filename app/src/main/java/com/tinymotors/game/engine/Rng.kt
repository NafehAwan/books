package com.tinymotors.game.engine

/**
 * Small seedable random number generator (SplitMix64). Its whole state is one Long,
 * so it can be saved with the game and restored after process death.
 */
class Rng(var state: Long) {

    fun nextLong(): Long {
        state += GOLDEN_GAMMA
        var z = state
        z = (z xor (z ushr 30)) * -0x40a7b892e31b1a47L
        z = (z xor (z ushr 27)) * -0x6b2fb644ecceee15L
        return z xor (z ushr 31)
    }

    /** Uniform int in [0, bound). */
    fun nextInt(bound: Int): Int {
        require(bound > 0) { "bound must be positive" }
        return ((nextLong() ushr 1) % bound).toInt()
    }

    private companion object {
        const val GOLDEN_GAMMA = -0x61c8864680b583ebL
    }
}
