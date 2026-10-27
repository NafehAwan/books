package com.tinymotors.game.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import com.tinymotors.game.engine.DailyGift
import com.tinymotors.game.engine.GameState
import com.tinymotors.game.engine.GameStateCodec
import kotlinx.coroutines.flow.first

/** Everything that is saved on the device (and in Android backup). Nothing is sent to the developer. */
data class SavedData(
    val game: GameState?,
    val settings: Settings,
    val giftDay: Int,
    val tutorialSeen: Boolean,
    val adsRemoved: Boolean = false,
    val rewardDay: Int = DailyGift.NEVER,
    val rewardCount: Int = 0,
    /** Google Play purchase tokens whose coins were already added (so they are never added twice). */
    val grantedPurchases: Set<String> = emptySet(),
)

/** Reads and writes the game with Jetpack DataStore. Works on plain JVM too (used by unit tests). */
class GameRepository(private val store: DataStore<Preferences>) {

    suspend fun load(): SavedData {
        // Reading can fail (for example disk errors); then the game starts with defaults.
        val p = try {
            store.data.first()
        } catch (e: java.io.IOException) {
            emptyPreferences()
        }
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
            adsRemoved = p[ADS_REMOVED] ?: false,
            rewardDay = p[REWARD_DAY] ?: DailyGift.NEVER,
            rewardCount = p[REWARD_COUNT] ?: 0,
            grantedPurchases = p[GRANTED] ?: emptySet(),
        )
    }

    /**
     * Saves the game with the coins of a coin pack already added, and marks the purchase as
     * granted, in one write. Only after this is the purchase consumed at Google Play, so the coins
     * can be neither lost (app closed in between) nor added twice (purchase seen again).
     */
    suspend fun saveGrantedPurchase(state: GameState, purchaseToken: String) {
        val text = GameStateCodec.encode(state)
        store.edit {
            it[GAME] = text
            it[GRANTED] = (it[GRANTED] ?: emptySet()) + purchaseToken
        }
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

    suspend fun saveAdsRemoved(removed: Boolean) {
        store.edit { it[ADS_REMOVED] = removed }
    }

    suspend fun saveRewardCount(day: Int, count: Int) {
        store.edit {
            it[REWARD_DAY] = day
            it[REWARD_COUNT] = count
        }
    }

    /** Parent "Start over": wipes the saved board and tutorial flag; settings and purchases stay. */
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
        val ADS_REMOVED = booleanPreferencesKey("ads_removed")
        val REWARD_DAY = intPreferencesKey("reward_day")
        val REWARD_COUNT = intPreferencesKey("reward_count")
        val GRANTED = stringSetPreferencesKey("granted_purchases")
    }
}
