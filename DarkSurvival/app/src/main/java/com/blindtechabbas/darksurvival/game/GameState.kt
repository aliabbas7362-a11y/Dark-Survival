package com.blindtechabbas.darksurvival.game

/** App screens (simple state-based navigation, no extra dependency). */
sealed class Screen {
    object MainMenu : Screen()
    object ChapterSelect : Screen()
    data class Match(val chapterId: Int) : Screen()
    object Settings : Screen()
}

data class GameUiState(
    val player: Player = Player(),
    val enemies: List<Enemy> = emptyList(),
    val chapterId: Int = 1,
    val unlockedChapters: Set<Int> = setOf(1),
    val screen: Screen = Screen.MainMenu,
    val lastAnnouncement: String = "",
    val lastAnnounceMs: Long = 0L,
    /** Bumped ONLY when a NEW announcement is made — TTS speaks on change. */
    val announceEventId: Long = 0,
    val lastSound: String = "",
    val soundPan: Float = 0f,
    val soundVolume: Float = 1f,
    /** Bumped ONLY when a sound plays — independent of announcements. */
    val soundEventId: Long = 0,
    val soundEnabled: Boolean = true,
    /** Sensor Mode: device tilt steers lanes (Settings toggle, persisted). */
    val sensorEnabled: Boolean = false,
    /** v2.8 CALIBRATION: captured neutral pitch/roll — everything relative
     *  to this is idle. Persisted; the Calibrate button resets it. */
    val sensorOffsetPitch: Float = 0f,
    val sensorOffsetRoll: Float = 0f,
    /** Master switch for ALL voice announcements (Settings toggle, persisted). */
    val announcementsEnabled: Boolean = true,
    /** PUBG-style heli + parachute intro — gameplay input unlocks after it. */
    val intro: Boolean = false,
    val kills: Int = 0,
    /** v2.4 — single source of truth: remaining = TOTAL_ENEMIES − killed. */
    val killedEnemies: Int = 0,
    /** Bumped ONLY on an enemy death transition (drives the death haptic). */
    val deathEventId: Long = 0,
    /** Bumped on match start — drives the dedicated HQ briefing player. */
    val briefingEventId: Long = 0,
    val gameOver: Boolean = false,
    /** WIN: all waves eliminated — mirrors gameOver but a result screen. */
    val victory: Boolean = false,
    /** Seconds the match lasted (set on win/lose for the result screens). */
    val elapsedSec: Int = 0,
    val matchStartMs: Long = 0L,
    /** Total enemies across ALL waves this match. */
    val totalEnemies: Int = 0,
    /** Current map zone index (0..4) along the linear path. */
    val currentZone: Int = 0,
    /** Bumped ONLY on a zone transition — ambience crossfade reacts to it. */
    val zoneEventId: Long = 0,
    /** River distance-attenuation volume (1 near Zone 2, fades away from it). */
    val riverVol: Float = 1f,
    /** v2.5 GRENADE HAZARD: lane index the grenade landed in (null = none). */
    val grenadeLane: Int? = null,
    /** Absolute time when the grenade explodes (2.0s dodge window). */
    val grenadeExplodeAt: Long = 0L,
    /** Bumped on grenade spawn — drives the pulsing haptic alert. */
    val grenadeEventId: Long = 0
)
