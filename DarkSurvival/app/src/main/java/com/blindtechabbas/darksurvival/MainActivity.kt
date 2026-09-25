package com.blindtechabbas.darksurvival

import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.blindtechabbas.darksurvival.audio.SoundManager
import com.blindtechabbas.darksurvival.audio.TtsManager
import com.blindtechabbas.darksurvival.game.GameViewModel
import com.blindtechabbas.darksurvival.game.Screen
import com.blindtechabbas.darksurvival.ui.navigation.AppNavHost
import com.blindtechabbas.darksurvival.ui.theme.DarkSurvivalTheme
import com.blindtechabbas.darksurvival.util.CrashLogger

class MainActivity : ComponentActivity() {

    private lateinit var soundManager: SoundManager
    private lateinit var ttsManager: TtsManager
    private val vm: GameViewModel by viewModels()

    // Phone tilt (accelerometer X axis, landscape) — second movement source.
    private lateinit var sensorManager: SensorManager
    private var tiltSensor: Sensor? = null
    private val tiltListener = object : SensorEventListener {
        override fun onSensorChanged(event: SensorEvent) {
            // Full vector — the ViewModel computes calibrated pitch/roll.
            val x = event.values.getOrNull(0) ?: 0f
            val y = event.values.getOrNull(1) ?: 0f
            val z = event.values.getOrNull(2) ?: 0f
            vm.onTilt(x, y, z)
        }

        override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        CrashLogger.init(applicationContext)
        soundManager = SoundManager(this)
        ttsManager = TtsManager(this)
        sensorManager = getSystemService(SENSOR_SERVICE) as SensorManager
        tiltSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

        // Announcements + Sensor Mode preferences (Settings toggles, persisted).
        val prefs = getSharedPreferences("darksurvival", MODE_PRIVATE)
        vm.syncAnnouncements(prefs.getBoolean("announcementsEnabled", true))
        vm.syncSensor(prefs.getBoolean("sensorEnabled", false))
        // Persisted calibration offsets — restored so neutral stays neutral.
        vm.syncSensorOffset(
            prefs.getFloat("sensorOffsetPitch", 0f),
            prefs.getFloat("sensorOffsetRoll", 0f)
        )

        // MULTI-TOUCH: splitMotionEvents=true on the root layout so the left
        // hand can hold movement gestures while the right hand taps buttons.
        val composeView = androidx.compose.ui.platform.ComposeView(this)
        val rootView = android.widget.FrameLayout(this).apply {
            isMotionEventSplittingEnabled = true // == android:splitMotionEvents="true"
            addView(composeView)
        }
        setContentView(rootView)
        composeView.setContent {
            DarkSurvivalTheme {
                val state by vm.uiState.collectAsStateWithLifecycle()

                // Persist the Settings toggles whenever they change.
                LaunchedEffect(state.announcementsEnabled) {
                    prefs.edit().putBoolean("announcementsEnabled", state.announcementsEnabled).apply()
                }
                LaunchedEffect(state.sensorEnabled) {
                    prefs.edit().putBoolean("sensorEnabled", state.sensorEnabled).apply()
                }
                LaunchedEffect(state.sensorOffsetPitch, state.sensorOffsetRoll) {
                    prefs.edit()
                        .putFloat("sensorOffsetPitch", state.sensorOffsetPitch)
                        .putFloat("sensorOffsetRoll", state.sensorOffsetRoll)
                        .apply()
                }

                // HQ WALKIE-TALKIE BRIEFING: dedicated MediaPlayer triggered on
                // EVERY match start (briefingEventId bumps in introEnd).
                LaunchedEffect(state.briefingEventId) {
                    if (state.briefingEventId > 0 && state.screen is Screen.Match) {
                        soundManager.playBriefing()
                    }
                }

                // ZONE 5 INTRUDER SIREN: loud siren + hostile radio chatter
                // looping ONLY while the player is inside the Enemy Camp;
                // starts on entry, stops on exit / match end / pause.
                LaunchedEffect(state.currentZone, state.screen, state.gameOver, state.victory) {
                    when {
                        state.screen is Screen.Match && !state.gameOver && !state.victory &&
                            state.currentZone == 4 -> soundManager.startSiren()
                        else -> soundManager.stopSiren()
                    }
                }

                // Zone ambience: 2.5s crossfade on every zone transition, with
                // river distance-attenuation (riverVol) applied to the river bed.
                LaunchedEffect(state.zoneEventId, state.riverVol) {
                    if (state.screen is Screen.Match && !state.gameOver && !state.victory) {
                        val zone = com.blindtechabbas.darksurvival.game.MAP_ZONES[state.currentZone]
                        soundManager.crossfadeTo(zone.ambKey, state.riverVol)
                    }
                }

                // Ambience is torn down on match end / app pause.
                LaunchedEffect(state.screen, state.chapterId, state.gameOver, state.victory) {
                    when {
                        state.gameOver || state.victory -> soundManager.stopAll()
                        state.screen !is Screen.Match -> soundManager.stopAmbience()
                    }
                }

                // SFX: reacts ONLY to soundEventId — game-loop footsteps can
                // never re-trigger a voice line.
                LaunchedEffect(state.soundEventId) {
                    if (state.lastSound.isNotBlank()) {
                        when (state.lastSound) {
                            "footstep" -> soundManager.playFootstep()
                            // Finger lifted: the step stream is cut the exact
                            // millisecond movement stops — zero trailing tail.
                            "stopstep" -> soundManager.stopStep()
                            else -> soundManager.playSpatial(state.lastSound, state.soundPan, state.soundVolume)
                        }
                    }
                }

                // TTS: speaks ONLY when a NEW announcement arrives (the id
                // changes). The engine itself also drops identical lines
                // within 1s — a line is spoken exactly once, never repeated.
                LaunchedEffect(state.announceEventId) {
                    if (state.lastAnnouncement.isNotBlank()) {
                        ttsManager.speak(state.lastAnnouncement)
                    }
                }

                // Warm up the TTS engine at launch so the first tap has no lag.
                LaunchedEffect(Unit) {
                    ttsManager.warmUp("Dark Survival")
                }

                AppNavHost(vm)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        tiltSensor?.let {
            sensorManager.registerListener(tiltListener, it, SensorManager.SENSOR_DELAY_GAME)
        }
        vm.resumeAll()
    }

    /**
     * Home button / app switch: EVERYTHING goes silent here, not only in
     * onDestroy. The match loop is cancelled and the sound pool pauses.
     */
    override fun onPause() {
        sensorManager.unregisterListener(tiltListener)
        vm.pauseAll()
        soundManager.stopAll()
        super.onPause()
    }

    override fun onDestroy() {
        super.onDestroy()
        sensorManager.unregisterListener(tiltListener)
        soundManager.release()
        ttsManager.shutdown()
    }
}
