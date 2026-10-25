package com.tinymotors.game.audio

import android.content.Context
import android.speech.tts.TextToSpeech
import java.util.Locale

/** Speaks car names with a friendly, slightly raised voice. Quietly does nothing if TTS is unavailable. */
class Speaker(context: Context) : TextToSpeech.OnInitListener {
    private var ready = false
    private var tts: TextToSpeech? = try {
        TextToSpeech(context.applicationContext, this)
    } catch (e: RuntimeException) {
        null
    }
    var enabled = true

    override fun onInit(status: Int) {
        val engine = tts ?: return
        if (status != TextToSpeech.SUCCESS) return
        val lang = engine.setLanguage(Locale.getDefault()).let {
            if (it == TextToSpeech.LANG_MISSING_DATA || it == TextToSpeech.LANG_NOT_SUPPORTED) engine.setLanguage(Locale.US) else it
        }
        if (lang == TextToSpeech.LANG_MISSING_DATA || lang == TextToSpeech.LANG_NOT_SUPPORTED) return
        engine.setPitch(1.3f)
        engine.setSpeechRate(0.9f)
        ready = true
    }

    fun say(text: String) {
        if (!enabled || !ready) return
        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "car")
    }

    fun stop() {
        if (ready) tts?.stop()
    }

    fun release() {
        ready = false
        tts?.shutdown()
        tts = null
    }
}
