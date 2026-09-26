package com.blacklab.buddybounce.game

import com.blacklab.buddybounce.game.MathX.clamp
import com.blacklab.buddybounce.game.MathX.wrapDelta
import com.blacklab.buddybounce.game.MathX.wrapX
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

/**
 * The simulation: infinite upward generation, one-way platform collisions, the up-only camera,
 * pick-ups, hazards and the difficulty curve. Knows nothing about drawing.
 */
class World(worldWidth: Float, private val events: Events) {

    interface Events {
        fun onBounce(platform: Platform, strength: Float) {}
        fun onPlatformBreak(platform: Platform) {}
        fun onPickup(pickup: Pickup) {}
        /** A power-up that was already running, converted to [coins] instead of being wasted. */
        fun onRedundantPickup(pickup: Pickup, coins: Int) {}
        /** Climbing past a height threshold has just earned [gained] more banked coins. */
        fun onHeightCoins(gained: Int, total: Int) {}
        fun onFlightStart(kind: Int) {}
        fun onFlightEnd(kind: Int) {}
        fun onStomp(enemy: Enemy) {}

        /** The one-in-thousands coin. Worth a fuss. */
        fun onSilverCoin(x: Float, y: Float) {}
        fun onShieldBreak(x: Float, y: Float) {}
        fun onRescue(x: Float, y: Float) {}
        fun onDeath(cause: Int) {}
        fun onBiomeChange(biome: Int) {}
    }

    var worldW: Float = worldWidth
        private set

    /** Physics wraps at the screen edge. See Tuning.WRAP_SHOW for why the DRAWING does not. */
    val wrapW: Float get() = worldW
    var metrics = Tuning.Metrics(worldWidth)
        private set

    val platforms = Pool { Platform() }
    val pickups = Pool { Pickup() }
    val enemies = Pool { Enemy() }
    val buddy = Buddy()

    private val rng = Random(System.nanoTime())

    var camY = 0f
        private set
    var startY = 0f
        private set
    var maxY = 0f
        private set
    var runCoins = 0
    /** Halos picked up this run. Only ever non-zero in [haloMode]. */
    var runHalos = 0
        private set
    var bonusScore = 0
        private set
    var finished = false
        private set
    /** Set once the death animation has played out and the game-over screen may take over. */
    var deathSettled = false
        private set

    var biome = 0
        private set

    // ---- run modifiers, set by a consumable power-up before the run starts ----
    var coinMultiplier = 1
        private set
    var safetyNets = 0
        private set
    var magnetForever = false
        private set
    private var lowGravityTime = 0f
    private var coinSpacingScale = 1f

    private var genY = 0f
    private var enemyCredit = 0f

    /** Where Buddy's feet were at the top of this frame. See the stomp test in updateEnemies. */
    private var buddyPrevFoot = 0f

    /**
     * Which coin of this run is the silver one, or -1 for the overwhelming majority of runs
     * that do not have one. Decided once, at reset - see Tuning.SILVER_COIN_CHANCE.
     */
    private var silverOnCoin = -1
    private var rowsSinceSolid = 0
    private var lastWasHazard = false
    private var lastPlatX = 0f
    private var accumulator = 0f

    /** Where the finger has dragged Buddy to. Only meaningful while a finger is down. */
    private var dragTargetX = 0f
    private var wasDragging = false
    private var nextCoinY = 0f
    /**
     * Heaven rules: the currency on the ground is halos, and height milestones pay NOTHING.
     * Halos have to be earned by actually collecting them, which is what makes a thousand of
     * them mean something.
     */
    /**
     * Which world's creatures these enemies are, band by band, so each one's hit box can be the
     * size of the thing actually drawn - see [EnemyBox].
     *
     * Per BAND, not per world: a band that leaves the element its world is made of needs other
     * creatures, and Deep Blue's Open Sky is above the water. Set by Game.applyScene alongside
     * [haloMode]; the simulation does not read the renderer itself.
     */
    var bandFauna = IntArray(1)

    /** The creatures that belong at [band], falling back to the first entry. */
    fun faunaAt(band: Int): Int {
        if (bandFauna.isEmpty()) return 0
        var i = band % bandFauna.size
        if (i < 0) i += bandFauna.size
        return bandFauna[i]
    }

    var haloMode = false
    /** Last value of [heightBonusCoins] we told anyone about, so we can report the increments. */
    private var reportedBonusCoins = 0
    private var coinsPlaced = 0

    val screens: Float get() = (maxY - startY) / Tuning.VIEW_H
    val heightWu: Float get() = maxY - startY
    val score: Int get() = (heightWu * Tuning.SCORE_PER_WU).toInt().coerceAtLeast(0) + bonusScore

    /** Coins awarded for the height climbed, on top of the ones picked up. */
    val heightBonusCoins: Int
        get() = if (haloMode) 0 else (score / Tuning.SCORE_PER_BONUS_COIN) * coinMultiplier

    /** Screens climbed at an arbitrary world Y - used by generation and the backdrop. */
    fun screensAt(y: Float): Float = (y - startY) / Tuning.VIEW_H

    // -------------------------------------------------------------------------------------
    // lifecycle
    // -------------------------------------------------------------------------------------

    fun reset() {
        platforms.clear(); pickups.clear(); enemies.clear()
        camY = 0f
        startY = Tuning.GROUND_Y
        maxY = startY
        runCoins = 0
        lapCoins = 0
        runHalos = 0
        // One roll for the whole run. Placed a few coins in, so a run that has one reaches it.
        silverOnCoin = if (rand(0f, 1f) < Tuning.SILVER_COIN_CHANCE) 2 + rng.nextInt(3) else -1
        bonusScore = 0
        reportedBonusCoins = 0
        finished = false
        deathSettled = false
        biome = 0
        enemyCredit = 0f
        rowsSinceSolid = 0
        lastWasHazard = false
        accumulator = 0f
        dragTargetX = buddy.x
        wasDragging = false
        coinMultiplier = 1
        safetyNets = 0
        magnetForever = false
        lowGravityTime = 0f
        coinSpacingScale = 1f
        coinsPlaced = 0
        nextCoinY = Tuning.GROUND_Y + rand(Tuning.COIN_SPACING_MIN, Tuning.COIN_SPACING_MAX) * 0.45f

        // The yard floor. It spans the whole playfield, so the opening fall is always safe no
        // matter how badly the first bounce goes; it is culled once the camera leaves.
        val ground = platforms.obtain()
        ground.kind = PlatKind.SOLID
        ground.isGround = true
        ground.w = worldW + 400f
        ground.x = worldW * 0.5f
        ground.y = Tuning.GROUND_Y
        ground.baseY = ground.y
        ground.seed = rng.nextInt(1024)

        // A launch pad just above it, so the run opens with a proper bounce.
        val pad = platforms.obtain()
        pad.kind = PlatKind.SOLID
        pad.w = 330f * metrics.platScale
        pad.x = worldW * 0.5f
        pad.y = Tuning.START_Y
        pad.baseY = pad.y
        pad.seed = rng.nextInt(1024)
        lastPlatX = pad.x

        genY = Tuning.START_Y
        buddy.reset(worldW * 0.5f, Tuning.START_Y)
        generateAhead()
    }

