package com.tinymotors.game.engine

/** Everything needed to undo one action. */
data class Snapshot(
    val board: List<Car?>,
    val score: Long,
    val towsBefore: Int,
    val queuedLevel: Int,
    val rngState: Long,
    val nextId: Int,
    /** Tows granted by the action this snapshot precedes (kept when undoing, so rewards are never taken back). */
    val rewardTows: Int = 0,
)

/** The full, persistable game state. Immutable; [GameEngine] produces new copies. */
data class GameState(
    val board: List<Car?>,
    val score: Long,
    val best: Long,
    val tows: Int,
    val undosLeft: Int,
    /** Bit n is set when car level n has ever been reached. Kept across games. */
    val unlockedMask: Int,
    val helperUsed: Boolean,
    val hypercarBuilt: Boolean,
    /** Pre-rolled random level for the next car (used when smart spawn does not apply). */
    val queuedLevel: Int,
    val rngState: Long,
    val nextId: Int,
    val history: List<Snapshot>,
    /** Coins earned by playing (or bought). Kept across games. */
    val coins: Long = 0,
    /** Bit n is set when board theme n is owned. Theme 0 is free. */
    val ownedThemes: Int = 1,
    /** The board theme in use. */
    val theme: Int = 0,
) {
    init {
        require(board.size == GameRules.SLOTS) { "board must have ${GameRules.SLOTS} slots" }
    }

    fun isUnlocked(level: Int): Boolean = unlockedMask and (1 shl level) != 0

    val unlockedCount: Int get() = (Cars.MIN_LEVEL..Cars.MAX_LEVEL).count { isUnlocked(it) }
    fun ownsTheme(id: Int): Boolean = ownedThemes and (1 shl id) != 0
    val carCount: Int get() = board.count { it != null }
    val emptySlots: List<Int> get() = board.indices.filter { board[it] == null }
    val isFull: Boolean get() = board.all { it != null }
}

object GameRules {
    const val COLUMNS = 5
    const val ROWS = 6
    const val SLOTS = COLUMNS * ROWS
    const val START_TOWS = 3
    const val MAX_TOWS = 5
    const val START_UNDOS = 3
    const val HISTORY_LIMIT = 10
    const val HELPER_REMOVES = 3
    val START_CARS = listOf(1, 1, 1, 1, 1, 2, 2)

    /** Coins: a merge pays the new car's level; firsts pay a bonus. */
    const val UNLOCK_COINS = 20
    const val HYPERCAR_COINS = 100
    const val GIFT_COINS = 25

    /** With automatic cars, the board never drops below this many cars. */
    const val MIN_CARS = 2

    /** Percent chances for a random new car. */
    const val LEVEL_2_PERCENT = 15
    const val LEVEL_3_PERCENT_AFTER_SUV = 7
}

/** Board themes sold for coins. The UI owns the colours; the engine only knows ids and prices. */
object Shop {
    val THEME_PRICES = intArrayOf(0, 200, 400, 600, 900)
    val THEME_COUNT get() = THEME_PRICES.size
}
