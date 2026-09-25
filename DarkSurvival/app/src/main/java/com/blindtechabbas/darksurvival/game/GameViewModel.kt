package com.blindtechabbas.darksurvival.game

import android.os.SystemClock
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.random.Random

/**
 * v1.7 — ANNOUNCEMENT FIX + CHAPTER-CLEAR FIX.
 *
 * Announcements (the spam bug, root-caused):
 *  - Sounds and announcements shared ONE event counter. The 700ms game loop
 *    bumped it for enemy footsteps, so the UI re-read the OLD announcement
 *    and re-spoke it ("Action performed… Action performed…"). Fixed: sounds
 *    and announcements now have SEPARATE counters, and the UI speaks only
 *    when announceEventId changes (true transition, never per-frame).
 *  - announce() also refuses to re-emit the same text within 2.5s at the
 *    state level, and TtsManager drops identical lines within 1s at the
 *    engine level. Triple protection — a line can only ever be spoken once.
 *
 * Chapter clear (the "killed enemies but no clear" bug, root-caused):
 *  - Enemies were killed by health reaching 0 but the DEAD enemy stayed in
 *    the list with health=0 forever; checkVictory() requires ALL cleared.
 *    It did run, but if even ONE enemy (e.g. Large Animal, 100 HP) survived
 *    at distance, nothing happened and there was no feedback. Fixed:
 *    victory check is exact, and progress is announced after every kill
 *    ("2 of 4 enemies down") so the player always knows what is left.
 */
class GameViewModel : ViewModel() {

    private val _uiState = MutableStateFlow(GameUiState())
    val uiState: StateFlow<GameUiState> = _uiState.asStateFlow()

    private var fireJob: Job? = null
    private var tickJob: Job? = null
    private var introJob: Job? = null
    private var walkJob: Job? = null
    private var heartbeatTick = 0
    private val spokenKeys = mutableSetOf<String>()
    private val announcedApproach = mutableSetOf<Int>()
    private val announcedTelegraph = mutableSetOf<Int>()
    private val lastDamageTick = mutableMapOf<Int, Long>()
    private var lastPitchDeg = 0f
    private var lastRollDeg = 0f
    private var tiltWalking = false // tilt-initiated walk (auto-stops in deadzone)
    private var lastTiltMoveAt = 0L
    // ---- Zone + wave system (v2.3) ----
    private var wavePlan: List<Int> = emptyList()
    private var wavesSpawned = 0
    private var nextEnemyId = 0

    // ---- Navigation ----
    fun goToMainMenu() {
        stopMatch()
        _uiState.update { it.copy(screen = Screen.MainMenu) }
        playSound("click")
        announce("Main menu")
    }

    fun goToChapterSelect() {
        stopMatch()
        _uiState.update { it.copy(screen = Screen.ChapterSelect) }
        playSound("click")
        announce("Chapter select")
    }

    fun goToSettings() {
        _uiState.update { it.copy(screen = Screen.Settings) }
        playSound("click")
        announce("Settings")
    }

    fun goToMatch(chapterId: Int) {
        stopMatch()
        spokenKeys.clear()
        announcedApproach.clear()
        announcedTelegraph.clear()
        lastDamageTick.clear()
        lastPitchDeg = 0f
        lastRollDeg = 0f
        tiltWalking = false
        heartbeatTick = 0
        // v2.4 — FIXED 13-enemy spawn table: Zone1=1, Zone2=2, Zone3=3,
        // Zone4=3, Zone5=4 (sum asserted == TOTAL_ENEMIES at init).
        wavePlan = (0..4).map { z -> ZONE_SPAWNS.count { it.first == z } }
        check(wavePlan.sum() == TOTAL_ENEMIES) { "Zone spawn table must sum to $TOTAL_ENEMIES" }
        wavesSpawned = 0
        nextEnemyId = 0
        val startMs = System.currentTimeMillis()
        _uiState.update {
            it.copy(
                screen = Screen.Match(chapterId),
                chapterId = chapterId,
                player = Player(),
                enemies = emptyList(),
                totalEnemies = TOTAL_ENEMIES,
                kills = 0,
                killedEnemies = 0,
                gameOver = false,
                victory = false,
                matchStartMs = startMs,
                elapsedSec = 0,
                intro = true,
                currentZone = 0,
                riverVol = 1f
            )
        }
        // PUBG-style intro: helicopter + parachute descent (7s, skippable).
        // Gameplay input unlocks when intro ends.
        playSound("heli")
        announceOnce("heli", "Boarding helicopter. Jumping into the drop zone.")
        introJob?.cancel()
        introJob = viewModelScope.launch {
            delay(8000)
            introEnd()
        }
        tickJob = viewModelScope.launch {
            while (isActive) {
                delay(700)
                tick()
            }
        }
    }

