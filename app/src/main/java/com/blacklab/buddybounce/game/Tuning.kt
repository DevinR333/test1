package com.blacklab.buddybounce.game

import com.blacklab.buddybounce.game.MathX.clamp01
import com.blacklab.buddybounce.game.MathX.lerp
import com.blacklab.buddybounce.game.MathX.pow
import com.blacklab.buddybounce.game.MathX.smoothstep

/**
 * Every gameplay number lives here. See docs/MECHANICS.md for where each value comes from.
 *
 * The unit is the "world unit" (wu). The camera always shows exactly [VIEW_H] wu of height on
 * every device, in both orientations, so a bounce covers the same fraction of the screen
 * whether you're on a 4:3 tablet or a 21:9 phone. Width is whatever the aspect ratio gives.
 */
object Tuning {

    // ---- frame of reference ----------------------------------------------------------------
    const val VIEW_H = 1600f
    /** Width of the reference playfield (a 9:16 phone), used to scale aspect-dependent values. */
    const val REF_W = 900f

    // ---- vertical physics ------------------------------------------------------------------
    const val GRAVITY = 3300f
    const val JUMP_V = 1720f              // apex = 448 wu = 0.28 screens
    const val TERMINAL_V = 3000f
    const val PHYSICS_STEP = 1f / 240f
    const val MAX_SUBSTEPS = 8

    // ---- horizontal ------------------------------------------------------------------------
    const val REF_MAX_VX = 1035f
    const val STEER_EASE_TILT = 9f
    const val STEER_EASE_DIGITAL = 16f
    const val TILT_DEADZONE = 0.6f        // m/s^2
    const val TILT_FULLSCALE = 4.5f       // m/s^2 (~27 degrees) for full deflection

    // ---- camera --------------------------------------------------------------------------
    const val CAM_ANCHOR = 0.45f          // fraction from the top of the view
    const val CAM_EASE = 18f
    const val CAM_START_EASE = 3.2f

    // ---- player ----------------------------------------------------------------------------
    const val BUDDY_W = 128f
    const val BUDDY_H = 128f
    /** Collision half-width at the paws - narrower than the art so near-misses feel generous. */
    const val BUDDY_FOOT_HALF = 44f
    const val BUDDY_HURT_HALF_W = 46f
    const val BUDDY_HURT_HALF_H = 46f

    // ---- platforms -------------------------------------------------------------------------
    const val PLAT_H = 30f
    const val PLAT_EDGE_MARGIN = 26f      // keep platforms off the very edge of the playfield
    const val CRUMBLE_TIME = 0.35f
    const val FRAGILE_TIME = 0.30f
    const val SLIDER_SPEED_MIN = 80f
    const val SLIDER_SPEED_MAX = 190f
    const val HOVER_AMPLITUDE = 55f
    const val HOVER_HZ = 0.6f

    // ---- boosts ----------------------------------------------------------------------------
    const val SPRING_MULT = 2.0f
    const val TRAMPOLINE_MULT = 2.6f
    const val PROPELLER_V = 1900f
    const val PROPELLER_TIME = 3.2f
    const val JETPACK_V = 2650f
    const val JETPACK_TIME = 4.0f
    const val ROCKET_V = 3600f
    const val ROCKET_TIME = 4.6f
    const val SHIELD_TIME = 12f
    const val MAGNET_TIME = 7f
    const val MAGNET_RANGE = 520f
    const val ENEMY_STOMP_V = 1400f
    const val FLIGHT_EXIT_V = 250f        // velocity handed back to gravity when flight ends

    // ---- economy ---------------------------------------------------------------------------
    const val COIN_VALUE = 1
    const val BONE_COIN_VALUE = 5
    const val GACHA_COST = 100
    const val DUPLICATE_REFUND = 35

    // ---- scoring -----------------------------------------------------------------------------
    const val SCORE_PER_WU = 0.1f         // 1 screen climbed = 160 points
    const val SCORE_BEE = 120
    const val SCORE_CROW = 150

    // ---- biomes ------------------------------------------------------------------------------
    /** Screens of climb per biome band. */
    const val BIOME_SPAN = 9f
    const val BIOME_COUNT = 5

    // ---- difficulty curve --------------------------------------------------------------------

    /** Difficulty 0..1 as a function of screens climbed. */
    fun difficulty(screens: Float): Float = smoothstep(0f, 55f, screens)

    fun gapMin(screens: Float): Float {
        val d = difficulty(screens)
        return lerp(170f, 265f, d)
    }

    fun gapMax(screens: Float): Float {
        val d = difficulty(screens)
        return lerp(215f, 336f, d)
    }

    /** Platform width before the aspect-ratio correction in [Metrics]. */
    fun platWidth(screens: Float): Float {
        val d = difficulty(screens)
        return lerp(200f, 148f, d)
    }

    /** Share of rows that get a crumbling/fragile platform. */
    fun hazardShare(screens: Float): Float = lerp(0f, 0.38f, smoothstep(2f, 45f, screens))

    /** Share of rows that get a moving platform. */
    fun moverShare(screens: Float): Float = lerp(0f, 0.36f, smoothstep(2f, 40f, screens))

    /** Expected enemies per screen of generated world. */
    fun enemyDensity(screens: Float): Float = lerp(0f, 1.3f, smoothstep(4f, 45f, screens))

    /** Chance a row spawns a second, side-by-side platform. */
    fun doubleRowChance(screens: Float): Float = lerp(0.12f, 0.30f, difficulty(screens))

    fun biomeIndex(screens: Float): Int {
        val i = (screens / BIOME_SPAN).toInt()
        return if (i < 0) 0 else i
    }

    /** 0..1 blend inside the current biome band, used to cross-fade the backdrop. */
    fun biomeBlend(screens: Float): Float {
        val raw = screens / BIOME_SPAN
        val frac = raw - raw.toInt()
        return clamp01((frac - 0.78f) / 0.22f)
    }

    /**
     * Aspect-ratio derived numbers. A landscape playfield is three times wider than a portrait
     * one, so speed, platform size and row density all get corrected - otherwise landscape is
     * a different (and much worse) game.
     */
    class Metrics(val worldW: Float) {
        val widthRatio: Float = (worldW / REF_W).coerceIn(0.55f, 4.0f)
        val maxVx: Float = REF_MAX_VX * pow(widthRatio, 0.6f)
        val platScale: Float = pow(widthRatio, 0.35f)
        /** Platforms generated per row band, so wide screens don't feel empty. */
        val rowMultiplier: Float = pow(widthRatio, 0.75f)

        fun platWidthAt(screens: Float): Float = platWidth(screens) * platScale
    }
}