    fun resize(newWorldW: Float) {
        if (newWorldW <= 0f || abs(newWorldW - worldW) < 0.5f) return
        val ratio = newWorldW / worldW
        val oldScale = metrics.platScale
        worldW = newWorldW
        metrics = Tuning.Metrics(newWorldW)
        val widthFix = metrics.platScale / oldScale
        for (p in platforms.items) {
            if (p.isGround) {
                p.w = worldW + 400f
                p.x = worldW * 0.5f
                continue
            }
            p.x *= ratio
            p.w *= widthFix
            p.x = clamp(p.x, Tuning.PLAT_EDGE_MARGIN + p.w * 0.5f, worldW - Tuning.PLAT_EDGE_MARGIN - p.w * 0.5f)
        }
        for (c in pickups.items) c.x *= ratio
        for (e in enemies.items) { e.x *= ratio; e.baseX *= ratio }
        buddy.x *= ratio
        lastPlatX *= ratio
    }

    // -------------------------------------------------------------------------------------
    // consumable power-ups, applied once just before the run starts
    // -------------------------------------------------------------------------------------

    fun startWithMoonJump(multiplier: Float) {
        buddy.vy = Tuning.JUMP_V * multiplier
        buddy.onBounce(multiplier)
    }

    fun startWithFlight(kind: Int, time: Float) = startFlight(kind, time)

    fun startWithShield(seconds: Float) { buddy.shieldTime = seconds }

    fun startWithMagnet() { magnetForever = true }

    fun startWithCoinMultiplier(multiplier: Int) { coinMultiplier = multiplier.coerceAtLeast(1) }

    /**
     * The lap purse: coins paid for going round the band cycle again, not picked up off anything.
     *
     * Into [runCoins] deliberately, not into the save: coins earned during a run are banked once
     * at the end of it, in one committed write, and a purse that went straight to disk mid-run
     * would break that (and could be farmed by killing the app at the right moment). This also
     * makes it show on the HUD counter the instant it is paid, because the counter reads
     * [runCoins] plus the height bonus.
     *
     * [lapCoins] is the same money counted on its own, so what the purse paid can be told apart
     * from what was picked up - which is the only way to check it.
     */
    fun grantLapCoins(n: Int) {
        if (n <= 0) return
        runCoins += n
        lapCoins += n
    }

    /** How much of [runCoins] came from the lap purse this run. */
    var lapCoins = 0
        private set

    fun startWithLuckyCoins(spacingScale: Float) {
        coinSpacingScale = spacingScale.coerceIn(0.2f, 1f)
        nextCoinY = buddy.y + rand(Tuning.COIN_SPACING_MIN, Tuning.COIN_SPACING_MAX) * coinSpacingScale * 0.4f
    }

    fun startWithLowGravity(seconds: Float) { lowGravityTime = seconds }

    fun startWithSafetyNets(count: Int) { safetyNets = count }

    /** Begins the run [screensUp] screens above the yard, with the height already scored. */
    fun startWithHeadStart(screensUp: Float) {
        val targetY = startY + screensUp * Tuning.VIEW_H
        var guard = 0
        while (genY < targetY + Tuning.VIEW_H && guard < 4000) {
            generateRow()
            guard++
        }
        var best: Platform? = null
        for (p in platforms.items) {
            if (!p.alive || p.isGround || p.kind != PlatKind.SOLID || p.boost != Boost.NONE) continue
            if (p.y < targetY) continue
            if (best == null || p.y < best.y) best = p
        }
        val landing = best ?: return
        buddy.x = landing.x
        buddy.y = landing.y
        buddy.vy = Tuning.JUMP_V
        maxY = buddy.y
        camY = buddy.y - (1f - Tuning.CAM_ANCHOR) * Tuning.VIEW_H
        nextCoinY = buddy.y + rand(Tuning.COIN_SPACING_MIN, Tuning.COIN_SPACING_MAX) * coinSpacingScale * 0.5f
        cull()
        generateAhead()
    }

    // -------------------------------------------------------------------------------------
    // update
    // -------------------------------------------------------------------------------------

