package com.blindtechabbas.darksurvival.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.SoundPool
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import com.blindtechabbas.darksurvival.game.Zones
import kotlin.math.max
import kotlin.math.min

private fun killMp(mp: MediaPlayer) {
    try {
        mp.stop()
    } catch (_: Exception) {
    }
    try {
        mp.release()
    } catch (_: Exception) {
    }
}

private class Slot(val mp: MediaPlayer, var cur: Float, var target: Float, var dying: Boolean)

/**
 * One looping background bed that can crossfade to another bed.
 * Every player that is fading out is tracked and released, so no "ghost"
 * ambience can stay behind (that was a bug in the old version).
 */
private class AmbLayer(private val ctx: Context, private val handler: Handler) {
    private val slots = ArrayList<Slot>()
    private var activeKey: String? = null
    private var level = 0.25f
    private var ticking = false

    private val ticker: Runnable = object : Runnable {
        override fun run() {
            val iter = slots.iterator()
            while (iter.hasNext()) {
                val s = iter.next()
                val step = max(level, 0.15f) * 0.05f
                if (s.cur < s.target) {
                    s.cur = min(s.target, s.cur + step)
                } else if (s.cur > s.target) {
                    s.cur = max(s.target, s.cur - step)
                }
                try {
                    s.mp.setVolume(s.cur, s.cur)
                } catch (_: Exception) {
                }
                if (s.dying && s.cur <= 0.005f) {
                    killMp(s.mp)
                    iter.remove()
                }
            }
            if (slots.isEmpty()) {
                ticking = false
            } else {
                handler.postDelayed(this, 100L)
            }
        }
    }

    private fun kick() {
        if (!ticking) {
            ticking = true
            handler.postDelayed(ticker, 100L)
        }
    }

    fun play(resName: String, vol: Float) {
        if (resName == activeKey) {
            setLevel(vol)
            return
        }
        for (s in slots) {
            s.dying = true
            s.target = 0f
        }
        while (slots.size > 3) {
            killMp(slots[0].mp)
            slots.removeAt(0)
        }
        val id = ctx.resources.getIdentifier(resName, "raw", ctx.packageName)
        if (id == 0) {
            activeKey = null
            kick()
            return
        }
        val mp: MediaPlayer? = try {
            MediaPlayer.create(ctx, id)?.apply {
                isLooping = true
                setVolume(0f, 0f)
                start()
            }
        } catch (_: Exception) {
            null
        }
        if (mp == null) {
            activeKey = null
            kick()
            return
        }
        level = vol
        slots.add(Slot(mp, 0f, vol, false))
        activeKey = resName
        kick()
    }

    fun setLevel(vol: Float) {
        level = vol
        for (s in slots) {
            if (!s.dying) s.target = vol
        }
        kick()
    }

    /** Fade everything out slowly. */
    fun fadeAll() {
        activeKey = null
        for (s in slots) {
            s.dying = true
            s.target = 0f
        }
        kick()
    }

    /** Stop right now. */
    fun stopNow() {
        handler.removeCallbacks(ticker)
        ticking = false
        for (s in slots) killMp(s.mp)
        slots.clear()
        activeKey = null
    }
}

/**
 * SoundManager v4.
 * - Short sounds go through a SoundPool and are placed in space (left/right, distance, behind).
 * - Backgrounds are MediaPlayer beds that change with the zone and with day and night.
 * - Sounds are loaded BY NAME, so one missing file can never silence all the others.
 */
class SoundManager(context: Context) {

    private val appContext: Context = context.applicationContext
    private val handler = Handler(Looper.getMainLooper())

