package com.blindtechabbas.darksurvival.game

import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

/**
 * MatchEngine - the whole game world, with no Android code in it.
 * The view model calls update() every 100 ms and then drain() to collect the
 * events (sounds, spoken lines, vibration). The engine never plays anything.
 *
 * Coordinates: x across (-150..150 feet), y along the path (0..1000 feet).
 * heading 0 = facing +y (toward the Enemy Camp), positive = turning right.
 */
class MatchEngine(private val rng: Random = Random(System.nanoTime())) {

    // ------------------------------------------------------------ player
    var px: Float = 0f
        private set
    var py: Float = 5f
        private set
    var heading: Float = 0f
        private set
    var hp: Int = Cfg.START_HP
        private set
    var weapon: WeaponType = WeaponType.PISTOL
        private set
    var rations: Int = Cfg.START_RATIONS
        private set
    var crouching: Boolean = false
        private set
    var nowMs: Long = 0L
        private set
    var over: Boolean = false
        private set
    var won: Boolean = false
        private set
    var kills: Int = 0
        private set
    var zoneId: Int = 0
        private set

    private val mag = IntArray(WeaponType.values().size)
    private val reserve = IntArray(WeaponType.values().size)

    // ------------------------------------------------------------ input state
    private var moveHeld = false
    private var moveHeldAt = 0L
    private var moveCancelled = false
    private var backMode = false
    private var fireHeld = false
    private var sensorWalk = 0

    // ------------------------------------------------------------ timers
    private var nextShotMs = 0L
    private var nextDryMs = 0L
    private var reloadUntilMs = 0L
    private var jumpUntilMs = 0L
    private var jumpReadyMs = 0L
    private var stepTimer = 0f
    private var lastBumpMs = -10000L
    private var lastHitPingMs = -10000L
    private var lockActive = false
    private var lockLastMs = -10000L
    private var lastNewEnemyMs = -10000L
    private var lastHeartbeatMs = -10000L
    private var lastTurnSoundMs = -10000L
    private var timeAnnounced = 0
    private var finalHunt = false

    private val out = ArrayList<GameEvent>()
    private val enemies = ArrayList<Enemy>()
    private val grenades = ArrayList<Grenade>()

    init {
        for (w in WeaponType.values()) {
            mag[w.ordinal] = w.magSize
            reserve[w.ordinal] = Cfg.RESERVE_MAGS
        }
        spawnEnemies()
    }

    // ------------------------------------------------------------ reading state (for the HUD)
    fun aliveCount(): Int {
        var n = 0
        for (e in enemies) if (e.alive) n++
        return n
    }

    fun ammoText(): String {
        return mag[weapon.ordinal].toString() + "/" + weapon.magSize + " +" + reserve[weapon.ordinal]
    }

    fun zoneName(): String = Zones.list[zoneId].name

    fun gameHour(): Float {
        val h = Cfg.START_HOUR + (nowMs / 1000f) / Cfg.MATCH_SECONDS * 24f
        return h % 24f
    }

    fun clockText(): String {
        val h = gameHour()
        val hh = h.toInt()
        val mm = ((h - hh) * 60f).toInt()
        val ampm = if (hh < 12) "AM" else "PM"
        var h12 = hh % 12
        if (h12 == 0) h12 = 12
        val mins = if (mm < 10) "0" + mm else mm.toString()
        return h12.toString() + ":" + mins + " " + ampm
    }

    /** 0 = full day, 1 = full night. Used to change the ambience. */
    fun nightness(): Float {
        val h = gameHour()
        return when {
            h < 4f -> 1f
            h < 6f -> 1f - (h - 4f) / 2f
            h < 18f -> 0f
            h < 21f -> (h - 18f) / 3f
            else -> 1f
        }
    }

    // ------------------------------------------------------------ input
    fun touchDown() {
        moveHeld = true
        moveHeldAt = nowMs
        moveCancelled = false
        backMode = false
    }

    fun touchUp() {
        moveHeld = false
        backMode = false
    }

    /** A quick flick happened, so this touch was not meant to start walking. */
    fun cancelMoveStart() {
        if (nowMs - moveHeldAt < Cfg.MOVE_START_MS + Cfg.TICK_MS) moveCancelled = true
    }

    fun setBack(b: Boolean) {
        backMode = b
    }

    fun steer(deg: Float) {
        heading = norm180(heading + deg)
    }

