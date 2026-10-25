package com.tinymotors.game.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.SoundPool
import android.os.Build
import com.tinymotors.game.R

/** All sound effects in the game. */
enum class Sfx(val res: Int) {
    Tap(R.raw.sfx_tap),
    Click(R.raw.sfx_click),
    Drop(R.raw.sfx_drop),
    Brum(R.raw.sfx_brum),
    Merge1(R.raw.sfx_merge_01), Merge2(R.raw.sfx_merge_02), Merge3(R.raw.sfx_merge_03),
    Merge4(R.raw.sfx_merge_04), Merge5(R.raw.sfx_merge_05), Merge6(R.raw.sfx_merge_06),
    Merge7(R.raw.sfx_merge_07), Merge8(R.raw.sfx_merge_08), Merge9(R.raw.sfx_merge_09),
    Merge10(R.raw.sfx_merge_10), Merge11(R.raw.sfx_merge_11),
    Combo1(R.raw.sfx_combo_1), Combo2(R.raw.sfx_combo_2), Combo3(R.raw.sfx_combo_3), Combo4(R.raw.sfx_combo_4),
    Unlock(R.raw.sfx_unlock),
    Beep(R.raw.sfx_beep),
    Honk(R.raw.sfx_honk),
    Whoosh(R.raw.sfx_whoosh),
    Win(R.raw.sfx_win),
    Gift(R.raw.sfx_gift),
    ;

    companion object {
        fun merge(level: Int): Sfx = entries[Merge1.ordinal + (level - 2).coerceIn(0, 10)]
        fun combo(count: Int): Sfx = entries[Combo1.ordinal + (count - 2).coerceIn(0, 3)]
    }
}

/**
 * Sound effects with SoundPool, background music with MediaPlayer.
 * Music is quiet by default, follows audio focus, and everything pauses in the background.
 */
class SoundManager(context: Context) : AudioManager.OnAudioFocusChangeListener {
    private val app = context.applicationContext
    private val audioManager = app.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val attributes = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_GAME)
        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
        .build()
    private val pool = SoundPool.Builder().setMaxStreams(6).setAudioAttributes(attributes).build()
    private val ids = IntArray(Sfx.entries.size)
    private var music: MediaPlayer? = null
    private var focusRequest: AudioFocusRequest? = null
    private var hasFocus = false
    private var foreground = false
    private var ducked = false

    var effectsOn = true
    var musicOn = true
        set(value) {
            field = value
            updateMusic()
        }

    init {
        Sfx.entries.forEach { ids[it.ordinal] = pool.load(app, it.res, 1) }
    }

    fun play(sfx: Sfx, volume: Float = 0.9f, rate: Float = 1f) {
        if (!effectsOn || !foreground) return
        pool.play(ids[sfx.ordinal], volume, volume, 1, 0, rate.coerceIn(0.5f, 2f))
    }

    fun onForeground() {
        foreground = true
        pool.autoResume()
        updateMusic()
    }

    fun onBackground() {
        foreground = false
        pool.autoPause()
        music?.takeIf { it.isPlaying }?.pause()
        abandonFocus()
    }

    fun release() {
        foreground = false
        abandonFocus()
        music?.release()
        music = null
        pool.release()
    }

    private fun updateMusic() {
        if (!musicOn || !foreground) {
            music?.takeIf { it.isPlaying }?.pause()
            if (!musicOn) abandonFocus()
            return
        }
        if (!requestFocus()) return
        val player = music ?: MediaPlayer.create(app, R.raw.music_loop)?.also {
            it.setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_GAME).setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build())
            it.isLooping = true
            music = it
        } ?: return
        player.setVolume(volume(), volume())
        if (!player.isPlaying) player.start()
    }

    private fun volume() = if (ducked) MUSIC_VOLUME * 0.35f else MUSIC_VOLUME

    private fun requestFocus(): Boolean {
        if (hasFocus) return true
        val result = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val req = focusRequest ?: AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                .setAudioAttributes(attributes)
                .setOnAudioFocusChangeListener(this)
                .setWillPauseWhenDucked(false)
                .build().also { focusRequest = it }
            audioManager.requestAudioFocus(req)
        } else {
            @Suppress("DEPRECATION")
            audioManager.requestAudioFocus(this, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN)
        }
        hasFocus = result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        return hasFocus
    }

    private fun abandonFocus() {
        if (!hasFocus) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            focusRequest?.let { audioManager.abandonAudioFocusRequest(it) }
        } else {
            @Suppress("DEPRECATION")
            audioManager.abandonAudioFocus(this)
        }
        hasFocus = false
    }

    override fun onAudioFocusChange(change: Int) {
        when (change) {
            AudioManager.AUDIOFOCUS_GAIN -> {
                hasFocus = true
                ducked = false
                updateMusic()
            }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {
                ducked = true
                music?.setVolume(volume(), volume())
            }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> music?.takeIf { it.isPlaying }?.pause()
            AudioManager.AUDIOFOCUS_LOSS -> {
                music?.takeIf { it.isPlaying }?.pause()
                abandonFocus()
            }
        }
    }

    private companion object {
        const val MUSIC_VOLUME = 0.22f
    }
}
