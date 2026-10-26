package com.tinymotors.game.engine

/** Merges within [windowMillis] of each other build a combo (x2, x3, ...). Times are passed in for testing. */
class ComboTracker(private val windowMillis: Long = 2_000L) {
    private var count = 0
    private var lastMergeAt = Long.MIN_VALUE

    /** Records a merge at [nowMillis] and returns the current combo count (1 = no combo yet). */
    fun onMerge(nowMillis: Long): Int {
        count = if (lastMergeAt != Long.MIN_VALUE && nowMillis - lastMergeAt in 0..windowMillis) count + 1 else 1
        lastMergeAt = nowMillis
        return count
    }

    fun reset() {
        count = 0
        lastMergeAt = Long.MIN_VALUE
    }
}
