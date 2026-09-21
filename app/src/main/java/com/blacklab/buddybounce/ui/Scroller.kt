package com.blacklab.buddybounce.ui

import com.blacklab.buddybounce.game.MathX.approach
import com.blacklab.buddybounce.game.MathX.clamp
import kotlin.math.abs

/**
 * A flick-scrollable list position, with momentum.
 *
 * Dragging moves it one-to-one with the finger. Letting go hands the list whatever speed the
 * finger had, and it coasts down from there - the wheel-of-fortune feel, where a flick keeps
 * going and slowly gives up. Putting a finger back down while it is still moving **grabs** it:
 * the coast stops dead and the list is under the finger again, and that press is swallowed so
 * it does not also count as tapping whatever it landed on.
 *
 * Hitting either end kills the momentum rather than bouncing, so a hard flick at the bottom of
 * a list stops at the bottom instead of quivering there.
 */
class Scroller {

    /** Current offset, always inside [0, max]. */
    var y = 0f
        private set

    /** Speed in units per second. Non-zero only while coasting. */
    private var velocity = 0f

    /** Distance dragged since the finger went down; used to tell a tap from a drag. */
    private var dragged = 0f

    private var wasDown = false

    /** True when the press that is currently down began by grabbing a moving list. */
    private var grabbed = false

    /** True while the list is coasting under its own momentum. */
    val coasting: Boolean get() = abs(velocity) > 1f

    /**
     * True when the press in progress should NOT be treated as a tap - either because it has
     * been dragged, or because it started as a grab of a moving list.
     */
    val suppressTap: Boolean get() = grabbed || dragged > TAP_SLOP

    fun reset() {
        y = 0f
        velocity = 0f
        dragged = 0f
        grabbed = false
    }

    /**
     * Scrolls by [delta] immediately, killing any momentum.
     *
     * Used to bring a controller-focused row into view. The jump is instant rather than eased
     * because the ring has already moved - easing the list behind it would leave the two out of
     * step for as long as the glide lasted.
     */
    fun nudge(delta: Float) {
        y += delta
        velocity = 0f
    }

    /**
     * Advance one frame.
     *
     * @param down      is a finger on the glass
     * @param dragDy    finger movement this frame, in the same units as [y]
     * @param max       the largest valid offset (0 when everything fits)
     */
    fun update(dt: Float, down: Boolean, dragDy: Float, max: Float) {
        if (down) {
            if (!wasDown) {
                // New press. If the list was still moving, this is a grab, not a tap.
                grabbed = coasting
                dragged = 0f
                velocity = 0f
            }
            dragged += abs(dragDy)
            y -= dragDy
            // Track the finger's speed so the release has something to hand over. Smoothed,
            // because a single frame's delta is noisy enough to throw a flick in the wrong
            // direction if the finger stutters on the last sample.
            if (dt > 0f) velocity = approach(velocity, -dragDy / dt, FLICK_SMOOTH, dt)
        } else {
            if (wasDown) grabbed = false
            if (coasting) {
                y += velocity * dt
                velocity = approach(velocity, 0f, FRICTION, dt)
            } else {
                velocity = 0f
            }
        }

        val clamped = clamp(y, 0f, max.coerceAtLeast(0f))
        // Running into an end stops the coast dead rather than letting it grind against the edge.
        if (clamped != y && !down) velocity = 0f
        y = clamped
        wasDown = down
    }

    private companion object {
        /** How quickly a released flick gives up, 1/s. Lower coasts further. */
        const val FRICTION = 3.2f
        /** Smoothing on the tracked finger speed, 1/s. */
        const val FLICK_SMOOTH = 30f
        /** Movement below this still counts as a tap rather than a drag. */
        const val TAP_SLOP = 26f
    }
}
