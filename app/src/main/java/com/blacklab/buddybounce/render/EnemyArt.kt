package com.blacklab.buddybounce.render

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import com.blacklab.buddybounce.game.EnemyKind
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

/**
 * Every hazard in the game, drawn per world.
 *
 * The simulation only knows four hazard *roles* and never changes:
 *
 * | role     | behaviour                                    |
 * |----------|----------------------------------------------|
 * | [EnemyKind.BEE]   | small drifter, bobs in place, stompable  |
 * | [EnemyKind.CROW]  | patroller, crosses the lane, stompable   |
 * | [EnemyKind.STORM] | static, lethal from every side           |
 * | [EnemyKind.RIFT]  | big static, lethal from every side       |
 *
 * What they look like is a property of the scene, so a run through the Deep Blue meets
 * pufferfish and jellyfish rather than bees and thunderclouds. Twenty designs, five sets of
 * four, dispatched on [Fauna].
 *
 * Everything is drawn around the origin with the hazard's collision box centred on it, and the
 * caller has already applied position, the death spin and the fade.
 */
class EnemyArt(private val art: Art) {

    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val path = Path()
    private val r = RectF()

    fun draw(c: Canvas, kind: Int, fauna: Int, t: Float, phase: Float, facing: Float, a: Float) {
        when (fauna) {
            Fauna.OCEAN -> when (kind) {
                EnemyKind.BEE -> puffer(c, t, phase, facing, a)
                EnemyKind.CROW -> angler(c, t, phase, facing, a)
                EnemyKind.STORM -> jellyfish(c, t, phase, a)
                else -> whirlpool(c, t, a)
            }
            Fauna.NEON -> when (kind) {
                EnemyKind.BEE -> drone(c, t, phase, facing, a)
                EnemyKind.CROW -> glitchBird(c, t, phase, facing, a)
                EnemyKind.STORM -> turret(c, t, phase, a)
                else -> dataVortex(c, t, a)
            }
            Fauna.FROST -> when (kind) {
                EnemyKind.BEE -> frostMoth(c, t, phase, facing, a)
                EnemyKind.CROW -> iceBat(c, t, phase, facing, a)
                EnemyKind.STORM -> blizzard(c, t, phase, a)
                else -> frozenRift(c, t, a)
            }
            Fauna.EMBER -> when (kind) {
                EnemyKind.BEE -> emberMoth(c, t, phase, facing, a)
                EnemyKind.CROW -> flameImp(c, t, phase, facing, a)
                EnemyKind.STORM -> moltenRock(c, t, phase, a)
                else -> obsidianRift(c, t, a)
            }
            Fauna.HEAVEN -> when (kind) {
                EnemyKind.BEE -> cherub(c, t, phase, facing, a)
                EnemyKind.CROW -> seraph(c, t, phase, facing, a)
                EnemyKind.STORM -> thundergate(c, t, phase, a)
                else -> lightWell(c, t, a)
            }
            else -> when (kind) {
                EnemyKind.BEE -> bee(c, t, phase, a)
                EnemyKind.CROW -> crow(c, t, phase, facing, a)
                EnemyKind.STORM -> storm(c, t, a)
                else -> rift(c, t, a)
            }
        }
    }

    // A fresh paint for every shape: these are drawn a handful of times a frame and getting the
    // style wrong once leaves a stroke turned on for the rest of the world.
    private fun fill(color: Int, a: Float) {
        p.reset(); p.isAntiAlias = true
        p.style = Paint.Style.FILL
        p.color = ColorX.withAlpha(color, a)
    }

    private fun stroke(color: Int, a: Float, w: Float) {
        p.reset(); p.isAntiAlias = true
        p.style = Paint.Style.STROKE
        p.strokeCap = Paint.Cap.ROUND
        p.strokeJoin = Paint.Join.ROUND
        p.strokeWidth = w
        p.color = ColorX.withAlpha(color, a)
    }

    /** Two whites and two pupils, the one thing every creature here shares. */
    private fun eyes(c: Canvas, x0: Float, x1: Float, y: Float, rad: Float, a: Float, iris: Int = 0xFF15161C.toInt()) {
        fill(0xFFFFFFFF.toInt(), a)
        c.drawCircle(x0, y, rad, p)
        c.drawCircle(x1, y, rad, p)
        fill(iris, a)
        c.drawCircle(x0 + rad * 0.2f, y + rad * 0.1f, rad * 0.5f, p)
        c.drawCircle(x1 + rad * 0.2f, y + rad * 0.1f, rad * 0.5f, p)
    }

    // =====================================================================================
    // Backyard Skies - bee, crow, thundercloud, void rift
    // =====================================================================================

    private fun bee(c: Canvas, t: Float, phase: Float, a: Float) {
        val flap = sin(t * 40f + phase)
        fill(0xFFE8F4FF.toInt(), a * 0.6f)
        c.save(); c.scale(1f, 0.4f + 0.6f * abs(flap))
        r.set(-46f, -58f, -4f, -20f); c.drawOval(r, p)
        r.set(4f, -58f, 46f, -20f); c.drawOval(r, p)
        c.restore()

        fill(0xFFF2C14E.toInt(), a)
        r.set(-40f, -28f, 40f, 30f)
        c.drawRoundRect(r, 28f, 28f, p)
        fill(0xFF2A2B32.toInt(), a)
        for (i in 0 until 2) {
            r.set(-2f + i * 20f, -27f, 12f + i * 20f, 29f)
            c.drawRect(r, p)
        }
        path.reset()
        path.moveTo(40f, 0f); path.lineTo(58f, 4f); path.lineTo(40f, 10f); path.close()
        c.drawPath(path, p)
        eyes(c, -22f, -2f, -8f, 9f, a)
        stroke(0xFF15161C.toInt(), a, 3.5f)
        c.drawLine(-32f, -20f, -16f, -15f, p)
        c.drawLine(8f, -15f, -8f, -20f, p)
    }

