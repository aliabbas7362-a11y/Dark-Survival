package com.blindtechabbas.darksurvival.audio

import android.content.Context
import android.content.SharedPreferences
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import java.util.Locale

/**
 * TtsManager v4 - the game's own voice (it does not use TalkBack).
 * - Priority queue: 1 = critical, 2 = combat, 3 = information. A critical line
 *   interrupts a lesser one; a lesser one never cuts a critical one.
 * - Old lines expire instead of being spoken late.
 * - You can choose the speech engine, speed and volume (saved).
 * - If the first choice fails to start, the phone default is tried.
 */
class TtsManager(context: Context, private val prefs: SharedPreferences) {

    private class Item(val text: String, val priority: Int, val createdAt: Long)

    private val appContext: Context = context.applicationContext
    private val handler = Handler(Looper.getMainLooper())

    private var tts: TextToSpeech? = null
    private var ready = false
    private val queue = ArrayList<Item>()
    private var speaking = false
    private var speakStartedAt = 0L
    private var currentPriority = 99
    private var currentId = ""
    private var counter = 0
    private var lastText = ""
    private var lastAt = 0L
    private var autoChecked = false
    private var currentPkg: String? = null
    private var engines: List<Pair<String, String>> = emptyList()
    private var sayAfterInit: String? = null

    var failed: Boolean = false
        private set
    var rate: Float = prefs.getFloat(KEY_RATE, 1.0f)
        private set
    var volume: Float = prefs.getFloat(KEY_VOLUME, 1.0f)
        private set

    /** Called when the engine becomes ready or fails, so the screen can update. */
    var onStatus: (() -> Unit)? = null

    private val progress = object : UtteranceProgressListener() {
        override fun onStart(utteranceId: String?) {
        }

        override fun onDone(utteranceId: String?) {
            finished(utteranceId)
        }

        override fun onError(utteranceId: String?) {
            finished(utteranceId)
        }

        override fun onStop(utteranceId: String?, interrupted: Boolean) {
            finished(utteranceId)
        }
    }

    init {
        create(prefs.getString(KEY_ENGINE, null))
    }

    private fun finished(utteranceId: String?) {
        handler.post {
            if (utteranceId == currentId) {
                speaking = false
                currentPriority = 99
                pump()
            }
        }
    }

    private fun create(pkg: String?) {
        ready = false
        try {
            tts?.stop()
            tts?.shutdown()
        } catch (_: Exception) {
        }
        tts = null
        currentPkg = pkg
        val listener = TextToSpeech.OnInitListener { status ->
            handler.post { onInit(status, pkg) }
        }
        tts = try {
            if (pkg == null) {
                TextToSpeech(appContext, listener)
            } else {
                TextToSpeech(appContext, listener, pkg)
            }
        } catch (_: Exception) {
            null
        }
        if (tts == null) {
            failed = true
            onStatus?.invoke()
        }
    }

    private fun onInit(status: Int, pkg: String?) {
        if (status != TextToSpeech.SUCCESS) {
            if (pkg != null) {
                create(null)
            } else {
                failed = true
                onStatus?.invoke()
            }
            return
        }
        val t = tts ?: return
        try {
            engines = t.engines.map { Pair(it.name, it.label) }
        } catch (_: Exception) {
            engines = emptyList()
        }
        // First run: prefer Google speech if the phone has it.
        if (!autoChecked && prefs.getString(KEY_ENGINE, null) == null && pkg == null) {
            autoChecked = true
            val hasGoogle = engines.any { it.first == GOOGLE }
            val defaultIsGoogle = try {
                t.defaultEngine == GOOGLE
            } catch (_: Exception) {
                true
            }
            if (hasGoogle && !defaultIsGoogle) {
                create(GOOGLE)
                return
            }
        }
        val r = t.setLanguage(Locale.US)
        if (r == TextToSpeech.LANG_MISSING_DATA || r == TextToSpeech.LANG_NOT_SUPPORTED) {
            t.setLanguage(Locale.getDefault())
        }
        t.setSpeechRate(rate)
        t.setOnUtteranceProgressListener(progress)
        ready = true
        failed = false
        onStatus?.invoke()
        val s = sayAfterInit
        sayAfterInit = null
        if (s != null) say(s, 2)
        pump()
    }

