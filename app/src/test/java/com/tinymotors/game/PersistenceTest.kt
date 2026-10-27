package com.tinymotors.game

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.tinymotors.game.data.GameRepository
import com.tinymotors.game.data.Settings
import com.tinymotors.game.engine.GameEngine
import com.tinymotors.game.engine.GameStateCodec
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

@OptIn(ExperimentalCoroutinesApi::class)
class PersistenceTest {

    @get:Rule val tmp = TemporaryFolder()

    private fun playedGame(): GameEngine {
        val e = GameEngine(GameEngine.newGameState(2024))
        repeat(12) { e.addCar() }
        e.addCoins(450)
        e.buyTheme(1)
        e.findHintPair()?.let { (a, b) -> e.drag(a, b) }
        e.tow(e.state.board.indexOfFirst { it != null })
        return e
    }

    @Test fun `codec round trip keeps every field including undo history`() {
        val state = playedGame().state
        val decoded = GameStateCodec.decode(GameStateCodec.encode(state))
        assertEquals(state, decoded)
    }

    @Test fun `saves from before coins existed still load`() {
        val old = GameStateCodec.encode(GameEngine.newGameState(9)).lineSequence()
            .filterNot { it.startsWith("coins=") || it.startsWith("themes=") || it.startsWith("theme=") }
            .joinToString("\n")
        val decoded = GameStateCodec.decode(old)!!
        assertEquals(0L, decoded.coins)
        assertEquals(0, decoded.theme)
        assertTrue(decoded.ownsTheme(0))
    }

    @Test fun `damaged or missing data decodes to null`() {
        assertNull(GameStateCodec.decode(null))
        assertNull(GameStateCodec.decode(""))
        assertNull(GameStateCodec.decode("v=1\nboard=1:1"))
        assertNull(GameStateCodec.decode("v=999\n"))
        val good = GameStateCodec.encode(GameEngine.newGameState(1))
        assertNull(GameStateCodec.decode(good.replace("score=0", "score=lots")))
    }

    @Test fun `datastore round trip survives a restart`() = runTest {
        val file = tmp.newFile("tiny.preferences_pb").also { it.delete() }
        val state = playedGame().state
        val settings = Settings(sound = false, music = true, vibration = false, voice = true, hints = false)

        val scope1 = CoroutineScope(UnconfinedTestDispatcher(testScheduler) + SupervisorJob())
        val repo1 = GameRepository(PreferenceDataStoreFactory.create(scope = scope1) { file })
        repo1.saveGame(state)
        repo1.saveSettings(settings)
        repo1.saveGiftDay(20261025)
        repo1.saveTutorialSeen(true)
        scope1.cancel() // simulate process death

        val scope2 = CoroutineScope(UnconfinedTestDispatcher(testScheduler) + SupervisorJob())
        val loaded = GameRepository(PreferenceDataStoreFactory.create(scope = scope2) { file }).load()
        assertEquals(state, loaded.game)
        assertEquals(settings, loaded.settings)
        assertEquals(20261025, loaded.giftDay)
        assertTrue(loaded.tutorialSeen)
        scope2.cancel()
    }

    @Test fun `start over clears progress but keeps settings`() = runTest {
        val file = tmp.newFile("reset.preferences_pb").also { it.delete() }
        val scope = CoroutineScope(UnconfinedTestDispatcher(testScheduler) + SupervisorJob())
        val repo = GameRepository(PreferenceDataStoreFactory.create(scope = scope) { file })
        repo.saveGame(playedGame().state)
        repo.saveSettings(Settings(music = false))
        repo.saveTutorialSeen(true)
        repo.clearProgress()
        val loaded = repo.load()
        assertNull(loaded.game)
        assertFalse(loaded.tutorialSeen)
        assertFalse(loaded.settings.music)
        scope.cancel()
    }

    @Test fun `a granted coin pack is saved with its coins and survives a restart and a reset`() = runTest {
        val file = tmp.newFile("grant.preferences_pb").also { it.delete() }
        val engine = playedGame()
        val before = engine.state.coins
        engine.addCoins(1500)

        val scope1 = CoroutineScope(UnconfinedTestDispatcher(testScheduler) + SupervisorJob())
        val repo1 = GameRepository(PreferenceDataStoreFactory.create(scope = scope1) { file })
        repo1.saveGrantedPurchase(engine.state, "token-1")
        repo1.saveGrantedPurchase(engine.state, "token-2")
        scope1.cancel() // process death right after granting, before Google Play consumed it

        val scope2 = CoroutineScope(UnconfinedTestDispatcher(testScheduler) + SupervisorJob())
        val repo2 = GameRepository(PreferenceDataStoreFactory.create(scope = scope2) { file })
        val loaded = repo2.load()
        assertEquals(before + 1500, loaded.game?.coins)
        assertEquals(setOf("token-1", "token-2"), loaded.grantedPurchases)
        repo2.clearProgress()
        assertEquals(setOf("token-1", "token-2"), repo2.load().grantedPurchases)
        scope2.cancel()
    }

    @Test fun `a damaged save file reads as empty instead of crashing`() = runTest {
        val file = tmp.newFile("broken.preferences_pb")
        file.writeBytes(byteArrayOf(0x0A, 0x7F, 0x01, 0x02, 0x03)) // not a valid preferences file
        val scope = CoroutineScope(UnconfinedTestDispatcher(testScheduler) + SupervisorJob())
        val store = PreferenceDataStoreFactory.create(
            corruptionHandler = androidx.datastore.core.handlers.ReplaceFileCorruptionHandler {
                androidx.datastore.preferences.core.emptyPreferences()
            },
            scope = scope,
        ) { file }
        val loaded = GameRepository(store).load()
        assertNull(loaded.game)
        assertTrue(loaded.grantedPurchases.isEmpty())
        scope.cancel()
    }
}
