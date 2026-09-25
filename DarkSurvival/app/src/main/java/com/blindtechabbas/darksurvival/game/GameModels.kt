package com.blindtechabbas.darksurvival.game

/** Weapon system — order: Pistol, Shotgun, Sniper, Machine Gun (cycle). */
enum class WeaponType(
    val displayName: String,
    val maxAmmo: Int,
    val damage: Int,
    val fireSound: String,
    val reloadSound: String
) {
    PISTOL("Pistol", 12, 15, "gunshot", "reload"),
    SHOTGUN("Shotgun", 6, 35, "shotgun", "reload"),
    SNIPER("Sniper", 5, 60, "sniper", "reload"),
    MACHINE_GUN("Machine Gun", 30, 7, "mg", "reload")
}

/** Vehicle system — motorbike / car / helicopter. */
enum class VehicleType(val displayName: String, val speed: Int, val armor: Int) {
    MOTORBIKE("Motorbike", 3, 0),
    CAR("Car", 2, 2),
    HELICOPTER("Helicopter", 4, 1)
}

/** Enemy types — Level 1 humans, Level 2 humans + animals. */
enum class EnemyType(val displayName: String, val health: Int, val damage: Int) {
    HUMAN("Human", 50, 10),
    WOLF("Wolf", 25, 12),
    SNAKE("Snake", 15, 20),
    LARGE_ANIMAL("Large Animal", 100, 25)
}

/** Speed control — walk / sprint (stamina) / sneak (silent). */
enum class MovementMode { WALK, SPRINT, SNEAK }

/**
 * Lane — v1.6 combat model. Player and every enemy occupy one of three
 * lanes (left / center / right). FIRE hits only enemies in the player's
 * current lane, so the player must move to the enemy's lane to kill it.
 */
enum class Lane(val displayName: String) {
    LEFT("left"),
    CENTER("center"),
    RIGHT("right")
}

/** Move one lane left (-1) or right (+1) with WRAP-AROUND: the lanes loop
 *  like music tracks — Right->Center->Left->Right… and back. */
fun Lane.move(delta: Int): Lane {
    val size = Lane.entries.size
    val idx = ((Lane.entries.indexOf(this) + delta) % size + size) % size
    return Lane.entries[idx]
}

fun Lane.label(): String = displayName

/** 3D spatial direction (kept for future use). */
enum class Direction { FRONT, LEFT, RIGHT, BEHIND }

/** Spoken label for a direction (used in HUD + announcements). */
fun Direction.label(): String = when (this) {
    Direction.FRONT -> "front"
    Direction.LEFT -> "left"
    Direction.RIGHT -> "right"
    Direction.BEHIND -> "behind"
}

/** Maps from the design doc. */
enum class MapType(val displayName: String) {
    RIVERBANK("Riverbank - Day"),
    DESERT("Desert - Night")
}

data class Player(
    val health: Int = 100,
    val maxHealth: Int = 100,
    val stamina: Int = 100,
    val weapon: WeaponType = WeaponType.PISTOL,
    val ammo: Int = 12,
    val hasMedkit: Boolean = true,
    val rationCount: Int = 3,
    val crouching: Boolean = false,
    val inCover: Boolean = false,
    val vehicle: VehicleType? = null,
    val movement: MovementMode = MovementMode.WALK,
    /** Current combat lane — changed by swipe/tilt. */
    val lane: Lane = Lane.CENTER,
    /** Linear path progress along the 5-zone map (0=drop point, 100=enemy camp). */
    val progress: Int = 0,
    /** While now < dodgeUntilMs the player is briefly invulnerable. */
    val dodgeUntilMs: Long = 0L
)

/** Enemy carry-weapons — each with its OWN range / damage / speed (v2.3). */
enum class EnemyWeaponType(
    val displayName: String,
    val range: Float,
    val dmg: Int,
    val speedMul: Float,
    val fireSound: String
) {
    KNIFE("Knife", 0.22f, 8, 1.3f, "melee"),
    AXE("Axe", 0.26f, 14, 1.0f, "melee"),
    BAT("Bat", 0.24f, 11, 1.1f, "melee"),
    PISTOL("Pistol", 0.34f, 9, 1.0f, "gunshot")
}

/** AI behaviour archetypes — Rusher / Tactical-Flanker / Tank (v2.3). */
enum class AiArchetype(val displayName: String, val speedMul: Float, val hpMul: Float, val dmgMul: Float) {
    RUSHER("Rusher", 1.6f, 0.6f, 0.8f),
    TACTICAL("Flanker", 1.0f, 1.0f, 1.0f),
    TANK("Tank", 0.6f, 2.2f, 1.6f)
}