    private val pool: SoundPool = SoundPool.Builder()
        .setMaxStreams(24)
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_GAME)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
        )
        .build()

    private val sampleByKey = HashMap<String, Int>()
    private val loaded = HashSet<Int>()

    private class Pending(
        val sampleId: Int,
        val left: Float,
        val right: Float,
        val rate: Float,
        val priority: Int,
        val at: Long
    )

    private val pending = ArrayList<Pending>()

    init {
        pool.setOnLoadCompleteListener { _, sampleId, status ->
            if (status == 0) {
                loaded.add(sampleId)
                flushPending(sampleId)
            }
        }
        for (key in KEYS) {
            val id = appContext.resources.getIdentifier(key, "raw", appContext.packageName)
            if (id != 0) {
                sampleByKey[key] = pool.load(appContext, id, 1)
            }
        }
    }

    private fun flushPending(sampleId: Int) {
        val now = SystemClock.uptimeMillis()
        val iter = pending.iterator()
        while (iter.hasNext()) {
            val p = iter.next()
            if (p.sampleId == sampleId) {
                if (now - p.at < 1500L) {
                    pool.play(p.sampleId, p.left, p.right, p.priority, 0, p.rate)
                }
                iter.remove()
            }
        }
    }

    /**
     * Play a sound at a place. bearing is relative to where the player faces
     * (0 = front, +90 = right, 180 = behind). distFt = how far away it is.
     */
    fun playAt(key: String, bearing: Float, distFt: Float, gain: Float = 1f, rate: Float = 1f) {
        val id = sampleByKey[key] ?: return
        val behind = Spatial.behind(bearing)
        val vol = Spatial.distanceGain(distFt) * gain * (1f - 0.45f * behind)
        if (vol <= 0.01f) return
        val pan = Spatial.pan(bearing)
        val l = Spatial.left(pan, vol)
        val r = Spatial.right(pan, vol)
        val rt = (rate * (1f - 0.07f * behind)).coerceIn(0.5f, 2f)
        val prio = if (key.startsWith("ping_")) 3 else 1
        if (loaded.contains(id)) {
            pool.play(id, l, r, prio, 0, rt)
        } else {
            pending.add(Pending(id, l, r, rt, prio, SystemClock.uptimeMillis()))
        }
    }

    /** A sound with no place: menus, countdown, results. */
    fun playUi(key: String, gain: Float = 1f) {
        playAt(key, 0f, 0f, gain, 1f)
    }

    // ------------------------------------------------------------ ambience
    private val zoneLayer = AmbLayer(appContext, handler)
    private val nightLayer = AmbLayer(appContext, handler)
    private var ambienceOn = false
    private var zoneId = 0
    private var night = 0f
    private var sirenPlayer: MediaPlayer? = null

    fun setZone(id: Int) {
        zoneId = id
        ambienceOn = true
        applyAmbience()
        if (id == 4) startSiren() else stopSiren()
    }

    /** 0 = day, 1 = night. Called about once a second during a match. */
    fun setNight(n: Float) {
        night = n
        if (ambienceOn) applyAmbience()
    }

    private fun applyAmbience() {
        val z = Zones.list[zoneId]
        zoneLayer.play(z.ambience, 0.26f * (1f - 0.55f * night))
        if (zoneId != 4 && night > 0.05f) {
            nightLayer.play("amb_night", 0.20f * night)
        } else {
            nightLayer.fadeAll()
        }
    }

    fun stopAmbience() {
        ambienceOn = false
        zoneLayer.stopNow()
        nightLayer.stopNow()
        stopSiren()
    }

    private fun startSiren() {
        if (sirenPlayer != null) return
        val id = appContext.resources.getIdentifier("amb_airraid", "raw", appContext.packageName)
        if (id == 0) return
        sirenPlayer = try {
            MediaPlayer.create(appContext, id)?.apply {
                isLooping = true
                setVolume(0.2f, 0.2f)
                start()
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun stopSiren() {
        sirenPlayer?.let { killMp(it) }
        sirenPlayer = null
    }

    // ------------------------------------------------------------ intro: helicopter, parachute, wind
    private var heli: MediaPlayer? = null
    private var heliStartMs = 0L
    private var heliTick = 0
    private var windStream = 0

    private val heliTicker: Runnable = object : Runnable {
        override fun run() {
            val mp = heli ?: return
            val t = (SystemClock.uptimeMillis() - heliStartMs) / 1000f
            var vol = 0.35f + 0.65f * min(1f, t / 2f)
            if (t > 6f) vol *= max(0.3f, 1f - (t - 6f) / 3f)
            try {
                mp.setVolume(vol, vol)
            } catch (_: Exception) {
            }
            heliTick++
            if (heliTick % 5 == 0) {
                // The helicopter sounds higher while it is up in the sky and lower as you come down.
                val pitch = 1.18f - 0.18f * min(1f, t / 5f)
                try {
                    mp.playbackParams = mp.playbackParams.setPitch(pitch)
                } catch (_: Exception) {
                }
            }
            handler.postDelayed(this, 100L)
        }
    }

    fun startIntro() {
        stopIntro()
        val id = appContext.resources.getIdentifier("sfx_heli", "raw", appContext.packageName)
        if (id == 0) return
        heli = try {
            MediaPlayer.create(appContext, id)?.apply {
                setVolume(0.35f, 0.35f)
                start()
            }
        } catch (_: Exception) {
            null
        }
        heliStartMs = SystemClock.uptimeMillis()
        heliTick = 0
        handler.removeCallbacks(heliTicker)
        handler.postDelayed(heliTicker, 100L)
    }

    fun stopIntro() {
        handler.removeCallbacks(heliTicker)
        heli?.let { killMp(it) }
        heli = null
    }

    fun startWind() {
        stopWind()
        val id = sampleByKey["sfx_wind_fall"] ?: return
        if (!loaded.contains(id)) return
        windStream = pool.play(id, 0.5f, 0.5f, 2, -1, 1f)
    }

    fun stopWind() {
        if (windStream != 0) {
            pool.stop(windStream)
            windStream = 0
        }
    }

    // ------------------------------------------------------------ HQ briefing (the commander's voice only)
    private var briefing: MediaPlayer? = null

    fun playBriefing() {
        stopBriefing()
        val id = appContext.resources.getIdentifier("voice_briefing", "raw", appContext.packageName)
        if (id == 0) return
        briefing = try {
            MediaPlayer.create(appContext, id)?.apply {
                setVolume(1f, 1f)
                start()
            }
        } catch (_: Exception) {
            null
        }
    }

    fun stopBriefing() {
        briefing?.let { killMp(it) }
        briefing = null
    }

    // ------------------------------------------------------------ lifecycle
    /** Home button or phone call: everything goes silent. */
    fun suspend() {
        stopIntro()
        stopWind()
        stopBriefing()
        zoneLayer.stopNow()
        nightLayer.stopNow()
        stopSiren()
        pool.autoPause()
    }

    /** Back in the game: the ambience starts again (it used to stay silent after a pause). */
    fun resumeFromSuspend() {
        pool.autoResume()
        if (ambienceOn) {
            applyAmbience()
            if (zoneId == 4) startSiren()
        }
    }

    fun stopAll() {
        stopIntro()
        stopWind()
        stopBriefing()
        stopAmbience()
    }

    fun release() {
        stopAll()
        pool.setOnLoadCompleteListener(null)
        pool.release()
    }

    companion object {
        val KEYS: List<String> = listOf(
        "ping_attack_grenade", "ping_attack_gun", "ping_attack_melee", "ping_enemy_new",
        "ping_hit", "ping_kill", "ping_lock", "sfx_bounce", "sfx_breath", "sfx_click",
        "sfx_crouch", "sfx_death", "sfx_dodge_slide", "sfx_dry_fire", "sfx_enemy_axe_swing",
        "sfx_enemy_bat_swing", "sfx_enemy_knife_swing", "sfx_enemy_pain", "sfx_enemy_step",
        "sfx_enemy_step_heavy", "sfx_enemy_step_light", "sfx_explosion", "sfx_gear", "sfx_grunt",
        "sfx_gunshot", "sfx_heartbeat", "sfx_heli_leave", "sfx_hit_mg", "sfx_hit_pistol",
        "sfx_hit_shotgun", "sfx_hit_sniper", "sfx_jump", "sfx_land_thud", "sfx_lose",
        "sfx_mg_shot", "sfx_miss_dirt_1", "sfx_miss_dirt_2", "sfx_miss_metal", "sfx_miss_rock_1",
        "sfx_miss_rock_2", "sfx_miss_water_1", "sfx_miss_water_2", "sfx_miss_wood_1",
        "sfx_miss_wood_2", "sfx_parachute_open", "sfx_pickup", "sfx_pin", "sfx_player_hurt_1",
        "sfx_player_hurt_2", "sfx_reload_mg", "sfx_reload_pistol", "sfx_reload_shotgun",
        "sfx_reload_sniper", "sfx_shotgun", "sfx_sniper", "sfx_stand", "sfx_step_grass_1",
        "sfx_step_grass_2", "sfx_step_leaves_1", "sfx_step_leaves_2", "sfx_step_metal_1",
        "sfx_step_metal_2", "sfx_step_rock_1", "sfx_step_rock_2", "sfx_step_water_1",
        "sfx_step_water_2", "sfx_tinnitus", "sfx_wall_bump", "sfx_weapon_switch", "sfx_win",
        "sfx_wind_fall", "ui_back", "ui_countdown_go", "ui_countdown_tick", "ui_locked",
        "ui_start", "ui_talkback_alert", "ui_toggle_off", "ui_toggle_on", "voice_grenade"
        )
    }
}