    // ------------------------------------------------------------ speaking
    fun say(text: String, priority: Int = 2) {
        if (text.isBlank()) return
        val now = SystemClock.uptimeMillis()
        if (text == lastText && now - lastAt < 1500L) return
        lastText = text
        lastAt = now
        queue.add(Item(text, priority, now))
        while (queue.size > 5) {
            var worst = 0
            for (i in queue.indices) {
                val a = queue[i]
                val b = queue[worst]
                if (a.priority > b.priority || (a.priority == b.priority && a.createdAt < b.createdAt)) {
                    worst = i
                }
            }
            queue.removeAt(worst)
        }
        if (speaking && priority < currentPriority) {
            try {
                tts?.stop()
            } catch (_: Exception) {
            }
            speaking = false
            currentId = ""
        }
        pump()
    }

    private fun pump() {
        if (!ready) return
        val now = SystemClock.uptimeMillis()
        if (speaking && now - speakStartedAt > 12000L) speaking = false
        if (speaking) return
        val iter = queue.iterator()
        while (iter.hasNext()) {
            val x = iter.next()
            val ttl = when (x.priority) {
                1 -> 6000L
                2 -> 4000L
                else -> 2500L
            }
            if (now - x.createdAt > ttl) iter.remove()
        }
        if (queue.isEmpty()) return
        var best = 0
        for (i in queue.indices) {
            val a = queue[i]
            val b = queue[best]
            if (a.priority < b.priority || (a.priority == b.priority && a.createdAt < b.createdAt)) {
                best = i
            }
        }
        val item = queue.removeAt(best)
        speaking = true
        speakStartedAt = now
        currentPriority = item.priority
        counter++
        currentId = "ds-" + now + "-" + counter
        val params = Bundle()
        params.putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, volume)
        val res = try {
            tts?.speak(item.text, TextToSpeech.QUEUE_FLUSH, params, currentId)
        } catch (_: Exception) {
            TextToSpeech.ERROR
        }
        if (res == TextToSpeech.ERROR) {
            speaking = false
            currentPriority = 99
        }
    }

    fun stop() {
        queue.clear()
        speaking = false
        currentPriority = 99
        currentId = ""
        try {
            tts?.stop()
        } catch (_: Exception) {
        }
    }

    // ------------------------------------------------------------ settings
    fun setRate(r: Float) {
        rate = r
        prefs.edit().putFloat(KEY_RATE, r).apply()
        try {
            tts?.setSpeechRate(r)
        } catch (_: Exception) {
        }
    }

    fun setVolume(v: Float) {
        volume = v
        prefs.edit().putFloat(KEY_VOLUME, v).apply()
    }

    fun engineLabel(): String {
        val pkg = currentPkg
        if (pkg == null) {
            return try {
                val def = tts?.defaultEngine
                engines.firstOrNull { it.first == def }?.second ?: "Default"
            } catch (_: Exception) {
                "Default"
            }
        }
        return engines.firstOrNull { it.first == pkg }?.second ?: "Default"
    }

    /** Switch to the next installed speech engine and say a line when it is ready. */
    fun nextEngine() {
        if (engines.isEmpty()) {
            say("No other speech engine found.", 2)
            return
        }
        var cur = currentPkg
        if (cur == null) {
            cur = try {
                tts?.defaultEngine
            } catch (_: Exception) {
                null
            }
        }
        var idx = engines.indexOfFirst { it.first == cur }
        idx = (idx + 1) % engines.size
        val pkg = engines[idx].first
        prefs.edit().putString(KEY_ENGINE, pkg).apply()
        sayAfterInit = "Speech engine: " + engines[idx].second
        stop()
        create(pkg)
    }

    fun shutdown() {
        stop()
        try {
            tts?.shutdown()
        } catch (_: Exception) {
        }
        tts = null
        ready = false
    }

    companion object {
        const val GOOGLE = "com.google.android.tts"
        const val KEY_ENGINE = "ttsEngine"
        const val KEY_RATE = "ttsRate"
        const val KEY_VOLUME = "ttsVolume"
    }
}