    private fun crow(c: Canvas, t: Float, phase: Float, facing: Float, a: Float) {
        val flap = sin(t * 14f + phase)
        c.scale(if (facing >= 0f) 1f else -1f, 1f)
        fill(0xFF23252D.toInt(), a)
        r.set(-52f, -22f, 44f, 30f)
        c.drawOval(r, p)
        c.drawCircle(34f, -10f, 26f, p)
        fill(0xFF15161B.toInt(), a)
        path.reset()
        path.moveTo(-8f, -6f)
        path.quadTo(-30f, -30f - flap * 34f, -66f, -10f - flap * 16f)
        path.quadTo(-34f, 10f, -8f, 14f)
        path.close()
        c.drawPath(path, p)
        path.reset()
        path.moveTo(-44f, 2f); path.lineTo(-76f, 18f); path.lineTo(-44f, 22f); path.close()
        c.drawPath(path, p)
        fill(0xFFF2A03C.toInt(), a)
        path.reset()
        path.moveTo(52f, -14f); path.lineTo(80f, -6f); path.lineTo(52f, 2f); path.close()
        c.drawPath(path, p)
        fill(0xFFFFFFFF.toInt(), a)
        c.drawCircle(40f, -18f, 8f, p)
        fill(0xFF0D0E12.toInt(), a)
        c.drawCircle(42f, -17f, 4f, p)
    }

    private fun storm(c: Canvas, t: Float, a: Float) {
        art.drawGlow(c, 0f, 0f, 150f, 0xFF6C7BD6.toInt(), 0.3f * a)
        fill(0xFF4A5170.toInt(), a)
        c.drawCircle(-44f, 4f, 36f, p)
        c.drawCircle(0f, -16f, 46f, p)
        c.drawCircle(44f, 4f, 34f, p)
        r.set(-60f, -8f, 60f, 34f)
        c.drawRoundRect(r, 24f, 24f, p)
        fill(0xFF2F3550.toInt(), a)
        r.set(-58f, 14f, 58f, 36f)
        c.drawRoundRect(r, 18f, 18f, p)
        eyes(c, -18f, 18f, 0f, 9f, a)
        if (((t * 3f).toInt() % 3) == 0) {
            fill(0xFFFFE07A.toInt(), a * (0.6f + 0.4f * sin(t * 40f)))
            path.reset()
            path.moveTo(-8f, 32f); path.lineTo(-22f, 62f); path.lineTo(-4f, 58f)
            path.lineTo(-14f, 92f); path.lineTo(20f, 52f); path.lineTo(2f, 54f)
            path.lineTo(14f, 32f); path.close()
            c.drawPath(path, p)
        }
    }

    private fun rift(c: Canvas, t: Float, a: Float) {
        swirl(c, t, a, 0xFF7B4BD8.toInt(), 0xFF07040F.toInt(), 0xFFCBB7FF.toInt())
    }

    /**
     * The shared skeleton of all four big static hazards: nested counter-rotating ovals falling
     * into a black core, with an arc skimming the rim. Only the colours differ.
     */
    private fun swirl(c: Canvas, t: Float, a: Float, outer: Int, inner: Int, rim: Int) {
        art.drawGlow(c, 0f, 0f, 220f, outer, 0.45f * a)
        for (i in 0 until 4) {
            val k = 1f - i * 0.22f
            fill(ColorX.lerp(outer, inner, i / 3f), a)
            c.save()
            c.rotate(t * (28f + i * 22f))
            r.set(-92f * k, -70f * k, 92f * k, 70f * k)
            c.drawOval(r, p)
            c.restore()
        }
        fill(0xFF000000.toInt(), a)
        c.drawCircle(0f, 0f, 30f, p)
        stroke(rim, a * 0.7f, 4f)
        c.save(); c.rotate(-t * 60f)
        r.set(-70f, -52f, 70f, 52f)
        c.drawArc(r, 20f, 200f, false, p)
        c.restore()
    }

    // =====================================================================================
    // Deep Blue - pufferfish, angler, jellyfish, whirlpool
    // =====================================================================================

    private fun puffer(c: Canvas, t: Float, phase: Float, facing: Float, a: Float) {
        val puff = 1f + 0.08f * sin(t * 3.4f + phase)
        c.scale(if (facing >= 0f) 1f else -1f, 1f)
        c.scale(puff, puff)

        // spines all the way round, drawn under the body so only the tips show
        fill(0xFF1D5F78.toInt(), a)
        for (i in 0 until 14) {
            val ang = i * 25.7f
            c.save(); c.rotate(ang)
            path.reset()
            path.moveTo(30f, -7f); path.lineTo(58f, 0f); path.lineTo(30f, 7f); path.close()
            c.drawPath(path, p)
            c.restore()
        }
        // tail
        fill(0xFF2E8BA8.toInt(), a)
        path.reset()
        path.moveTo(-32f, 0f); path.lineTo(-64f, -24f); path.lineTo(-56f, 0f)
        path.lineTo(-64f, 24f); path.close()
        c.drawPath(path, p)

        fill(0xFF4FC3D9.toInt(), a)
        c.drawCircle(0f, 0f, 42f, p)
        fill(0xFF7FE2EE.toInt(), a * 0.85f)   // pale belly
        r.set(-34f, 6f, 34f, 44f)
        c.drawOval(r, p)
        fill(0xFF2E8BA8.toInt(), a * 0.5f)    // speckles
        c.drawCircle(-14f, -18f, 5f, p)
        c.drawCircle(8f, -22f, 4f, p)
        c.drawCircle(20f, -6f, 4.5f, p)

        eyes(c, 12f, 30f, -12f, 10f, a)
        stroke(0xFF14485C.toInt(), a, 4f)      // small cross mouth
        c.drawLine(24f, 14f, 38f, 14f, p)
    }