    fun restart() = goToMatch(_uiState.value.chapterId)

    /** Skip the heli/parachute intro — tap anywhere on the intro screen. */
    fun skipIntro() {
        introJob?.cancel()
        introEnd()
    }

    private fun introEnd() {
        if (_uiState.value.intro) {
            _uiState.update {
                it.copy(intro = false, briefingEventId = it.briefingEventId + 1)
            }
            // v2.6 — briefing now plays on its own DEDICATED MediaPlayer
            // (MainActivity reacts to briefingEventId) — never cut by the
            // SoundPool stream limit, so it fires EVERY match start.
            announceOnce(
                "deployed",
                "HQ: Agent, drop point reached. Move towards the River Bank, clear all hostile zones, and destroy the Enemy Camp at the ridge. Out."
            )
        }
    }

    fun selectChapter(id: Int) {
        if (id in _uiState.value.unlockedChapters) goToMatch(id)
    }

    // ---- Announcements master switch (Settings toggle, persisted) ----
    /** Called once at startup: sync the persisted preference into state. */
    fun syncAnnouncements(enabled: Boolean) {
        _uiState.update { it.copy(announcementsEnabled = enabled) }
    }

    fun toggleAnnouncements() {
        val newVal = !_uiState.value.announcementsEnabled
        _uiState.update { it.copy(announcementsEnabled = newVal) }
        playSound("click")
        if (newVal) announce("Announcements on") // OFF = total silence, no voice
    }

    // ---- Sound toggle ----
    fun toggleSound() {
        val newVal = !_uiState.value.soundEnabled
        _uiState.update { it.copy(soundEnabled = newVal) }
        if (newVal) {
            playSound("click")
            announce("Sound on")
        } else {
            announce("Sound off")
        }
    }

    // ---- Combat: lane-based firing ----
    fun fire(announceShot: Boolean = true) {
        val s = _uiState.value
        if (s.gameOver || s.victory || s.intro) return
        if (s.player.ammo <= 0) {
            playSound("click")
            if (announceShot) announceOnce("noammo", "No ammo. Reload.")
            return
        }
        val lane = s.player.lane
        val before = s.enemies
        _uiState.update {
            it.copy(
                player = it.player.copy(ammo = it.player.ammo - 1),
                enemies = it.enemies.map { e ->
                    if (e.lane == lane && e.health > 0) {
                        e.copy(health = (e.health - it.player.weapon.damage).coerceAtLeast(0))
                    } else {
                        e
                    }
                }
            )
        }
        val after = _uiState.value.enemies
        val killedCount = before.count { e ->
            e.lane == lane && e.health > 0 && after.none { it.id == e.id && it.health > 0 }
        }
        playSound(s.player.weapon.fireSound)
        // Gunshot noise: every living enemy in this zone shifts to HUNT mode
        // and starts moving toward the player's coordinates (v2.4).
        if (killedCount > 0) {
            // ROOT-CAUSE FIX (glitched "0 of 11 left"): the old line counted
            // remaining from ONLY the currently-spawned wave — dead enemies
            // from earlier waves and not-yet-spawned ones were invisible.
            // Now killedEnemies is the single transition-based counter and
            // remaining = TOTAL_ENEMIES(13) − killed, always correct.
            val killed = (s.killedEnemies + killedCount).coerceAtMost(TOTAL_ENEMIES)
            val remaining = (TOTAL_ENEMIES - killed).coerceAtLeast(0)
            _uiState.update {
                it.copy(
                    kills = killed,
                    killedEnemies = killed,
                    deathEventId = it.deathEventId + killedCount // drives death haptic
                )
            }
            viewModelScope.launch { delay(140); playSound("death") }
            announce("Enemy eliminated! $remaining remaining.")
            // Hunt alert: the gunfire reveals the player's position.
            _uiState.update { st ->
                st.copy(
                    enemies = st.enemies.map { e ->
                        if (e.health > 0 && e.zone == st.currentZone && e.aiState != AiState.HUNT) {
                            e.copy(aiState = AiState.HUNT)
                        } else {
                            e
                        }
                    }
                )
            }
            checkVictory()
        }
    }

