package com.blindtechabbas.darksurvival.game

import android.app.Application
import android.content.SharedPreferences
import android.os.SystemClock
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.blindtechabbas.darksurvival.audio.Haptics
import com.blindtechabbas.darksurvival.audio.SoundManager
import com.blindtechabbas.darksurvival.audio.TtsManager
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.abs

/**
 * GameViewModel v4. It owns the sound, the voice and the match engine and joins them to the screens.
 * The game talks by itself, so every menu is spoken here (no screen reader needed).
 */
class GameViewModel(app: Application) : AndroidViewModel(app) {

    private val prefs: SharedPreferences = app.getSharedPreferences("darksurvival", 0)
    private val audio = SoundManager(app)
    private val speech = TtsManager(app, prefs)
    private val haptics = Haptics(app)

    private val _ui = MutableStateFlow(initialState())
    val ui: StateFlow<UiState> = _ui.asStateFlow()

    private var engine: MatchEngine? = null
    private var loopJob: Job? = null
    private var introJob: Job? = null
    private var gateJob: Job? = null
    private var exitJob: Job? = null

    private var returnScreen: Screen? = null
    private var gateOverridden = false
    private var backArmedUntil = 0L
    private var hintGiven = false

    private var neutralFwd = prefs.getFloat("neutralFwd", 0f)
    private var neutralRight = prefs.getFloat("neutralRight", 0f)
    private var lastFwd = 0f
    private var lastRight = 0f
    private var turnLatch = false

    init {
        speech.onStatus = {
            _ui.update { it.copy(ttsOk = !speech.failed, engineLabel = speech.engineLabel()) }
        }
    }

    private fun initialState(): UiState {
        return UiState(
            screen = Screen.GATE,
            speechFeedback = prefs.getBoolean("speechFeedback", true),
            announcements = prefs.getBoolean("announcements", true),
            sensorOn = prefs.getBoolean("sensorOn", false),
            engineLabel = speech.engineLabel(),
            speechRate = speech.rate,
            speechVolume = speech.volume
        )
    }

    // ============================================================ speaking helpers
    /** Menu voice (Settings > Speech feedback). */
    private fun menuSay(text: String) {
        if (_ui.value.speechFeedback) speech.say(text, 2)
    }

    /** Match voice (Settings > Announcements). */
    private fun matchSay(text: String, priority: Int) {
        if (_ui.value.announcements) speech.say(text, priority)
    }

    // ============================================================ the TalkBack gate
    fun onAccessibilityState(on: Boolean) {
        if (_ui.value.talkBackOn != on) gateOverridden = false
        _ui.update { it.copy(talkBackOn = on) }
        evaluateGate()
    }

    fun gateOverride() {
        if (_ui.value.screen != Screen.GATE) return
        gateOverridden = true
        evaluateGate()
    }

    private fun evaluateGate() {
        val s = _ui.value
        val blocked = s.talkBackOn && !gateOverridden
        if (blocked) {
            if (s.screen != Screen.GATE) {
                if (s.screen == Screen.MATCH && s.phase == Phase.PLAYING) {
                    returnScreen = Screen.MATCH
                    pauseMatch()
                } else {
                    returnScreen = if (s.screen == Screen.MATCH) Screen.MAIN_MENU else s.screen
                    if (s.screen == Screen.MATCH) {
                        stopMatchJobs()
                        audio.stopAll()
                    }
                }
                _ui.update { it.copy(screen = Screen.GATE) }
            }
            startGateSpeech()
        } else if (s.screen == Screen.GATE) {
            stopGateSpeech()
            val target = returnScreen ?: Screen.MAIN_MENU
            returnScreen = null
            if (target == Screen.MATCH) {
                _ui.update { it.copy(screen = Screen.MATCH) }
                resumeMatch()
            } else {
                enterMenu(target, if (target == Screen.MAIN_MENU) "Dark Survival. Main menu" else "Back in the game")
            }
        }
    }

    private fun startGateSpeech() {
        if (gateJob?.isActive == true) return
        gateJob = viewModelScope.launch {
            while (isActive) {
                audio.playUi("ui_talkback_alert")
                speech.say(
                    "TalkBack is on. This game talks by itself. Suspend TalkBack to play. " +
                        "If it is already off, double tap the screen.",
                    1
                )
                delay(14000L)
            }
        }
    }

    private fun stopGateSpeech() {
        gateJob?.cancel()
        gateJob = null
    }

