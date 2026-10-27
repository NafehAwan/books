package com.tinymotors.game

import android.app.Application
import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.preferencesDataStore
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.tinymotors.game.audio.Sfx
import com.tinymotors.game.audio.SoundManager
import com.tinymotors.game.audio.Speaker
import com.tinymotors.game.data.GameRepository
import com.tinymotors.game.data.Settings
import com.tinymotors.game.engine.ComboTracker
import com.tinymotors.game.engine.DailyGift
import com.tinymotors.game.engine.GameEngine
import com.tinymotors.game.engine.GameEvent
import com.tinymotors.game.engine.GameState
import com.tinymotors.game.monetize.AdsManager
import com.tinymotors.game.monetize.BillingManager
import com.tinymotors.game.ui.CarArt
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.Calendar

// A damaged save file (power loss mid-write, a bad backup restore) starts fresh instead of
// crashing the app on every launch.
private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(
    name = "tiny_motors",
    corruptionHandler = ReplaceFileCorruptionHandler { emptyPreferences() },
)

enum class Screen { Home, Game, Garage, Settings, Store }

data class UiState(
    val loaded: Boolean = false,
    val screen: Screen = Screen.Home,
    val game: GameState? = null,
    val nextLevel: Int = 1,
    val canUndo: Boolean = false,
    val stuck: Boolean = false,
    val canHelper: Boolean = false,
    val settings: Settings = Settings(),
    val giftAvailable: Boolean = false,
    val tutorialSeen: Boolean = true,
    val showWin: Boolean = false,
    val adsRemoved: Boolean = false,
    val rewardedReady: Boolean = false,
    val adsLeftToday: Int = REWARDS_PER_DAY,
    val prices: Map<String, String> = emptyMap(),
)

/** Reward videos per calendar day, and coins per video. */
const val REWARDS_PER_DAY = 5
const val REWARD_COINS = 50

/** A batch of game events for the UI to animate, with the current combo count. */
data class Effects(val events: List<GameEvent>, val combo: Int = 1)

class GameViewModel(app: Application, private val saved: SavedStateHandle) : AndroidViewModel(app) {

    /** Car pictures, decoded once for the whole app. */
    val art = CarArt(app)

    private val repo = GameRepository(app.dataStore)

    /** Completes once the saved game is loaded; purchases wait for it so nothing is overwritten. */
    private val loaded = CompletableDeferred<Unit>()
    private var grantedPurchases: Set<String> = emptySet()

    val ads = AdsManager(app)
    private val billing = BillingManager(
        app,
        onCoins = { token, amount, consume -> viewModelScope.launch { grantCoins(token, amount, consume) } },
        onAdsRemoved = {
            viewModelScope.launch {
                loaded.await()
                if (!_ui.value.adsRemoved) {
                    _ui.update { it.copy(adsRemoved = true) }
                    repo.saveAdsRemoved(true)
                }
            }
        },
    )
    private var rewardDay = DailyGift.NEVER
    private var rewardCount = 0
    private val sound = SoundManager(app)
    private val speaker = Speaker(app)
    private val combo = ComboTracker()
    private var engine = GameEngine(GameEngine.newGameState(System.nanoTime()))
    private var giftDay = DailyGift.NEVER
    private val saveQueue = Channel<GameState>(Channel.CONFLATED)

    private val _ui = MutableStateFlow(UiState(screen = saved.get<String>(KEY_SCREEN)?.let { Screen.valueOf(it) } ?: Screen.Home))
    val ui: StateFlow<UiState> = _ui.asStateFlow()

    private val _effects = MutableSharedFlow<Effects>(extraBufferCapacity = 32)
    val effects: SharedFlow<Effects> = _effects

    init {
        viewModelScope.launch {
            val data = repo.load()
            data.game?.let { engine = GameEngine(it) }
            giftDay = data.giftDay
            rewardDay = data.rewardDay
            rewardCount = data.rewardCount
            grantedPurchases = data.grantedPurchases
            applySettings(data.settings)
            _ui.update {
                it.copy(
                    loaded = true,
                    settings = data.settings,
                    tutorialSeen = data.tutorialSeen,
                    giftAvailable = DailyGift.isAvailable(giftDay, today()),
                    showWin = saved.get<Boolean>(KEY_WIN) ?: false,
                    adsRemoved = data.adsRemoved,
                    adsLeftToday = adsLeft(),
                )
            }
            publish(save = data.game == null)
            loaded.complete(Unit)
        }
        viewModelScope.launch {
            for (state in saveQueue) repo.saveGame(state)
        }
        viewModelScope.launch { ads.rewardedReady.collect { r -> _ui.update { it.copy(rewardedReady = r) } } }
        viewModelScope.launch { billing.prices.collect { p -> _ui.update { it.copy(prices = p) } } }
    }