    /**
     * @param steer   rate steering in [-1, 1] from tilt / pad / keys.
     * @param dragDx  world units of finger travel since the last frame, already gained up.
     * @param dragging true while a finger is on the glass; drag overrides the rate routes.
     */
    fun update(frameDt: Float, steer: Float, digitalInput: Boolean, dragDx: Float, dragging: Boolean) {
        val dt = min(frameDt, 0.05f)

        // --- carry the drag target -----------------------------------------------------------
        // The finger moves a target position, not a speed. Buddy chases it, so the distance you
        // swipe is the distance he covers and he holds station the moment you stop.
        if (dragging) {
            if (!wasDragging) dragTargetX = buddy.x
            dragTargetX = wrapX(dragTargetX + dragDx, wrapW)
            // Never let the target run away from him: a lead he cannot close in a beat would
            // mean releasing the finger leaves him coasting, which is exactly what we removed.
            val maxLead = metrics.maxVx * Tuning.DRAG_LEAD_SECONDS
            val lead = MathX.wrapDelta(dragTargetX, buddy.x, wrapW)
            if (lead > maxLead) {
                dragTargetX = wrapX(buddy.x + maxLead, wrapW)
            } else if (lead < -maxLead) {
                dragTargetX = wrapX(buddy.x - maxLead, wrapW)
            }
        }
        wasDragging = dragging

        updatePlatforms(dt)
        updateEnemies(dt)

        // Fixed-step integration so nothing tunnels through a platform at rocket speed.
        accumulator += dt
        var steps = 0
        while (accumulator >= Tuning.PHYSICS_STEP && steps < Tuning.MAX_SUBSTEPS) {
            stepPhysics(Tuning.PHYSICS_STEP, steer, digitalInput, dragging)
            accumulator -= Tuning.PHYSICS_STEP
            steps++
        }
        if (steps == Tuning.MAX_SUBSTEPS) accumulator = 0f

        updatePickups(dt)
        updateCamera(dt)
        generateAhead()
        cull()
        buddy.updateAnim(dt, metrics.maxVx)
        if (lowGravityTime > 0f) lowGravityTime -= dt

        // Enemies still above the view get one more look, because rows keep being laid under
        // them and the one that turns out to matter can arrive long after they did. Only above
        // the view: an enemy the player can see must never jump sideways, and an enemy below
        // them is not in the way of anything.
        routeSweep -= dt
        if (routeSweep <= 0f) {
            routeSweep = 0.25f
            unblockEnemies(camY + Tuning.VIEW_H * 1.35f, Tuning.VIEW_H * 0.3f, aboveOnly = true)
        }

        // Height coins accrue continuously with the score; tell the HUD about each step up so
        // the player can see WHEN they earned them rather than only at the end of the run.
        val bonus = heightBonusCoins
        if (bonus > reportedBonusCoins) {
            events.onHeightCoins(bonus - reportedBonusCoins, bonus)
            reportedBonusCoins = bonus
        }

        val b = Tuning.biomeIndex(screens).coerceAtMost(Tuning.BIOME_COUNT * 6)
        if (b != biome) {
            biome = b
            events.onBiomeChange(b)
        }

        if (buddy.dying) {
            if (buddy.deathT > 0.95f || buddy.y + Tuning.BUDDY_H < camY - Tuning.VIEW_H * 0.35f) {
                deathSettled = true
            }
        }

        platforms.sweep(); pickups.sweep(); enemies.sweep()
    }

    private fun stepPhysics(dt: Float, steer: Float, digital: Boolean, dragging: Boolean) {
        val b = buddy

        // --- horizontal ---
        val control = when {
            b.dying -> 0f
            b.flight == Flight.ROCKET -> 0.55f   // a rocket is hard to steer
            b.flight != Flight.NONE -> 0.85f
            else -> 1f
        }
        val maxVx = metrics.maxVx * control
        val targetVx: Float
        if (dragging && !b.dying) {
            // Positional: aim at the speed that closes the remaining gap to the finger's target
            // this beat, capped a little above cruising speed so long swipes still feel snappy.
            val err = MathX.wrapDelta(dragTargetX, b.x, wrapW)
            val cap = maxVx * Tuning.DRAG_OVERSPEED
            targetVx = MathX.clamp(err * Tuning.DRAG_STIFFNESS, -cap, cap)
        } else {
            targetVx = steer * maxVx
        }
        // Braking (heading back toward zero, or reversing) is far snappier than accelerating,
        // which is what makes a mid-fall correction actually land where you aimed it.
        val braking = abs(targetVx) < abs(b.vx) || targetVx * b.vx < 0f
        val rate = when {
            dragging -> if (braking) Tuning.STEER_BRAKE_DRAG else Tuning.STEER_ACCEL_DRAG
            digital -> if (braking) Tuning.STEER_BRAKE_DIGITAL else Tuning.STEER_ACCEL_DIGITAL
            else -> if (braking) Tuning.STEER_BRAKE_TILT else Tuning.STEER_ACCEL_TILT
        }
        b.vx = MathX.approach(b.vx, targetVx, rate, dt)
        b.x = wrapX(b.x + b.vx * dt, wrapW)

        // --- vertical ---
        val prevFoot = b.y
        buddyPrevFoot = prevFoot
        val gravity = if (lowGravityTime > 0f) Tuning.GRAVITY * 0.62f else Tuning.GRAVITY
        if (b.flying && !b.dying) {
            val v = when (b.flight) {
                Flight.PROPELLER -> Tuning.PROPELLER_V
                Flight.JETPACK -> Tuning.JETPACK_V
                else -> Tuning.ROCKET_V
            }
            b.vy = MathX.approach(b.vy, v, 9f, dt)
            b.y += b.vy * dt
            b.flightTime -= dt
            if (b.flightTime <= 0f) {
                val ended = b.flight
                b.flight = Flight.NONE
                b.vy = Tuning.FLIGHT_EXIT_V
                events.onFlightEnd(ended)
            }
        } else {
            b.vy -= gravity * dt
            if (b.vy < -Tuning.TERMINAL_V) b.vy = -Tuning.TERMINAL_V
            b.y += b.vy * dt
            if (!b.dying && b.vy < 0f) collidePlatforms(prevFoot)
        }

        if (b.shieldTime > 0f) b.shieldTime -= dt
        if (b.magnetTime > 0f) b.magnetTime -= dt
        if (b.invulnT > 0f) b.invulnT -= dt

        if (b.y > maxY) maxY = b.y

        if (!b.dying && b.y + Tuning.BUDDY_H < camY) {
            if (safetyNets > 0) {
                safetyNets--
                rescue()
            } else {
                die(DeathCause.FELL)
            }
        }
    }

    /** Safety Net: catch the fall on a fresh ledge just inside the bottom of the view. */
    private fun rescue() {
        val p = standOnFreshLedge(2f, camY + Tuning.VIEW_H * 0.14f)
        events.onRescue(p.x, p.y)
    }

    /**
     * Puts a solid ledge under him, just inside the bottom of the view, and stands him on it
     * with a bounce.
     *
     * Both ways of coming back from a fall go through here, and the LEDGE is the point: putting
     * him back in mid-air only works if something happens to be underneath, and after a fall the
     * bottom of the view is exactly where nothing is - the platforms down there are the ones he
     * just missed. Measured over 400 falls with no steering at all, the mid-air placement lost
     * 23.8% of revived runs inside four seconds; standing him on something loses 10%, and that
     * remainder is a dog nobody is steering. See tools/revive/ReviveCheck.kt.
     */
    private fun standOnFreshLedge(invuln: Float, atY: Float): Platform {
        val b = buddy
        val p = platforms.obtain()
        p.kind = PlatKind.SOLID
        p.w = 300f * metrics.platScale
        p.x = clamp(b.x, Tuning.PLAT_EDGE_MARGIN + p.w * 0.5f, worldW - Tuning.PLAT_EDGE_MARGIN - p.w * 0.5f)
        p.y = atY
        p.baseY = p.y
        p.prevY = p.y
        p.seed = rng.nextInt(1024)
        p.rescue = true
        b.x = p.x
        b.y = p.y
        b.vy = Tuning.JUMP_V * 1.35f
        b.invulnT = invuln
        b.onBounce(1.35f)
        return p
    }