    /** Hold-to-fire: continuous shots at the weapon's fire rate (MG is fast). */
    fun startFire() {
        fireJob?.cancel()
        fireJob = viewModelScope.launch {
            while (isActive) {
                if (_uiState.value.player.ammo <= 0) {
                    playSound("click")
                    break
                }
                fire(announceShot = false)
                delay(rateMs(_uiState.value.player.weapon))
            }
        }
    }

    fun stopFire() {
        fireJob?.cancel()
    }

    /** Called by the Activity in onPause — Home button silences everything.
     *  Grenade timers also die with the tick loop — no leaks/crashes. */
    fun pauseAll() {
        stopWalking()
        stopMatch()
        _uiState.update { it.copy(grenadeLane = null) }
    }

    /** Called by the Activity in onResume after a pause — loop resumes. */
    fun resumeAll() {
        val s = _uiState.value
        if (s.screen is Screen.Match && !s.gameOver && !s.victory && s.enemies.isNotEmpty()) {
            tickJob = viewModelScope.launch {
                while (isActive) {
                    delay(700)
                    tick()
                }
            }
        }
    }

    private fun rateMs(w: WeaponType): Long = when (w) {
        WeaponType.MACHINE_GUN -> 140L
        WeaponType.PISTOL -> 300L
        WeaponType.SHOTGUN -> 450L
        WeaponType.SNIPER -> 700L
    }

    fun reload() {
        _uiState.update { it.copy(player = it.player.copy(ammo = it.player.weapon.maxAmmo)) }
        playSound("reload")
        announceOnce("reload", "Reloaded")
    }

    fun switchWeapon(next: Boolean) {
        val order = WeaponType.entries
        val cur = _uiState.value.player.weapon
        val idx = order.indexOf(cur)
        val newIdx = if (next) (idx + 1) % order.size else (idx - 1 + order.size) % order.size
        val newWeapon = order[newIdx]
        _uiState.update {
            it.copy(player = it.player.copy(weapon = newWeapon, ammo = newWeapon.maxAmmo))
        }
        playSound("click")
        announce("${newWeapon.displayName} selected")
    }

    // ---- Movement: lane change / advance / retreat (user-initiated only) ----
    fun moveLane(delta: Int) {
        val s = _uiState.value
        // Guards: no movement after win/lose, none during the heli intro —
        // so no ghost footsteps on the result screens.
        if (s.gameOver || s.victory || s.intro || s.screen !is Screen.Match) return
        val now = System.currentTimeMillis()
        val newLane = s.player.lane.move(delta)
        if (newLane == s.player.lane) return
        _uiState.update {
            it.copy(player = it.player.copy(lane = newLane, dodgeUntilMs = now + 700L))
        }
        playSound("footstep")
        // Direction cue: if an enemy is engaging in this lane, say so — fire now.
        val foe = _uiState.value.enemies.firstOrNull { it.health > 0 && it.lane == newLane && it.distance < 0.8f }
        announce(
            if (foe != null) "${newLane.label()} lane. Enemy ${foe.type.displayName} ${newLane.label()}. Fire!"
            else "Moved ${newLane.label()}"
        )
    }

    // ---- Continuous walking (v2.1): hold = forward, drag down = back ----
    private fun canMove(): Boolean {
        val s = _uiState.value
        // v2.5 CROUCH MOVEMENT LOCK: while crouching, forward/backward speed
        // is strictly 0 — the walk loops re-check this every step and halt.
        return s.screen is Screen.Match && !s.gameOver && !s.victory && !s.intro && !s.player.crouching
    }

