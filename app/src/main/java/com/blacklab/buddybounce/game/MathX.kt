package com.blacklab.buddybounce.game

import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.pow
import kotlin.math.sqrt

/** Tiny float helpers used all over the game and UI. All allocation-free. */
object MathX {

    fun lerp(a: Float, b: Float, t: Float): Float = a + (b - a) * t

    fun clamp(v: Float, lo: Float, hi: Float): Float = if (v < lo) lo else if (v > hi) hi else v

    fun clamp01(v: Float): Float = clamp(v, 0f, 1f)

    /** 0..1 ramp between [edge0] and [edge1], smoothed at both ends. */
    fun smoothstep(edge0: Float, edge1: Float, x: Float): Float {
        if (edge1 == edge0) return if (x < edge0) 0f else 1f
        val t = clamp01((x - edge0) / (edge1 - edge0))
        return t * t * (3f - 2f * t)
    }

    /** Frame-rate independent exponential approach: moves [cur] toward [target]. */
    fun approach(cur: Float, target: Float, rate: Float, dt: Float): Float {
        if (rate <= 0f) return target
        val k = 1f - exp(-rate * dt)
        return cur + (target - cur) * k
    }

    /**
     * Shortest signed horizontal delta between two x positions on a playfield that wraps at
     * [width]. Positive means [a] is to the right of [b].
     */
    fun wrapDelta(a: Float, b: Float, width: Float): Float {
        var d = a - b
        if (width <= 0f) return d
        val half = width * 0.5f
        while (d > half) d -= width
        while (d < -half) d += width
        return d
    }

    /** Wraps an x position into [0, width). */
    fun wrapX(x: Float, width: Float): Float {
        if (width <= 0f) return x
        var v = x
        while (v < 0f) v += width
        while (v >= width) v -= width
        return v
    }

    fun mix(a: Float, b: Float, c: Float, t: Float): Float =
        if (t < 0.5f) lerp(a, b, t * 2f) else lerp(b, c, (t - 0.5f) * 2f)

    fun pow(a: Float, b: Float): Float = a.toDouble().pow(b.toDouble()).toFloat()

    fun sqrtf(a: Float): Float = sqrt(a.toDouble()).toFloat()

    fun absf(a: Float): Float = abs(a)
}
