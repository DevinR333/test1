package com.blacklab.buddybounce.input

import com.blacklab.buddybounce.data.Save
import com.blacklab.buddybounce.game.MathX
import com.blacklab.buddybounce.game.MathX.clamp
import com.blacklab.buddybounce.game.Tuning
import kotlin.math.abs

/**
 * Turns the three input routes - phone tilt, touch, and a physical controller - into a single
 * steering value in [-1, 1].
 *
 * Touch is *relative*: wherever you put your finger down becomes the centre, and sliding either
 * side of that point steers. It works anywhere on the screen, so you never have to look for a
 * control, and the anchor drags along if you push past full lock so a reversal responds at once.
 *
 * The tilt route is remapped through the display rotation by the activity, otherwise landscape
 * steers the wrong way.
 */
class Controls(private val save: Save) {

    /** Acceleration along the screen's horizontal axis, m/s^2, already remapped for rotation. */
    var tiltRaw = 0f
        private set
    var tiltAvailable = false

    // ---- relative touch ----
    var touchActive = false
        private set
    var touchAnchorX = 0f
        private set
    var touchAnchorY = 0f
        private set
    var touchX = 0f
        private set
    var touchY = 0f
        private set
    private var touchSteer = 0f

    /** Distance, in UI units, from the anchor to full lock. Set by the game on resize. */
    var touchRange = 300f

    var keyLeft = false
    var keyRight = false
    var padAxis = 0f

    /** True when the most recent steering came from a digital/absolute source. */
    var lastInputDigital = false
        private set

    private var smoothedTilt = 0f

    fun onTiltSample(screenAxisAccel: Float) {
        tiltRaw = screenAxisAccel
        // Enough smoothing to kill hand tremor, little enough that the lag is not felt.
        smoothedTilt += (screenAxisAccel - smoothedTilt) * 0.55f
    }

    /**
     * Mild expo. A small movement gives a small, precise correction while full deflection is
     * still full speed - the difference between "nudge him left a bit" being possible and not.
     */
    private fun shape(v: Float): Float {
        val a = if (v < 0f) -v else v
        val shaped = MathX.pow(a, Tuning.STEER_EXPO)
        return if (v < 0f) -shaped else shaped
    }

    // -------------------------------------------------------------------------------------
    // touch
    // -------------------------------------------------------------------------------------

    fun touchDown(x: Float, y: Float) {
        touchActive = true
        touchAnchorX = x
        touchAnchorY = y
        touchX = x
        touchY = y
        touchSteer = 0f
    }

    fun touchMove(x: Float, y: Float) {
        if (!touchActive) {
            touchDown(x, y)
            return
        }
        touchX = x
        touchY = y
        var dx = x - touchAnchorX
        // Push past full lock and the anchor follows, so flicking the other way is instant.
        if (dx > touchRange) {
            touchAnchorX = x - touchRange
            dx = touchRange
        } else if (dx < -touchRange) {
            touchAnchorX = x + touchRange
            dx = -touchRange
        }
        touchSteer = clamp(dx / touchRange, -1f, 1f)
    }

    fun touchUp() {
        touchActive = false
        touchSteer = 0f
    }

    fun clearTouch() {
        touchActive = false
        touchSteer = 0f
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
        return shape(clamp(v, -1f, 1f))
    }

    /** The value the simulation should use this frame. */
    fun steer(): Float {
        val pad = clamp(padAxis, -1f, 1f)
        val keys = (if (keyRight) 1f else 0f) - (if (keyLeft) 1f else 0f)

        if (touchActive && touchEnabled) {
            lastInputDigital = true
            return shape(touchSteer)
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

    /** Captures the current tilt as "neutral" so you can play lying down. */
    fun calibrate() {
        save.tiltCalibration = smoothedTilt
    }
}