    private fun advanceStep(forward: Boolean) {
        if (!canMove()) return
        val s = _uiState.value
        // Linear path progress: 0 = drop point, 100 = enemy camp (v2.3).
        // OUT-OF-BOUNDS WALL: progress clamps at 100 — the invisible boundary
        // at the end of Zone 5. Pushing into it announces once.
        val newProgress = (s.player.progress + if (forward) 1 else -1).coerceIn(0, 100)
        if (forward && s.player.progress == 100 && newProgress == 100) {
            announceOnce(
                "oob",
                "The mission area ends here. Reach the extraction radio or eliminate the camp guards."
            )
            return
        }
        _uiState.update {
            it.copy(
                player = it.player.copy(
                    progress = newProgress,
                    dodgeUntilMs = System.currentTimeMillis() + 400L
                ),
                riverVol = riverVolumeAt(newProgress)
            )
        }
        playSound("footstep")
        // Footstep NOISE EVENT: patrolling enemies in this zone hear it and
        // shift to ALERT — they leave patrol and search toward the player.
        _uiState.update { st ->
            st.copy(
                enemies = st.enemies.map { e ->
                    if (e.health > 0 && e.zone == st.currentZone && e.aiState == AiState.PATROL) {
                        e.copy(aiState = AiState.ALERT)
                    } else {
                        e
                    }
                }
            )
        }
        updateZone()
        checkVictory() // extraction trigger can fire on reaching Zone 5's end
    }

    /** River distance attenuation: full volume at the stream (Zone 2,
     *  progress ~27), fading linearly as the player walks away from it. */
    private fun riverVolumeAt(p: Int): Float =
        (1f - (kotlin.math.abs(p - 27) / 35f)).coerceIn(0.15f, 1f)

    // ---- Zone system (v2.3): linear path, one-shot triggers ----
    /** Fires ONCE per zone on correct forward/backward entry; announces via
     *  TTS and spawns that zone's enemy wave. */
    private fun updateZone() {
        val s = _uiState.value
        val z = zoneForProgress(s.player.progress)
        if (z == s.currentZone) return
        _uiState.update {
            it.copy(currentZone = z, zoneEventId = it.zoneEventId + 1)
        }
        announceOnce("zone$z", "Entering ${MAP_ZONES[z].name} area")
        spawnWave(z)
    }

    /** FIXED 13-enemy zone spawns straight from the ZONE_SPAWNS table —
     *  weapons stay random (Knife/Axe/Bat/Pistol); archetype per zone spec. */
    private fun spawnWave(zoneId: Int) {
        val specs = ZONE_SPAWNS.filter { it.first == zoneId }
        if (specs.isEmpty()) return
        wavesSpawned++
        val newEnemies = specs.map { (_, arch) ->
            val w = EnemyWeaponType.entries.random()
            Enemy(
                id = nextEnemyId++,
                type = EnemyType.HUMAN,
                health = (EnemyType.HUMAN.health * arch.hpMul).toInt(),
                distance = 1.3f + Random.nextFloat() * 0.4f,
                lane = Lane.entries.random(),
                weapon = w,
                archetype = arch,
                speed = (0.03f + Random.nextFloat() * 0.03f) * w.speedMul * arch.speedMul,
                attackInterval = 1.6f + Random.nextFloat() * 1.8f,
                zone = zoneId,
                aiState = AiState.PATROL
            )
        }
        _uiState.update { it.copy(enemies = it.enemies + newEnemies) }
        announceOnce("wave$zoneId", "${specs.size} enemies in the ${MAP_ZONES[zoneId].name}")
    }

    /** HOLD on the move zone: continuous forward walking, footstep every 500ms.
     *  Free movement — the player walks as long as the finger stays down. */
    fun startWalking() {
        walkJob?.cancel()
        walkJob = viewModelScope.launch {
            while (isActive && canMove()) {
                advanceStep(true)
                delay(500)
            }
        }
    }