    private fun angler(c: Canvas, t: Float, phase: Float, facing: Float, a: Float) {
        val bob = sin(t * 5f + phase)
        c.scale(if (facing >= 0f) 1f else -1f, 1f)

        // the lure, glowing on its stalk
        stroke(0xFF1B3A4A.toInt(), a, 6f)
        path.reset()
        path.moveTo(18f, -26f)
        path.quadTo(46f, -70f - bob * 6f, 66f, -50f - bob * 8f)
        c.drawPath(path, p)
        art.drawGlow(c, 66f, -50f - bob * 8f, 58f, 0xFF9BF6D8.toInt(), (0.5f + 0.2f * bob) * a)
        fill(0xFFDFFFF2.toInt(), a)
        c.drawCircle(66f, -50f - bob * 8f, 11f, p)

        // tail and fins
        fill(0xFF1B3A4A.toInt(), a)
        path.reset()
        path.moveTo(-40f, 0f); path.lineTo(-78f, -26f); path.lineTo(-66f, 2f)
        path.lineTo(-78f, 28f); path.close()
        c.drawPath(path, p)
        path.reset()
        path.moveTo(-10f, -22f); path.lineTo(-24f, -50f); path.lineTo(6f, -26f); path.close()
        c.drawPath(path, p)

        fill(0xFF27506A.toInt(), a)
        r.set(-46f, -34f, 46f, 34f)
        c.drawOval(r, p)

        // gaping jaw with a row of needle teeth
        fill(0xFF0B1A25.toInt(), a)
        path.reset()
        path.moveTo(6f, -6f)
        path.quadTo(46f, -16f, 52f, 6f)
        path.quadTo(40f, 30f, 2f, 22f)
        path.close()
        c.drawPath(path, p)
        fill(0xFFF2F7FF.toInt(), a)
        for (i in 0 until 5) {
            val x = 10f + i * 9f
            path.reset()
            path.moveTo(x, -8f); path.lineTo(x + 4f, 4f); path.lineTo(x + 8f, -8f); path.close()
            c.drawPath(path, p)
            path.reset()
            path.moveTo(x, 20f); path.lineTo(x + 4f, 9f); path.lineTo(x + 8f, 20f); path.close()
            c.drawPath(path, p)
        }
        fill(0xFFFFFFFF.toInt(), a)
        c.drawCircle(14f, -18f, 9f, p)
        fill(0xFF0D0E12.toInt(), a)
        c.drawCircle(16f, -17f, 4.5f, p)
    }

    private fun jellyfish(c: Canvas, t: Float, phase: Float, a: Float) {
        val pulse = sin(t * 2.2f + phase)
        art.drawGlow(c, 0f, -6f, 170f, 0xFFB98CFF.toInt(), 0.32f * a)

        // tentacles first, trailing from under the bell
        stroke(0xFFC9A6FF.toInt(), a * 0.85f, 7f)
        for (i in 0 until 6) {
            val x = -44f + i * 17.5f
            val sway = sin(t * 2.6f + phase + i * 0.8f)
            path.reset()
            path.moveTo(x, 18f)
            path.quadTo(x + sway * 20f, 56f, x - sway * 16f, 96f)
            c.drawPath(path, p)
        }
        // the frilled skirt
        fill(0xFF8E62D6.toInt(), a * 0.9f)
        for (i in 0 until 5) {
            r.set(-52f + i * 22f, 8f, -30f + i * 22f, 34f)
            c.drawOval(r, p)
        }
        // bell
        fill(0xFFB98CFF.toInt(), a)
        path.reset()
        path.moveTo(-58f, 16f)
        path.quadTo(-58f, -54f - pulse * 8f, 0f, -54f - pulse * 8f)
        path.quadTo(58f, -54f - pulse * 8f, 58f, 16f)
        path.close()
        c.drawPath(path, p)
        fill(0xFFE6D4FF.toInt(), a * 0.55f)
        r.set(-34f, -42f, -6f, -16f)
        c.drawOval(r, p)
        eyes(c, -16f, 16f, -8f, 9f, a, 0xFF3A1E6E.toInt())
    }

    private fun whirlpool(c: Canvas, t: Float, a: Float) {
        swirl(c, t, a, 0xFF2A9BC4.toInt(), 0xFF041E2E.toInt(), 0xFFCFF3FF.toInt())
        // a few bubbles escaping the throat
        fill(0xFFD8F4FF.toInt(), a * 0.7f)
        for (i in 0 until 4) {
            val ph = t * 1.6f + i * 1.57f
            val rad = 4f + (i % 2) * 3f
            c.drawCircle(cos(ph) * 56f, sin(ph) * 40f, rad, p)
        }
    }

