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
 *
 * NOTE ON ZOOM: the world view is 2560 wu tall while the UI is laid out in a 1600-unit space
 * (see Theme.SCREEN_H). Buddy and the platforms are sized in absolute wu, so making the world
 * view taller is exactly a camera zoom-out: the same jump, more sky around it.
 */
object Tuning {

    // ---- frame of reference ----------------------------------------------------------------
    const val VIEW_H = 2560f
    /** Width of the reference playfield (a 9:16 phone), used to scale aspect-dependent values. */
    const val REF_W = 1440f

    // ---- vertical physics ------------------------------------------------------------------
    const val GRAVITY = 6250f
    const val JUMP_V = 3250f              // apex = v^2/2g = 845 wu = 33% of the view
    const val TERMINAL_V = 5400f
    const val PHYSICS_STEP = 1f / 240f
    const val MAX_SUBSTEPS = 8

    // ---- horizontal ------------------------------------------------------------------------
    const val REF_MAX_VX = 1900f

    // Steering uses ASYMMETRIC rates: speeding up is deliberately softer than slowing down.
    // A single symmetric rate is what made falls feel imprecise - the velocity lagged the
    // finger on the way in AND on the way out, so Buddy kept drifting after a correction and
    // sailed past the platform. Braking hard means "stop" is instant and a correction lands.
    const val STEER_ACCEL_TILT = 15f
    const val STEER_BRAKE_TILT = 38f
    const val STEER_ACCEL_DIGITAL = 26f
    const val STEER_BRAKE_DIGITAL = 55f
    /** Small inputs get finer control; full deflection is still full speed. */
    const val STEER_EXPO = 1.25f

    // ---- positional drag (touch) -----------------------------------------------------------
    // Touch used to be a *rate* control: finger offset set a speed you had to HOLD. That is why
    // moving him any distance meant swiping the whole screen and then keeping the finger out
    // there - and why a mid-fall correction never landed, because letting go stopped him dead.
    // Now the finger drags a target position and Buddy servos to it, so distance swiped maps
    // directly to distance travelled and releasing leaves him where you put him.

    /** World units Buddy moves per world unit of finger travel. >1 so a thumb-flick crosses. */
    const val DRAG_GAIN = 2.35f
    /** How hard he chases the drag target, in 1/s. High = he tracks the finger almost exactly. */
    const val DRAG_STIFFNESS = 11.0f
    /**
     * How far ahead of Buddy the drag target is allowed to get, expressed as SECONDS of travel
     * at his top speed rather than a fixed distance. A fixed distance was wrong on landscape,
     * where the playfield is three times wider and he moves twice as fast: the same 560 wu that
     * felt right in portrait threw away most of a long swipe. As a time it means the same thing
     * everywhere - "he may be up to a third of a second behind your finger" - which caps the
     * coast after you let go without ever clipping a swipe he could actually have followed.
     */
    const val DRAG_LEAD_SECONDS = 0.30f
    /** Closing a big gap is allowed to exceed the steady-state speed cap by this much. */
    const val DRAG_OVERSPEED = 1.45f
    /** Velocity easing while dragging: stiff both ways, because the finger *is* the position. */
    const val STEER_ACCEL_DRAG = 42f
    const val STEER_BRAKE_DRAG = 70f

    const val TILT_DEADZONE = 0.45f       // m/s^2
    const val TILT_FULLSCALE = 4.2f       // m/s^2 (~25 degrees) for full deflection

    // ---- camera --------------------------------------------------------------------------
    const val CAM_ANCHOR = 0.45f          // fraction from the top of the view
    const val CAM_EASE = 18f

    // ---- player ----------------------------------------------------------------------------
    // He is drawn in profile, so he is wider than he is tall.
    const val BUDDY_W = 220f
    const val BUDDY_H = 160f
    /** Collision half-width at the paws - narrower than the art so near-misses feel generous. */
    const val BUDDY_FOOT_HALF = 56f
    /**
     * Extra landing tolerance granted when he is still travelling toward the platform's centre.
     * Clipping the corner of a ledge you were obviously aiming for should catch, not drop you.
     */
    const val LAND_GRAB = 26f
    const val BUDDY_HURT_HALF_W = 62f
    const val BUDDY_HURT_HALF_H = 52f

    // ---- the starting yard -------------------------------------------------------------------
    /** World Y of the solid ground Buddy starts above. Falling onto it is always safe. */
    const val GROUND_Y = 150f
    const val START_Y = 470f

