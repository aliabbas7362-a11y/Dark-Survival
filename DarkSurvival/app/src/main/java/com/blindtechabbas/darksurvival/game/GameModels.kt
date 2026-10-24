package com.blindtechabbas.darksurvival.game

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2

/**
 * Dark Survival v4.0 - "Survive 24 Hours".
 * Every number that can be tuned lives here. Distances are in FEET, times in
 * seconds or milliseconds as the name says. This file has NO Android code,
 * so the game rules stay separate from sound, screens and touch.
 */
object Cfg {
    const val HALF_WIDTH = 150f          // map is 300 feet wide: x from -150 to +150
    const val MAP_LENGTH = 1000f         // and 1000 feet long: y from 0 to 1000
    const val MATCH_SECONDS = 600f       // 10 real minutes = 24 game hours
    const val START_HOUR = 6f
    const val TICK_MS = 100L

    const val WALK_FPS = 6f              // feet per second, forward
    const val BACK_FPS = 3f
    const val MOVE_START_MS = 150L       // finger must stay down this long before walking starts
    const val FLICK_TURN_DEG = 30f
    const val STEER_DEG_PER_PX = 0.15f

    const val START_HP = 100
    const val START_RATIONS = 3
    const val HEAL_AMOUNT = 30
    const val RESERVE_MAGS = 3           // spare magazines for every weapon

    const val WARNING_MS = 3000L         // warning before an enemy attack lands
    const val SWING_LEAD_MS = 800L       // melee swing sound this long before impact
    const val JUMP_MS = 1000L
    const val JUMP_COOLDOWN_MS = 1500L

    const val FINAL_HUNT_SECONDS = 480f  // last 2 minutes: every enemy hunts the player
    const val GRENADE_FUSE_MS = 3000L
    const val GRENADE_DAMAGE = 35
    const val GRENADE_RADIUS = 30f

    const val HEAR_ENEMY_FT = 150f       // you hear enemies this far
    const val STEP_NOISE_FT = 120f       // enemies hear your footsteps this far
    const val SIGHT_FT = 70f             // enemy notices you this close
    const val SIGHT_CROUCH_FT = 25f      // ...and this close when you crouch
}

enum class WeaponType(
    val label: String,
    val rangeFt: Float,
    val dmgNear: Int,
    val dmgFar: Int,
    val shotsPerSec: Float,
    val magSize: Int,
    val coneDeg: Float,
    val fireKey: String,
    val reloadKey: String,
    val hitKey: String,
    val reloadMs: Long,
    val noiseFt: Float,
    val pellets: Boolean
) {
    PISTOL("Pistol", 80f, 15, 8, 3f, 12, 12f, "sfx_gunshot", "sfx_reload_pistol", "sfx_hit_pistol", 1300L, 300f, false),
    SHOTGUN("Shotgun", 40f, 45, 10, 1.3f, 6, 25f, "sfx_shotgun", "sfx_reload_shotgun", "sfx_hit_shotgun", 2200L, 350f, true),
    SNIPER("Sniper", 400f, 70, 70, 0.8f, 5, 4f, "sfx_sniper", "sfx_reload_sniper", "sfx_hit_sniper", 2400L, 600f, false),
    MACHINE_GUN("Machine Gun", 150f, 8, 5, 7f, 30, 10f, "sfx_mg_shot", "sfx_reload_mg", "sfx_hit_mg", 2800L, 400f, false)
}

enum class EnemyKind(
    val label: String,
    val hp: Int,
    val speedFps: Float,
    val stepKey: String,
    val stepEvery: Float
) {
    RUSHER("Rusher", 30, 9f, "sfx_enemy_step_light", 0.28f),
    FLANKER("Flanker", 50, 6f, "sfx_enemy_step", 0.45f),
    TANK("Tank", 110, 3.5f, "sfx_enemy_step_heavy", 0.75f)
}

enum class EnemyWeapon(
    val label: String,
    val melee: Boolean,
    val rangeFt: Float,
    val damage: Int,
    val sound: String
) {
    KNIFE("Knife", true, 8f, 8, "sfx_enemy_knife_swing"),
    BAT("Bat", true, 8f, 11, "sfx_enemy_bat_swing"),
    AXE("Axe", true, 9f, 14, "sfx_enemy_axe_swing"),
    PISTOL("Pistol", false, 80f, 8, "sfx_gunshot")      // SAME sound as the player's pistol
}

