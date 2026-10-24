package com.blindtechabbas.darksurvival.audio

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator

/** Phone vibration. kind: 0 = you were hurt, 1 = enemy killed, 2 = grenade blast. */
class Haptics(context: Context) {

    private val vibrator: Vibrator? =
        context.applicationContext.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator

    @Suppress("DEPRECATION")
    fun pulse(kind: Int) {
        try {
            val v = vibrator ?: return
            val millis: Long = when (kind) {
                0 -> 160L
                1 -> 90L
                else -> 400L
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                v.vibrate(VibrationEffect.createOneShot(millis, VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                v.vibrate(millis)
            }
        } catch (_: Exception) {
        }
    }

    fun cancel() {
        try {
            vibrator?.cancel()
        } catch (_: Exception) {
        }
    }
}