    // ---- platforms -------------------------------------------------------------------------
    const val PLAT_H = 34f
    const val PLAT_EDGE_MARGIN = 40f
    const val CRUMBLE_TIME = 0.35f
    const val FRAGILE_TIME = 0.30f
    const val SLIDER_SPEED_MIN = 130f
    const val SLIDER_SPEED_MAX = 300f
    const val HOVER_AMPLITUDE = 88f
    const val HOVER_HZ = 0.6f

    // ---- boosts ----------------------------------------------------------------------------
    const val SPRING_MULT = 2.0f
    const val TRAMPOLINE_MULT = 2.6f
    const val PROPELLER_V = 3040f
    const val PROPELLER_TIME = 3.2f
    const val JETPACK_V = 4240f
    const val JETPACK_TIME = 4.0f
    const val ROCKET_V = 5760f
    const val ROCKET_TIME = 4.6f
    const val SHIELD_TIME = 12f
    const val MAGNET_TIME = 7f
    const val MAGNET_RANGE = 830f
    const val ENEMY_STOMP_V = 2640f
    const val FLIGHT_EXIT_V = 400f        // velocity handed back to gravity when flight ends

    // ---- economy ---------------------------------------------------------------------------
    // Coins are deliberately scarce: a pull should feel earned. Most of a run's coins come from
    // the height bonus at the end, the rest from the handful of coins actually on the way up.
    const val COIN_VALUE = 1
    const val BONE_COIN_VALUE = 5
    const val GACHA_COST = 100
    const val DUPLICATE_REFUND = 35
    /** Points per coin awarded at the end of a run. */
    const val SCORE_PER_BONUS_COIN = 300
    /** A coin is placed roughly this far apart, in wu of climb. */
    const val COIN_SPACING_MIN = 7600f
    const val COIN_SPACING_MAX = 15400f
    /** One coin in this many is a 5-coin bone instead. */
    const val BONE_EVERY = 5

    // ---- scoring -----------------------------------------------------------------------------
    const val SCORE_PER_WU = 0.0625f      // 1 screen climbed = 160 points
    const val SCORE_BEE = 120
    const val SCORE_CROW = 150

    // ---- biomes ------------------------------------------------------------------------------
    /** Screens of climb per biome band. */
    const val BIOME_SPAN = 9f
    const val BIOME_COUNT = 5

    // ---- difficulty curve --------------------------------------------------------------------

    /** Difficulty 0..1 as a function of screens climbed. */
    fun difficulty(screens: Float): Float = smoothstep(0f, 55f, screens)

    // Dense and forgiving down in the yard, thinning out as the climb gets serious.
    fun gapMin(screens: Float): Float = lerp(300f, 575f, difficulty(screens))

    fun gapMax(screens: Float): Float = lerp(380f, 640f, difficulty(screens))

    /** Platform width before the aspect-ratio correction in [Metrics]. */
    fun platWidth(screens: Float): Float = lerp(190f, 140f, difficulty(screens))

    /** Share of rows whose main platform crumbles after one bounce. */
    fun hazardShare(screens: Float): Float = lerp(0f, 0.34f, smoothstep(2f, 45f, screens))

    /**
     * Chance a row gets a fragile platform ALONGSIDE its safe one. Fragiles give no bounce at
     * all, so one is never allowed to be a row's only platform - that is a forced death, not a
     * challenge.
     */
    fun fragileShare(screens: Float): Float = lerp(0f, 0.30f, smoothstep(6f, 45f, screens))

    /** Share of rows that get a moving platform. */
    fun moverShare(screens: Float): Float = lerp(0f, 0.36f, smoothstep(2f, 40f, screens))

    /** Expected enemies per screen of generated world. */
    fun enemyDensity(screens: Float): Float = lerp(0f, 1.1f, smoothstep(4f, 45f, screens))

    /**
     * Chance a row spawns a second, side-by-side platform. High early so the first screens are
     * busy and forgiving, falling away as you climb so the top is genuinely sparse.
     */
    fun doubleRowChance(screens: Float): Float = lerp(0.38f, 0.05f, smoothstep(0f, 26f, screens))

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
        val rowMultiplier: Float = pow(widthRatio, 0.55f)

        fun platWidthAt(screens: Float): Float = platWidth(screens) * platScale
    }
}