enum class AiState { PATROL, ALERT, HUNT, RETREAT }

class ZoneDef(
    val id: Int,
    val name: String,
    val startFt: Float,
    val endFt: Float,
    val stepKey: String,
    val missKey: String,
    val ambience: String
)

object Zones {
    val list: List<ZoneDef> = listOf(
        ZoneDef(0, "Drop Point", 0f, 150f, "sfx_step_grass", "sfx_miss_dirt", "amb_morning"),
        ZoneDef(1, "River Bank", 150f, 400f, "sfx_step_water", "sfx_miss_water", "amb_river"),
        ZoneDef(2, "Deep Forest", 400f, 650f, "sfx_step_leaves", "sfx_miss_wood", "amb_forest"),
        ZoneDef(3, "Rocky Ridge", 650f, 850f, "sfx_step_rock", "sfx_miss_rock", "amb_ridge"),
        ZoneDef(4, "Enemy Camp", 850f, 1000f, "sfx_step_metal", "sfx_miss_dirt", "amb_night")
    )

    fun forY(y: Float): ZoneDef {
        for (z in list) {
            if (y < z.endFt) return z
        }
        return list[list.size - 1]
    }
}

/** One enemy. Mutable on purpose: the match engine updates it every tick. */
class Enemy(
    val id: Int,
    val kind: EnemyKind,
    val weapon: EnemyWeapon,
    var x: Float,
    var y: Float,
    val homeZone: Int
) {
    var hp: Int = kind.hp
    var state: AiState = AiState.PATROL
    var targetX: Float = x
    var targetY: Float = y
    var patrolTimer: Float = 0f
    var stepTimer: Float = 0f
    var attackAtMs: Long = 0L        // 0 = no attack being prepared
    var swingPlayed: Boolean = false
    var nextAttackMs: Long = 0L
    var announced: Boolean = false
    var alertUntilMs: Long = 0L
    var retreatUntilMs: Long = 0L
    var retreated: Boolean = false
    var flankSide: Float = 1f
    var grenadeAtMs: Long = 0L
    var lastCueMs: Long = -10000L

    val alive: Boolean
        get() = hp > 0
}

class Grenade(val x: Float, val y: Float, val explodeAtMs: Long) {
    var bounced: Boolean = false
}

/** What the match engine tells the rest of the app. The engine never plays sound itself. */
sealed class GameEvent {
    /** A sound at a place: bearing is relative to where the player faces (0 = front, + = right). */
    class Sfx(
        val key: String,
        val bearing: Float,
        val distFt: Float,
        val gain: Float = 1f,
        val rate: Float = 1f
    ) : GameEvent()

    /** A spoken line. priority 1 = critical, 2 = combat, 3 = information. */
    class Say(val text: String, val priority: Int) : GameEvent()

    /** kind: 0 = player hurt, 1 = enemy killed, 2 = grenade. */
    class Haptic(val kind: Int) : GameEvent()

    class ZoneChanged(val zone: Int) : GameEvent()

    class MatchOver(val won: Boolean, val reason: String) : GameEvent()
}

// ---------------------------------------------------------------- helpers

fun norm180(a: Float): Float {
    var x = a % 360f
    if (x > 180f) x -= 360f
    if (x < -180f) x += 360f
    return x
}

/** Bearing of the vector (dx, dy) in degrees: 0 = straight along +y, positive = toward +x. */
fun bearingOf(dx: Float, dy: Float): Float {
    return (atan2(dx.toDouble(), dy.toDouble()) * 180.0 / PI).toFloat()
}

fun roundFeet(d: Float): Int {
    val r = ((d / 5f) + 0.5f).toInt() * 5
    return if (r < 5) 5 else r
}

fun dirWord(b: Float): String {
    val a = abs(b)
    return when {
        a <= 22.5f -> "front"
        a <= 67.5f -> if (b > 0f) "front right" else "front left"
        a <= 112.5f -> if (b > 0f) "right" else "left"
        a <= 157.5f -> if (b > 0f) "back right" else "back left"
        else -> "back"
    }
}