    // =====================================================================================
    // Neon City - drone, glitch bird, spark turret, data vortex
    // =====================================================================================

    private fun drone(c: Canvas, t: Float, phase: Float, facing: Float, a: Float) {
        c.scale(if (facing >= 0f) 1f else -1f, 1f)
        // rotor discs, blurred by drawing them squashed
        fill(0xFF3BF0FF.toInt(), a * 0.35f)
        val spin = 0.35f + 0.65f * abs(sin(t * 30f + phase))
        c.save(); c.scale(1f, spin)
        r.set(-58f, -46f, -12f, -28f); c.drawOval(r, p)
        r.set(12f, -46f, 58f, -28f); c.drawOval(r, p)
        c.restore()
        stroke(0xFF5A6480.toInt(), a, 5f)
        c.drawLine(-36f, -34f, -14f, -14f, p)
        c.drawLine(36f, -34f, 14f, -14f, p)

        fill(0xFF2B3350.toInt(), a)
        r.set(-42f, -20f, 42f, 26f)
        c.drawRoundRect(r, 16f, 16f, p)
        fill(0xFF161C2E.toInt(), a)
        r.set(-34f, 16f, 34f, 30f)
        c.drawRoundRect(r, 8f, 8f, p)
        // scanner eye sweeping side to side
        art.drawGlow(c, sin(t * 2.4f + phase) * 14f, 2f, 70f, 0xFFFF3CAC.toInt(), 0.5f * a)
        fill(0xFFFF3CAC.toInt(), a)
        r.set(-26f, -8f, 26f, 12f)
        c.drawRoundRect(r, 10f, 10f, p)
        fill(0xFFFFE3F4.toInt(), a)
        c.drawCircle(sin(t * 2.4f + phase) * 16f, 2f, 7f, p)
    }

    private fun glitchBird(c: Canvas, t: Float, phase: Float, facing: Float, a: Float) {
        val flap = sin(t * 15f + phase)
        c.scale(if (facing >= 0f) 1f else -1f, 1f)
        // chromatic-aberration ghosts: the same silhouette offset in cyan and magenta
        for (pass in 0 until 3) {
            val off = when (pass) { 0 -> -7f; 1 -> 7f; else -> 0f }
            val col = when (pass) { 0 -> 0xFF00E5FF.toInt(); 1 -> 0xFFFF2D9B.toInt(); else -> 0xFF171A2C.toInt() }
            fill(col, if (pass == 2) a else a * 0.55f)
            c.save(); c.translate(off, off * 0.4f)
            r.set(-48f, -20f, 40f, 28f)
            c.drawOval(r, p)
            c.drawCircle(32f, -12f, 24f, p)
            path.reset()
            path.moveTo(-8f, -6f)
            path.quadTo(-28f, -30f - flap * 30f, -62f, -10f - flap * 14f)
            path.quadTo(-32f, 10f, -8f, 14f)
            path.close()
            c.drawPath(path, p)
            c.restore()
        }
        // scanline break across the body
        fill(0xFF00E5FF.toInt(), a * 0.8f)
        val band = -18f + ((t * 60f + phase * 20f) % 44f)
        r.set(-46f, band, 42f, band + 5f)
        c.drawRect(r, p)
        fill(0xFFFF9C3C.toInt(), a)
        path.reset()
        path.moveTo(50f, -16f); path.lineTo(76f, -8f); path.lineTo(50f, 0f); path.close()
        c.drawPath(path, p)
        fill(0xFF00E5FF.toInt(), a)
        c.drawCircle(38f, -20f, 7f, p)
    }

    private fun turret(c: Canvas, t: Float, phase: Float, a: Float) {
        art.drawGlow(c, 0f, 0f, 150f, 0xFFFFE04A.toInt(), 0.3f * a)
        // housing
        fill(0xFF232A44.toInt(), a)
        r.set(-58f, -18f, 58f, 30f)
        c.drawRoundRect(r, 16f, 16f, p)
        fill(0xFF141930.toInt(), a)
        r.set(-46f, 18f, 46f, 36f)
        c.drawRoundRect(r, 8f, 8f, p)
        // the two coil posts
        fill(0xFF6E7BA8.toInt(), a)
        r.set(-46f, -54f, -26f, -12f); c.drawRoundRect(r, 8f, 8f, p)
        r.set(26f, -54f, 46f, -12f); c.drawRoundRect(r, 8f, 8f, p)
        fill(0xFFFFE04A.toInt(), a)
        c.drawCircle(-36f, -58f, 13f, p)
        c.drawCircle(36f, -58f, 13f, p)
        // arcing bolt between them, stuttering
        if (((t * 6f + phase).toInt() % 2) == 0) {
            stroke(0xFFFFF7C2.toInt(), a * (0.65f + 0.35f * sin(t * 50f)), 5f)
            path.reset()
            path.moveTo(-32f, -58f)
            path.lineTo(-12f, -44f)
            path.lineTo(4f, -70f)
            path.lineTo(20f, -50f)
            path.lineTo(32f, -58f)
            c.drawPath(path, p)
        }
        eyes(c, -18f, 18f, 4f, 9f, a, 0xFF2A1A05.toInt())
    }

