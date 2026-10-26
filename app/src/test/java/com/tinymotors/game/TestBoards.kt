package com.tinymotors.game

import com.tinymotors.game.engine.Car
import com.tinymotors.game.engine.GameEngine
import com.tinymotors.game.engine.GameRules
import com.tinymotors.game.engine.GameState

/** Builds a state from a list of levels (0 = empty), padded to 30 slots. */
fun stateWith(
    levels: List<Int>,
    tows: Int = GameRules.START_TOWS,
    unlockedLevels: List<Int> = listOf(1, 2),
    seed: Long = 42L,
): GameState {
    val base = GameEngine.newGameState(seed)
    var id = 100
    val board = List(GameRules.SLOTS) { i ->
        val lv = levels.getOrElse(i) { 0 }
        if (lv == 0) null else Car(id++, lv)
    }
    var mask = 0
    unlockedLevels.forEach { mask = mask or (1 shl it) }
    return base.copy(board = board, tows = tows, unlockedMask = mask, nextId = id)
}

/** A full board where no two mergeable cars match. */
fun fullBoardNoPairs(): List<Int> {
    // Only 10 mergeable levels exist, so a 30-slot board with no pair must use Hypercars (level 11) for the rest.
    return (1..10).toList() + List(20) { 11 }
}