    fun turnFlick(dir: Int) {
        heading = norm180(heading + dir * Cfg.FLICK_TURN_DEG)
        if (nowMs - lastTurnSoundMs > 250L) {
            lastTurnSoundMs = nowMs
            out.add(GameEvent.Sfx("sfx_dodge_slide", 0f, 0f, 0.5f))
        }
    }

    fun setSensorWalk(v: Int) {
        sensorWalk = v
    }

    fun fireDown() {
        fireHeld = true
    }

    fun fireUp() {
        fireHeld = false
    }

    fun reload() {
        if (over) return
        val i = weapon.ordinal
        if (reloadUntilMs != 0L) return
        if (mag[i] >= weapon.magSize) {
            out.add(GameEvent.Say("Magazine full.", 2))
            return
        }
        if (reserve[i] <= 0) {
            out.add(GameEvent.Say("No magazines left.", 2))
            return
        }
        reloadUntilMs = nowMs + weapon.reloadMs
        out.add(GameEvent.Sfx(weapon.reloadKey, 0f, 0f, 1f))
    }

    fun switchWeapon() {
        if (over) return
        val all = WeaponType.values()
        weapon = all[(weapon.ordinal + 1) % all.size]
        reloadUntilMs = 0L
        out.add(GameEvent.Sfx("sfx_weapon_switch", 0f, 0f, 1f))
        out.add(GameEvent.Say(Phrases.weapon(weapon.label, mag[weapon.ordinal]), 2))
    }

    fun toggleCrouch() {
        if (over) return
        crouching = !crouching
        out.add(GameEvent.Sfx(if (crouching) "sfx_crouch" else "sfx_stand", 0f, 0f, 1f))
    }

    fun jump() {
        if (over || crouching) return
        if (nowMs < jumpReadyMs) return
        jumpUntilMs = nowMs + Cfg.JUMP_MS
        jumpReadyMs = nowMs + Cfg.JUMP_COOLDOWN_MS
        out.add(GameEvent.Sfx("sfx_jump", 0f, 0f, 1f))
    }

    fun heal() {
        if (over) return
        if (rations <= 0) {
            out.add(GameEvent.Say("No rations left.", 2))
            return
        }
        if (hp >= Cfg.START_HP) {
            out.add(GameEvent.Say("Health is full.", 2))
            return
        }
        rations--
        hp = min(Cfg.START_HP, hp + Cfg.HEAL_AMOUNT)
        out.add(GameEvent.Sfx("sfx_pickup", 0f, 0f, 1f))
        out.add(GameEvent.Say(Phrases.heal(hp, rations), 2))
    }

    fun scan() {
        if (over) return
        val list = ArrayList<Enemy>()
        for (e in enemies) if (e.alive) list.add(e)
        list.sortBy { hypot(it.x - px, it.y - py) }
        val parts = ArrayList<String>()
        var count = 0
        for (e in list) {
            if (count >= 4) break
            val d = hypot(e.x - px, e.y - py)
            val rel = relBearing(e.x, e.y)
            parts.add(Phrases.enemyAt(rel, d))
            if (count < 3) out.add(GameEvent.Sfx("ping_enemy_new", rel, d, 1f))
            count++
        }
        out.add(GameEvent.Say(Phrases.scan(parts, list.size), 2))
    }

    /** Hand the events collected so far to the caller and start a fresh list. */
    fun drain(): List<GameEvent> {
        if (out.isEmpty()) return emptyList()
        val copy = ArrayList<GameEvent>(out)
        out.clear()
        return copy
    }

    // ------------------------------------------------------------ the tick
    fun update(dtMs: Long) {
        if (over) return
        nowMs += dtMs
        val dt = dtMs / 1000f
        updateClock()
        movePlayer(dt)
        updateFire()
        updateReload()
        updateEnemies(dt)
        updateGrenades()
        updateLock()
        updateHeartbeat()
        checkEnd()
    }

    private fun relBearing(x: Float, y: Float): Float {
        return norm180(bearingOf(x - px, y - py) - heading)
    }

    private fun isJumping(): Boolean = nowMs < jumpUntilMs