    private fun collidePlatforms(prevFoot: Float) {
        val b = buddy
        var best: Platform? = null
        var bestY = -Float.MAX_VALUE
        for (p in platforms.items) {
            if (!p.alive || p.state != 0) continue
            // Buddy must have been ABOVE this platform's surface where that surface was at the
            // start of the frame, and be at or below where it is now. Testing his old position
            // against the platform's new one let a rising HOVER platform sweep up through him
            // and count as a landing - which is exactly the "I lived without hitting anything"
            // bounce at the bottom of the screen.
            if (prevFoot < p.prevY - 2f || b.y > p.y) continue
            val dx = wrapDelta(b.x, p.x, wrapW)
            val span = p.w * 0.5f + Tuning.BUDDY_FOOT_HALF
            if (abs(dx) > span) {
                // Clipping the very edge of a ledge you were clearly steering toward catches
                // instead of dropping you. Only while still moving inward, so it never feels
                // like being yanked onto something you were leaving.
                val movingIn = (dx > 0f && b.vx < -40f) || (dx < 0f && b.vx > 40f)
                if (!movingIn || abs(dx) > span + Tuning.LAND_GRAB) continue
            }
            if (p.y > bestY) { bestY = p.y; best = p }
        }
        val p = best ?: return
        land(p)
    }

    private fun land(p: Platform) {
        val b = buddy
        p.hitAnim = 1f

        if (p.kind == PlatKind.FRAGILE) {
            // No bounce at all: the plank gives way and Buddy drops straight through.
            p.state = 1
            p.timer = Tuning.FRAGILE_TIME
            p.fallVy = -140f
            p.tilt = if (wrapDelta(b.x, p.x, wrapW) > 0f) 0.5f else -0.5f
            events.onPlatformBreak(p)
            return
        }

        b.y = p.y
        val mult = when (p.boost) {
            Boost.SPRING -> Tuning.SPRING_MULT
            Boost.TRAMPOLINE -> Tuning.TRAMPOLINE_MULT
            else -> 1f
        }
        b.vy = Tuning.JUMP_V * mult
        if (p.boost != Boost.NONE) p.boostAnim = 1f
        b.onBounce(mult)
        events.onBounce(p, mult)

        if (p.kind == PlatKind.CRUMBLE) {
            p.state = 1
            p.timer = Tuning.CRUMBLE_TIME
            p.fallVy = -60f
        }
    }

    private fun updatePlatforms(dt: Float) {
        for (p in platforms.items) {
            if (!p.alive) continue
            // Where the surface was before this frame moved it. See Platform.prevY.
            p.prevY = p.y
            p.hitAnim = MathX.approach(p.hitAnim, 0f, 6f, dt)
            p.boostAnim = MathX.approach(p.boostAnim, 0f, 5f, dt)

            when (p.kind) {
                PlatKind.SLIDER -> {
                    if (p.state == 0) {
                        p.x += p.vx * dt
                        val lo = Tuning.PLAT_EDGE_MARGIN + p.w * 0.5f
                        val hi = worldW - Tuning.PLAT_EDGE_MARGIN - p.w * 0.5f
                        if (p.x < lo) { p.x = lo; p.vx = abs(p.vx) }
                        if (p.x > hi) { p.x = hi; p.vx = -abs(p.vx) }
                    }
                }
                PlatKind.HOVER -> {
                    if (p.state == 0) {
                        p.phase += dt * Tuning.HOVER_HZ * 6.2831855f
                        p.y = p.baseY + sin(p.phase) * Tuning.HOVER_AMPLITUDE
                    }
                }
            }

            if (p.state == 1) {
                p.timer -= dt
                p.fallVy -= 1500f * dt
                p.y += p.fallVy * dt
                p.alpha = clamp(p.timer / Tuning.CRUMBLE_TIME, 0f, 1f)
                if (p.timer <= 0f) p.alive = false
            }
        }
    }

    private fun updatePickups(dt: Float) {
        val b = buddy
        val magnet = b.magnetTime > 0f || magnetForever
        for (c in pickups.items) {
            if (!c.alive) continue
            c.t += dt

            val carrier = c.carrier
            if (carrier != null) {
                if (!carrier.alive || carrier.state != 0) { c.alive = false; continue }
                c.x = carrier.x + c.carrierOffsetX
                c.y = carrier.y + c.carrierOffsetY
            } else if (c.magnetised) {
                c.x = wrapX(c.x + c.vx * dt, wrapW)
                c.y += c.vy * dt
            }

            if (PickupKind.isCurrency(c.kind) && carrier == null && (magnet || c.magnetised)) {
                val dx = wrapDelta(b.x, c.x, wrapW)
                val dy = (b.y + Tuning.BUDDY_H * 0.5f) - c.y
                val d2 = dx * dx + dy * dy
                // Once a coin has committed to him it keeps seeking even if he outruns the
                // magnet's radius - otherwise a coin that was already on its way gets abandoned
                // half-travelled, which looks like the magnet giving up.
                if (c.magnetised || d2 < Tuning.MAGNET_RANGE * Tuning.MAGNET_RANGE) {
                    val d = MathX.sqrtf(d2).coerceAtLeast(1f)
                    c.magnetised = true
                    // Re-AIM the velocity at where he is now, rather than adding to whatever it
                    // already was. The old version accumulated impulses, so a coin built up
                    // sideways momentum and sailed past a moving Buddy in a long arc - which is
                    // exactly the "they avoid him if you move" behaviour. A seek that rewrites
                    // the velocity every frame converges on him no matter how he moves.
                    val closeness = 1f - clamp(d / Tuning.MAGNET_RANGE, 0f, 1f)
                    val speed = metrics.maxVx *
                        (Tuning.MAGNET_SPEED_FAR + closeness * Tuning.MAGNET_SPEED_NEAR_GAIN)
                    c.vx = MathX.approach(c.vx, dx / d * speed, Tuning.MAGNET_TURN, dt)
                    c.vy = MathX.approach(c.vy, dy / d * speed, Tuning.MAGNET_TURN, dt)
                }
            }

            if (b.dying) continue

            val dx = wrapDelta(b.x, c.x, wrapW)
            val dy = (b.y + Tuning.BUDDY_H * 0.5f) - c.y
            val reach = c.radius + Tuning.BUDDY_HURT_HALF_W
            if (dx * dx + dy * dy <= reach * reach) {
                collect(c)
            }
        }
    }

