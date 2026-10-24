package com.blindtechabbas.darksurvival.game

/** Every line the game speaks during a match. One place, so Urdu can be added later. */
object Phrases {
    fun enemyAt(rel: Float, dist: Float): String =
        "Enemy, " + dirWord(rel) + ", " + roundFeet(dist) + " feet"

    fun attack(rel: Float, dist: Float): String =
        "Attack, " + dirWord(rel) + ", " + roundFeet(dist) + " feet"

    fun grenade(rel: Float, dist: Float): String =
        "Grenade, " + dirWord(rel) + ", " + roundFeet(dist) + " feet"

    fun hit(hp: Int): String = "Hit. Health " + hp

    fun dead(): String = "You were hit. You are down."

    fun dodged(): String = "Dodged."

    fun down(left: Int): String =
        if (left <= 0) "Enemy down. That was the last one." else "Enemy down. " + left + " left."

    fun weapon(label: String, bullets: Int): String = label + ", " + bullets + " bullets"

    fun heal(hp: Int, rations: Int): String = "Health " + hp + ". " + rations + " rations left."

    fun timeCheck(label: String, left: Int): String = label + ". " + left + " enemies left."

    fun matchStart(): String =
        "Match started. It is six in the morning. Find and eliminate every enemy."

    fun finalHunt(): String = "Last two minutes. The enemies are hunting you."

    fun thirtySeconds(): String = "Thirty seconds left."

    fun boundary(): String = "Boundary."

    fun scan(parts: List<String>, left: Int): String {
        if (left <= 0) return "No enemies left."
        val sb = StringBuilder()
        for (p in parts) {
            sb.append(p).append(". ")
        }
        sb.append(left).append(" enemies left.")
        return sb.toString()
    }
}
