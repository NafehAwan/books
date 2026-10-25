package com.tinymotors.game

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.tinymotors.game.data.GameRepository
import com.tinymotors.game.data.Settings
import com.tinymotors.game.engine.GameEngine
import com.tinymotors.game.engine.GameStateCodec
import kotlinx.coroutines.CoroutineScope
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

class PersistenceTest {

    @get:Rule val tmp = TemporaryFolder()

    private fun playedGame(): GameEngine {
        val e = GameEngine(GameEngine.newGameState(2024))
        repeat(12) { e.addCar() }
        e.findHintPair()?.let { (a, b) -> e.drag(a, b) }
        e.tow(e.state.board.indexOfFirst { it != null })
        return e
    }

    @Test fun `codec round trip keeps every field including undo history`() {
        val state = playedGame().state
        val decoded = GameStateCodec.decode(GameStateCodec.encode(state))
        assertEquals(state, decoded)
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
}
