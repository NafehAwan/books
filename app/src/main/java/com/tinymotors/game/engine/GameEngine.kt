package com.tinymotors.game.engine

/**
 * All Tiny Motors rules. Pure Kotlin: no Android, no clocks, no global randomness.
 * Every action updates [state] and returns the [GameEvent]s that the UI should celebrate.
 */
class GameEngine(initial: GameState, private val autoSpawn: Boolean = true) {

    var state: GameState = initial
        private set

    // ---------- queries ----------

    /** Level of the car that will drive in next (shown as a preview). */
    val nextSpawnLevel: Int get() = smartSpawnLevel(state.board) ?: state.queuedLevel

    /** True when the board is full, nothing can merge and no tow helpers are left. */
    val isStuck: Boolean get() = isStuck(state)

    /** Helper tow truck is offered once per game, only when stuck. */
    val canUseHelper: Boolean get() = isStuck && !state.helperUsed

    val canUndo: Boolean get() = state.undosLeft > 0 && state.history.isNotEmpty()

    /** A pair of slots holding identical mergeable cars (lowest level first), for idle hints. */
    fun findHintPair(): Pair<Int, Int>? {
        val bySlot = state.board.withIndex().filter { it.value != null && Cars.canMerge(it.value!!.level) }
        return bySlot.groupBy { it.value!!.level }
            .filterValues { it.size >= 2 }
            .minByOrNull { it.key }
            ?.value
            ?.let { it[0].index to it[1].index }
    }

    // ---------- actions ----------

    /** Drives a new car onto a random empty slot as its own undoable action. */
    fun addCar(): List<GameEvent> {
        if (state.isFull) return listOf(GameEvent.BoardFull)
        val before = snapshot()
        val added = spawn() ?: return listOf(GameEvent.BoardFull)
        state = state.copy(history = pushHistory(before))
        return withStuckCheck(listOf(added))
    }

    /**
     * Drag a car from [from] to [to]: empty slot = move, identical car = merge, other car = swap.
     * Returns no events when nothing happens (same slot, empty source).
     */
    fun drag(from: Int, to: Int): List<GameEvent> {
        if (from !in state.board.indices || to !in state.board.indices || from == to) return emptyList()
        val moving = state.board[from] ?: return emptyList()
        val target = state.board[to]
        val before = snapshot()
        val events = when {
            target == null -> {
                state = state.copy(
                    board = state.board.replace(to, moving).replace(from, null),
                    history = pushHistory(before),
                )
                listOf(GameEvent.Moved(moving, from, to))
            }
            target.level == moving.level && Cars.canMerge(moving.level) -> merge(moving, from, to, before)
            else -> {
                state = state.copy(
                    board = state.board.replace(to, moving).replace(from, target),
                    history = pushHistory(before),
                )
                listOf(GameEvent.Swapped(moving, target, from, to))
            }
        }
        // Every move brings a new car (it is part of the same undoable step).
        val arrived = if (autoSpawn) listOfNotNull(spawn()) else emptyList()
        return withStuckCheck(events + arrived)
    }

    /** Hooks the car at [slot] onto the tow truck, costing one tow helper. */
    fun tow(slot: Int): List<GameEvent> {
        val car = state.board.getOrNull(slot) ?: return emptyList()
        if (state.tows <= 0) return listOf(GameEvent.NoTowsLeft)
        val before = snapshot()
        state = state.copy(
            board = state.board.replace(slot, null),
            tows = state.tows - 1,
            history = pushHistory(before),
        )
        val events = mutableListOf<GameEvent>(GameEvent.Towed(car, slot))
        // never leave the child with nothing to merge
        while (autoSpawn && state.carCount < GameRules.MIN_CARS) events += spawn() ?: break
        return events
    }

    /** Takes back the last action. Three per game. Rewards already earned are kept. */
    fun undo(): List<GameEvent> {
        if (!canUndo) return listOf(GameEvent.NoUndo)
        val last = state.history.last()
        state = state.copy(
            board = last.board,
            score = last.score,
            tows = (last.towsBefore + last.rewardTows).coerceAtMost(GameRules.MAX_TOWS),
            queuedLevel = last.queuedLevel,
            rngState = last.rngState,
            nextId = maxOf(state.nextId, last.nextId),
            undosLeft = state.undosLeft - 1,
            history = state.history.dropLast(1),
        )
        return listOf(GameEvent.Undone)
    }

