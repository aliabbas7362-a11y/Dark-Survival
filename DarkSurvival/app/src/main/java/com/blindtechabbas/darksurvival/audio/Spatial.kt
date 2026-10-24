package com.blindtechabbas.darksurvival.audio

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin

/**
 * How a sound at a place is turned into left and right volume.
 * bearing: 0 = straight ahead, +90 = right, -90 = left, 180 = behind.
 * Phone speakers cannot tell front from back, so a sound behind you is also
 * made a little quieter and lower. The spoken callout always names the direction too.
 */
object Spatial {
    /** Volume from distance in feet: 1.0 close, about 0.5 at 60 feet, 0.08 at 300 feet. */
    fun distanceGain(distFt: Float): Float {
        val d = max(distFt, 0f)
        val g = 1f / (1f + (d / 60f).pow(1.5f))
        return max(g, 0.06f)
    }

    /** -1 (full left) .. +1 (full right). */
    fun pan(bearing: Float): Float {
        return sin(Math.toRadians(bearing.toDouble())).toFloat()
    }

    /** 0 = in front of you, 1 = straight behind you. */
    fun behind(bearing: Float): Float {
        val c = cos(Math.toRadians(bearing.toDouble())).toFloat()
        return max(0f, -c)
    }

    fun left(pan: Float, volume: Float): Float {
        val angle = (pan + 1f) * (PI.toFloat() / 4f)
        return min(1f, volume * cos(angle) * 1.4f)
    }

    fun right(pan: Float, volume: Float): Float {
        val angle = (pan + 1f) * (PI.toFloat() / 4f)
        return min(1f, volume * sin(angle) * 1.4f)
    }
}