/** Enemy AI state machine — Patrol → Alert → Hunt (v2.4). */
enum class AiState { PATROL, ALERT, HUNT }

/**
 * Fixed 13-enemy zone spawn table (v2.4):
 * Zone 1 drop point: 1 patrol · Zone 2 river: 2 guards · Zone 3 forest:
 * 3 hunters/rushers · Zone 4 ridge: 3 tactical · Zone 5 camp: 4 heavies.
 */
val ZONE_SPAWNS: List<Pair<Int, AiArchetype>> = listOf(
    1 to AiArchetype.TACTICAL,
    1 to AiArchetype.TACTICAL,
    2 to AiArchetype.RUSHER,
    2 to AiArchetype.RUSHER,
    2 to AiArchetype.RUSHER,
    3 to AiArchetype.TACTICAL,
    3 to AiArchetype.TACTICAL,
    3 to AiArchetype.TACTICAL,
    4 to AiArchetype.TANK,
    4 to AiArchetype.TANK,
    4 to AiArchetype.TANK,
    4 to AiArchetype.TANK
)

const val TOTAL_ENEMIES = 12

/** Linear map zones along one north-to-south path (v2.3 level design). */
data class MapZone(
    val id: Int,
    val name: String,
    val start: Int,
    val end: Int,
    val ambKey: String
)

val MAP_ZONES = listOf(
    MapZone(0, "Helicopter Drop Point", 0, 14, "morning"),
    MapZone(1, "River Bank", 15, 39, "river"),
    MapZone(2, "Deep Forest", 40, 64, "forest"),
    MapZone(3, "Rocky Ridge", 65, 84, "ridge"),
    MapZone(4, "Enemy Camp", 85, 100, "night")
)

fun zoneForProgress(p: Int): Int = MAP_ZONES.first { p >= it.start && p <= it.end }.id

data class Enemy(
    val id: Int,
    val type: EnemyType,
    val health: Int,
    /** 1.5 = far, shrinks every tick; weapon.range = engagement range. */
    val distance: Float,
    /** Which lane this enemy attacks from. */
    val lane: Lane,
    /** Set when the enemy telegraphs its attack — damage begins ~2s later. */
    val attackStartMs: Long = 0L,
    /** Per-instance approach speed (already includes weapon+archetype multipliers). */
    val speed: Float = 0.04f,
    /** Seconds between this enemy's repeat attacks (1.6..3.4). */
    val attackInterval: Float = 2.5f,
    /** v2.0 — each enemy carries its OWN weapon (unique range/damage/speed). */
    val weapon: EnemyWeaponType = EnemyWeaponType.PISTOL,
    /** v2.3 — AI archetype: Rusher / Tactical-Flanker / Tank. */
    val archetype: AiArchetype = AiArchetype.TACTICAL,
    /** v2.4 — which map zone this enemy belongs to (0..4). */
    val zone: Int = 0,
    /** v2.4 — AI state machine: Patrol → Alert → Hunt. */
    val aiState: AiState = AiState.PATROL
)

data class Chapter(
    val id: Int,
    val title: String,
    val map: MapType,
    val unlockWeapon: WeaponType?,
    val unlockVehicle: VehicleType?,
    val description: String
)

val CHAPTERS = listOf(
    Chapter(1, "Chapter 1 - First Steps", MapType.RIVERBANK, WeaponType.PISTOL, null,
        "Basic pistol, few enemies, explore the map."),
    Chapter(2, "Chapter 2 - Shotgun", MapType.RIVERBANK, WeaponType.SHOTGUN, VehicleType.MOTORBIKE,
        "Shotgun unlocked, more enemies, motorbike found."),
    Chapter(3, "Chapter 3 - Sniper", MapType.RIVERBANK, WeaponType.SNIPER, VehicleType.CAR,
        "Sniper unlocked, smarter enemies, car found."),
    Chapter(4, "Chapter 4 - Machine Gun", MapType.RIVERBANK, WeaponType.MACHINE_GUN, null,
        "Machine gun unlocked, heavy firepower."),
    Chapter(5, "Chapter 5 - HQ Raid", MapType.RIVERBANK, null, VehicleType.HELICOPTER,
        "Raid the HQ, grab the helicopter, fly away."),
    Chapter(6, "Chapter 6 - Desert Night", MapType.DESERT, null, null,
        "Desert map, old weapons, animals appear."),
    Chapter(7, "Chapter 7 - Ambush", MapType.DESERT, null, null,
        "Room ambushes, night insects signal enemies.")
)
