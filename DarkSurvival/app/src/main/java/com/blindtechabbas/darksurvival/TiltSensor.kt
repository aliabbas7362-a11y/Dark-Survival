package com.blindtechabbas.darksurvival.util

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.view.Surface
import android.view.WindowManager
import kotlin.math.atan2
import kotlin.math.sqrt

/**
 * Phone tilt, worked out FOR THE SCREEN the player is looking at.
 * The old version used the portrait axes in landscape, so forward and sideways were swapped.
 * Here the screen rotation (both landscape directions) is used to find
 *   forward: how far the top of the screen is tipped away from you (degrees, grows when tipped away)
 *   right:   how far the right edge is tipped down (degrees, positive = right edge down)
 */
class TiltSensor(private val context: Context, private val onTilt: (Float, Float) -> Unit) : SensorEventListener {

    private val sm: SensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val sensor: Sensor? = sm.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
    private val wm: WindowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private var sx = 0f
    private var sy = 0f
    private var sz = 0f
    private var primed = false

    fun start() {
        primed = false
        val s = sensor ?: return
        sm.registerListener(this, s, SensorManager.SENSOR_DELAY_GAME)
    }

    fun stop() {
        sm.unregisterListener(this)
    }

    @Suppress("DEPRECATION")
    private fun rotation(): Int {
        return try {
            wm.defaultDisplay.rotation
        } catch (_: Exception) {
            Surface.ROTATION_90
        }
    }

    override fun onSensorChanged(event: SensorEvent) {
        val vals = event.values
        if (vals.size < 3) return
        // Smooth the raw numbers a little.
        if (!primed) {
            sx = vals[0]
            sy = vals[1]
            sz = vals[2]
            primed = true
        } else {
            sx += 0.25f * (vals[0] - sx)
            sy += 0.25f * (vals[1] - sy)
            sz += 0.25f * (vals[2] - sz)
        }
        var up = 0f
        var rightComp = 0f
        when (rotation()) {
            Surface.ROTATION_90 -> {
                up = sx
                rightComp = -sy
            }
            Surface.ROTATION_180 -> {
                up = -sy
                rightComp = -sx
            }
            Surface.ROTATION_270 -> {
                up = -sx
                rightComp = sy
            }
            else -> {
                up = sy
                rightComp = sx
            }
        }
        val fwd = Math.toDegrees(atan2(sz.toDouble(), up.toDouble())).toFloat()
        val horiz = sqrt((up * up + sz * sz).toDouble())
        val right = Math.toDegrees(atan2((-rightComp).toDouble(), horiz)).toFloat()
        onTilt(fwd, right)
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
    }
}
