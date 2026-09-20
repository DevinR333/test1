package com.blacklab.buddybounce.game

/** Deterministic, allocation-free pseudo-random values keyed by an index. */
object Hash {

    fun hash(i: Int, salt: Int): Int {
        var h = i * 374761393 + salt * 668265263
        h = (h xor (h ushr 13)) * 1274126177
        return h xor (h ushr 16)
    }

    /** Stable value in [0, 1) for the pair (i, salt). */
    fun f(i: Int, salt: Int): Float = (hash(i, salt) ushr 8).toFloat() / 16777216f

    fun range(i: Int, salt: Int, lo: Float, hi: Float): Float = lo + (hi - lo) * f(i, salt)

    fun int(i: Int, salt: Int, bound: Int): Int =
        if (bound <= 0) 0 else ((hash(i, salt) ushr 1) % bound)
}