    /** DRAG DOWN while holding: continuous backward walking. */
    fun startWalkingBack() {
        walkJob?.cancel()
        walkJob = viewModelScope.launch {
            while (isActive && canMove()) {
                advanceStep(false)
                delay(560)
            }
        }
    }

    /** Release the hold — walking stops. The player NEVER moves by itself. */
    fun stopWalking() {
        walkJob?.cancel()
        walkJob = null
        // Anti-dragging: the step stream is cut the exact millisecond the
        // movement stops — MainActivity routes this to SoundManager.stopStep().
        playSound("stopstep")
    }

    // ---- Sensor Mode (Settings toggle, persisted, CALIBRATED) ----
    fun syncSensor(enabled: Boolean) {
        _uiState.update { it.copy(sensorEnabled = enabled) }
    }

    fun syncSensorOffset(pitch: Float, roll: Float) {
        _uiState.update { it.copy(sensorOffsetPitch = pitch, sensorOffsetRoll = roll) }
    }

    /**
     * CALIBRATE: capture the CURRENT phone angle as the 0-degree neutral
     * point. Also auto-runs when Sensor Mode is switched ON — so activating
     * the sensor in a comfortable landscape hold is instantly idle and the
     * old "player runs on activation" bug is impossible.
     */
    fun calibrateSensor() {
        _uiState.update {
            it.copy(sensorOffsetPitch = lastPitchDeg, sensorOffsetRoll = lastRollDeg)
        }
        playSound("click")
        announce("Sensor calibrated. Current position is neutral.")
    }

    fun toggleSensor() {
        val v = !_uiState.value.sensorEnabled
        _uiState.update { it.copy(sensorEnabled = v) }
        playSound("click")
        if (v) {
            // Auto-neutralize on activation — no runaway movement.
            _uiState.update {
                it.copy(sensorOffsetPitch = lastPitchDeg, sensorOffsetRoll = lastRollDeg)
            }
            announce("Sensor mode on. Current position set as neutral. Tilt forward to walk, left or right past 15 degrees to change lanes.")
        } else {
            tiltWalking = false
            announce("Sensor mode off.")
        }
    }

    /**
     * v2.8 CALIBRATED SENSOR STEERING — receives raw accelerometer vectors
     * and converts them to pitch/roll degrees relative to the CALIBRATED
     * neutral offset:
     *  - pitch forward (top away) beyond ~9 deg  = walk forward
     *  - pitch backward (top towards) beyond 9   = walk backward
     *  - roll left/right beyond 15 deg           = lane change (wrapped)
     * Inside the deadzones = strictly idle (no movement).
     */
    fun onTilt(x: Float, y: Float, z: Float) {
        val pitchDeg = Math.toDegrees(Math.atan2(y.toDouble(), Math.sqrt((x * x + z * z).toDouble()))).toFloat()
        val rollDeg = Math.toDegrees(Math.atan2(x.toDouble(), z.toDouble())).toFloat()
        lastPitchDeg = pitchDeg
        lastRollDeg = rollDeg

        val s = _uiState.value
        val active = s.sensorEnabled && s.screen is Screen.Match && !s.gameOver && !s.victory && !s.intro
        if (!active) {
            if (tiltWalking) {
                tiltWalking = false
                stopWalking()
            }
            return
        }
        val effPitch = pitchDeg - s.sensorOffsetPitch
        val effRoll = rollDeg - s.sensorOffsetRoll
        val now = System.currentTimeMillis()

        // LANE: beyond 15 degrees roll (rate-limited), wrap-around lanes.
        if (now - lastTiltMoveAt > 350L && Math.abs(effRoll) > 15f) {
            lastTiltMoveAt = now
            moveLane(if (effRoll > 0f) 1 else -1)
        }

        // FORWARD/BACK: pitch beyond the 9-degree deadzone; inside it = idle.
        if (effPitch > 9f) {
            if (!tiltWalking) {
                tiltWalking = true
                startWalking()
            }
        } else if (effPitch < -9f) {
            if (!tiltWalking) {
                tiltWalking = true
                startWalkingBack()
            }
        } else if (tiltWalking) {
            tiltWalking = false
            stopWalking()
        }
    }