    private fun dataVortex(c: Canvas, t: Float, a: Float) {
        swirl(c, t, a, 0xFFFF3CAC.toInt(), 0xFF0B0418.toInt(), 0xFF7CF6FF.toInt())
        // falling glyph columns
        fill(0xFF7CF6FF.toInt(), a * 0.8f)
        for (i in 0 until 5) {
            val x = -60f + i * 30f
            val y = -70f + ((t * 170f + i * 47f) % 150f)
            r.set(x, y, x + 7f, y + 16f)
            c.drawRect(r, p)
        }
    }

    // =====================================================================================
    // Frozen Peaks - frost moth, ice bat, blizzard, frozen rift
    // =====================================================================================

    private fun frostMoth(c: Canvas, t: Float, phase: Float, facing: Float, a: Float) {
        val flap = sin(t * 26f + phase)
        c.scale(if (facing >= 0f) 1f else -1f, 1f)
        art.drawGlow(c, 0f, -10f, 110f, 0xFFBFEFFF.toInt(), 0.26f * a)
        // four crystalline wings
        fill(0xFFDDF6FF.toInt(), a * 0.78f)
        c.save(); c.scale(1f, 0.45f + 0.55f * abs(flap))
        path.reset()
        path.moveTo(-6f, -18f); path.lineTo(-56f, -62f); path.lineTo(-64f, -18f)
        path.lineTo(-30f, 2f); path.close()
        c.drawPath(path, p)
        path.reset()
        path.moveTo(6f, -18f); path.lineTo(56f, -62f); path.lineTo(64f, -18f)
        path.lineTo(30f, 2f); path.close()
        c.drawPath(path, p)
        c.restore()
        stroke(0xFF8FD3F4.toInt(), a * 0.9f, 3f)
        c.drawLine(-10f, -16f, -54f, -50f, p)
        c.drawLine(10f, -16f, 54f, -50f, p)

        fill(0xFF6FA8D6.toInt(), a)
        r.set(-16f, -26f, 16f, 30f)
        c.drawRoundRect(r, 14f, 14f, p)
        fill(0xFFCFEAFF.toInt(), a)   // frosted collar
        r.set(-18f, -20f, 18f, -6f)
        c.drawRoundRect(r, 8f, 8f, p)
        stroke(0xFFCFEAFF.toInt(), a, 3f)   // antennae
        c.drawLine(-8f, -26f, -22f, -48f, p)
        c.drawLine(8f, -26f, 22f, -48f, p)
        eyes(c, -8f, 8f, -12f, 7f, a, 0xFF1B3A55.toInt())
    }

    private fun iceBat(c: Canvas, t: Float, phase: Float, facing: Float, a: Float) {
        val flap = sin(t * 13f + phase)
        c.scale(if (facing >= 0f) 1f else -1f, 1f)
        // scalloped wings
        fill(0xFF4E7FB5.toInt(), a)
        for (s in 0 until 2) {
            val d = if (s == 0) -1f else 1f
            path.reset()
            path.moveTo(0f, -4f)
            path.lineTo(d * 34f, -34f - flap * 26f)
            path.lineTo(d * 56f, -10f - flap * 18f)
            path.lineTo(d * 44f, -14f - flap * 14f)
            path.lineTo(d * 62f, 14f - flap * 8f)
            path.lineTo(d * 44f, 8f)
            path.lineTo(d * 52f, 30f)
            path.lineTo(0f, 18f)
            path.close()
            c.drawPath(path, p)
        }
        fill(0xFF2E547F.toInt(), a)
        r.set(-22f, -22f, 22f, 28f)
        c.drawOval(r, p)
        fill(0xFF3A6699.toInt(), a)
        c.drawCircle(0f, -26f, 24f, p)
        path.reset()   // pointed ears
        path.moveTo(-22f, -38f); path.lineTo(-28f, -68f); path.lineTo(-6f, -46f); path.close()
        c.drawPath(path, p)
        path.reset()
        path.moveTo(22f, -38f); path.lineTo(28f, -68f); path.lineTo(6f, -46f); path.close()
        c.drawPath(path, p)
        eyes(c, -9f, 9f, -28f, 8f, a, 0xFF9BE8FF.toInt())
        fill(0xFFF2FBFF.toInt(), a)   // little fangs
        path.reset()
        path.moveTo(-8f, -12f); path.lineTo(-4f, -2f); path.lineTo(0f, -12f); path.close()
        c.drawPath(path, p)
        path.reset()
        path.moveTo(2f, -12f); path.lineTo(6f, -2f); path.lineTo(10f, -12f); path.close()
        c.drawPath(path, p)
    }

    private fun blizzard(c: Canvas, t: Float, phase: Float, a: Float) {
        art.drawGlow(c, 0f, 0f, 160f, 0xFFBFEFFF.toInt(), 0.3f * a)
        fill(0xFF8FB7D6.toInt(), a)
        c.drawCircle(-44f, 4f, 36f, p)
        c.drawCircle(0f, -16f, 46f, p)
        c.drawCircle(44f, 4f, 34f, p)
        r.set(-60f, -8f, 60f, 34f)
        c.drawRoundRect(r, 24f, 24f, p)
        fill(0xFF5E88AC.toInt(), a)
        r.set(-58f, 14f, 58f, 36f)
        c.drawRoundRect(r, 18f, 18f, p)
        // icicles under the belly
        fill(0xFFE8F8FF.toInt(), a)
        for (i in 0 until 5) {
            val x = -44f + i * 22f
            val len = 20f + ((i * 7 + 3) % 4) * 9f
            path.reset()
            path.moveTo(x - 8f, 32f); path.lineTo(x, 32f + len); path.lineTo(x + 8f, 32f); path.close()
            c.drawPath(path, p)
        }
        // driven snow
        fill(0xFFFFFFFF.toInt(), a * 0.9f)
        for (i in 0 until 7) {
            val ph = t * 2.2f + phase + i * 0.9f
            c.drawCircle(-56f + i * 19f + sin(ph) * 10f, 46f + ((t * 130f + i * 31f) % 70f), 3.6f, p)
        }
        eyes(c, -18f, 18f, 0f, 9f, a, 0xFF20364A.toInt())
    }