    private fun updateClock() {
        val sec = nowMs / 1000f
        if (sec >= 150f && (timeAnnounced and 1) == 0) {
            timeAnnounced = timeAnnounced or 1
            out.add(GameEvent.Say(Phrases.timeCheck("Noon", aliveCount()), 3))
        }
        if (sec >= 300f && (timeAnnounced and 2) == 0) {
            timeAnnounced = timeAnnounced or 2
            out.add(GameEvent.Say(Phrases.timeCheck("Evening, six o clock", aliveCount()), 3))
        }
        if (sec >= 450f && (timeAnnounced and 4) == 0) {
            timeAnnounced = timeAnnounced or 4
            out.add(GameEvent.Say(Phrases.timeCheck("Midnight", aliveCount()), 3))
        }
        if (sec >= Cfg.FINAL_HUNT_SECONDS && !finalHunt) {
            finalHunt = true
            for (e in enemies) {
                if (e.alive && e.state != AiState.HUNT) {
                    e.state = AiState.HUNT
                    e.nextAttackMs = nowMs + 2000L
                }
            }
            out.add(GameEvent.Sfx("ping_attack_melee", 0f, 0f, 1f))
            out.add(GameEvent.Say(Phrases.finalHunt(), 1))
        }
        if (sec >= Cfg.MATCH_SECONDS - 30f && (timeAnnounced and 8) == 0) {
            timeAnnounced = timeAnnounced or 8
            out.add(GameEvent.Say(Phrases.thirtySeconds(), 1))
        }
    }

    // ------------------------------------------------------------ player movement
    private fun movePlayer(dt: Float) {
        var dir = 0
        if (!crouching) {
            if (moveHeld && !moveCancelled && nowMs - moveHeldAt >= Cfg.MOVE_START_MS) {
                dir = if (backMode) -1 else 1
            } else if (sensorWalk != 0) {
                dir = sensorWalk
            }
        }
        if (dir != 0) {
            val speed = if (dir > 0) Cfg.WALK_FPS else Cfg.BACK_FPS
            val rad = Math.toRadians(heading.toDouble())
            val fx = sin(rad).toFloat()
            val fy = cos(rad).toFloat()
            var nx = px + fx * speed * dt * dir
            var ny = py + fy * speed * dt * dir
            var bumped = false
            if (nx < -Cfg.HALF_WIDTH) {
                nx = -Cfg.HALF_WIDTH
                bumped = true
            }
            if (nx > Cfg.HALF_WIDTH) {
                nx = Cfg.HALF_WIDTH
                bumped = true
            }
            if (ny < 0f) {
                ny = 0f
                bumped = true
            }
            if (ny > Cfg.MAP_LENGTH) {
                ny = Cfg.MAP_LENGTH
                bumped = true
            }
            px = nx
            py = ny
            if (bumped) bump()
            stepTimer -= dt
            if (stepTimer <= 0f) {
                stepTimer = if (dir > 0) 0.5f else 0.62f
                val z = Zones.forY(py)
                out.add(GameEvent.Sfx(z.stepKey + "_" + (1 + rng.nextInt(2)), 0f, 0f, 1f))
                makeNoise(px, py, Cfg.STEP_NOISE_FT)
            }
        } else {
            stepTimer = 0f
        }
        val zone = Zones.forY(py)
        if (zone.id != zoneId) {
            zoneId = zone.id
            out.add(GameEvent.ZoneChanged(zone.id))
            out.add(GameEvent.Say(zone.name, 3))
        }
    }

    private fun bump() {
        if (nowMs - lastBumpMs < 1500L) return
        lastBumpMs = nowMs
        out.add(GameEvent.Sfx("sfx_wall_bump", 0f, 0f, 1f))
        out.add(GameEvent.Say(Phrases.boundary(), 3))
    }

    // ------------------------------------------------------------ firing
    private fun updateFire() {
        if (fireHeld) tryFire()
    }

    private fun tryFire() {
        if (over || isJumping()) return
        if (reloadUntilMs != 0L) return
        if (nowMs < nextShotMs) return
        val w = weapon
        val i = w.ordinal
        if (mag[i] <= 0) {
            if (nowMs >= nextDryMs) {
                nextDryMs = nowMs + 800L
                out.add(GameEvent.Sfx("sfx_dry_fire", 0f, 0f, 1f))
                out.add(GameEvent.Say("Empty. Reload.", 2))
            }
            return
        }
        mag[i] = mag[i] - 1
        nextShotMs = nowMs + (1000f / w.shotsPerSec).toLong()
        out.add(GameEvent.Sfx(w.fireKey, 0f, 0f, 1f))
        makeNoise(px, py, w.noiseFt)
        resolveShot(w)
    }

