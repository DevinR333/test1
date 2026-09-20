package com.blacklab.buddybounce.game

import com.blacklab.buddybounce.game.MathX.approach
import com.blacklab.buddybounce.game.MathX.clamp
import kotlin.math.abs

/**
 * The dog. [y] is the paw line (bottom of the collision box); the art is drawn from there up.
 * Everything in here that isn't physics is animation state the renderer reads.
 */
class Buddy {

    var x = 0f
    var y = 0f
    var vx = 0f
    var vy = 0f

    var alive = true
    var dying = false
    var deathT = 0f
    var deathSpin = 0f

    var flight = Flight.NONE
    var flightTime = 0f
    var shieldTime = 0f
    var magnetTime = 0f
    var invulnT = 0f

    // ---- animation ----
    var t = 0f
    var facing = 1f
    /** > 0 stretched (rising fast), < 0 squashed (just landed). */
    var squash = 0f
    var lean = 0f
    var earFlap = 0f
    var tailPhase = 0f
    var blinkT = 2.4f
    var blinkAmount = 0f
    var mouthOpen = 0f
    var landFlash = 0f
    var hurtFlash = 0f

    val flying: Boolean get() = flight != Flight.NONE

    fun reset(startX: Float, startY: Float) {
        x = startX; y = startY
        vx = 0f; vy = Tuning.JUMP_V
        alive = true; dying = false; deathT = 0f; deathSpin = 0f
        flight = Flight.NONE; flightTime = 0f
        shieldTime = 0f; magnetTime = 0f; invulnT = 0f
        t = 0f; facing = 1f; squash = 0f; lean = 0f
        earFlap = 0f; tailPhase = 0f; blinkT = 2.4f; blinkAmount = 0f
        mouthOpen = 0f; landFlash = 0f; hurtFlash = 0f
    }

    fun onBounce(strength: Float) {
        squash = -1f
        landFlash = 1f
        earFlap = 1f
        mouthOpen = clamp(strength * 0.55f, 0.25f, 1f)
    }

    fun updateAnim(dt: Float, maxVx: Float) {
        t += dt

        if (abs(vx) > maxVx * 0.08f) facing = if (vx > 0f) 1f else -1f

        // Stretch with vertical speed, but let the landing squash win for a moment.
        val speedStretch = clamp(vy / (Tuning.JUMP_V * 1.35f), -0.55f, 0.85f)
        val target = if (squash < 0f) squash else speedStretch
        squash = approach(squash, target, 11f, dt)
        if (squash < 0f) squash = approach(squash, 0f, 9f, dt)

        lean = approach(lean, clamp(vx / maxVx, -1f, 1f), 7f, dt)
        earFlap = approach(earFlap, 0f, 4.5f, dt)
        landFlash = approach(landFlash, 0f, 7f, dt)
        hurtFlash = approach(hurtFlash, 0f, 3.5f, dt)
        mouthOpen = approach(mouthOpen, if (flying) 0.7f else 0f, 3.2f, dt)

        // Tail wags faster while climbing, slower on the way down.
        val wagRate = if (vy > 0f) 11f else 6.5f
        tailPhase += dt * wagRate

        blinkT -= dt
        if (blinkT <= 0f) {
            blinkT = 2.2f + (t * 37f % 2.6f)
            blinkAmount = 1f
        }
        blinkAmount = approach(blinkAmount, 0f, 13f, dt)

        if (dying) {
            deathT += dt
            deathSpin += dt * (2.4f + deathT * 3.2f)
        }
    }
}