    // ---- Action: REAL crouch / stand toggle (replaces the dummy "action") ----
    fun action() = toggleCrouch()

    /** Press once = sit down, press again = stand up. While crouched the
     *  enemies' detection radius shrinks (sneaking advantage) AND all
     *  walking is LOCKED (speed 0) until you explicitly stand up.
     *  Audio: crouch = heavy cloth rustle; stand = boot plant + gear clink. */
    fun toggleCrouch() {
        val s = _uiState.value
        val nowCrouch = !s.player.crouching
        if (nowCrouch) stopWalking() // sit down halts any walk instantly
        _uiState.update { it.copy(player = it.player.copy(crouching = nowCrouch)) }
        playSound(if (nowCrouch) "crouch" else "stand")
        announce(if (nowCrouch) "Crouching. Movement locked until you stand up." else "Standing up. Movement unlocked.")
    }

    /** Ration (consumable, long-press RELOAD): +30 health, full stamina. */
    fun eatRation() {
        val s = _uiState.value
        if (s.player.rationCount > 0) {
            _uiState.update {
                it.copy(
                    player = it.player.copy(
                        health = (it.player.health + 30).coerceAtMost(it.player.maxHealth),
                        stamina = 100,
                        rationCount = it.player.rationCount - 1
                    )
                )
            }
            playSound("pickup")
            announce("Ate ration. ${_uiState.value.player.rationCount} left")
        } else {
            playSound("click")
            announceOnce("noration", "No ration left")
        }
    }