    private fun frozenRift(c: Canvas, t: Float, a: Float) {
        swirl(c, t, a, 0xFF7BD8F0.toInt(), 0xFF07223A.toInt(), 0xFFFFFFFF.toInt())
        // shards hanging in the mouth of it
        fill(0xFFE8FAFF.toInt(), a * 0.85f)
        for (i in 0 until 5) {
            c.save()
            c.rotate(t * 24f + i * 72f)
            path.reset()
            path.moveTo(-9f, -48f); path.lineTo(0f, -76f); path.lineTo(9f, -48f); path.close()
            c.drawPath(path, p)
            c.restore()
        }
    }

    // =====================================================================================
    // Emberfall - ember moth, flame imp, molten rock, obsidian rift
    // =====================================================================================

    private fun emberMoth(c: Canvas, t: Float, phase: Float, facing: Float, a: Float) {
        val flap = sin(t * 30f + phase)
        c.scale(if (facing >= 0f) 1f else -1f, 1f)
        art.drawGlow(c, 0f, -8f, 130f, 0xFFFF7A3C.toInt(), 0.36f * a)
        fill(0xFFFF9B4A.toInt(), a * 0.82f)
        c.save(); c.scale(1f, 0.4f + 0.6f * abs(flap))
        path.reset()
        path.moveTo(-6f, -16f); path.lineTo(-58f, -56f); path.lineTo(-58f, -8f)
        path.lineTo(-26f, 6f); path.close()
        c.drawPath(path, p)
        path.reset()
        path.moveTo(6f, -16f); path.lineTo(58f, -56f); path.lineTo(58f, -8f)
        path.lineTo(26f, 6f); path.close()
        c.drawPath(path, p)
        fill(0xFFFFE07A.toInt(), a * 0.7f)   // hot inner edge of each wing
        path.reset()
        path.moveTo(-8f, -14f); path.lineTo(-40f, -38f); path.lineTo(-24f, 0f); path.close()
        c.drawPath(path, p)
        path.reset()
        path.moveTo(8f, -14f); path.lineTo(40f, -38f); path.lineTo(24f, 0f); path.close()
        c.drawPath(path, p)
        c.restore()

        fill(0xFF3A1508.toInt(), a)
        r.set(-15f, -24f, 15f, 30f)
        c.drawRoundRect(r, 13f, 13f, p)
        fill(0xFFFF5E2E.toInt(), a)   // glowing seams down the abdomen
        for (i in 0 until 3) {
            r.set(-12f, -8f + i * 12f, 12f, -3f + i * 12f)
            c.drawRoundRect(r, 3f, 3f, p)
        }
        stroke(0xFFFFC46B.toInt(), a, 3f)
        c.drawLine(-7f, -24f, -20f, -46f, p)
        c.drawLine(7f, -24f, 20f, -46f, p)
        eyes(c, -8f, 8f, -12f, 7f, a, 0xFF5A1B00.toInt())
    }

    private fun flameImp(c: Canvas, t: Float, phase: Float, facing: Float, a: Float) {
        val lick = sin(t * 11f + phase)
        c.scale(if (facing >= 0f) 1f else -1f, 1f)
        art.drawGlow(c, 0f, -10f, 160f, 0xFFFF6A22.toInt(), 0.42f * a)

        // the body IS a flame: an outer tongue, a middle and a white-hot core
        fun tongue(color: Int, k: Float, wobble: Float, alpha: Float) {
            fill(color, alpha)
            path.reset()
            path.moveTo(-40f * k, 32f)
            path.quadTo(-52f * k, -18f * k, -16f * k, -34f * k)
            path.quadTo(-24f * k, -58f * k + wobble * 8f, 2f * k, -74f * k + wobble * 12f)
            path.quadTo(6f * k, -44f * k, 22f * k, -36f * k)
            path.quadTo(54f * k, -20f * k, 40f * k, 32f)
            path.close()
            c.drawPath(path, p)
        }
        tongue(0xFFFF4A12.toInt(), 1f, lick, a)
        tongue(0xFFFF9A2E.toInt(), 0.74f, -lick, a)
        tongue(0xFFFFE27A.toInt(), 0.44f, lick * 0.6f, a * 0.95f)

        // horns and a grin, so it reads as a creature and not just a fire
        fill(0xFF5A1B00.toInt(), a)
        path.reset()
        path.moveTo(-26f, -22f); path.lineTo(-40f, -56f); path.lineTo(-12f, -34f); path.close()
        c.drawPath(path, p)
        path.reset()
        path.moveTo(26f, -22f); path.lineTo(40f, -56f); path.lineTo(12f, -34f); path.close()
        c.drawPath(path, p)
        eyes(c, -13f, 13f, -8f, 9f, a, 0xFF3A0B00.toInt())
        fill(0xFF3A0B00.toInt(), a)
        r.set(-16f, 8f, 16f, 22f)
        c.drawRoundRect(r, 7f, 7f, p)
        fill(0xFFFFE9B0.toInt(), a)
        for (i in 0 until 3) {
            r.set(-12f + i * 9f, 8f, -6f + i * 9f, 15f)
            c.drawRect(r, p)
        }
    }