    /** The free helper tow truck: removes the 3 lowest-level cars, once per game, when stuck. */
    fun useHelper(): List<GameEvent> {
        if (!canUseHelper) return emptyList()
        val victims = state.board.withIndex()
            .filter { it.value != null }
            .sortedWith(compareBy({ it.value!!.level }, { it.index }))
            .take(GameRules.HELPER_REMOVES)
            .map { it.index }
        var board = state.board
        victims.forEach { board = board.replace(it, null) }
        state = state.copy(board = board, helperUsed = true, history = emptyList())
        return listOf(GameEvent.HelperTowed(victims))
    }

    /** Daily gift: one extra tow helper (never above the maximum) and some coins. */
    fun giftTow(): List<GameEvent> {
        val added = state.tows < GameRules.MAX_TOWS
        state = state.copy(
            tows = if (added) state.tows + 1 else state.tows,
            coins = state.coins + GameRules.GIFT_COINS,
        )
        return listOf(GameEvent.TowGifted(added, GameRules.GIFT_COINS))
    }

    /** Coins from a reward video or a coin pack. */
    fun addCoins(amount: Int): List<GameEvent> {
        if (amount <= 0) return emptyList()
        state = state.copy(coins = state.coins + amount)
        return listOf(GameEvent.CoinsAdded(amount))
    }

    /** Buys board theme [id] with coins, or switches to it when already owned. */
    fun buyTheme(id: Int): List<GameEvent> {
        if (id !in 0 until Shop.THEME_COUNT) return emptyList()
        if (state.ownsTheme(id)) return selectTheme(id)
        val price = Shop.THEME_PRICES[id].toLong()
        if (state.coins < price) return listOf(GameEvent.NotEnoughCoins(price - state.coins))
        state = state.copy(coins = state.coins - price, ownedThemes = state.ownedThemes or (1 shl id), theme = id)
        return listOf(GameEvent.ThemeBought(id))
    }

    fun selectTheme(id: Int): List<GameEvent> {
        if (!state.ownsTheme(id)) return emptyList()
        state = state.copy(theme = id)
        return listOf(GameEvent.ThemeSelected(id))
    }

    /** Starts a fresh board. Best score, unlocked cars, coins, themes and extra tows are kept. */
    fun newGame(seed: Long): List<GameEvent> {
        state = newGameState(seed, state)
        return listOf(GameEvent.NewGame)
    }

    // ---------- internals ----------

    private fun merge(moving: Car, from: Int, to: Int, before: Snapshot): List<GameEvent> {
        val newLevel = moving.level + 1
        val newCar = Car(state.nextId, newLevel)
        val points = Cars.value(newLevel)
        val firstTime = !state.isUnlocked(newLevel)
        val towGranted = firstTime && state.tows < GameRules.MAX_TOWS
        val reward = if (towGranted) 1 else 0
        val newScore = state.score + points
        val firstHypercar = newLevel == Cars.MAX_LEVEL && !state.hypercarBuilt
        val coins = newLevel + (if (firstTime) GameRules.UNLOCK_COINS else 0) + (if (firstHypercar) GameRules.HYPERCAR_COINS else 0)
        state = state.copy(
            coins = state.coins + coins,
            board = state.board.replace(to, newCar).replace(from, null),
            score = newScore,
            best = maxOf(state.best, newScore),
            tows = state.tows + reward,
            unlockedMask = state.unlockedMask or (1 shl newLevel),
            hypercarBuilt = state.hypercarBuilt || newLevel == Cars.MAX_LEVEL,
            nextId = state.nextId + 1,
            history = pushHistory(before.copy(rewardTows = reward)),
        )
        val events = mutableListOf<GameEvent>(GameEvent.Merged(newCar, to, from, points, coins))
        if (firstTime) events += GameEvent.NewCarUnlocked(newLevel, towGranted)
        if (firstHypercar) events += GameEvent.HypercarBuilt
        return events
    }

