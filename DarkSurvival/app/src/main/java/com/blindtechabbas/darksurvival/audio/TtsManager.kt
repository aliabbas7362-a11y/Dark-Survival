package com.blindtechabbas.darksurvival.audio

import android.content.Context
import android.os.SystemClock
import android.speech.tts.TextToSpeech
import java.util.Locale

/**
 * TtsManager — voice announcements.
 * v1.7: speaks each DISTINCT line exactly once. An identical line within
 * 1 second is dropped (engine-level dedupe on top of the state-level one).
 * QUEUE_FLUSH means a new line interrupts the old one — never queues up.
 */
class TtsManager(context: Context) : TextToSpeech.OnInitListener {

    private var tts: TextToSpeech? = null
    private var ready = false
    private val appContext = context.applicationContext
    private var lastText = ""
    private var lastSpeakAt = 0L

    init {
        tts = TextToSpeech(appContext, this)
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            tts?.language = Locale.US
            ready = true
        }
    }

    fun speak(text: String) {
        if (!ready) return
        val now = SystemClock.uptimeMillis()
        // Engine-level guard: identical line within 1s is never re-queued.
        if (text == lastText && now - lastSpeakAt < 1000) return
        lastText = text
        lastSpeakAt = now
        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "ds-$now")
    }

    /** Warm up the engine at app start so the first tap is instant. */
    fun warmUp(text: String) = speak(text)

    fun stop() {
        tts?.stop()
    }

    fun shutdown() {
        tts?.stop()
        tts?.shutdown()
    }
}