    /**
     * True when this pick-up would do nothing because the very same power-up is already running.
     *
     * Deliberately SAME-TYPE only: grabbing a magnet while a magnet is up is a wasted pick-up and
     * gets paid out, but grabbing a magnet while a shield is up is just two power-ups, and is
     * left alone.
     */
    private fun isRedundant(kind: Int): Boolean {
        val b = buddy
        return when (kind) {
            PickupKind.SHIELD -> b.shieldTime > 0f
            PickupKind.MAGNET -> b.magnetTime > 0f || magnetForever
            PickupKind.PROPELLER -> b.flight == Flight.PROPELLER
            PickupKind.JETPACK -> b.flight == Flight.JETPACK
            PickupKind.ROCKET -> b.flight == Flight.ROCKET
            else -> false
        }
    }

    private fun collect(c: Pickup) {
        val b = buddy
        c.alive = false

        if (isRedundant(c.kind)) {
            // Can't stack it, so it pays instead of evaporating.
            val paid = Tuning.REDUNDANT_PICKUP_COINS * coinMultiplier
            runCoins += paid
            events.onRedundantPickup(c, paid)
            return
        }

        when (c.kind) {
            PickupKind.HALO -> runHalos += Tuning.HALO_VALUE
            PickupKind.COIN -> runCoins += Tuning.COIN_VALUE * coinMultiplier
            PickupKind.BONE -> runCoins += Tuning.BONE_COIN_VALUE * coinMultiplier
            PickupKind.SILVER -> {
                runCoins += Tuning.SILVER_COIN_VALUE * coinMultiplier
                events.onSilverCoin(c.x, c.y)
            }
            PickupKind.SHIELD -> { b.shieldTime = Tuning.SHIELD_TIME }
            PickupKind.MAGNET -> { b.magnetTime = Tuning.MAGNET_TIME }
            PickupKind.PROPELLER -> startFlight(Flight.PROPELLER, Tuning.PROPELLER_TIME)
            PickupKind.JETPACK -> startFlight(Flight.JETPACK, Tuning.JETPACK_TIME)
            PickupKind.ROCKET -> startFlight(Flight.ROCKET, Tuning.ROCKET_TIME)
        }
        events.onPickup(c)
    }

    private fun startFlight(kind: Int, time: Float) {
        buddy.flight = kind
        buddy.flightTime = time
        buddy.vy = buddy.vy.coerceAtLeast(Tuning.JUMP_V * 0.6f)
        events.onFlightStart(kind)
    }

    private fun updateEnemies(dt: Float) {
        val b = buddy
        for (e in enemies.items) {
            if (!e.alive) continue
            e.t += dt
            if (e.dying) {
                e.dieT += dt
                e.y -= 900f * e.dieT * dt * 6f
                if (e.dieT > 0.55f) e.alive = false
                continue
            }
            // Where it was before it moved this frame. A bee bobs and a crow flies, so testing
            // "did his feet start above its crown" against the crown it has AFTER moving let a
            // rising enemy climb into him and turn his own clean drop into a side-on hit.
            val eWasY = e.y
            when (e.kind) {
                EnemyKind.BEE -> {
                    e.x = e.baseX + sin(e.t * 1.7f + e.phase) * e.amp
                    e.y = e.baseY + cos(e.t * 2.3f + e.phase) * 34f
                    e.facing = if (cos(e.t * 1.7f + e.phase) > 0f) 1f else -1f
                }
                EnemyKind.CROW -> {
                    e.x = wrapX(e.x + e.vx * dt, wrapW)
                    e.y = e.baseY + sin(e.t * 3.1f + e.phase) * 24f
                    e.facing = if (e.vx > 0f) 1f else -1f
                }
                EnemyKind.STORM -> {
                    e.y = e.baseY + sin(e.t * 1.1f + e.phase) * 18f
                }
                EnemyKind.RIFT -> {
                    e.y = e.baseY
                }
            }

            if (b.dying) continue

            // Kept up to date before anything here can end the frame, and whatever he is doing
            // sideways - he may well drift over an enemy only after dropping past its head.
            val wasAbove = e.aboveT > 0f
            e.aboveT = if (b.y >= minOf(e.y, eWasY) + e.halfH - 2f) Tuning.STOMP_GRACE
            else (e.aboveT - dt).coerceAtLeast(0f)

            val dx = wrapDelta(b.x, e.x, wrapW)
            if (abs(dx) >= e.halfW + Tuning.BUDDY_HURT_HALF_W) continue

            // The crown it had BEFORE it moved this frame as well as after, whichever is lower.
            // A bee bobs and a crow flies; testing only the crown it ends the frame with let one
            // rise into him and turn his own clean drop into a side-on hit.
            val crown = minOf(e.y, eWasY) + e.halfH

            // A STOMP IS HIS FEET SWEEPING DOWN THROUGH THAT CROWN. Not two boxes meeting.
            //
            // This is what made stomping a coin flip. His hurt box is 104 tall and starts 28
            // above his feet, so against an enemy 88 tall the boxes do not overlap until his
            // CENTRE is within 96 of the enemy's - by which point his feet are already below
            // its middle and well past the crown. Every test that then asked "did he come from
            // above?" was being asked a frame or two too late and answered no, so a clean drop
            // was scored as a side-on hit and killed him. Falling fast he clears the whole body
            // between one frame and the next and the boxes may never overlap at all.
            //
            // Asking about the feet, and asking on the frame they cross, is the same question
            // at the moment it can still be answered.
            // ...and his feet have to GET THERE. Coming from above was the whole test, which
            // meant falling anywhere above an enemy - a whole screen above it - killed it,
            // because "above" is refreshed every frame he is above and nothing then asked how
            // far. Landing on thin air and watching something die under you is as bad as the
            // unfairness this came from.
            //
            // Swept, not sampled: falling fast he clears a whole creature between one frame and
            // the next, so the question is whether the path his feet took this frame passed
            // through the band the creature occupies, not where they happened to end up.
            val belly = minOf(e.y, eWasY) - e.halfH
            val footHi = maxOf(buddyPrevFoot, b.y)
            val footLo = minOf(buddyPrevFoot, b.y)
            val reached = footHi >= belly && footLo <= crown + 2f
            // Rising into one, or drifting into one level with you, still kills you - which is
            // where an enemy's threat belongs.
            if (b.vy < 0f && reached &&
                (wasAbove || buddyPrevFoot >= crown - 2f)
            ) {
                killEnemy(e, scoreFor(e.kind))
                b.vy = Tuning.ENEMY_STOMP_V
                b.onBounce(0.8f)
                events.onStomp(e)
                continue
            }

            // Anything else is a hit, and only where the bodies really do overlap.
            val dy = (b.y + Tuning.BUDDY_H * 0.5f) - e.y
            if (abs(dy) >= e.halfH + Tuning.BUDDY_HURT_HALF_H) continue

            if (b.flying) {
                killEnemy(e, scoreFor(e.kind))
                continue
            }

            if (b.invulnT > 0f) continue

            if (b.shieldTime > 0f) {
                b.shieldTime = 0f
                b.invulnT = 1.2f
                b.hurtFlash = 1f
                killEnemy(e, 0)
                events.onShieldBreak(b.x, b.y + Tuning.BUDDY_H * 0.5f)
                continue
            }

            die(DeathCause.ENEMY)
        }
    }

