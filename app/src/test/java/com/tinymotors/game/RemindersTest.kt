package com.tinymotors.game

import com.tinymotors.game.notify.Reminders
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar
import java.util.TimeZone

class RemindersTest {
    private fun at(year: Int, month: Int, day: Int, hour: Int, minute: Int = 0): Long =
        Calendar.getInstance().apply {
            clear()
            set(year, month - 1, day, hour, minute)
        }.timeInMillis

    @Test fun `notes arrive in the late afternoon, 1, 3 and 7 days later`() {
        val zone = TimeZone.getDefault()
        try {
            TimeZone.setDefault(TimeZone.getTimeZone("Europe/London"))
            val leftAt = at(2026, 10, 27, 21, 40)
            assertEquals(at(2026, 10, 28, Reminders.HOUR), Reminders.fireTime(leftAt, 1))
            assertEquals(at(2026, 10, 30, Reminders.HOUR), Reminders.fireTime(leftAt, 3))
            assertEquals(at(2026, 11, 3, Reminders.HOUR), Reminders.fireTime(leftAt, 7))
            // played in the small hours: the first note still waits for the next afternoon
            val night = at(2026, 12, 31, 1, 0)
            assertEquals(at(2027, 1, 1, Reminders.HOUR), Reminders.fireTime(night, 1))
        } finally {
            TimeZone.setDefault(zone)
        }
    }

    @Test fun `only three notes, then they stop`() {
        assertEquals(listOf(1, 3, 7), Reminders.DAYS.toList())
        assertTrue(Reminders.HOUR in 15..19)
    }
}
