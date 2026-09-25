package com.blindtechabbas.darksurvival.util

/**
 * The 9-zone Passthrough Touch layout from the design doc.
 * Fire and Reload zones are FIXED and never change.
 */
enum class Zone(val label: String, val description: String) {
    WEAPON_SWITCH(
        "Weapon Switch",
        "Single tap: next weapon. Double tap: previous. Long press: ammo check."
    ),
    MOVE_FORWARD(
        "Move Forward",
        "Single tap: walk. Double tap: sprint. Long press: sneak."
    ),
    VEHICLE(
        "Vehicle Control",
        "Single tap: enter. Double tap: exit. Long press: condition."
    ),
    MOVE_LEFT(
        "Move Left",
        "Single tap: walk left. Double tap: sprint left. Long press: sneak left."
    ),
    ACTION(
        "Action",
        "Single tap: action. Double tap: crouch. Long press: cover."
    ),
    MOVE_RIGHT(
        "Move Right",
        "Single tap: walk right. Double tap: sprint right. Long press: sneak right."
    ),
    FIRE(
        "Fire",
        "Single tap: fire. Double tap: grenade. Long press: aimed shot."
    ),
    MOVE_BACK(
        "Move Back",
        "Single tap: walk back. Double tap: sprint back. Long press: sneak back."
    ),
    RELOAD(
        "Reload",
        "Single tap: reload. Double tap: speed boost. Long press: heal."
    )
}
