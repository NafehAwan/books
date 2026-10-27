package com.tinymotors.game.engine

/**
 * Compact text encoding of [GameState] for storage. Format version 1:
 * one `key=value` per line; boards are comma-separated `id:level` or `_` for empty.
 */
object GameStateCodec {
    private const val VERSION = "1"

    fun encode(s: GameState): String = buildString {
        line("v", VERSION)
        line("board", encodeBoard(s.board))
        line("score", s.score)
        line("best", s.best)
        line("tows", s.tows)
        line("undos", s.undosLeft)
        line("unlocked", s.unlockedMask)
        line("helper", s.helperUsed)
        line("hyper", s.hypercarBuilt)
        line("queued", s.queuedLevel)
        line("rng", s.rngState)
        line("nextId", s.nextId)
        line("history", s.history.joinToString("|") { encodeSnapshot(it) })
        line("coins", s.coins)
        line("themes", s.ownedThemes)
        line("theme", s.theme)
        line("skins", s.ownedSkins)
        line("worn", s.wornSkins)
    }

    /** Returns null for missing, unknown or damaged data (the app then starts a new game). */
    fun decode(text: String?): GameState? {
        if (text.isNullOrBlank()) return null
        return try {
            val map = text.lineSequence().filter { '=' in it }
                .associate { it.substringBefore('=') to it.substringAfter('=') }
            if (map["v"] != VERSION) return null
            val state = GameState(
                board = decodeBoard(map.getValue("board")),
                score = map.getValue("score").toLong(),
                best = map.getValue("best").toLong(),
                tows = map.getValue("tows").toInt().coerceIn(0, GameRules.MAX_TOWS),
                undosLeft = map.getValue("undos").toInt().coerceIn(0, GameRules.START_UNDOS),
                unlockedMask = map.getValue("unlocked").toInt(),
                helperUsed = map.getValue("helper").toBooleanStrict(),
                hypercarBuilt = map.getValue("hyper").toBooleanStrict(),
                queuedLevel = map.getValue("queued").toInt().coerceIn(Cars.MIN_LEVEL, 3),
                rngState = map.getValue("rng").toLong(),
                nextId = map.getValue("nextId").toInt(),
                history = map["history"].orEmpty().split('|').filter { it.isNotEmpty() }.map { decodeSnapshot(it) },
                coins = map["coins"]?.toLong()?.coerceAtLeast(0) ?: 0,
                ownedThemes = (map["themes"]?.toInt() ?: 1) or 1,
                theme = map["theme"]?.toInt()?.coerceIn(0, Shop.THEME_COUNT - 1) ?: 0,
            )
            val owned = (map["skins"]?.toInt() ?: 0) and Skins.ALL_BITS
            state.copy(ownedSkins = owned, wornSkins = Skins.cleanWorn(map["worn"]?.toInt() ?: 0, owned))
        } catch (e: IllegalArgumentException) {
            null
        } catch (e: NoSuchElementException) {
            null
        } catch (e: IndexOutOfBoundsException) {
            null
        }
    }

    private fun StringBuilder.line(key: String, value: Any) {
        append(key).append('=').append(value).append('\n')
    }

    private fun encodeBoard(board: List<Car?>): String =
        board.joinToString(",") { c -> if (c == null) "_" else "${c.id}:${c.level}" }

    private fun decodeBoard(text: String): List<Car?> {
        val cells = text.split(',')
        require(cells.size == GameRules.SLOTS) { "bad board size" }
        return cells.map { cell ->
            if (cell == "_") {
                null
            } else {
                val (id, level) = cell.split(':')
                val lv = level.toInt()
                require(lv in Cars.MIN_LEVEL..Cars.MAX_LEVEL) { "bad level" }
                Car(id.toInt(), lv)
            }
        }
    }

    private fun encodeSnapshot(s: Snapshot): String = listOf(
        encodeBoard(s.board).replace(',', ';'),
        s.score, s.towsBefore, s.queuedLevel, s.rngState, s.nextId, s.rewardTows,
    ).joinToString("~")

    private fun decodeSnapshot(text: String): Snapshot {
        val p = text.split('~')
        require(p.size == 7) { "bad snapshot" }
        return Snapshot(
            board = decodeBoard(p[0].replace(';', ',')),
            score = p[1].toLong(),
            towsBefore = p[2].toInt(),
            queuedLevel = p[3].toInt(),
            rngState = p[4].toLong(),
            nextId = p[5].toInt(),
            rewardTows = p[6].toInt(),
        )
    }
}