    // ============================================================ menus
    private fun enterMenu(screen: Screen, intro: String) {
        _ui.update { it.copy(screen = screen, menuIndex = 0) }
        val items = menuLabels(_ui.value)
        var text = intro
        if (items.isNotEmpty()) text = text + ". " + items[0]
        if (!hintGiven && screen == Screen.MAIN_MENU) {
            hintGiven = true
            text = text + ". Swipe right or left to move. Double tap to choose."
        }
        menuSay(text)
    }

    fun menuNext() {
        moveMenu(1)
    }

    fun menuPrev() {
        moveMenu(-1)
    }

    private fun moveMenu(delta: Int) {
        val s = _ui.value
        val items = menuLabels(s)
        if (items.isEmpty()) return
        val n = items.size
        val idx = ((s.menuIndex + delta) % n + n) % n
        _ui.update { it.copy(menuIndex = idx) }
        audio.playUi("sfx_click")
        menuSay(items[idx])
    }

    fun menuRepeat() {
        val s = _ui.value
        val items = menuLabels(s)
        if (items.isEmpty()) return
        menuSay(items[s.menuIndex.coerceIn(0, items.size - 1)])
    }

    fun menuSelect() {
        val s = _ui.value
        when (s.screen) {
            Screen.MAIN_MENU -> {
                when (s.menuIndex) {
                    0 -> {
                        audio.playUi("ui_start")
                        startMatch()
                    }
                    1 -> {
                        audio.playUi("sfx_click")
                        enterMenu(Screen.SETTINGS, "Settings")
                    }
                    else -> exitGame()
                }
            }
            Screen.SETTINGS -> settingsSelect(s.menuIndex)
            Screen.RESULT -> {
                if (s.menuIndex == 0) {
                    audio.playUi("ui_start")
                    startMatch()
                } else {
                    audio.playUi("sfx_click")
                    enterMenu(Screen.MAIN_MENU, "Main menu")
                }
            }
            else -> {
            }
        }
    }

    private fun settingsSelect(i: Int) {
        val s = _ui.value
        when (i) {
            0 -> {
                val v = !s.speechFeedback
                if (!v) speech.say("Speech feedback off", 1)
                prefs.edit().putBoolean("speechFeedback", v).apply()
                _ui.update { it.copy(speechFeedback = v) }
                audio.playUi(if (v) "ui_toggle_on" else "ui_toggle_off")
                if (v) speech.say("Speech feedback on", 2)
            }
            1 -> {
                val v = !s.announcements
                prefs.edit().putBoolean("announcements", v).apply()
                _ui.update { it.copy(announcements = v) }
                audio.playUi(if (v) "ui_toggle_on" else "ui_toggle_off")
                menuSay("Announcements " + onOff(v))
            }
            2 -> {
                val v = !s.sensorOn
                prefs.edit().putBoolean("sensorOn", v).apply()
                _ui.update { it.copy(sensorOn = v) }
                audio.playUi(if (v) "ui_toggle_on" else "ui_toggle_off")
                if (v) {
                    calibrate()
                    menuSay("Sensor on. Your current position is neutral. Tip the top of the screen away to walk, tip right or left to turn.")
                } else {
                    engine?.setSensorWalk(0)
                    menuSay("Sensor off")
                }
            }
            3 -> {
                calibrate()
                audio.playUi("sfx_click")
                menuSay("Sensor calibrated. Your current position is neutral.")
            }
            4 -> {
                audio.playUi("sfx_click")
                speech.nextEngine()
            }
            5 -> {
                val list = listOf(0.8f, 1.0f, 1.2f, 1.5f, 1.8f, 2.2f)
                var idx = list.indexOfFirst { abs(it - s.speechRate) < 0.05f }
                idx = (idx + 1) % list.size
                val r = list[idx]
                speech.setRate(r)
                _ui.update { it.copy(speechRate = r) }
                audio.playUi("sfx_click")
                menuSay("Speech speed " + rateLabel(r))
            }
            6 -> {
                val list = listOf(0.4f, 0.6f, 0.8f, 1.0f)
                var idx = list.indexOfFirst { abs(it - s.speechVolume) < 0.05f }
                idx = (idx + 1) % list.size
                val v = list[idx]
                speech.setVolume(v)
                _ui.update { it.copy(speechVolume = v) }
                audio.playUi("sfx_click")
                menuSay("Speech volume " + volumeLabel(v))
            }
            else -> {
                audio.playUi("ui_back")
                enterMenu(Screen.MAIN_MENU, "Main menu")
            }
        }
    }

    private fun calibrate() {
        neutralFwd = lastFwd
        neutralRight = lastRight
        prefs.edit().putFloat("neutralFwd", neutralFwd).putFloat("neutralRight", neutralRight).apply()
    }

