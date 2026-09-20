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
        fun onFlightStart(kind: Int) {}
        fun onFlightEnd(kind: Int) {}
        fun onStomp(enemy: Enemy) {}
        fun onShieldBreak(x: Float, y: Float) {}
        fun onDeath(cause: Int) {}
        fun onBiomeChange(biome: Int) {}
    }

    var worldW: Float = worldWidth
        private set
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

    private var genY = 0f
    private var enemyCredit = 0f
    private var rowsSinceSolid = 0
    private var lastWasHazard = false
    private var lastPlatX = 0f
    private var accumulator = 0f

    val screens: Float get() = (maxY - startY) / Tuning.VIEW_H
    val heightWu: Float get() = maxY - startY
    val score: Int get() = (heightWu * Tuning.SCORE_PER_WU).toInt().coerceAtLeast(0) + bonusScore

    /** Screens climbed at an arbitrary world Y - used by generation and the backdrop. */
    fun screensAt(y: Float): Float = (y - startY) / Tuning.VIEW_H

    // -------------------------------------------------------------------------------------
    // lifecycle
    // -------------------------------------------------------------------------------------

    fun reset() {
        platforms.clear(); pickups.clear(); enemies.clear()
        camY = 0f
        startY = 260f
        maxY = startY
        runCoins = 0
        bonusScore = 0
        finished = false
        deathSettled = false
        biome = 0
        enemyCredit = 0f
        rowsSinceSolid = 0
        lastWasHazard = false
        accumulator = 0f

        // A generous launch pad, dead centre.
        val pad = platforms.obtain()
        pad.kind = PlatKind.SOLID
        pad.w = 300f * metrics.platScale
        pad.x = worldW * 0.5f
        pad.y = startY
        pad.baseY = pad.y
        pad.seed = rng.nextInt(1024)
        lastPlatX = pad.x

        genY = startY
        buddy.reset(worldW * 0.5f, startY)
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
    // update
    // -------------------------------------------------------------------------------------

    fun update(frameDt: Float, steer: Float, digitalInput: Boolean) {
        val dt = min(frameDt, 0.05f)

        updatePlatforms(dt)
        updateEnemies(dt)

        // Fixed-step integration so nothing tunnels through a platform at rocket speed.
        accumulator += dt
        var steps = 0
        while (accumulator >= Tuning.PHYSICS_STEP && steps < Tuning.MAX_SUBSTEPS) {
            stepPhysics(Tuning.PHYSICS_STEP, steer, digitalInput)
            accumulator -= Tuning.PHYSICS_STEP
            steps++
        }
        if (steps == Tuning.MAX_SUBSTEPS) accumulator = 0f

        updatePickups(dt)
        updateCamera(dt)
        generateAhead()
        cull()
        buddy.updateAnim(dt, metrics.maxVx)

        val b = Tuning.biomeIndex(screens).coerceAtMost(Tuning.BIOME_COUNT * 4)
        if (b != biome) {
            biome = b
            events.onBiomeChange(b)
        }

        if (buddy.dying) {
            // Let the fall play out, then hand over to the game-over screen.
            if (buddy.deathT > 0.95f || buddy.y + Tuning.BUDDY_H < camY - Tuning.VIEW_H * 0.35f) {
                deathSettled = true
            }
        }

        platforms.sweep(); pickups.sweep(); enemies.sweep()
    }

    private fun stepPhysics(dt: Float, steer: Float, digital: Boolean) {
        val b = buddy

        // --- horizontal ---
        val ease = if (digital) Tuning.STEER_EASE_DIGITAL else Tuning.STEER_EASE_TILT
        val control = when {
            b.dying -> 0f
            b.flight == Flight.ROCKET -> 0.55f   // a rocket is hard to steer
            b.flight != Flight.NONE -> 0.85f
            else -> 1f
        }
        val targetVx = steer * metrics.maxVx * control
        b.vx = MathX.approach(b.vx, targetVx, ease, dt)
        b.x = wrapX(b.x + b.vx * dt, worldW)

        // --- vertical ---
        val prevFoot = b.y
        if (b.flying && !b.dying) {
            val v = when (b.flight) {
                Flight.PROPELLER -> Tuning.PROPELLER_V
                Flight.JETPACK -> Tuning.JETPACK_V
                else -> Tuning.ROCKET_V
            }
            // Ease into the boost so the hand-off from a bounce isn't a jolt.
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
            b.vy -= Tuning.GRAVITY * dt
            if (b.vy < -Tuning.TERMINAL_V) b.vy = -Tuning.TERMINAL_V
            b.y += b.vy * dt
            if (!b.dying && b.vy < 0f) collidePlatforms(prevFoot)
        }

        if (b.shieldTime > 0f) b.shieldTime -= dt
        if (b.magnetTime > 0f) b.magnetTime -= dt
        if (b.invulnT > 0f) b.invulnT -= dt

        if (b.y > maxY) maxY = b.y

        if (!b.dying && b.y + Tuning.BUDDY_H < camY) die(DeathCause.FELL)
    }

    private fun collidePlatforms(prevFoot: Float) {
        val b = buddy
        var best: Platform? = null
        var bestY = -Float.MAX_VALUE
        for (p in platforms.items) {
            if (!p.alive || p.state != 0) continue
            // Must have crossed the surface downward during this step.
            if (prevFoot < p.y - 2f || b.y > p.y) continue
            val dx = wrapDelta(b.x, p.x, worldW)
            if (abs(dx) > p.w * 0.5f + Tuning.BUDDY_FOOT_HALF) continue
            // If several qualify, land on the highest one.
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
            p.tilt = if (wrapDelta(b.x, p.x, worldW) > 0f) 0.5f else -0.5f
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
        val magnet = b.magnetTime > 0f
        for (c in pickups.items) {
            if (!c.alive) continue
            c.t += dt

            val carrier = c.carrier
            if (carrier != null) {
                if (!carrier.alive || carrier.state != 0) { c.alive = false; continue }
                c.x = carrier.x + c.carrierOffsetX
                c.y = carrier.y + c.carrierOffsetY
            } else if (c.magnetised) {
                c.x = wrapX(c.x + c.vx * dt, worldW)
                c.y += c.vy * dt
            }

            if (magnet && PickupKind.isCurrency(c.kind) && carrier == null) {
                val dx = wrapDelta(b.x, c.x, worldW)
                val dy = (b.y + Tuning.BUDDY_H * 0.5f) - c.y
                val d2 = dx * dx + dy * dy
                if (d2 < Tuning.MAGNET_RANGE * Tuning.MAGNET_RANGE) {
                    val d = MathX.sqrtf(d2).coerceAtLeast(1f)
                    val pull = 2600f * dt
                    c.magnetised = true
                    c.vx += dx / d * pull
                    c.vy += dy / d * pull
                    c.vx = clamp(c.vx, -2400f, 2400f)
                    c.vy = clamp(c.vy, -2400f, 2400f)
                }
            }

            if (b.dying) continue

            val dx = wrapDelta(b.x, c.x, worldW)
            val dy = (b.y + Tuning.BUDDY_H * 0.5f) - c.y
            val reach = c.radius + Tuning.BUDDY_HURT_HALF_W
            if (dx * dx + dy * dy <= reach * reach) {
                collect(c)
            }
        }
    }

    private fun collect(c: Pickup) {
        val b = buddy
        c.alive = false
        when (c.kind) {
            PickupKind.COIN -> runCoins += Tuning.COIN_VALUE
            PickupKind.BONE -> runCoins += Tuning.BONE_COIN_VALUE
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
            when (e.kind) {
                EnemyKind.BEE -> {
                    e.x = e.baseX + sin(e.t * 1.7f + e.phase) * e.amp
                    e.y = e.baseY + cos(e.t * 2.3f + e.phase) * 26f
                    e.facing = if (cos(e.t * 1.7f + e.phase) > 0f) 1f else -1f
                }
                EnemyKind.CROW -> {
                    e.x = wrapX(e.x + e.vx * dt, worldW)
                    e.y = e.baseY + sin(e.t * 3.1f + e.phase) * 18f
                    e.facing = if (e.vx > 0f) 1f else -1f
                }
                EnemyKind.STORM -> {
                    e.y = e.baseY + sin(e.t * 1.1f + e.phase) * 14f
                }
                EnemyKind.RIFT -> {
                    e.y = e.baseY
                }
            }

            if (b.dying) continue

            val dx = wrapDelta(b.x, e.x, worldW)
            val dy = (b.y + Tuning.BUDDY_H * 0.5f) - e.y
            val overlapX = abs(dx) < e.halfW + Tuning.BUDDY_HURT_HALF_W
            val overlapY = abs(dy) < e.halfH + Tuning.BUDDY_HURT_HALF_H
            if (!overlapX || !overlapY) continue

            if (b.flying) {
                // Flying through a hazard destroys it, exactly like the reference game.
                if (EnemyKind.stompable(e.kind)) {
                    killEnemy(e, scoreFor(e.kind))
                } else {
                    killEnemy(e, 0)
                }
                continue
            }

            val stomping = EnemyKind.stompable(e.kind) && b.vy < 0f &&
                (b.y > e.y + e.halfH * 0.15f)
            if (stomping) {
                killEnemy(e, scoreFor(e.kind))
                b.vy = Tuning.ENEMY_STOMP_V
                b.onBounce(0.8f)
                events.onStomp(e)
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
        else -> 0
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
        buddy.vy = if (cause == DeathCause.ENEMY) 420f else buddy.vy
        finished = true
        events.onDeath(cause)
    }

    private fun updateCamera(dt: Float) {
        val target = buddy.y - (1f - Tuning.CAM_ANCHOR) * Tuning.VIEW_H
        if (target > camY) {
            // Snap hard when the player is way ahead (rockets), ease otherwise.
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

        // Wide playfields get extra platforms so a row isn't one lonely ledge.
        val extras = (metrics.rowMultiplier - 1f).coerceIn(0f, 2.4f)
        var extraCount = extras.toInt()
        if (rand(0f, 1f) < extras - extraCount) extraCount++
        if (rand(0f, 1f) < Tuning.doubleRowChance(s)) extraCount++
        for (i in 0 until extraCount) {
            val w2 = metrics.platWidthAt(s)
            val cand = pickX(w2, avoid = p.x, avoidSpan = width * 0.5f + w2 * 0.5f + 150f)
            val sib = spawnPlatformAt(cand, genY + rand(-34f, 34f), w2, s, forceSafe = true)
            maybeDecorate(sib, s, siblingsAllowed = false)
        }

        maybeDecorate(p, s, siblingsAllowed = true)

        // Hazards live between rows.
        enemyCredit += Tuning.enemyDensity(s) * gap / Tuning.VIEW_H
        if (enemyCredit >= 1f) {
            enemyCredit -= 1f
            spawnEnemy(genY - gap * 0.45f, s, p)
        }
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
        if (rowsSinceSolid >= 5) return PlatKind.SOLID

        val hazard = if (lastWasHazard) 0f else Tuning.hazardShare(s)
        val mover = Tuning.moverShare(s)
        val roll = rand(0f, 1f)

        if (roll < hazard) {
            val fragileOk = s > 6f
            return if (fragileOk && rand(0f, 1f) < 0.42f) PlatKind.FRAGILE else PlatKind.CRUMBLE
        }
        if (roll < hazard + mover) {
            val hoverOk = s > 9f
            return if (hoverOk && rand(0f, 1f) < 0.3f) PlatKind.HOVER else PlatKind.SLIDER
        }
        return PlatKind.SOLID
    }

    /** Boosts, coins and power-ups that sit on (or just above) a platform. */
    private fun maybeDecorate(p: Platform, s: Float, siblingsAllowed: Boolean) {
        val safeKind = p.kind == PlatKind.SOLID || p.kind == PlatKind.SLIDER
        if (safeKind && s > 1f) {
            val r = rand(0f, 1f)
            if (r < 0.03f && s > 3f) p.boost = Boost.TRAMPOLINE
            else if (r < 0.12f) p.boost = Boost.SPRING
        }

        if (p.boost == Boost.NONE && safeKind) {
            val r = rand(0f, 1f)
            val kind = when {
                r < 0.0018f && s > 8f -> PickupKind.ROCKET
                r < 0.0098f && s > 5f -> PickupKind.JETPACK
                r < 0.0258f && s > 2.5f -> PickupKind.PROPELLER
                r < 0.0368f && s > 6f -> PickupKind.SHIELD
                r < 0.0498f && s > 3f -> PickupKind.MAGNET
                else -> -1
            }
            if (kind >= 0) {
                val c = pickups.obtain()
                c.kind = kind
                c.carrier = p
                c.carrierOffsetX = 0f
                c.carrierOffsetY = 62f
                c.x = p.x
                c.y = p.y + 62f
                c.t = rand(0f, 3f)
                return
            }
        }

        // Coins: rare, and occasionally strung into an arc worth going out of your way for.
        val coinRoll = rand(0f, 1f)
        if (siblingsAllowed && coinRoll < 0.045f) {
            spawnCoinArc(p, s)
        } else if (coinRoll < 0.13f) {
            val c = pickups.obtain()
            c.kind = if (rand(0f, 1f) < 0.09f) PickupKind.BONE else PickupKind.COIN
            c.x = p.x + rand(-p.w * 0.25f, p.w * 0.25f)
            c.y = p.y + rand(110f, 190f)
            c.t = rand(0f, 3f)
        }
    }

    private fun spawnCoinArc(p: Platform, s: Float) {
        val count = 4 + rng.nextInt(3)
        val dir = if (rng.nextBoolean()) 1f else -1f
        val span = rand(240f, 420f) * metrics.platScale
        val rise = rand(150f, 260f)
        for (i in 0 until count) {
            val t = i / (count - 1f)
            val c = pickups.obtain()
            c.kind = PickupKind.COIN
            c.x = wrapX(p.x + dir * span * (t - 0.5f) * 2f, worldW)
            c.y = p.y + 120f + sin(t * 3.14159f) * rise
            c.t = t * 0.6f
        }
    }

    private fun spawnEnemy(y: Float, s: Float, near: Platform) {
        val kinds = ArrayList<Int>(4)
        if (s > 4f) kinds.add(EnemyKind.BEE)
        if (s > 11f) kinds.add(EnemyKind.CROW)
        if (s > 20f) kinds.add(EnemyKind.STORM)
        if (s > 30f) kinds.add(EnemyKind.RIFT)
        if (kinds.isEmpty()) return

        val kind = kinds[rng.nextInt(kinds.size)]
        val e = enemies.obtain()
        e.kind = kind
        e.seed = rng.nextInt(1024)
        e.phase = rand(0f, 6.283f)
        e.baseY = y
        e.y = y
        val x = pickX(160f, avoid = near.x, avoidSpan = near.w * 0.5f + 220f)
        e.baseX = x
        e.x = x
        when (kind) {
            EnemyKind.BEE -> e.amp = rand(90f, 220f) * metrics.platScale
            EnemyKind.CROW -> e.vx = (if (rng.nextBoolean()) 1f else -1f) * rand(150f, 260f) * metrics.platScale
            EnemyKind.STORM -> e.amp = 0f
            EnemyKind.RIFT -> e.amp = 0f
        }
    }

    /** Picks a platform centre, nudged away from [avoid] when asked. */
    private fun pickX(width: Float, avoid: Float, avoidSpan: Float): Float {
        val lo = Tuning.PLAT_EDGE_MARGIN + width * 0.5f
        val hi = worldW - Tuning.PLAT_EDGE_MARGIN - width * 0.5f
        if (hi <= lo) return worldW * 0.5f
        var best = rand(lo, hi)
        if (avoidSpan > 0f) {
            var tries = 0
            while (abs(wrapDelta(best, avoid, worldW)) < avoidSpan && tries < 6) {
                best = rand(lo, hi)
                tries++
            }
        }
        return best
    }

    private fun cull() {
        val floor = camY - 420f
        for (p in platforms.items) if (p.alive && p.y < floor) p.alive = false
        for (c in pickups.items) if (c.alive && c.y < floor) c.alive = false
        for (e in enemies.items) if (e.alive && e.y < floor) e.alive = false
    }

    private fun rand(a: Float, b: Float): Float = a + (b - a) * rng.nextFloat()
}
