package com.blacklab.buddybounce.input

import com.blacklab.buddybounce.data.Save
import com.blacklab.buddybounce.game.MathX
import com.blacklab.buddybounce.game.MathX.clamp
import com.blacklab.buddybounce.game.Tuning
import kotlin.math.abs

/**
 * Turns the three input routes - phone tilt, touch, and a physical controller - into steering.
 *
 * Tilt and the pad are *rate* controls: they produce a steering value in [-1, 1] that maps to a
 * speed. Touch is not. Touch is a **positional drag**: the finger carries Buddy, so however far
 * you slide is however far he goes, and he stays there when you stop. That means this class
 * reports touch as a *delta* ([consumeDragDx]) rather than as a steer value, and the world
 * servos him to the dragged target.
 *
 * It works anywhere on the screen, so you never have to look for a control, and nothing is drawn
 * for it.
 *
 * The tilt route is remapped through the display rotation by the activity, otherwise landscape
 * steers the wrong way.
 */
class Controls(private val save: Save) {

    /** Acceleration along the screen's horizontal axis, m/s^2, already remapped for rotation. */
    var tiltRaw = 0f
        private set
    var tiltAvailable = false

    // ---- positional drag ----
    var touchActive = false
        private set
    var touchX = 0f
        private set
    var touchY = 0f
        private set
    /** Finger travel, in UI units, accumulated since the simulation last consumed it. */
    private var dragDx = 0f

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
        touchX = x
        touchY = y
        dragDx = 0f
    }

    fun touchMove(x: Float, y: Float) {
        if (!touchActive) {
            touchDown(x, y)
            return
        }
        // Pure delta. No anchor, no range, no dead zone: every pixel of finger travel counts,
        // and it keeps counting however far across the screen you go.
        dragDx += x - touchX
        touchX = x
        touchY = y
    }

    fun touchUp() {
        touchActive = false
        dragDx = 0f
    }

    fun clearTouch() {
        touchActive = false
        dragDx = 0f
    }

    /** True when a finger is down and touch steering is turned on. */
    val dragging: Boolean
        get() = touchActive && touchEnabled

    /**
     * Hands the simulation the finger travel since the last call and resets it, so a frame can
     * never apply the same movement twice or silently drop part of a fast swipe.
     */
    fun consumeDragDx(): Float {
        val d = dragDx
        dragDx = 0f
        return d
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

    /**
     * The rate-control steering for this frame. Touch is deliberately absent - it is positional
     * and goes through [consumeDragDx] instead - so a finger on the glass simply means the tilt
     * and pad routes stop fighting it.
     */
    fun steer(): Float {
        if (dragging) {
            lastInputDigital = true
            return 0f
        }
        val pad = clamp(padAxis, -1f, 1f)
        val keys = (if (keyRight) 1f else 0f) - (if (keyLeft) 1f else 0f)

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