    private fun resolveShot(w: WeaponType) {
        val hits = ArrayList<Enemy>()
        for (e in enemies) {
            if (!e.alive) continue
            val d = hypot(e.x - px, e.y - py)
            if (d > w.rangeFt) continue
            val rel = relBearing(e.x, e.y)
            if (abs(rel) > w.coneDeg) continue
            hits.add(e)
        }
        if (hits.isEmpty()) {
            missImpact(w)
            return
        }
        hits.sortBy { hypot(it.x - px, it.y - py) }
        val take = if (w.pellets) min(3, hits.size) else 1
        for (n in 0 until take) {
            hitEnemy(hits[n], w)
        }
    }

    private fun hitEnemy(e: Enemy, w: WeaponType) {
        val d = hypot(e.x - px, e.y - py)
        val rel = relBearing(e.x, e.y)
        val frac = (d / w.rangeFt).coerceIn(0f, 1f)
        val dmg = (w.dmgNear + (w.dmgFar - w.dmgNear) * frac).toInt()
        e.hp -= dmg
        out.add(GameEvent.Sfx(w.hitKey, rel, d, 1f))
        if (nowMs - lastHitPingMs >= 150L) {
            lastHitPingMs = nowMs
            out.add(GameEvent.Sfx("ping_hit", 0f, 0f, 0.9f))
        }
        if (e.hp <= 0) {
            kills++
            e.attackAtMs = 0L
            out.add(GameEvent.Sfx("sfx_death", rel, d, 1f))
            out.add(GameEvent.Sfx("ping_kill", 0f, 0f, 1f))
            out.add(GameEvent.Haptic(1))
            out.add(GameEvent.Say(Phrases.down(aliveCount()), 2))
        } else {
            out.add(GameEvent.Sfx("sfx_enemy_pain", rel, d, 1f))
            if (e.state != AiState.HUNT && e.state != AiState.RETREAT) becomeHunt(e)
        }
    }

    /** The bullet hit the ground or a wall. The sound lands in front of you, at the distance of the nearest enemy ahead. */
    private fun missImpact(w: WeaponType) {
        val zone = Zones.forY(py)
        var d = 80f
        var bestDist = 100000f
        for (e in enemies) {
            if (!e.alive) continue
            val rel = relBearing(e.x, e.y)
            val dist = hypot(e.x - px, e.y - py)
            if (abs(rel) < 35f && dist <= w.rangeFt + 60f && dist < bestDist) {
                bestDist = dist
                d = dist
            }
        }
        out.add(GameEvent.Sfx(zone.missKey + "_" + (1 + rng.nextInt(2)), 0f, d, 1f))
    }

    private fun updateReload() {
        if (reloadUntilMs != 0L && nowMs >= reloadUntilMs) {
            reloadUntilMs = 0L
            val i = weapon.ordinal
            mag[i] = weapon.magSize
            reserve[i] = reserve[i] - 1
            out.add(GameEvent.Say("Reloaded.", 2))
        }
    }

    private fun updateLock() {
        var locked = false
        if (!over && reloadUntilMs == 0L && mag[weapon.ordinal] > 0 && !isJumping()) {
            for (e in enemies) {
                if (!e.alive) continue
                val d = hypot(e.x - px, e.y - py)
                if (d > weapon.rangeFt) continue
                if (abs(relBearing(e.x, e.y)) <= weapon.coneDeg) {
                    locked = true
                    break
                }
            }
        }
        if (locked) {
            if (!lockActive || nowMs - lockLastMs >= 900L) {
                lockLastMs = nowMs
                out.add(GameEvent.Sfx("ping_lock", 0f, 0f, 1f))
            }
            lockActive = true
        } else {
            lockActive = false
        }
    }

    private fun updateHeartbeat() {
        if (hp in 1..30 && nowMs - lastHeartbeatMs >= 2000L) {
            lastHeartbeatMs = nowMs
            out.add(GameEvent.Sfx("sfx_heartbeat", 0f, 0f, 1f))
        }
    }

    // ------------------------------------------------------------ noise
    private fun makeNoise(x: Float, y: Float, radius: Float) {
        for (e in enemies) {
            if (!e.alive) continue
            if (e.state == AiState.HUNT || e.state == AiState.RETREAT) continue
            val d = hypot(e.x - x, e.y - y)
            if (d <= radius) {
                becomeAlert(e, x + rng.nextFloat() * 20f - 10f, y + rng.nextFloat() * 20f - 10f)
            }
        }
    }