    private fun moltenRock(c: Canvas, t: Float, phase: Float, a: Float) {
        val heat = 0.55f + 0.45f * sin(t * 2.1f + phase)
        art.drawGlow(c, 0f, 4f, 170f, 0xFFFF5E2E.toInt(), (0.22f + 0.2f * heat) * a)

        // a lump of dark basalt, cracked open and glowing through
        fill(0xFF2B2320.toInt(), a)
        path.reset()
        path.moveTo(-62f, 18f); path.lineTo(-52f, -22f); path.lineTo(-20f, -44f)
        path.lineTo(18f, -46f); path.lineTo(54f, -24f); path.lineTo(64f, 16f)
        path.lineTo(40f, 38f); path.lineTo(-38f, 38f); path.close()
        c.drawPath(path, p)
        fill(0xFF453833.toInt(), a)   // lit top faces
        path.reset()
        path.moveTo(-52f, -22f); path.lineTo(-20f, -44f); path.lineTo(18f, -46f)
        path.lineTo(2f, -20f); path.lineTo(-30f, -12f); path.close()
        c.drawPath(path, p)

        stroke(ColorX.lerp(0xFFFF5E2E.toInt(), 0xFFFFE07A.toInt(), heat), a, 6f)
        path.reset()
        path.moveTo(-44f, 6f); path.lineTo(-16f, -4f); path.lineTo(-6f, 16f)
        path.lineTo(24f, 4f); path.lineTo(50f, 12f)
        c.drawPath(path, p)
        path.reset()
        path.moveTo(-14f, 36f); path.lineTo(-4f, 16f)
        c.drawPath(path, p)

        // lava bubbling out of the top
        fill(ColorX.lerp(0xFFFF7A3C.toInt(), 0xFFFFD25A.toInt(), heat), a)
        r.set(-26f, -52f, 26f, -34f)
        c.drawOval(r, p)
        for (i in 0 until 3) {
            val ph = t * 3.1f + phase + i * 2.1f
            val up = (ph % 2f) / 2f
            fill(ColorX.lerp(0xFFFFD25A.toInt(), 0xFFFF5E2E.toInt(), up), a * (1f - up))
            c.drawCircle(-16f + i * 16f, -50f - up * 44f, 7f - up * 3f, p)
        }
        eyes(c, -20f, 20f, 6f, 9f, a, 0xFF2A0C00.toInt())
    }

    private fun obsidianRift(c: Canvas, t: Float, a: Float) {
        swirl(c, t, a, 0xFFFF6A22.toInt(), 0xFF120604.toInt(), 0xFFFFC46B.toInt())
        // cinders spat out of it
        for (i in 0 until 6) {
            val ph = t * 2.3f + i * 1.05f
            val d = 34f + ((ph * 40f) % 60f)
            fill(ColorX.lerp(0xFFFFD25A.toInt(), 0xFF8E2B08.toInt(), (d - 34f) / 60f), a * 0.85f)
            c.drawCircle(cos(ph * 1.7f) * d, sin(ph * 1.3f) * d * 0.72f, 5.5f, p)
        }
    }

    // =====================================================================================
    // Heaven - cherub, seraph, thundergate, light well
    // =====================================================================================

    /** A small round cherub with stubby wings and its own little halo. */
    private fun cherub(c: Canvas, t: Float, phase: Float, facing: Float, a: Float) {
        val flap = sin(t * 12f + phase)
        c.scale(if (facing >= 0f) 1f else -1f, 1f)
        art.drawGlow(c, 0f, -6f, 120f, 0xFFFFF4C2.toInt(), 0.3f * a)

        fill(0xFFFFFDF2.toInt(), a * 0.9f)                 // wings
        c.save(); c.scale(1f, 0.55f + 0.45f * abs(flap))
        path.reset()
        path.moveTo(-8f, -18f)
        path.cubicTo(-44f, -46f, -62f, -14f, -30f, -2f)
        path.cubicTo(-20f, 2f, -12f, -6f, -8f, -18f)
        path.close()
        c.drawPath(path, p)
        path.reset()
        path.moveTo(8f, -18f)
        path.cubicTo(44f, -46f, 62f, -14f, 30f, -2f)
        path.cubicTo(20f, 2f, 12f, -6f, 8f, -18f)
        path.close()
        c.drawPath(path, p)
        c.restore()

        fill(0xFFF6D9B8.toInt(), a)                        // body + head
        r.set(-22f, -12f, 22f, 30f)
        c.drawOval(r, p)
        c.drawCircle(4f, -26f, 24f, p)
        fill(0xFFE0B892.toInt(), a * 0.6f)
        r.set(-16f, 8f, 18f, 30f)
        c.drawOval(r, p)

        eyes(c, -4f, 14f, -28f, 6f, a, 0xFF3A2A18.toInt())
        fill(0xFFE07A88.toInt(), a * 0.55f)                 // cheeks
        c.drawCircle(-12f, -20f, 6f, p)
        c.drawCircle(22f, -20f, 6f, p)

        halo(c, 4f, -58f, 22f, a, t)
    }

