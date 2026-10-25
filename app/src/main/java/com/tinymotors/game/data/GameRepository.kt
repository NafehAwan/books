package com.tinymotors.game.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.tinymotors.game.engine.DailyGift
import com.tinymotors.game.engine.GameState
import com.tinymotors.game.engine.GameStateCodec
import kotlinx.coroutines.flow.first

/** Everything that is saved on the device. Nothing ever leaves it. */
data class SavedData(
    val game: GameState?,
    val settings: Settings,
    val giftDay: Int,
    val tutorialSeen: Boolean,
)

/** Reads and writes the game with Jetpack DataStore. Works on plain JVM too (used by unit tests). */
class GameRepository(private val store: DataStore<Preferences>) {

    suspend fun load(): SavedData {
        val p = store.data.first()
        return SavedData(
            game = GameStateCodec.decode(p[GAME]),
            settings = Settings(
                sound = p[SOUND] ?: true,
                music = p[MUSIC] ?: true,
                vibration = p[VIBRATION] ?: true,
                voice = p[VOICE] ?: true,
                hints = p[HINTS] ?: true,
            ),
            giftDay = p[GIFT_DAY] ?: DailyGift.NEVER,
            tutorialSeen = p[TUTORIAL] ?: false,
        )
    }

    suspend fun saveGame(state: GameState) {
        val text = GameStateCodec.encode(state)
        store.edit { it[GAME] = text }
    }

    suspend fun saveSettings(s: Settings) {
        store.edit {
            it[SOUND] = s.sound
            it[MUSIC] = s.music
            it[VIBRATION] = s.vibration
            it[VOICE] = s.voice
            it[HINTS] = s.hints
        }
    }

    suspend fun saveGiftDay(day: Int) {
        store.edit { it[GIFT_DAY] = day }
    }

    suspend fun saveTutorialSeen(seen: Boolean) {
        store.edit { it[TUTORIAL] = seen }
    }

    /** Parent "Start over": wipes progress but keeps settings. */
    suspend fun clearProgress() {
        store.edit {
            it.remove(GAME)
            it.remove(TUTORIAL)
        }
    }

    private companion object {
        val GAME = stringPreferencesKey("game_state")
        val SOUND = booleanPreferencesKey("sound")
        val MUSIC = booleanPreferencesKey("music")
        val VIBRATION = booleanPreferencesKey("vibration")
        val VOICE = booleanPreferencesKey("voice")
        val HINTS = booleanPreferencesKey("hints")
        val GIFT_DAY = intPreferencesKey("gift_day")
        val TUTORIAL = booleanPreferencesKey("tutorial_seen")
    }
}
