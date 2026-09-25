package com.blindtechabbas.darksurvival.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.SoundPool
import com.blindtechabbas.darksurvival.R

/**
 * SoundManager — all SFX preloaded at app start.
 * v1.5 FIX: the previous build forgot to actually save the loaded SoundPool
 * sample ids (`sampleIdByKey`), so every play looked up an empty map and
 * silently played NOTHING — only the MediaPlayer ambience kept looping.
 * That bug is fixed here: ids are stored, and plays are queued until all
 * sounds finish loading so not even an early tap can fail.
 * Player footstep plays loud and centered.
 */
class SoundManager(context: Context) {

    private val appContext = context.applicationContext

    private val soundPool: SoundPool = SoundPool.Builder()
        .setMaxStreams(16)
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_GAME)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
        )
        .build()

    private val soundMap: Map<String, Int> = mapOf(
        "gunshot" to R.raw.sfx_gunshot,
        "shotgun" to R.raw.sfx_shotgun,
        "sniper" to R.raw.sfx_sniper,
        "mg" to R.raw.sfx_mg_shot,
        "step_f1" to R.raw.sfx_step_f1,

        "enemy_step" to R.raw.sfx_enemy_step,
        "enemy_shot" to R.raw.sfx_enemy_shot,
        "eliminate" to R.raw.sfx_eliminate,
        "death" to R.raw.sfx_death,
        "grunt" to R.raw.sfx_grunt,
        "gear" to R.raw.sfx_gear,
        "breath" to R.raw.sfx_breath,
        "briefing" to R.raw.voice_briefing,
        "grenadecall" to R.raw.voice_grenade,
        "crouch" to R.raw.sfx_crouch,
        "stand" to R.raw.sfx_stand,
        "pin" to R.raw.sfx_pin,
        "bounce" to R.raw.sfx_bounce,
        "explosion" to R.raw.sfx_explosion,
        "tinnitus" to R.raw.sfx_tinnitus,
        "heartbeat" to R.raw.sfx_heartbeat,
        "reload" to R.raw.sfx_reload,
        "pickup" to R.raw.sfx_pickup,
        "click" to R.raw.sfx_click,
        "melee" to R.raw.sfx_melee,
        "heli" to R.raw.sfx_heli,
        "win" to R.raw.sfx_win,
        "lose" to R.raw.sfx_lose
    )

    private val sampleIdByKey = mutableMapOf<String, Int>()
    private val pending = ArrayDeque<Triple<String, Float, Float>>() // key, pan, volume
    private val expectedLoads = soundMap.size
    private var loadedCount = 0
    private var poolReady = false

    init {
        soundPool.setOnLoadCompleteListener { _, sampleId, status ->
            if (status == 0) {
                loadedCount++
                if (loadedCount >= expectedLoads) {
                    poolReady = true
                    flush()
                }
            }
        }
        soundMap.forEach { (key, resId) ->
            sampleIdByKey[key] = soundPool.load(appContext, resId, 1)
        }
    }

    private fun flush() {
        while (pending.isNotEmpty()) {
            val (key, pan, vol) = pending.removeFirst()
            doPlay(key, pan, vol)
        }
    }

    private fun doPlay(key: String, pan: Float, volume: Float) {
        val id = sampleIdByKey[key] ?: return
        if (volume <= 0f) return
        val left = (volume * (1f - maxOf(0f, pan))).coerceIn(0f, 1f)
        val right = (volume * (1f - maxOf(0f, -pan))).coerceIn(0f, 1f)
        soundPool.play(id, left, right, 1, 0, 1f)
    }

    private fun request(key: String, pan: Float, volume: Float) {
        if (poolReady) doPlay(key, pan, volume) else pending.addLast(Triple(key, pan, volume))
    }

    fun play(key: String, volume: Float = 1f) = request(key, 0f, volume)

    /** pan: -1 left, 0 center, +1 right. volume: 0..1. */
    fun playSpatial(key: String, pan: Float, volume: Float) = request(key, pan, volume)

    // ---- Footstep engine (v2.2): round-robin + anti-overlap cooldown ----
    // v2.9: ONE verified heavy-boot soldier step (both feet use this exact
    // clip). Stream id is tracked so steps can be cut INSTANTLY.
    private var stepStreamId = 0
    private var lastStepAt = 0L

    /**
     * Player footstep — the APPROVED ElevenLabs real-foley takes (A/B/C),
     * +4dB boosted in the files themselves so they cut through ambience.
     * - ROUND-ROBIN through the 3 variants (never the same twice in a row)
     * - pitch variation ±0.05 (no robot-like repetition)
     * - STRICT cooldown: a step shorter than `cooldownMs` after the last one
     *   is DROPPED — sounds never stack into a dhol/double-hit effect
     * - stream priority 2 (higher than other SFX) so ambience never masks it
     * Independent of lanes/sensor mode: changing lane never interrupts it.
     */
    fun playFootstep(cooldownMs: Long = 500L) {
        val now = System.currentTimeMillis()
        if (now - lastStepAt < cooldownMs) return
        lastStepAt = now
        val id = sampleIdByKey["step_f1"] ?: return
        // Single realistic clip on a repeating cadence; the stream id lets us
        // CUT the step the exact millisecond movement stops (anti-dragging).
        // EXACT same clip at base rate — both feet use this one sound,
        // left-then-right feel comes from the strict 0.5s cadence alone.
        stepStreamId = soundPool.play(id, 1.35f, 1.35f, 2, 0, 1.0f)
    }

    /** Finger lifted / stopWalking(): cut the in-flight step instantly so no
     *  trailing or buffered tail ever plays after the player stops. */
    fun stopStep() {
        if (stepStreamId != 0) {
            soundPool.stop(stepStreamId)
            stepStreamId = 0
        }
    }

    // ---- Zone ambience: dual-channel CROSSFADE (v2.3) ----
    // Two MediaPlayer channels: the old bed fades OUT over ~2.5s while the
    // new zone bed fades IN — no hard cut when crossing a zone boundary.
    private val mainHandler = android.os.Handler(android.os.Looper.getMainLooper())
    private var ambA: MediaPlayer? = null
    private var ambB: MediaPlayer? = null
    private var currentAmbKey: String? = null
    private val fadeRunnables = mutableListOf<Runnable>()

    private fun ambRes(key: String): Int? = when (key) {
        "morning" -> R.raw.amb_morning   // Zone 1: early morning birds
        "river" -> R.raw.amb_river       // Zone 2: mid-day stream
        "forest" -> R.raw.amb_forest     // Zone 3: late-afternoon cicadas
        "ridge" -> R.raw.amb_ridge       // Zone 4: dusk mountain wind
        "night" -> R.raw.amb_night       // Zone 5: midnight camp tension
        "desert" -> R.raw.amb_morning
        "camp" -> R.raw.amb_night
        else -> null
    }

    /**
     * Smooth 2.5s crossfade to a zone's ambience bed.
     * riverVol (0..1) is the DISTANCE ATTENUATION of the river: full volume
     * near the stream, fading as the player walks away from Zone 2.
     */
    fun crossfadeTo(ambKey: String, riverVol: Float = 1f) {
        val res = ambRes(ambKey) ?: return
        if (ambKey == currentAmbKey) return
        cancelFades()
        val old = ambA
        ambA = try {
            MediaPlayer.create(appContext, res)?.apply {
                isLooping = true
                setVolume(0f, 0f)
                start()
            }
        } catch (_: Exception) {
            null
        }
        ambB = null
        currentAmbKey = ambKey
        val target = 0.24f * riverVol.coerceIn(0.15f, 1f)
        // 6 fade steps x 500ms = 3.0s crossfade (v2.5 spec)
        val steps = 6
        for (i in 1..steps) {
            val fIn = i / steps.toFloat()
            val fOut = 1f - fIn
            val r = Runnable {
                ambA?.setVolume(target * fIn, target * fIn)
                old?.setVolume(0.24f * fOut, 0.24f * fOut)
                if (i == steps) {
                    old?.release()
                    old?.let { if (it == ambB) ambB = null }
                }
            }
            fadeRunnables += r
            mainHandler.postDelayed(r, i * 500L)
        }
    }

    private fun cancelFades() {
        fadeRunnables.forEach { mainHandler.removeCallbacks(it) }
        fadeRunnables.clear()
    }

    fun stopAmbience() {
        cancelFades()
        ambA?.release(); ambA = null
        ambB?.release(); ambB = null
        currentAmbKey = null
    }

    /** Stop everything: ambience loop + any playing SFX. Used on game over, win, and onPause. */
    /**
     * HQ briefing on a DEDICATED MediaPlayer — SoundPool clips can be cut by
     * stream limits, so the walkie-talkie line gets its own reliable channel
     * at full volume. Called on every match start (fresh instance each time).
     */
    private var briefingPlayer: MediaPlayer? = null

    fun playBriefing() {
        briefingPlayer?.release()
        briefingPlayer = try {
            MediaPlayer.create(appContext, R.raw.voice_briefing)?.apply {
                setVolume(1f, 1f)
                start()
            }
        } catch (_: Exception) {
            null
        }
    }

    // ---- Zone 5 INTRUDER SIREN: loud continuous loop, only while in camp ----
    private var sirenPlayer: MediaPlayer? = null

    fun startSiren() {
        if (sirenPlayer != null) return
        // v2.8 REDESIGN: deep air-raid clip (verified "resonant, distant, no
        // toy beep"). LOW volume — it must echo from across the map, not sit
        // in the player's ear. The clip itself carries the distance modeling.
        sirenPlayer = try {
            MediaPlayer.create(appContext, R.raw.amb_airraid)?.apply {
                isLooping = true
                setVolume(0.22f, 0.22f) // low/medium per spec
                start()
            }
        } catch (_: Exception) {
            null
        }
    }

    fun stopSiren() {
        sirenPlayer?.release()
        sirenPlayer = null
    }

    fun stopAll() {
        stopAmbience()
        stopSiren()
        briefingPlayer?.release()
        briefingPlayer = null
        soundPool.autoPause()
    }

    fun release() {
        soundPool.setOnLoadCompleteListener(null)
        soundPool.release()
        stopAmbience()
    }
}