    fun onBack() {
        val s = _ui.value
        val now = SystemClock.elapsedRealtime()
        when (s.screen) {
            Screen.GATE, Screen.MAIN_MENU -> {
                if (now < backArmedUntil) {
                    exitGame()
                } else {
                    backArmedUntil = now + 3000L
                    speech.say("Press back again to exit.", 1)
                }
            }
            Screen.SETTINGS -> {
                audio.playUi("ui_back")
                enterMenu(Screen.MAIN_MENU, "Main menu")
            }
            Screen.RESULT -> enterMenu(Screen.MAIN_MENU, "Main menu")
            Screen.MATCH -> {
                if (now < backArmedUntil) {
                    leaveMatch()
                } else {
                    backArmedUntil = now + 3000L
                    speech.say("Leave the match? Press back again.", 1)
                }
            }
        }
    }

    private fun exitGame() {
        stopMatchJobs()
        stopGateSpeech()
        audio.stopAll()
        speech.say("Closing the game. Remember to turn TalkBack back on.", 1)
        exitJob?.cancel()
        exitJob = viewModelScope.launch {
            delay(4500L)
            _ui.update { it.copy(exit = true) }
        }
    }

    // ============================================================ match flow
    private fun stopMatchJobs() {
        loopJob?.cancel()
        loopJob = null
        introJob?.cancel()
        introJob = null
    }

    private fun startMatch() {
        stopMatchJobs()
        audio.stopAll()
        engine = MatchEngine()
        turnLatch = false
        _ui.update {
            it.copy(
                screen = Screen.MATCH,
                phase = Phase.INTRO,
                countdown = 0,
                result = null,
                paused = false,
                hud = Hud()
            )
        }
        audio.startIntro()
        audio.playBriefing()
        introJob = viewModelScope.launch {
            delay(4500L)
            audio.playUi("sfx_parachute_open")
            delay(500L)
            audio.startWind()
            delay(3000L)
            audio.stopWind()
            audio.playUi("sfx_land_thud")
            audio.stopIntro()
            audio.playUi("sfx_heli_leave", 0.7f)
            runCountdown()
        }
    }

    fun skipIntro() {
        if (_ui.value.phase != Phase.INTRO) return
        introJob?.cancel()
        audio.stopIntro()
        audio.stopWind()
        audio.stopBriefing()
        introJob = viewModelScope.launch {
            runCountdown()
        }
    }

    private suspend fun runCountdown() {
        _ui.update { it.copy(phase = Phase.COUNTDOWN) }
        for (i in 3 downTo 1) {
            _ui.update { it.copy(countdown = i) }
            audio.playUi("ui_countdown_tick")
            delay(1000L)
        }
        audio.playUi("ui_countdown_go")
        beginPlaying()
    }

    private fun beginPlaying() {
        val e = engine ?: return
        _ui.update { it.copy(phase = Phase.PLAYING, countdown = 0) }
        audio.setZone(e.zoneId)
        audio.setNight(e.nightness())
        matchSay(Phrases.matchStart(), 2)
        publishHud(e)
        startLoop()
    }

    private fun startLoop() {
        loopJob?.cancel()
        loopJob = viewModelScope.launch {
            var last = SystemClock.elapsedRealtime()
            var lastNight = 0L
            while (isActive) {
                delay(Cfg.TICK_MS)
                val now = SystemClock.elapsedRealtime()
                var dt = now - last
                last = now
                if (dt < 50L) dt = 50L
                if (dt > 200L) dt = 200L
                val e = engine ?: break
                if (_ui.value.paused) continue
                e.update(dt)
                dispatch(e.drain())
                if (now - lastNight > 1000L) {
                    lastNight = now
                    audio.setNight(e.nightness())
                }
                publishHud(e)
            }
        }
    }

    private fun dispatch(events: List<GameEvent>) {
        for (ev in events) {
            when (ev) {
                is GameEvent.Sfx -> audio.playAt(ev.key, ev.bearing, ev.distFt, ev.gain, ev.rate)
                is GameEvent.Say -> matchSay(ev.text, ev.priority)
                is GameEvent.Haptic -> haptics.pulse(ev.kind)
                is GameEvent.ZoneChanged -> audio.setZone(ev.zone)
                is GameEvent.MatchOver -> finishMatch(ev.won, ev.reason)
            }
        }
    }

    private fun publishHud(e: MatchEngine) {
        val h = Hud(
            hp = e.hp,
            weapon = e.weapon.label,
            ammo = e.ammoText(),
            enemies = e.aliveCount(),
            time = e.clockText(),
            zone = e.zoneName(),
            rations = e.rations,
            crouching = e.crouching
        )
        _ui.update { it.copy(hud = h) }
    }