    /** Places the next car on a random empty slot (no history entry). Null when the board is full. */
    private fun spawn(): GameEvent.CarAdded? {
        val empties = state.emptySlots
        if (empties.isEmpty()) return null
        val rng = Rng(state.rngState)
        val smart = smartSpawnLevel(state.board)
        val level = smart ?: state.queuedLevel
        val slot = empties[rng.nextInt(empties.size)]
        val car = Car(state.nextId, level)
        val newQueued = if (smart == null) rollLevel(rng, state.isUnlocked(Cars.SUV_LEVEL)) else state.queuedLevel
        state = state.copy(
            board = state.board.replace(slot, car),
            unlockedMask = state.unlockedMask or (1 shl level),
            queuedLevel = newQueued,
            rngState = rng.state,
            nextId = state.nextId + 1,
        )
        return GameEvent.CarAdded(car, slot)
    }

    private fun withStuckCheck(events: List<GameEvent>): List<GameEvent> =
        if (isStuck) events + GameEvent.Stuck else events

    private fun snapshot() = Snapshot(
        board = state.board,
        score = state.score,
        towsBefore = state.tows,
        queuedLevel = state.queuedLevel,
        rngState = state.rngState,
        nextId = state.nextId,
    )

    private fun pushHistory(s: Snapshot): List<Snapshot> = (state.history + s).takeLast(GameRules.HISTORY_LIMIT)

    companion object {
        fun isStuck(state: GameState): Boolean =
            state.isFull && state.tows <= 0 && !hasMergeablePair(state.board)

        fun hasMergeablePair(board: List<Car?>): Boolean {
            val seen = BooleanArray(Cars.MAX_LEVEL + 1)
            for (car in board) {
                if (car == null || !Cars.canMerge(car.level)) continue
                if (seen[car.level]) return true
                seen[car.level] = true
            }
            return false
        }

        /**
         * Smart spawn: when no two cars share a level (Hypercars ignored), the next car copies the
         * lowest level on the board so a merge is always possible. Returns null when it does not apply.
         */
        fun smartSpawnLevel(board: List<Car?>): Int? {
            val levels = board.mapNotNull { it?.level }.filter { Cars.canMerge(it) }
            if (levels.isEmpty() || levels.size != levels.toSet().size) return null
            return levels.min()
        }

        /** Random car: level 1 at 85% and level 2 at 15%; after the SUV: 78% / 15% / 7% for level 3. */
        fun rollLevel(rng: Rng, suvUnlocked: Boolean): Int {
            val d = rng.nextInt(100)
            return when {
                suvUnlocked && d < GameRules.LEVEL_3_PERCENT_AFTER_SUV -> 3
                suvUnlocked && d < GameRules.LEVEL_3_PERCENT_AFTER_SUV + GameRules.LEVEL_2_PERCENT -> 2
                !suvUnlocked && d < GameRules.LEVEL_2_PERCENT -> 2
                else -> 1
            }
        }

        /** A new game. [previous] carries over best score, unlocked cars and extra tows. */
        fun newGameState(seed: Long, previous: GameState? = null): GameState {
            val rng = Rng(seed)
            val board = MutableList<Car?>(GameRules.SLOTS) { null }
            val slots = (0 until GameRules.SLOTS).toMutableList()
            var id = previous?.nextId ?: 1
            for (level in GameRules.START_CARS) {
                val pick = slots.removeAt(rng.nextInt(slots.size))
                board[pick] = Car(id++, level)
            }
            var mask = previous?.unlockedMask ?: 0
            GameRules.START_CARS.forEach { mask = mask or (1 shl it) }
            val queued = rollLevel(rng, mask and (1 shl Cars.SUV_LEVEL) != 0)
            return GameState(
                board = board,
                score = 0,
                best = previous?.best ?: 0,
                tows = maxOf(GameRules.START_TOWS, previous?.tows ?: 0),
                undosLeft = GameRules.START_UNDOS,
                unlockedMask = mask,
                helperUsed = false,
                hypercarBuilt = false,
                queuedLevel = queued,
                rngState = rng.state,
                nextId = id,
                history = emptyList(),
                coins = previous?.coins ?: 0,
                ownedThemes = previous?.ownedThemes ?: 1,
                theme = previous?.theme ?: 0,
            )
        }
    }
}

private fun <T> List<T>.replace(index: Int, value: T): List<T> =
    toMutableList().also { it[index] = value }
