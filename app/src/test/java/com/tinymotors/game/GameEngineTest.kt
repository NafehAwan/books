package com.tinymotors.game

import com.tinymotors.game.engine.Cars
import com.tinymotors.game.engine.GameEngine
import com.tinymotors.game.engine.GameEvent
import com.tinymotors.game.engine.GameRules
import com.tinymotors.game.engine.Rng
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GameEngineTest {

    @Test fun `new game has seven cars, five level 1 and two level 2`() {
        val s = GameEngine.newGameState(seed = 1)
        val levels = s.board.filterNotNull().map { it.level }.sorted()
        assertEquals(listOf(1, 1, 1, 1, 1, 2, 2), levels)
        assertEquals(30, s.board.size)
        assertEquals(3, s.tows)
        assertEquals(3, s.undosLeft)
        assertEquals(0L, s.score)
        assertTrue(s.isUnlocked(1) && s.isUnlocked(2))
        assertFalse(s.isUnlocked(3))
    }

    @Test fun `car values double up the ladder`() {
        assertEquals(2, Cars.value(1))
        assertEquals(4, Cars.value(2))
        assertEquals(2048, Cars.value(11))
    }

    @Test fun `same seed gives the same game`() {
        val a = GameEngine(GameEngine.newGameState(7))
        val b = GameEngine(GameEngine.newGameState(7))
        repeat(10) { a.addCar(); b.addCar() }
        assertEquals(a.state, b.state)
    }

    @Test fun `dragging onto an identical car merges into the next level`() {
        val e = GameEngine(stateWith(listOf(1, 1)))
        val events = e.drag(0, 1)
        val merged = events.filterIsInstance<GameEvent.Merged>().single()
        assertEquals(2, merged.car.level)
        assertEquals(1, merged.slot)
        assertNull(e.state.board[0])
        assertEquals(2, e.state.board[1]!!.level)
        assertEquals(4L, e.state.score)
        assertEquals(4L, e.state.best)
    }

    @Test fun `dragging onto an empty slot moves the car`() {
        val e = GameEngine(stateWith(listOf(3, 0)))
        val id = e.state.board[0]!!.id
        val events = e.drag(0, 1)
        assertTrue(events.single() is GameEvent.Moved)
        assertNull(e.state.board[0])
        assertEquals(id, e.state.board[1]!!.id)
        assertEquals(0L, e.state.score)
    }

    @Test fun `dragging onto a different car swaps them`() {
        val e = GameEngine(stateWith(listOf(1, 2)))
        val events = e.drag(0, 1)
        assertTrue(events.single() is GameEvent.Swapped)
        assertEquals(2, e.state.board[0]!!.level)
        assertEquals(1, e.state.board[1]!!.level)
    }

    @Test fun `hypercars cannot merge, they swap instead`() {
        val e = GameEngine(stateWith(listOf(11, 11), unlockedLevels = (1..11).toList()))
        val events = e.drag(0, 1)
        assertTrue(events.single() is GameEvent.Swapped)
        assertEquals(listOf(11, 11), e.state.board.take(2).map { it!!.level })
    }

    @Test fun `dropping on itself or from an empty slot does nothing`() {
        val e = GameEngine(stateWith(listOf(1, 0)))
        val before = e.state
        assertTrue(e.drag(0, 0).isEmpty())
        assertTrue(e.drag(1, 0).isEmpty())
        assertTrue(e.drag(0, 99).isEmpty())
        assertEquals(before, e.state)
    }

    @Test fun `first time a level is reached grants a tow and an unlock event`() {
        val e = GameEngine(stateWith(listOf(2, 2), tows = 3))
        val events = e.drag(0, 1)
        val unlock = events.filterIsInstance<GameEvent.NewCarUnlocked>().single()
        assertEquals(3, unlock.level)
        assertTrue(unlock.towGranted)
        assertEquals(4, e.state.tows)
        assertTrue(e.state.isUnlocked(3))
    }

    @Test fun `unlock reward only happens once and never above five tows`() {
        val e = GameEngine(stateWith(listOf(2, 2, 2, 2), tows = 5))
        val first = e.drag(0, 1)
        assertFalse(first.filterIsInstance<GameEvent.NewCarUnlocked>().single().towGranted)
        assertEquals(5, e.state.tows)
        val second = e.drag(2, 3)
        assertTrue(second.none { it is GameEvent.NewCarUnlocked })
    }

    @Test fun `building the hypercar triggers the win once`() {
        val e = GameEngine(stateWith(listOf(10, 10, 10, 10), unlockedLevels = (1..10).toList()))
        assertTrue(e.drag(0, 1).any { it is GameEvent.HypercarBuilt })
        assertTrue(e.state.hypercarBuilt)
        assertEquals(2048L, e.state.score)
        assertTrue(e.drag(2, 3).none { it is GameEvent.HypercarBuilt })
    }

    @Test fun `add car fills a random empty slot and adds nothing on a full board`() {
        val e = GameEngine(stateWith(listOf(1, 1)))
        val ev = e.addCar().filterIsInstance<GameEvent.CarAdded>().single()
        assertEquals(3, e.state.board.count { it != null })
        assertNotNull(e.state.board[ev.slot])
        val full = GameEngine(stateWith(List(30) { 1 }))
        assertEquals(listOf(GameEvent.BoardFull), full.addCar())
        assertEquals(30, full.state.board.count { it != null })
    }

    @Test fun `rapid add car taps fill the board then stop`() {
        val e = GameEngine(GameEngine.newGameState(3))
        repeat(100) { e.addCar() }
        assertTrue(e.state.isFull)
        assertEquals(30, e.state.board.mapNotNull { it?.id }.toSet().size)
    }

    @Test fun `spawn odds are 85 to 15 before the SUV`() {
        val rng = Rng(12345)
        val counts = IntArray(4)
        val n = 100_000
        repeat(n) { counts[GameEngine.rollLevel(rng, suvUnlocked = false)]++ }
        assertEquals(0.85, counts[1] / n.toDouble(), 0.01)
        assertEquals(0.15, counts[2] / n.toDouble(), 0.01)
        assertEquals(0, counts[3])
    }

    @Test fun `after the SUV there is a 7 percent chance of level 3`() {
        val rng = Rng(999)
        val counts = IntArray(4)
        val n = 100_000
        repeat(n) { counts[GameEngine.rollLevel(rng, suvUnlocked = true)]++ }
        assertEquals(0.78, counts[1] / n.toDouble(), 0.01)
        assertEquals(0.15, counts[2] / n.toDouble(), 0.01)
        assertEquals(0.07, counts[3] / n.toDouble(), 0.01)
    }

    @Test fun `smart spawn copies the lowest level when no pair exists`() {
        val e = GameEngine(stateWith(listOf(3, 5, 4, 11)))
        assertEquals(3, e.nextSpawnLevel)
        val added = e.addCar().filterIsInstance<GameEvent.CarAdded>().single()
        assertEquals(3, added.car.level)
        // Hypercars are ignored, so two of them do not count as a pair.
        assertEquals(2, GameEngine.smartSpawnLevel(stateWith(listOf(11, 11, 2)).board))
        // With a pair on the board, smart spawn does not apply.
        assertNull(GameEngine.smartSpawnLevel(stateWith(listOf(4, 4, 6)).board))
        assertNull(GameEngine.smartSpawnLevel(stateWith(listOf(11)).board))
    }

    @Test fun `preview matches the car that actually arrives`() {
        val e = GameEngine(GameEngine.newGameState(11))
        repeat(20) {
            val preview = e.nextSpawnLevel
            val added = e.addCar().filterIsInstance<GameEvent.CarAdded>().firstOrNull() ?: return
            assertEquals(preview, added.car.level)
        }
    }

    @Test fun `stuck means full, no pair and no tows`() {
        val noPairs = fullBoardNoPairs()
        assertTrue(GameEngine(stateWith(noPairs, tows = 0)).isStuck)
        assertFalse("tows left", GameEngine(stateWith(noPairs, tows = 1)).isStuck)
        val withPair = noPairs.toMutableList().also { it[1] = 1 }
        assertFalse("pair exists", GameEngine(stateWith(withPair, tows = 0)).isStuck)
        val notFull = noPairs.toMutableList().also { it[29] = 0 }
        assertFalse("space left", GameEngine(stateWith(notFull, tows = 0)).isStuck)
    }

    @Test fun `smart spawn keeps a full board mergeable`() {
        // Levels 1..10 once each plus Hypercars, one gap: smart spawn copies level 1, so a pair exists.
        val e = GameEngine(stateWith(fullBoardNoPairs().toMutableList().also { it[29] = 0 }, tows = 0))
        val events = e.addCar()
        assertTrue(e.state.isFull)
        assertFalse(events.contains(GameEvent.Stuck))
        assertFalse(e.isStuck)
    }

    @Test fun `adding the last car onto a board of hypercars reports stuck`() {
        val e = GameEngine(stateWith(List(29) { 11 } + 0, tows = 0, unlockedLevels = (1..11).toList()))
        val events = e.addCar()
        assertTrue(events.contains(GameEvent.Stuck))
        assertTrue(e.isStuck)
        assertTrue(e.canUseHelper)
    }

    @Test fun `tow removes a car and costs one helper`() {
        val e = GameEngine(stateWith(listOf(4, 1), tows = 2))
        val events = e.tow(0)
        assertTrue(events.single() is GameEvent.Towed)
        assertNull(e.state.board[0])
        assertEquals(1, e.state.tows)
    }

    @Test fun `tow with no helpers left does nothing`() {
        val e = GameEngine(stateWith(listOf(4), tows = 0))
        assertEquals(listOf(GameEvent.NoTowsLeft), e.tow(0))
        assertNotNull(e.state.board[0])
        assertTrue(e.tow(5).isEmpty()) // empty slot
    }

    @Test fun `undo restores the board and is limited to three per game`() {
        val e = GameEngine(stateWith(listOf(1, 1, 2, 2, 3, 3, 4, 4)))
        val start = e.state.board
        e.drag(0, 1)
        e.undo()
        assertEquals(start, e.state.board)
        assertEquals(0L, e.state.score)
        e.drag(2, 3); e.undo()
        e.drag(4, 5); e.undo()
        assertEquals(0, e.state.undosLeft)
        e.drag(6, 7)
        assertEquals(listOf(GameEvent.NoUndo), e.undo())
        assertEquals(5, e.state.board[7]!!.level)
    }

    @Test fun `undo of a tow gives the tow back`() {
        val e = GameEngine(stateWith(listOf(4), tows = 2))
        e.tow(0)
        e.undo()
        assertEquals(2, e.state.tows)
        assertEquals(4, e.state.board[0]!!.level)
    }

    @Test fun `undo keeps an unlock reward`() {
        val e = GameEngine(stateWith(listOf(2, 2), tows = 3))
        e.drag(0, 1)
        assertEquals(4, e.state.tows)
        e.undo()
        assertEquals(4, e.state.tows)
        assertTrue(e.state.isUnlocked(3))
    }

    @Test fun `undo of add car gives the same car again`() {
        val e = GameEngine(GameEngine.newGameState(5))
        val first = e.addCar().filterIsInstance<GameEvent.CarAdded>().single()
        e.undo()
        val again = e.addCar().filterIsInstance<GameEvent.CarAdded>().single()
        assertEquals(first.slot, again.slot)
        assertEquals(first.car.level, again.car.level)
    }

    @Test fun `helper tow truck removes the three lowest cars once per game`() {
        val board = fullBoardNoPairs()
        val e = GameEngine(stateWith(board, tows = 0))
        assertTrue(e.canUseHelper)
        val ev = e.useHelper().single() as GameEvent.HelperTowed
        assertEquals(listOf(0, 1, 2), ev.slots) // levels 1, 2, 3
        assertEquals(27, e.state.board.count { it != null })
        assertTrue(e.state.helperUsed)
        assertFalse(e.canUseHelper)
        assertTrue(e.useHelper().isEmpty())
    }

    @Test fun `helper is only offered when stuck`() {
        val e = GameEngine(stateWith(listOf(1, 1), tows = 0))
        assertFalse(e.canUseHelper)
        assertTrue(e.useHelper().isEmpty())
    }

    @Test fun `new game keeps best score, unlocked cars and extra tows`() {
        val e = GameEngine(stateWith(listOf(5, 5), tows = 5, unlockedLevels = (1..5).toList()))
        e.drag(0, 1)
        val best = e.state.best
        e.newGame(seed = 77)
        assertEquals(0L, e.state.score)
        assertEquals(best, e.state.best)
        assertTrue(e.state.isUnlocked(6))
        assertEquals(5, e.state.tows)
        assertEquals(GameRules.START_UNDOS, e.state.undosLeft)
        assertFalse(e.state.helperUsed)
        assertEquals(7, e.state.board.count { it != null })
    }

    @Test fun `gift adds a tow up to five`() {
        val e = GameEngine(stateWith(listOf(1), tows = 4))
        assertEquals(listOf(GameEvent.TowGifted(true)), e.giftTow())
        assertEquals(5, e.state.tows)
        assertEquals(listOf(GameEvent.TowGifted(false)), e.giftTow())
        assertEquals(5, e.state.tows)
    }

    @Test fun `hint finds the lowest mergeable pair`() {
        val e = GameEngine(stateWith(listOf(4, 2, 4, 2, 11, 11)))
        assertEquals(1 to 3, e.findHintPair())
        assertNull(GameEngine(stateWith(listOf(11, 11, 3))).findHintPair())
    }
}