    private fun scoreFor(kind: Int): Int = when (kind) {
        EnemyKind.BEE -> Tuning.SCORE_BEE
        EnemyKind.CROW -> Tuning.SCORE_CROW
        // the two that only turn up high are worth more, and they are harder to land on
        else -> Tuning.SCORE_CROW * 2
    }

    private fun killEnemy(e: Enemy, points: Int) {
        e.dying = true
        e.dieT = 0f
        bonusScore += points
    }

    private fun die(cause: Int) {
        if (buddy.dying) return
        buddy.dying = true
        buddy.alive = false
        buddy.deathT = 0f
        buddy.vy = if (cause == DeathCause.ENEMY) 700f else buddy.vy
        finished = true
        events.onDeath(cause)
    }

    /**
     * Second Life: put him back on his paws where he fell and let the run continue.
     *
     * The camera, the height already climbed and every platform stay exactly as they were -
     * this is a continue, not a restart. He gets a bounce's worth of upward speed and a moment
     * of invulnerability so he is not killed again by whatever he landed in.
     */
    fun revive() {
        val b = buddy
        b.dying = false
        b.alive = true
        b.deathT = 0f
        b.vx = 0f
        // Back on a ledge of his own, not hanging in the air where he died - see
        // [standOnFreshLedge]. A Second Life that drops him into the same gap he just fell
        // through is not a second life. Where he was, if he was still in the view; lifted back
        // into it if he had already fallen out of the bottom.
        standOnFreshLedge(
            2.5f,
            b.y.coerceIn(camY + Tuning.VIEW_H * 0.14f, camY + Tuning.VIEW_H * 0.82f)
        )
        finished = false
        deathSettled = false
    }

    private fun updateCamera(dt: Float) {
        val target = buddy.y - (1f - Tuning.CAM_ANCHOR) * Tuning.VIEW_H
        if (target > camY) {
            val gap = target - camY
            val rate = if (gap > Tuning.VIEW_H * 0.35f) 40f else Tuning.CAM_EASE
            camY = MathX.approach(camY, target, rate, dt)
            if (camY > target) camY = target
        }
    }

    // -------------------------------------------------------------------------------------
    // generation
    // -------------------------------------------------------------------------------------

    private fun generateAhead() {
        val top = camY + Tuning.VIEW_H * 1.9f
        var guard = 0
        while (genY < top && guard < 200) {
            generateRow()
            guard++
        }
    }

    private fun generateRow() {
        val s = screensAt(genY)
        val gap = rand(Tuning.gapMin(s), Tuning.gapMax(s))
        genY += gap

        val width = metrics.platWidthAt(s)
        val p = spawnPlatform(genY, width, s)

        // Wide playfields get a few extra platforms so a row isn't one lonely ledge, but the
        // whole point of this pass is that rows stay sparse.
        val extras = (metrics.rowMultiplier - 1f).coerceIn(0f, 1.6f)
        var extraCount = extras.toInt()
        if (rand(0f, 1f) < extras - extraCount) extraCount++
        if (rand(0f, 1f) < Tuning.doubleRowChance(s)) extraCount++
        for (i in 0 until extraCount) {
            val w2 = metrics.platWidthAt(s)
            val cand = pickX(w2, avoid = p.x, avoidSpan = width * 0.5f + w2 * 0.5f + 260f)
            val sib = spawnPlatformAt(cand, genY + rand(-24f, 24f), w2, s, forceSafe = true)
            maybeDecorate(sib, s)
        }

        // A crumbling platform is a ROUTE THAT EXPIRES. It gives one bounce and then it is gone
        // for good, so a row whose only platform is a crumble stops existing the moment it is
        // used - and if the player falls back afterwards, the hole left behind is two gaps tall
        // and nothing can cross it. Fragiles were already handled this way (see below); crumbles
        // were not, because they do at least bounce you once, which misses the point. Pair every
        // crumble with a solid one in the same row so the row survives being used.
        if (p.kind == PlatKind.CRUMBLE && extraCount == 0) {
            val wb = metrics.platWidthAt(s)
            val bx = pickX(wb, avoid = p.x, avoidSpan = width * 0.5f + wb * 0.5f + 200f)
            val backup = spawnPlatformAt(bx, genY + rand(-20f, 20f), wb, s, forceSafe = true)
            maybeDecorate(backup, s)
        }

        // A fragile platform is a trap set beside a real route, never the route itself.
        if (rand(0f, 1f) < Tuning.fragileShare(s)) {
            val wf = metrics.platWidthAt(s)
            val fx = pickX(wf, avoid = p.x, avoidSpan = width * 0.5f + wf * 0.5f + 200f)
            val trap = spawnPlatformAt(fx, genY + rand(-30f, 30f), wf, s, forceSafe = true)
            trap.kind = PlatKind.FRAGILE
            trap.vx = 0f
        }

        maybeDecorate(p, s)
        maybeCoin(p, s)

        enemyCredit += Tuning.enemyDensity(s) * gap / Tuning.VIEW_H
        if (enemyCredit >= 1f) {
            enemyCredit -= 1f
            // Never over the widest gaps. A stomp carries further than a bounce, so on a gap
            // near the limit the enemy quietly becomes the route - and a route that dies when
            // you use it is the same trap as a crumble that is the only platform in its row.
            val stretch = (gap - Tuning.gapMin(s)) /
                (Tuning.gapMax(s) - Tuning.gapMin(s)).coerceAtLeast(1f)
            if (stretch < 0.8f) spawnEnemy(genY - gap * 0.45f, s, p, gap)
            else enemyCredit += 1f
        }

        // Whatever this row just laid down, make sure nothing already in the air has become the
        // only way past it. Every row, not only the ones that spawn something - see below.
        unblockEnemies(genY, gap)
    }