    // ------------------------------------------------------------ enemy AI
    private fun spawnEnemies() {
        val plan = listOf(
            listOf(EnemyKind.FLANKER, EnemyKind.RUSHER, EnemyKind.FLANKER),
            listOf(EnemyKind.RUSHER, EnemyKind.FLANKER, EnemyKind.FLANKER, EnemyKind.RUSHER, EnemyKind.TANK),
            listOf(EnemyKind.FLANKER, EnemyKind.FLANKER, EnemyKind.TANK, EnemyKind.RUSHER, EnemyKind.FLANKER),
            listOf(
                EnemyKind.TANK, EnemyKind.TANK, EnemyKind.FLANKER, EnemyKind.FLANKER,
                EnemyKind.RUSHER, EnemyKind.TANK, EnemyKind.FLANKER
            )
        )
        var id = 0
        for (zi in 1..4) {
            val z = Zones.list[zi]
            for (kind in plan[zi - 1]) {
                val w = when (kind) {
                    EnemyKind.RUSHER -> if (rng.nextBoolean()) EnemyWeapon.KNIFE else EnemyWeapon.BAT
                    EnemyKind.FLANKER -> EnemyWeapon.PISTOL
                    EnemyKind.TANK -> if (rng.nextBoolean()) EnemyWeapon.AXE else EnemyWeapon.BAT
                }
                val x = rng.nextFloat() * 220f - 110f
                val y = z.startFt + 12f + rng.nextFloat() * (z.endFt - z.startFt - 24f)
                val e = Enemy(id, kind, w, x, y, zi)
                id++
                e.flankSide = if (rng.nextBoolean()) 1f else -1f
                e.patrolTimer = rng.nextFloat() * 3f
                enemies.add(e)
            }
        }
    }

    private fun sightRange(): Float = if (crouching) Cfg.SIGHT_CROUCH_FT else Cfg.SIGHT_FT

    private fun updateEnemies(dt: Float) {
        var stepsThisTick = 0
        for (e in enemies) {
            if (!e.alive) continue
            val dist = hypot(px - e.x, py - e.y)
            val rel = relBearing(e.x, e.y)
            if (!e.announced && dist <= Cfg.HEAR_ENEMY_FT && nowMs - lastNewEnemyMs > 1500L) {
                e.announced = true
                lastNewEnemyMs = nowMs
                out.add(GameEvent.Sfx("ping_enemy_new", rel, dist, 1f))
                out.add(GameEvent.Say(Phrases.enemyAt(rel, dist), 2))
            }
            val moving = when (e.state) {
                AiState.PATROL -> patrol(e, dist, dt)
                AiState.ALERT -> alert(e, dist, dt)
                AiState.HUNT -> hunt(e, dist, rel, dt)
                AiState.RETREAT -> retreat(e, dist, dt)
            }
            if (moving && dist <= Cfg.HEAR_ENEMY_FT && stepsThisTick < 4) {
                e.stepTimer -= dt
                if (e.stepTimer <= 0f) {
                    e.stepTimer = e.kind.stepEvery
                    out.add(GameEvent.Sfx(e.kind.stepKey, rel, dist, 0.9f))
                    stepsThisTick++
                }
            }
        }
    }

    private fun moveToward(e: Enemy, tx: Float, ty: Float, speed: Float, dt: Float): Boolean {
        val dx = tx - e.x
        val dy = ty - e.y
        val d = hypot(dx, dy)
        if (d < 0.5f) return false
        val step = min(d, speed * dt)
        e.x = (e.x + dx / d * step).coerceIn(-Cfg.HALF_WIDTH, Cfg.HALF_WIDTH)
        e.y = (e.y + dy / d * step).coerceIn(0f, Cfg.MAP_LENGTH)
        return true
    }

    private fun becomeAlert(e: Enemy, tx: Float, ty: Float) {
        val wasPatrol = e.state == AiState.PATROL
        e.state = AiState.ALERT
        e.targetX = tx
        e.targetY = ty
        val travel = hypot(tx - e.x, ty - e.y) / (e.kind.speedFps * 0.8f)
        e.alertUntilMs = nowMs + (travel * 1000f).toLong() + 4000L
        if (wasPatrol) cue(e, "sfx_gear")
    }

