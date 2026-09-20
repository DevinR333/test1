package com.blacklab.buddybounce.input

import com.blacklab.buddybounce.data.Save
import com.blacklab.buddybounce.game.MathX.clamp
import com.blacklab.buddybounce.game.Tuning
import kotlin.math.abs

/**
 * Turns the three input routes - phone tilt, the on-screen slide gauge and a physical
 * controller/keyboard - into a single steering value in [-1, 1].
 *
 * The tilt route is the interesting one: the accelerometer reports in *device* axes, so the
 * reading has to be remapped through the current display rotation or landscape mode steers
 * the wrong way. The activity does that remap and hands us [tiltRaw] already in screen space.
 */
class Controls(private val save: Save) {

    /** Acceleration along the screen's horizontal axis, m/s^2, already remapped for rotation. */
    var tiltRaw = 0f
        private set
    var tiltAvailable = false

    var gaugeActive = false
        private set
    var gaugeValue = 0f
        private set

    var keyLeft = false
    var keyRight = false
    var padAxis = 0f

    /** True when the most recent steering came from a digital/absolute source. */
    var lastInputDigital = false
        private set

    private var smoothedTilt = 0f

    fun onTiltSample(screenAxisAccel: Float) {
        tiltRaw = screenAxisAccel
        // A light low-pass keeps hand tremor out without adding noticeable lag.
        smoothedTilt += (screenAxisAccel - smoothedTilt) * 0.35f
    }

    fun onGauge(value: Float) {
        gaugeValue = clamp(value, -1f, 1f)
        gaugeActive = true
    }

    fun releaseGauge() {
        gaugeActive = false
    }

    fun clearGauge() {
        gaugeActive = false
        gaugeValue = 0f
    }

    /** Captures the current tilt as "neutral" so you can play lying down. */
    fun calibrate() {
        save.tiltCalibration = smoothedTilt
    }

    fun resetCalibration() {
        save.tiltCalibration = 0f
    }

    val tiltEnabled: Boolean
        get() = save.controlMode == Save.CONTROL_TILT || save.controlMode == Save.CONTROL_BOTH

    val touchEnabled: Boolean
        get() = save.controlMode == Save.CONTROL_TOUCH || save.controlMode == Save.CONTROL_BOTH

    /** Raw tilt steering, before any of the other sources get a say. */
    fun tiltSteer(): Float {
        if (!tiltEnabled || !tiltAvailable) return 0f
        var v = smoothedTilt - save.tiltCalibration
        val dead = Tuning.TILT_DEADZONE
        v = when {
            v > dead -> v - dead
            v < -dead -> v + dead
            else -> 0f
        }
        v /= (Tuning.TILT_FULLSCALE - dead)
        v *= save.tiltSensitivity
        if (save.invertTilt) v = -v
        return clamp(v, -1f, 1f)
    }

    /** The value the simulation should use this frame. */
    fun steer(): Float {
        val pad = clamp(padAxis, -1f, 1f)
        val keys = (if (keyRight) 1f else 0f) - (if (keyLeft) 1f else 0f)

        if (gaugeActive && touchEnabled) {
            lastInputDigital = true
            return gaugeValue
        }
        if (abs(keys) > 0.01f) {
            lastInputDigital = true
            return keys
        }
        if (abs(pad) > 0.12f) {
            lastInputDigital = true
            return pad
        }
        lastInputDigital = false
        return tiltSteer()
    }

    /** Gauge position to draw, even when the finger is off the track. */
    fun displayValue(): Float = if (gaugeActive) gaugeValue else steer()
}