    private fun spawnPlatform(y: Float, width: Float, s: Float): Platform {
        val x = pickX(width, avoid = lastPlatX, avoidSpan = 0f)
        return spawnPlatformAt(x, y, width, s, forceSafe = false)
    }

    private fun spawnPlatformAt(x: Float, y: Float, width: Float, s: Float, forceSafe: Boolean): Platform {
        val p = platforms.obtain()
        p.w = width
        p.x = clamp(x, Tuning.PLAT_EDGE_MARGIN + width * 0.5f, worldW - Tuning.PLAT_EDGE_MARGIN - width * 0.5f)
        p.y = y
        p.baseY = y
        p.seed = rng.nextInt(1024)
        p.biome = Tuning.biomeIndex(s)
        p.kind = if (forceSafe) PlatKind.SOLID else chooseKind(s)

        when (p.kind) {
            PlatKind.SLIDER -> {
                val sp = rand(Tuning.SLIDER_SPEED_MIN, Tuning.SLIDER_SPEED_MAX) *
                    (0.75f + 0.5f * Tuning.difficulty(s)) * metrics.platScale
                p.vx = if (rng.nextBoolean()) sp else -sp
            }
            PlatKind.HOVER -> p.phase = rand(0f, 6.283f)
        }

        if (!forceSafe) {
            lastPlatX = p.x
            val hazard = p.kind == PlatKind.CRUMBLE || p.kind == PlatKind.FRAGILE
            lastWasHazard = hazard
            rowsSinceSolid = if (p.kind == PlatKind.SOLID) 0 else rowsSinceSolid + 1
        }
        return p
    }

    private fun chooseKind(s: Float): Int {
        if (s < 1.2f) return PlatKind.SOLID
        if (rowsSinceSolid >= 3) return PlatKind.SOLID

        val hazard = if (lastWasHazard) 0f else Tuning.hazardShare(s)
        val mover = Tuning.moverShare(s)
        val roll = rand(0f, 1f)

        // Deliberately no FRAGILE here: a platform that gives no bounce may never be the
        // only way out of a row. Fragiles are added beside a safe platform further down.
        if (roll < hazard) return PlatKind.CRUMBLE
        if (roll < hazard + mover) {
            val hoverOk = s > 9f
            return if (hoverOk && rand(0f, 1f) < 0.3f) PlatKind.HOVER else PlatKind.SLIDER
        }
        return PlatKind.SOLID
    }

    /** Springs, trampolines and the in-run power-ups that sit on a platform. */
    private fun maybeDecorate(p: Platform, s: Float) {
        val safeKind = p.kind == PlatKind.SOLID || p.kind == PlatKind.SLIDER
        if (!safeKind || s <= 1f) return

        val r = rand(0f, 1f)
        if (r < Tuning.TRAMPOLINE_CHANCE && s > Tuning.TRAMPOLINE_FROM) {
            p.boost = Boost.TRAMPOLINE; return
        }
        if (r < Tuning.TRAMPOLINE_CHANCE + Tuning.SPRING_CHANCE) { p.boost = Boost.SPRING; return }

        val r2 = rand(0f, 1f)
        val kind = when {
            r2 < Tuning.ROCKET_CHANCE && s > 8f -> PickupKind.ROCKET
            r2 < Tuning.JETPACK_CHANCE && s > 5f -> PickupKind.JETPACK
            r2 < Tuning.PROPELLER_CHANCE && s > 2.5f -> PickupKind.PROPELLER
            r2 < Tuning.SHIELD_CHANCE && s > 6f -> PickupKind.SHIELD
            r2 < Tuning.MAGNET_CHANCE && s > 3f -> PickupKind.MAGNET
            else -> -1
        }
        if (kind >= 0) {
            val c = pickups.obtain()
            c.kind = kind
            c.carrier = p
            c.carrierOffsetX = 0f
            c.carrierOffsetY = 100f
            c.x = p.x
            c.y = p.y + 100f
            c.t = rand(0f, 3f)
        }
    }

    /**
     * Coins are paced by height rather than sprinkled per platform: one every few screens, so
     * spotting one is an event and 100 of them is a real haul.
     */
    private fun maybeCoin(p: Platform, s: Float) {
        if (genY < nextCoinY) return
        val spacing = coinSpacingScale * (if (haloMode) Tuning.HALO_SPACING_SCALE else 1f)
        nextCoinY = genY + rand(Tuning.COIN_SPACING_MIN, Tuning.COIN_SPACING_MAX) * spacing
        coinsPlaced++
        val c = pickups.obtain()
        c.kind = when {
            // Heaven pays in halos, so there is nothing for a silver coin to be worth there.
            haloMode -> PickupKind.HALO
            coinsPlaced == silverOnCoin -> PickupKind.SILVER
            coinsPlaced % Tuning.BONE_EVERY == 0 -> PickupKind.BONE
            else -> PickupKind.COIN
        }
        // Slightly off to one side and up, so it is a small detour rather than a freebie.
        c.x = clamp(
            p.x + rand(-1f, 1f) * p.w * 0.9f,
            Tuning.PLAT_EDGE_MARGIN, worldW - Tuning.PLAT_EDGE_MARGIN
        )
        c.y = p.y + rand(210f, 330f)
        c.t = rand(0f, 3f)
    }

