package com.tinymotors.game.engine

/**
 * Once-per-calendar-day gift. Days are plain `yyyymmdd` integers computed by the app from the
 * device's local calendar, so there are no streaks and missing days has no effect.
 */
object DailyGift {
    const val NEVER = 0

    fun dayKey(year: Int, month: Int, dayOfMonth: Int): Int = year * 10_000 + month * 100 + dayOfMonth

    /** A gift is waiting when it has not been claimed on [today] yet. */
    fun isAvailable(lastClaimedDay: Int, today: Int): Boolean = lastClaimedDay != today
}