    // ---------- navigation ----------

    fun navigate(screen: Screen) {
        if (screen == Screen.Store) ads.retryRewardedIfIdle()
        saved[KEY_SCREEN] = screen.name
        _ui.update { it.copy(screen = screen) }
        click()
    }

    /** Returns true when the back press was handled inside the app. */
    fun back(): Boolean {
        val s = _ui.value
        return when {
            s.screen == Screen.Home -> false
            else -> {
                navigate(Screen.Home)
                true
            }
        }
    }

    fun click() = sound.play(Sfx.Click, 0.7f)

    // ---------- game actions ----------

    fun drag(from: Int, to: Int, nowMillis: Long) {
        val events = engine.drag(from, to)
        var comboCount = 1
        for (e in events) {
            when (e) {
                is GameEvent.Moved, is GameEvent.Swapped -> sound.play(Sfx.Drop, 0.8f)
                is GameEvent.CarAdded -> later(250) { sound.play(Sfx.Brum, 0.7f) }
                is GameEvent.Merged -> {
                    comboCount = combo.onMerge(nowMillis)
                    sound.play(Sfx.merge(e.car.level))
                    if (comboCount >= 2) sound.play(Sfx.combo(comboCount), 0.75f)
                }
                is GameEvent.NewCarUnlocked -> later(350) {
                    sound.play(Sfx.Unlock)
                    later(900) { sound.play(Sfx.Beep, 0.8f) }
                }
                GameEvent.HypercarBuilt -> {
                    saved[KEY_WIN] = true
                    _ui.update { it.copy(showWin = true) }
                    later(1500) { sound.play(Sfx.Win) }
                }
                else -> Unit
            }
        }
        emit(events, comboCount)
    }

    fun tow(slot: Int) {
        val events = engine.tow(slot)
        events.forEach {
            when (it) {
                is GameEvent.Towed -> sound.play(Sfx.Whoosh)
                GameEvent.NoTowsLeft -> sound.play(Sfx.Tap)
                else -> Unit
            }
        }
        emit(events)
    }

    fun undo() {
        val events = engine.undo()
        sound.play(if (events.contains(GameEvent.Undone)) Sfx.Whoosh else Sfx.Tap, 0.6f, 1.3f)
        combo.reset()
        emit(events)
    }

    fun useHelper() {
        val events = engine.useHelper()
        if (events.isNotEmpty()) sound.play(Sfx.Whoosh)
        emit(events)
    }

    fun newGame() {
        startNewBoard()
        click()
    }

    /** PLAY on the home screen always starts a fresh board. Coins, Garage cars, best score and themes stay. */
    fun play() {
        startNewBoard()
        navigate(Screen.Game)
    }

    private fun startNewBoard() {
        engine.newGame(System.nanoTime())
        combo.reset()
        saved[KEY_WIN] = false
        _ui.update { it.copy(showWin = false) }
        emit(listOf(GameEvent.NewGame))
    }

    fun keepPlaying() {
        saved[KEY_WIN] = false
        _ui.update { it.copy(showWin = false) }
        click()
    }

    /** Child tapped a car: speak its name. */
    fun sayName(name: String, honk: Boolean) {
        sound.play(if (honk) Sfx.Honk else Sfx.Tap, 0.8f)
        if (honk) later(350) { speaker.say(name) } else speaker.say(name)
    }

    fun tapLocked() = sound.play(Sfx.Tap, 0.7f)

    fun claimGift() {
        val events = engine.giftTow()
        giftDay = today()
        viewModelScope.launch { repo.saveGiftDay(giftDay) }
        sound.play(Sfx.Gift)
        _ui.update { it.copy(giftAvailable = false) }
        emit(events)
    }

    // ---------- store, coins and ads ----------

    /** Buys a board theme with coins, or switches to it if already owned. */
    fun buyTheme(id: Int): List<GameEvent> {
        val events = engine.buyTheme(id)
        when (events.firstOrNull()) {
            is GameEvent.ThemeBought -> sound.play(Sfx.Unlock)
            is GameEvent.ThemeSelected -> click()
            is GameEvent.NotEnoughCoins -> sound.play(Sfx.Tap)
            else -> Unit
        }
        emit(events)
        return events
    }