    // ---- Game engine loop ----
    private fun tick() {
        val s = _uiState.value
        if (s.gameOver || s.victory || s.intro || s.enemies.isEmpty()) return
        val now = System.currentTimeMillis()
        heartbeatTick++
        val playerLane = s.player.lane
        val dodging = now < s.player.dodgeUntilMs

        // Enemies approach at THEIR OWN speed — AI STATE MACHINE (v2.4):
        // PATROL  = slow drift, passive until it hears/sees something
        // ALERT   = heard footsteps nearby — searching, moving toward player
        // HUNT    = gunshot heard — fast pursuit of the player's position
        val moved = s.enemies.map { e ->
            if (e.health <= 0) {
                e
            } else {
                val st2 = if (e.aiState == AiState.PATROL && e.distance < 0.75f) AiState.ALERT else e.aiState
                val spd = when (st2) {
                    AiState.HUNT -> e.speed * 1.2f
                    AiState.ALERT -> e.speed
                    AiState.PATROL -> e.speed * 0.15f
                }
                e.copy(distance = (e.distance - spd).coerceAtLeast(0.06f), aiState = st2)
            }
        }

        // Enemy footsteps: panned by lane + distance volume, every 2nd tick.
        // Sounds ONLY — no announcement here, so nothing repeats per tick.
        if (heartbeatTick % 2 == 0) {
            moved.filter { it.health > 0 && it.distance < 0.9f }.forEach { e ->
                val pan = when (e.lane) {
                    Lane.LEFT -> -0.8f
                    Lane.RIGHT -> 0.8f
                    Lane.CENTER -> 0f
                }
                playSound("enemy_step", pan, (1f - e.distance).coerceIn(0.3f, 1f))
            }
            if (moved.any { it.health > 0 && it.distance < 0.3f }) playSound("heartbeat")
        }
        // IDLE FOLEY (v2.6): even standing enemies make presence noise —
        // hostile grunts, gear rustle, heavy breathing — panned by lane and
        // attenuated by distance, so the player HEARS an enemy nearby in
        // every zone, moving or not.
        if (heartbeatTick % 7 == 3) {
            val idle = moved.filter { it.health > 0 && it.distance < 0.85f }
            if (idle.isNotEmpty()) {
                val e = idle.random()
                val pan = when (e.lane) {
                    Lane.LEFT -> -0.85f
                    Lane.RIGHT -> 0.85f
                    Lane.CENTER -> 0f
                }
                playSound(listOf("grunt", "gear", "breath").random(), pan, (1f - e.distance).coerceIn(0.4f, 1f))
            }
        }

        // Crouching shrinks every enemy's detection radius (sneak advantage).
        val stealth = if (s.player.crouching) 0.6f else 1f

        // One-time lane announcement per enemy — at ITS WEAPON's range,
        // naming the archetype + weapon (all combinations sound different).
        moved.filter { it.health > 0 && it.distance < it.weapon.range * stealth && announcedApproach.add(it.id) }.forEach { e ->
            announceOnce("approach${e.id}", "${e.archetype.displayName} enemy with ${e.weapon.displayName} in ${e.lane.label()} lane")
        }

        // Attack telegraph: once per enemy, at ITS OWN detect radius,
        // ~2s before real damage. The enemy fires ITS OWN weapon so every
        // attacker sounds different (pistol crack / shotgun boom / sniper / MG).
        val telegraphed = moved.map { e ->
            if (e.health > 0 && e.distance < e.weapon.range * stealth && announcedTelegraph.add(e.id)) {
                playSound(e.weapon.fireSound)
                announceOnce("telegraph${e.id}", "${e.archetype.displayName} with ${e.weapon.displayName} ${e.lane.label()} attacking. Move to dodge!")
                e.copy(attackStartMs = now)
            } else {
                e
            }
        }

        // Damage: only if the player is STILL in the enemy's lane 2s after the
        // telegraph. Changing lane dodges it — a fair chance to survive.
        var health = s.player.health
        var gotHit = false
        val attackers = telegraphed.filter {
            it.health > 0 && it.distance < it.weapon.range * 0.9f && it.lane == playerLane && !dodging
        }
        attackers.forEach { e ->
            val grace = if (e.attackStartMs > 0L) now - e.attackStartMs else Long.MAX_VALUE
            // Per-enemy randomized attack interval — no shared rhythm.
            if (grace >= 2000L && (now - (lastDamageTick[e.id] ?: 0L)) > (e.attackInterval * 1000).toLong()) {
                gotHit = true
                lastDamageTick[e.id] = now
            }
        }
        if (gotHit) {
            val dmg = (attackers.sumOf { (it.weapon.dmg * it.archetype.dmgMul).toInt() } / 2).coerceIn(3, 18)
            health = (health - dmg).coerceAtLeast(0)
            playSound("enemy_shot")
            announce("Hit! Health ${health}")
        }

        // ---- GRENADE HAZARD (v2.5): audio-first dodge mechanic ----
        // Phase 1: enemy voice line ("Grenade out!") + metallic pin-pull.
        // Phase 2: metallic bounce HARD-PANNED to the lane it landed in.
        // Phase 3: pulsing haptic alert (MatchScreen reacts to grenadeEventId).
        // Phase 4: 2.0s dodge window — be in a DIFFERENT lane at detonation or
        //          take heavy AOE damage + 3s tinnitus ringing.
        var gLane = s.grenadeLane
        var gExplodeAt = s.grenadeExplodeAt
        var gEvent = s.grenadeEventId
        if (gLane == null) {
            val engaged = telegraphed.any { it.health > 0 && it.distance < it.weapon.range * 1.2f }
            if (engaged && Random.nextInt(100) < 8) {
                gLane = if (Random.nextBoolean()) 0 else 2 // LEFT or RIGHT only
                gExplodeAt = now + 2000L
                gEvent = s.grenadeEventId + 1
                playSound("grenadecall")
                playSound("pin")
                playSound("bounce", if (gLane == 0) -0.9f else 0.9f, 1f)
                announce("Grenade ${if (gLane == 0) "left" else "right"} side! Move away now!")
            }
        } else if (now >= gExplodeAt) {
            val dodged = playerLane.ordinal != gLane
            playSound("explosion")
            if (!dodged) {
                playSound("tinnitus")
                health = (health - 30).coerceAtLeast(0)
                gotHit = true
                announce("Grenade hit! Health ${health}")
            } else {
                announce("Grenade dodged!")
            }
            gLane = null
        }

        val dead = health <= 0
        val elapsed = ((now - s.matchStartMs) / 1000L).toInt()
        _uiState.update {
            it.copy(
                enemies = telegraphed,
                player = it.player.copy(
                    health = health,
                    dodgeUntilMs = if (gotHit) now + 800L else it.player.dodgeUntilMs
                ),
                gameOver = dead,
                elapsedSec = elapsed,
                grenadeLane = gLane,
                grenadeExplodeAt = gExplodeAt,
                grenadeEventId = gEvent
            )
        }
        if (dead) {
            walkJob?.cancel()
            playSound("lose")
            announceOnce("gameover", "Survival incomplete. You were killed after ${s.kills} kills.")
            tickJob?.cancel()
        } else {
            checkVictory()
        }
    }