    private fun becomeHunt(e: Enemy) {
        if (e.state == AiState.HUNT) return
        e.state = AiState.HUNT
        e.nextAttackMs = nowMs + 800L
        cue(e, "sfx_grunt")
    }

    /** A short sound so the player can tell the enemy changed mind. */
    private fun cue(e: Enemy, key: String) {
        if (nowMs - e.lastCueMs < 3000L) return
        e.lastCueMs = nowMs
        val d = hypot(e.x - px, e.y - py)
        out.add(GameEvent.Sfx(key, relBearing(e.x, e.y), d, 1f))
    }

    private fun patrol(e: Enemy, dist: Float, dt: Float): Boolean {
        if (dist <= sightRange()) {
            becomeHunt(e)
            return false
        }
        e.patrolTimer -= dt
        if (e.patrolTimer <= 0f) {
            e.patrolTimer = 4f + rng.nextFloat() * 4f
            val zone = Zones.list[e.homeZone]
            e.targetX = (e.x + rng.nextFloat() * 50f - 25f).coerceIn(-120f, 120f)
            e.targetY = (e.y + rng.nextFloat() * 50f - 25f).coerceIn(zone.startFt + 5f, zone.endFt - 5f)
        }
        return moveToward(e, e.targetX, e.targetY, e.kind.speedFps * 0.4f, dt)
    }

    private fun alert(e: Enemy, dist: Float, dt: Float): Boolean {
        if (dist <= sightRange()) {
            becomeHunt(e)
            return false
        }
        val moved = moveToward(e, e.targetX, e.targetY, e.kind.speedFps * 0.8f, dt)
        if (!moved && nowMs > e.alertUntilMs) {
            e.state = AiState.PATROL
            e.patrolTimer = 1f
        }
        return moved
    }

    private fun hunt(e: Enemy, dist: Float, rel: Float, dt: Float): Boolean {
        if (e.kind == EnemyKind.FLANKER && !finalHunt && !e.retreated &&
            e.attackAtMs == 0L && e.hp * 100 < e.kind.hp * 30
        ) {
            e.retreated = true
            e.state = AiState.RETREAT
            e.retreatUntilMs = nowMs + 9000L
            return false
        }
        val w = e.weapon
        val attackRange = if (w.melee) w.rangeFt - 1f else 60f
        var moved = false
        if (e.attackAtMs != 0L) {
            progressAttack(e, dist, rel)
        } else {
            if (dist > attackRange) {
                if (w.melee) {
                    moved = moveToward(e, px, py, e.kind.speedFps, dt)
                } else {
                    // Flanker: come in from the side, not straight at the player.
                    val ux = (px - e.x) / max(dist, 1f)
                    val uy = (py - e.y) / max(dist, 1f)
                    val tx = px - uy * 30f * e.flankSide
                    val ty = py + ux * 30f * e.flankSide
                    moved = moveToward(e, tx, ty, e.kind.speedFps, dt)
                }
            } else if (nowMs >= e.nextAttackMs) {
                startAttack(e, dist, rel)
            }
            maybeThrowGrenade(e, dist, rel)
        }
        return moved
    }

    private fun startAttack(e: Enemy, dist: Float, rel: Float) {
        e.attackAtMs = nowMs + Cfg.WARNING_MS
        e.swingPlayed = false
        val key = if (e.weapon.melee) "ping_attack_melee" else "ping_attack_gun"
        out.add(GameEvent.Sfx(key, rel, dist, 1f))
        out.add(GameEvent.Say(Phrases.attack(rel, dist), 1))
    }

    private fun progressAttack(e: Enemy, dist: Float, rel: Float) {
        if (e.weapon.melee && !e.swingPlayed && nowMs >= e.attackAtMs - Cfg.SWING_LEAD_MS) {
            e.swingPlayed = true
            out.add(GameEvent.Sfx(e.weapon.sound, rel, dist, 1f))
        }
        if (nowMs >= e.attackAtMs) {
            resolveAttack(e, dist, rel)
            e.attackAtMs = 0L
            e.nextAttackMs = nowMs + 2500L + rng.nextInt(1500)
        }
    }

    /** The warning time is over. Crouch beats bullets, a jump beats a swing, walking away beats a swing. */
    private fun resolveAttack(e: Enemy, dist: Float, rel: Float) {
        val w = e.weapon
        if (!w.melee) {
            out.add(GameEvent.Sfx(w.sound, rel, dist, 1f))
        }
        val reach = if (w.melee) w.rangeFt + 4f else w.rangeFt + 20f
        if (dist > reach) return
        val dodged = if (w.melee) isJumping() else crouching
        if (dodged) {
            out.add(GameEvent.Sfx("sfx_dodge_slide", 0f, 0f, 1f))
            out.add(GameEvent.Say(Phrases.dodged(), 2))
        } else {
            hurtPlayer(w.damage)
        }
    }