    /** Child chose to watch a reward video (at most [REWARDS_PER_DAY] a day). */
    fun watchAd(activity: android.app.Activity) {
        if (adsLeft() <= 0 || !_ui.value.rewardedReady) return
        sound.onBackground()
        ads.showRewarded(
            activity,
            onReward = {
                val day = today()
                if (rewardDay != day) {
                    rewardDay = day
                    rewardCount = 0
                }
                rewardCount++
                viewModelScope.launch { repo.saveRewardCount(rewardDay, rewardCount) }
                _ui.update { it.copy(adsLeftToday = adsLeft()) }
                emit(engine.addCoins(REWARD_COINS))
                later(400) { sound.play(Sfx.Gift) }
            },
            onClosed = { sound.onForeground() },
        )
    }

    /**
     * Adds the coins of a coin pack bought at Google Play. Waits for the saved game, skips purchases
     * already granted, saves the coins together with the purchase token, and only then lets the
     * purchase be consumed.
     */
    private suspend fun grantCoins(token: String, amount: Int, consume: () -> Unit) {
        loaded.await()
        if (token in grantedPurchases) {
            consume()
            return
        }
        val events = engine.addCoins(amount)
        grantedPurchases = grantedPurchases + token
        publish(save = false)
        _effects.tryEmit(Effects(events))
        sound.play(Sfx.Gift)
        repo.saveGrantedPurchase(engine.state, token)
        saveQueue.trySend(engine.state) // the newest state is always the last one written
        consume()
    }

    /** A grown-up passed the gate: open Google Play for [productId]. Returns false if not available. */
    fun buyProduct(activity: android.app.Activity, productId: String): Boolean = billing.buy(activity, productId)

    private fun adsLeft(): Int = if (rewardDay == today()) (REWARDS_PER_DAY - rewardCount).coerceAtLeast(0) else REWARDS_PER_DAY

    fun completeTutorial() {
        _ui.update { it.copy(tutorialSeen = true) }
        viewModelScope.launch { repo.saveTutorialSeen(true) }
    }

    // ---------- settings ----------

    fun updateSettings(s: Settings) {
        applySettings(s)
        _ui.update { it.copy(settings = s) }
        viewModelScope.launch { repo.saveSettings(s) }
        click()
    }

    /**
     * Parent gate passed: wipe the Garage stickers, best score and board. Settings, coins and
     * bought themes stay, because they may have been paid for.
     */
    fun startOver() {
        viewModelScope.launch {
            repo.clearProgress()
            val old = engine.state
            engine = GameEngine(
                GameEngine.newGameState(System.nanoTime())
                    .copy(coins = old.coins, ownedThemes = old.ownedThemes, theme = old.theme),
            )
            combo.reset()
            saved[KEY_WIN] = false
            _ui.update { it.copy(tutorialSeen = false, showWin = false) }
            sound.play(Sfx.Whoosh)
            emit(listOf(GameEvent.NewGame))
        }
    }

    private fun applySettings(s: Settings) {
        sound.effectsOn = s.sound
        sound.musicOn = s.music
        speaker.enabled = s.voice
        if (!s.voice) speaker.stop()
    }

    // ---------- lifecycle ----------

    fun onForeground() {
        sound.onForeground()
        ads.start()
        ads.retryRewardedIfIdle()
        billing.start()
        _ui.update { it.copy(giftAvailable = it.loaded && DailyGift.isAvailable(giftDay, today()), adsLeftToday = adsLeft()) }
    }

    fun onBackground() {
        sound.onBackground()
        speaker.stop()
    }

    override fun onCleared() {
        billing.end()
        sound.release()
        speaker.release()
    }

    // ---------- internals ----------

    private fun emit(events: List<GameEvent>, comboCount: Int = 1) {
        if (events.isEmpty()) return
        publish(save = true)
        _effects.tryEmit(Effects(events, comboCount))
    }

    private fun publish(save: Boolean) {
        val s = engine.state
        _ui.update {
            it.copy(
                game = s,
                nextLevel = engine.nextSpawnLevel,
                canUndo = engine.canUndo,
                stuck = engine.isStuck,
                canHelper = engine.canUseHelper,
            )
        }
        if (save) saveQueue.trySend(s)
    }

    private fun later(ms: Long, block: () -> Unit) {
        viewModelScope.launch {
            delay(ms)
            block()
        }
    }

    private fun today(): Int {
        val c = Calendar.getInstance()
        return DailyGift.dayKey(c.get(Calendar.YEAR), c.get(Calendar.MONTH) + 1, c.get(Calendar.DAY_OF_MONTH))
    }

    private companion object {
        const val KEY_SCREEN = "screen"
        const val KEY_WIN = "win"
    }
}