    /**
     * Would an enemy at [x] still leave something dependable to land on at this height?
     *
     * The route up has to exist without taking a hit. An enemy is already kept off the platform
     * its own row just placed, but that platform can be a crumble - and if the solid one beside
     * it is the one the enemy is sitting over, the only way on is through the enemy. Getting hit
     * is a price the player chooses, never the price of carrying on.
     *
     * Dependable means it is still there after you touch it: a crumble or a fragile does not
     * count as a way up, whatever else is going on. If there is nothing dependable in reach at
     * all, the enemy is not what is blocking the way and moving it would not help.
     */
    private fun leavesAWayUp(x: Float, halfW: Float, y: Float, gap: Float): Boolean {
        val window = maxOf(gap * 1.1f, ROUTE_WINDOW)
        var dependable = 0
        var clear = 0
        for (p in platforms.items) {
            if (!p.alive || p.isGround || p.state != 0) continue
            if (p.kind == PlatKind.CRUMBLE || p.kind == PlatKind.FRAGILE) continue
            if (abs(p.y - y) > window) continue
            dependable++
            // Landing room is the platform's own width less what the enemy reaches over, plus
            // half a dog either side - a strip you can only just squeeze onto is not a route.
            val reach = halfW + p.w * 0.5f + Tuning.BUDDY_HURT_HALF_W * 0.5f
            if (abs(wrapDelta(x, p.x, wrapW)) > reach) clear++
        }
        return dependable == 0 || clear > 0
    }

    /**
     * Moves any enemy that has ended up as the only way past the row just generated.
     *
     * Checking at spawn time is not enough on its own. An enemy is placed between two rows, and
     * the row ABOVE it does not exist yet - so the platform that turns out to be the only
     * dependable one at that height can be laid down under it afterwards. Rows are generated
     * one at a time and never removed, so the fix is to re-check the neighbourhood each time a
     * row appears: by then everything that height has to offer is there.
     *
     * An enemy that cannot be put anywhere clear is simply dropped. There is always another
     * row, and a quiet screen is better than a screen you can only leave by taking a hit.
     */
    private fun unblockEnemies(rowY: Float, gap: Float, aboveOnly: Boolean = false) {
        for (e in enemies.items) {
            if (!e.alive || e.dying) continue
            if (aboveOnly && e.baseY < camY + Tuning.VIEW_H) continue
            // Two rows' worth, not one: a row laid a long way above an enemy can still be
            // the platform that height depends on, and an enemy only gets re-checked while a
            // row is landing near it.
            if (!aboveOnly && abs(e.baseY - rowY) > ROUTE_WINDOW + gap) continue
            val deny = EnemyBox.halfW(e.fauna, e.kind) + e.amp
            if (leavesAWayUp(e.baseX, deny, e.baseY, gap)) continue
            var moved = false
            for (attempt in 0 until 8) {
                val nx = rand(
                    Tuning.PLAT_EDGE_MARGIN + 80f,
                    (worldW - Tuning.PLAT_EDGE_MARGIN - 80f).coerceAtLeast(Tuning.PLAT_EDGE_MARGIN + 80f)
                )
                if (!leavesAWayUp(nx, deny, e.baseY, gap)) continue
                e.baseX = nx
                e.x = nx
                moved = true
                break
            }
            if (!moved) e.alive = false
        }
    }

    private fun spawnEnemy(y: Float, s: Float, near: Platform, gap: Float) {
        val kinds = ArrayList<Int>(4)
        if (s > 4f) kinds.add(EnemyKind.BEE)
        if (s > 11f) kinds.add(EnemyKind.CROW)
        if (s > 20f) kinds.add(EnemyKind.STORM)
        if (s > 30f) kinds.add(EnemyKind.RIFT)
        if (kinds.isEmpty()) return

        val kind = kinds[rng.nextInt(kinds.size)]
        // The band it is BORN in, so it keeps its own look and its own box for its whole life -
        // including if the player falls back down past it into the band below.
        val fauna = faunaAt(Tuning.biomeIndex(s))

        // Somewhere that is not on top of the only way up. A handful of tries, and if none of
        // them leaves a route the row simply goes without an enemy - there is always another
        // row, and a run that can only continue by taking a hit is worse than a quiet screen.
        // A bee sweeps, so the strip it denies you is its whole swing, not the spot it starts
        // in. A crow crosses everything but is gone a moment later; a storm and a rift do not
        // move at all. The envelope is what the check has to use.
        val amp = if (kind == EnemyKind.BEE) rand(140f, 330f) * metrics.platScale else 0f
        val vx = if (kind == EnemyKind.CROW) {
            (if (rng.nextBoolean()) 1f else -1f) * rand(220f, 400f) * metrics.platScale
        } else {
            0f
        }

        var ex = 0f
        var placed = false
        val halfW = EnemyBox.halfW(fauna, kind) + amp
        for (attempt in 0 until 6) {
            ex = pickX(160f, avoid = near.x, avoidSpan = near.w * 0.5f + 300f)
            if (leavesAWayUp(ex, halfW, y, gap)) { placed = true; break }
        }
        if (!placed) return

        val e = enemies.obtain()
        e.kind = kind
        e.fauna = fauna
        e.seed = rng.nextInt(1024)
        e.phase = rand(0f, 6.283f)
        e.baseY = y
        e.y = y
        e.baseX = ex
        e.x = ex
        e.amp = amp
        e.vx = vx
    }

    /** Picks a platform centre, nudged away from [avoid] when asked. */
    /**
     * How far up and down an enemy is considered to be standing in the way.
     *
     * A little over one jump: platforms further off than this are a different decision, and
     * being denied one of them is not being denied the route.
     */
    private val ROUTE_WINDOW = 420f

    /** Counts down to the next sweep of the enemies above the view. See [unblockEnemies]. */
    private var routeSweep = 0f

    private fun pickX(width: Float, avoid: Float, avoidSpan: Float): Float {
        val lo = Tuning.PLAT_EDGE_MARGIN + width * 0.5f
        val hi = worldW - Tuning.PLAT_EDGE_MARGIN - width * 0.5f
        if (hi <= lo) return worldW * 0.5f
        var best = rand(lo, hi)
        if (avoidSpan > 0f) {
            var tries = 0
            while (abs(wrapDelta(best, avoid, wrapW)) < avoidSpan && tries < 8) {
                best = rand(lo, hi)
                tries++
            }
        }
        return best
    }

    private fun cull() {
        val floor = camY - 620f
        for (p in platforms.items) if (p.alive && p.y < floor) p.alive = false
        for (c in pickups.items) if (c.alive && c.y < floor) c.alive = false
        for (e in enemies.items) if (e.alive && e.y < floor) e.alive = false
    }

    private fun rand(a: Float, b: Float): Float = a + (b - a) * rng.nextFloat()
}
