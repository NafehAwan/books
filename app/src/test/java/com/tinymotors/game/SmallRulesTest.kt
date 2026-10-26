package com.tinymotors.game

import com.tinymotors.game.engine.ComboTracker
import com.tinymotors.game.engine.DailyGift
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SmallRulesTest {

    @Test fun `merges within two seconds build a combo`() {
        val c = ComboTracker()
        assertEquals(1, c.onMerge(1_000))
        assertEquals(2, c.onMerge(2_500))
        assertEquals(3, c.onMerge(4_400))
        assertEquals(1, c.onMerge(6_500)) // 2.1 s later: combo over
        c.reset()
        assertEquals(1, c.onMerge(6_600))
    }

    @Test fun `daily gift is available once per calendar day`() {
        val oct24 = DailyGift.dayKey(2026, 10, 24)
        val oct25 = DailyGift.dayKey(2026, 10, 25)
        assertEquals(20261025, oct25)
        assertTrue(DailyGift.isAvailable(DailyGift.NEVER, oct25))
        assertFalse(DailyGift.isAvailable(oct25, oct25))
        assertTrue(DailyGift.isAvailable(oct24, oct25))
    }

    @Test fun `missing days has no penalty and month and year changes count as new days`() {
        assertTrue(DailyGift.isAvailable(DailyGift.dayKey(2026, 1, 3), DailyGift.dayKey(2026, 3, 1)))
        assertTrue(DailyGift.isAvailable(DailyGift.dayKey(2026, 12, 31), DailyGift.dayKey(2027, 1, 1)))
    }
}