    /** A tall winged figure that patrols - all wings and light, no face to speak of. */
    private fun seraph(c: Canvas, t: Float, phase: Float, facing: Float, a: Float) {
        val flap = sin(t * 7f + phase)
        c.scale(if (facing >= 0f) 1f else -1f, 1f)
        art.drawGlow(c, 0f, -10f, 200f, 0xFFFFE9A8.toInt(), 0.36f * a)

        // three pairs of wings, each pair beating out of phase with the last
        for (pair in 0 until 3) {
            val k = 1f - pair * 0.24f
            val beat = sin(t * 7f + phase + pair * 1.1f)
            fill(0xFFFFFDF4.toInt(), a * (0.85f - pair * 0.2f))
            for (side in 0 until 2) {
                val d = if (side == 0) -1f else 1f
                path.reset()
                path.moveTo(0f, -10f + pair * 14f)
                path.cubicTo(
                    d * 44f * k, -52f * k - beat * 18f,
                    d * 82f * k, -18f * k - beat * 10f,
                    d * 52f * k, 14f * k
                )
                path.cubicTo(d * 30f * k, 10f * k, d * 12f * k, 2f, 0f, -10f + pair * 14f)
                path.close()
                c.drawPath(path, p)
            }
        }
        fill(0xFFFFF4D8.toInt(), a)                         // the body: a column of light
        r.set(-13f, -34f, 13f, 34f)
        c.drawRoundRect(r, 13f, 13f, p)
        fill(0xFFFFFFFF.toInt(), a * 0.9f)
        r.set(-7f, -28f, 7f, 22f)
        c.drawRoundRect(r, 7f, 7f, p)
        halo(c, 0f, -52f - flap * 3f, 26f, a, t)
    }

    /** A gate of cloud with light hammering through the bars. Static, and it hurts. */
    private fun thundergate(c: Canvas, t: Float, phase: Float, a: Float) {
        art.drawGlow(c, 0f, 0f, 190f, 0xFFFFE9A8.toInt(), 0.34f * a)
        fill(0xFFE8EFF8.toInt(), a)                          // the cloud bank it sits in
        c.drawCircle(-46f, 6f, 34f, p)
        c.drawCircle(0f, -14f, 44f, p)
        c.drawCircle(46f, 6f, 32f, p)
        r.set(-60f, -6f, 60f, 32f)
        c.drawRoundRect(r, 22f, 22f, p)

        // gilded bars
        fill(0xFFE8C87A.toInt(), a)
        for (i in 0 until 4) {
            val x = -36f + i * 24f
            r.set(x - 5f, -24f, x + 5f, 40f)
            c.drawRoundRect(r, 4f, 4f, p)
        }
        r.set(-44f, -30f, 44f, -20f)
        c.drawRoundRect(r, 5f, 5f, p)

        // the light between the bars, pulsing
        val glow = 0.4f + 0.6f * abs(sin(t * 2.4f + phase))
        fill(0xFFFFFBE8.toInt(), a * glow * 0.7f)
        for (i in 0 until 3) {
            val x = -24f + i * 24f
            r.set(x - 6f, -20f, x + 6f, 38f)
            c.drawRect(r, p)
        }
        eyes(c, -20f, 20f, 2f, 8f, a, 0xFF6E5A28.toInt())
    }

    /** A shaft of light with a ring turning inside it. The big static one. */
    private fun lightWell(c: Canvas, t: Float, a: Float) {
        art.drawGlow(c, 0f, 0f, 260f, 0xFFFFFFFF.toInt(), 0.5f * a)
        // the shaft, widening upward
        fill(0xFFFFF8E0.toInt(), a * 0.34f)
        path.reset()
        path.moveTo(-26f, 70f)
        path.lineTo(-92f, -110f)
        path.lineTo(92f, -110f)
        path.lineTo(26f, 70f)
        path.close()
        c.drawPath(path, p)
        fill(0xFFFFFFFF.toInt(), a * 0.3f)
        path.reset()
        path.moveTo(-12f, 66f)
        path.lineTo(-44f, -110f)
        path.lineTo(44f, -110f)
        path.lineTo(12f, 66f)
        path.close()
        c.drawPath(path, p)

        // rings falling through it
        for (i in 0 until 3) {
            val k = ((t * 0.5f + i * 0.33f) % 1f)
            val y = -90f + k * 150f
            val rad = 30f + k * 36f
            stroke(0xFFFFE9A8.toInt(), a * (1f - k) * 0.9f, 6f)
            r.set(-rad, y - rad * 0.3f, rad, y + rad * 0.3f)
            c.drawOval(r, p)
        }
        fill(0xFFFFFFFF.toInt(), a * 0.85f)
        c.drawCircle(0f, 0f, 16f, p)
    }

    /** The ring every Heaven creature wears. Also used for Buddy's own halo. */
    private fun halo(c: Canvas, x: Float, y: Float, rad: Float, a: Float, t: Float) {
        val tilt = 0.3f + 0.06f * sin(t * 1.7f)
        art.drawGlow(c, x, y, rad * 4.5f, 0xFFFFE9A8.toInt(), 0.4f * a)
        stroke(0xFFFFE9A8.toInt(), a, rad * 0.22f)
        r.set(x - rad, y - rad * tilt, x + rad, y + rad * tilt)
        c.drawOval(r, p)
        stroke(0xFFFFFDF0.toInt(), a * 0.8f, rad * 0.08f)
        c.drawOval(r, p)
    }
}