    /**
     * Core rule: ALL enemies dead = SURVIVE COMPLETE (win). The old flow
     * silently "unlocked" the next chapter while staying on the empty map
     * with just river ambience — that is removed. Win now tears everything
     * down exactly like game over: loop stopped, win screen shown.
     */
    /**
     * v2.4 — MATCH COMPLETION with BOTH win conditions:
     *  1. CLEAR ALL: remainingEnemies == 0 → "Mission Accomplished!"
     *  2. EXTRACTION: at the end of Zone 5 (progress 100) with all four camp
     *     guards' wave cleared behind (killed >= 9), the extraction radio
     *     triggers victory even with camp stragglers alive — you escaped.
     * The old "endless walk" is gone: progress clamps at 100 (invisible
     * wall) and victory fires from either condition.
     */
    private fun checkVictory() {
        val s = _uiState.value
        if (s.victory || s.gameOver) return
        val campGuards = ZONE_SPAWNS.count { it.first == 4 } // Enemy Camp (0-based zone)
        when {
            s.killedEnemies >= TOTAL_ENEMIES ->
                win("Mission accomplished! All enemies eliminated.")
            s.player.progress >= 100 && s.killedEnemies >= TOTAL_ENEMIES - campGuards ->
                win("Extraction helicopter reached. Mission accomplished.")
        }
    }

    private fun win(message: String) {
        val s = _uiState.value
        val elapsed = ((System.currentTimeMillis() - s.matchStartMs) / 1000L).toInt()
        walkJob?.cancel()
        _uiState.update {
            it.copy(victory = true, enemies = emptyList(), elapsedSec = elapsed)
        }
        playSound("win")
        announceOnce("victory", message)
        tickJob?.cancel()
    }

    private fun stopMatch() {
        fireJob?.cancel(); fireJob = null
        tickJob?.cancel(); tickJob = null
    }

    /**
     * State-level dedupe: the same text is not re-emitted within 2.5s, and
     * only a NEW line bumps announceEventId — the UI speaks on that change.
     */
    fun announce(text: String) {
        val now = SystemClock.uptimeMillis()
        val s = _uiState.value
        // MASTER GATE: when the Settings toggle is OFF, no announcement is
        // ever emitted — this is the single entry point, so OFF kills every
        // in-game voice line (approach, telegraph, hits, UI) at the source.
        if (!s.announcementsEnabled) return
        if (text == s.lastAnnouncement && now - s.lastAnnounceMs < 2500) return
        _uiState.update {
            it.copy(
                lastAnnouncement = text,
                lastAnnounceMs = now,
                announceEventId = it.announceEventId + 1
            )
        }
    }

    /** Speak once per key per match (start, per-enemy cues, victory…). */
    private fun announceOnce(key: String, text: String) {
        if (key in spokenKeys) return
        spokenKeys += key
        announce(text)
    }

    /** Sounds bump ONLY soundEventId — never touches the announcement. */
    private fun playSound(key: String, pan: Float = 0f, volume: Float = 1f) {
        if (!_uiState.value.soundEnabled) return
        _uiState.update {
            it.copy(lastSound = key, soundPan = pan, soundVolume = volume, soundEventId = it.soundEventId + 1)
        }
    }

    /** 10-13 enemies split across the 4 combat zones (index = zone id). */
    override fun onCleared() {
        stopMatch()
        super.onCleared()
    }
}
