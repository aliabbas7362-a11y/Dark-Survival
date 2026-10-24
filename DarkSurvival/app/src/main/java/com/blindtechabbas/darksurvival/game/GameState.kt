package com.blindtechabbas.darksurvival.game

enum class Screen { GATE, MAIN_MENU, SETTINGS, MATCH, RESULT }

enum class Phase { INTRO, COUNTDOWN, PLAYING }

data class Hud(
    val hp: Int = 100,
    val weapon: String = "",
    val ammo: String = "",
    val enemies: Int = 0,
    val time: String = "",
    val zone: String = "",
    val rations: Int = 3,
    val crouching: Boolean = false
)

data class ResultInfo(
    val won: Boolean,
    val reason: String,
    val kills: Int,
    val seconds: Int,
    val hp: Int
)

data class UiState(
    val screen: Screen = Screen.GATE,
    val phase: Phase = Phase.INTRO,
    val countdown: Int = 0,
    val talkBackOn: Boolean = false,
    val menuIndex: Int = 0,
    val hud: Hud = Hud(),
    val result: ResultInfo? = null,
    val speechFeedback: Boolean = true,
    val announcements: Boolean = true,
    val sensorOn: Boolean = false,
    val engineLabel: String = "Default",
    val speechRate: Float = 1.0f,
    val speechVolume: Float = 1.0f,
    val ttsOk: Boolean = true,
    val paused: Boolean = false,
    val exit: Boolean = false
)

fun onOff(b: Boolean): String = if (b) "on" else "off"

fun rateLabel(r: Float): String = (Math.round(r * 10f) / 10f).toString()

fun volumeLabel(v: Float): String = Math.round(v * 100f).toString() + " percent"

/** The items of the current menu. Used by the screen to draw and by the view model to act. */
fun menuLabels(ui: UiState): List<String> {
    return when (ui.screen) {
        Screen.MAIN_MENU -> listOf("Survive 24 Hours", "Settings", "Exit")
        Screen.SETTINGS -> listOf(
            "Speech feedback: " + onOff(ui.speechFeedback),
            "Announcements: " + onOff(ui.announcements),
            "Sensor: " + onOff(ui.sensorOn),
            "Calibrate sensor",
            "Speech engine: " + ui.engineLabel,
            "Speech speed: " + rateLabel(ui.speechRate),
            "Speech volume: " + volumeLabel(ui.speechVolume),
            "Back"
        )
        Screen.RESULT -> listOf("Play again", "Main menu")
        else -> emptyList()
    }
}