    private fun hurtPlayer(amount: Int) {
        if (amount <= 0) return
        hp -= amount
        out.add(GameEvent.Sfx("sfx_player_hurt_" + (1 + rng.nextInt(2)), 0f, 0f, 1f))
        out.add(GameEvent.Haptic(0))
        out.add(GameEvent.Say(if (hp > 0) Phrases.hit(hp) else Phrases.dead(), 1))
    }

    private fun retreat(e: Enemy, dist: Float, dt: Float): Boolean {
        if (finalHunt) {
            e.state = AiState.HUNT
            return false
        }
        var moved = false
        if (dist < 110f) {
            val d = max(dist, 1f)
            val ax = (e.x - px) / d
            val ay = (e.y - py) / d
            moved = moveToward(e, e.x + ax * 30f, e.y + ay * 30f, e.kind.speedFps, dt)
        }
        if (nowMs >= e.retreatUntilMs) {
            e.state = AiState.HUNT
            e.nextAttackMs = nowMs + 1500L
        }
        return moved
    }

    // ------------------------------------------------------------ grenades
    private fun maybeThrowGrenade(e: Enemy, dist: Float, rel: Float) {
        if (grenades.isNotEmpty() || nowMs < e.grenadeAtMs) return
        if (dist < 30f || dist > 110f) return
        if (rng.nextFloat() > 0.012f) return
        e.grenadeAtMs = nowMs + 18000L
        val ang = rng.nextFloat() * 6.2831f
        val r = 12f + rng.nextFloat() * 14f
        val gx = (px + sin(ang) * r).coerceIn(-Cfg.HALF_WIDTH, Cfg.HALF_WIDTH)
        val gy = (py + cos(ang) * r).coerceIn(0f, Cfg.MAP_LENGTH)
        grenades.add(Grenade(gx, gy, nowMs + Cfg.GRENADE_FUSE_MS))
        out.add(GameEvent.Sfx("voice_grenade", rel, dist, 1f))
        out.add(GameEvent.Sfx("sfx_pin", rel, dist, 1f))
        val grel = relBearing(gx, gy)
        val gd = hypot(gx - px, gy - py)
        out.add(GameEvent.Sfx("ping_attack_grenade", grel, gd, 1f))
        out.add(GameEvent.Say(Phrases.grenade(grel, gd), 1))
    }

    private fun updateGrenades() {
        val iter = grenades.iterator()
        while (iter.hasNext()) {
            val g = iter.next()
            val rel = relBearing(g.x, g.y)
            val d = hypot(g.x - px, g.y - py)
            if (!g.bounced && nowMs >= g.explodeAtMs - Cfg.GRENADE_FUSE_MS + 500L) {
                g.bounced = true
                out.add(GameEvent.Sfx("sfx_bounce", rel, d, 1f))
            }
            if (nowMs >= g.explodeAtMs) {
                out.add(GameEvent.Sfx("sfx_explosion", rel, d, 1f))
                if (d < Cfg.GRENADE_RADIUS) {
                    var dmg = (Cfg.GRENADE_DAMAGE * (1f - d / Cfg.GRENADE_RADIUS)).toInt()
                    if (crouching) dmg /= 2
                    out.add(GameEvent.Haptic(2))
                    out.add(GameEvent.Sfx("sfx_tinnitus", 0f, 0f, 1f))
                    hurtPlayer(max(dmg, 1))
                }
                iter.remove()
            }
        }
    }

    // ------------------------------------------------------------ the end
    private fun checkEnd() {
        if (over) return
        if (hp <= 0) {
            finish(false, "You were killed.")
        } else if (aliveCount() == 0) {
            finish(true, "All enemies down.")
        } else if (nowMs >= (Cfg.MATCH_SECONDS * 1000f).toLong()) {
            finish(false, "Time is up. Enemies are still alive.")
        }
    }

    private fun finish(didWin: Boolean, reason: String) {
        over = true
        won = didWin
        fireHeld = false
        moveHeld = false
        out.add(GameEvent.MatchOver(didWin, reason))
    }
}
