package com.blacklab.buddybounce.input

/**
 * Turns raw accelerometer axes into "which way is down, on screen".
 *
 * At rest the accelerometer reports the UP direction in device axes, times one g. What every
 * caller actually wants is the opposite, resolved onto the screen as it is currently being
 * held - and the screen is not the device: a phone in landscape has its natural top edge off
 * to one side, so the device's own x and y are meaningless until they are remapped through the
 * display rotation.
 *
 * `rotation` is a `Surface.ROTATION_*` constant, but the class is deliberately free of android
 * imports so this can be checked headlessly - see tools/tilt/TiltCheck.kt, which drives it with
 * real physical poses (upright, upside down, on its side, face up on a table) and asserts where
 * the gravity ends up pointing on screen.
 */
object TiltMap {

    const val ROTATION_0 = 0
    const val ROTATION_90 = 1
    const val ROTATION_180 = 2
    const val ROTATION_270 = 3

    /** One g, m/s^2 - what a reading of this size along an axis means. */
    const val G = 9.81f

    /**
     * Acceleration along the screen's horizontal axis, positive when gravity pulls toward the
     * RIGHT-hand edge of the screen. This is what steering uses: tilt the right edge down and
     * Buddy goes right.
     */
    fun steer(x: Float, y: Float, rotation: Int): Float = when (rotation) {
        ROTATION_90 -> y
        ROTATION_180 -> x
        ROTATION_270 -> -y
        else -> -x
    }

    /**
     * Acceleration along the screen's vertical axis, positive when gravity pulls toward the
     * BOTTOM of the screen.
     *
     * Screen-down is screen-right turned a quarter turn, so each case is the other device axis.
     * Steering never needed this - Buddy only moves sideways - which is why it did not exist,
     * and why the capsules in the prize machine only knew left from right.
     */
    fun down(x: Float, y: Float, rotation: Int): Float = when (rotation) {
        ROTATION_90 -> x
        ROTATION_180 -> -y
        ROTATION_270 -> -x
        else -> y
    }
}