    private fun finishMatch(won: Boolean, reason: String) {
        loopJob?.cancel()
        loopJob = null
        val e = engine
        audio.stopAmbience()
        audio.playUi(if (won) "sfx_win" else "sfx_lose")
        val secs = ((e?.nowMs ?: 0L) / 1000L).toInt()
        val result = ResultInfo(won, reason, e?.kills ?: 0, secs, e?.hp ?: 0)
        _ui.update { it.copy(screen = Screen.RESULT, result = result, menuIndex = 0, paused = false) }
        val mins = secs / 60
        val rest = secs % 60
        val head = if (won) "You survived." else "Game over."
        val line = head + " " + reason + " Kills " + result.kills + ". Time " + mins + " minutes " + rest + " seconds."
        viewModelScope.launch {
            delay(1800L)
            speech.say(line, 1)
            delay(300L)
            val items = menuLabels(_ui.value)
            if (items.isNotEmpty()) menuSay(items[0])
        }
    }

    private fun leaveMatch() {
        stopMatchJobs()
        audio.stopAll()
        engine = null
        enterMenu(Screen.MAIN_MENU, "Match left. Main menu")
    }

    private fun pauseMatch() {
        _ui.update { it.copy(paused = true) }
        audio.suspend()
        speech.stop()
        engine?.fireUp()
        engine?.touchUp()
    }

    private fun resumeMatch() {
        _ui.update { it.copy(paused = false) }
        audio.resumeFromSuspend()
        matchSay("Match resumed.", 2)
    }

    // ============================================================ lifecycle (called by the activity)
    fun onAppPause() {
        val s = _ui.value
        stopGateSpeech()
        audio.suspend()
        speech.stop()
        haptics.cancel()
        if (s.screen == Screen.MATCH) {
            if (s.phase == Phase.PLAYING) {
                _ui.update { it.copy(paused = true) }
                engine?.fireUp()
                engine?.touchUp()
            } else {
                stopMatchJobs()
                audio.stopAll()
                engine = null
                _ui.update { it.copy(screen = Screen.MAIN_MENU, menuIndex = 0) }
            }
        }
    }

    fun onAppResume() {
        val s = _ui.value
        audio.resumeFromSuspend()
        if (s.screen == Screen.MATCH && s.phase == Phase.PLAYING && s.paused && !(s.talkBackOn && !gateOverridden)) {
            resumeMatch()
        }
        evaluateGate()
    }

    // ============================================================ match input (from the touch zones)
    private fun playing(): MatchEngine? {
        val s = _ui.value
        if (s.screen != Screen.MATCH || s.phase != Phase.PLAYING || s.paused) return null
        return engine
    }

    fun moveDown() {
        playing()?.touchDown()
    }

    fun moveUp() {
        engine?.touchUp()
    }

    fun flickTurn(dir: Int) {
        val e = playing() ?: return
        e.cancelMoveStart()
        e.turnFlick(dir)
    }

    fun flickJump() {
        val e = playing() ?: return
        e.cancelMoveStart()
        e.jump()
    }

    fun twoFinger() {
        playing()?.jump()
    }

    fun steer(deg: Float) {
        playing()?.steer(deg)
    }

    fun moveBack(b: Boolean) {
        playing()?.setBack(b)
    }

    fun fireDown() {
        playing()?.fireDown()
    }

    fun fireUp() {
        engine?.fireUp()
    }

    fun reload() {
        playing()?.reload()
    }

    fun switchWeapon() {
        playing()?.switchWeapon()
    }

    fun crouch() {
        playing()?.toggleCrouch()
    }

    fun scan() {
        playing()?.scan()
    }

    fun heal() {
        playing()?.heal()
    }

    // ============================================================ phone tilt (optional)
    fun onTilt(fwd: Float, right: Float) {
        lastFwd = fwd
        lastRight = right
        val s = _ui.value
        if (!s.sensorOn) return
        val e = playing() ?: return
        val df = fwd - neutralFwd
        e.setSensorWalk(if (df > 9f) 1 else if (df < -9f) -1 else 0)
        val dr = right - neutralRight
        if (!turnLatch) {
            if (dr > 15f) {
                e.turnFlick(1)
                turnLatch = true
            } else if (dr < -15f) {
                e.turnFlick(-1)
                turnLatch = true
            }
        } else if (abs(dr) < 8f) {
            turnLatch = false
        }
    }

    override fun onCleared() {
        stopMatchJobs()
        stopGateSpeech()
        exitJob?.cancel()
        audio.release()
        speech.shutdown()
        super.onCleared()
    }
}
