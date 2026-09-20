package com.blacklab.buddybounce.render

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import com.blacklab.buddybounce.game.Boost
import com.blacklab.buddybounce.game.Enemy
import com.blacklab.buddybounce.game.EnemyKind
import com.blacklab.buddybounce.game.Hash
import com.blacklab.buddybounce.game.MathX.clamp01
import com.blacklab.buddybounce.game.PickupKind
import com.blacklab.buddybounce.game.Pickup
import com.blacklab.buddybounce.game.PlatKind
import com.blacklab.buddybounce.game.Platform
import com.blacklab.buddybounce.game.Tuning
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

/** Draws the things in the world: platforms, pick-ups and hazards. */
class GameRenderer(private val art: Art) {

    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val ink = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
        strokeWidth = 5f
        color = 0xCC080A0F.toInt()
    }
    private val path = Path()
    private val r = RectF()

    // -----------------------------------------------------------------------------------
    // platforms
    // -----------------------------------------------------------------------------------

    fun drawPlatform(c: Canvas, plat: Platform, pal: BiomePalette, viewTop: Float) {
        // The starting ground is part of the backdrop; it just happens to also be collidable.
        if (plat.isGround) return
        val alpha = plat.alpha
        if (alpha <= 0.02f) return
        val top = viewTop - plat.y
        if (top < -240f || top > Tuning.VIEW_H + 240f) return

        val squash = plat.hitAnim
        val h = Tuning.PLAT_H * (1f - squash * 0.28f)
        val w = plat.w * (1f + squash * 0.05f)
        val left = plat.x - w * 0.5f
        val right = plat.x + w * 0.5f
        val skin = skinFor(pal.style)

        c.save()
        if (plat.state == 1 && plat.tilt != 0f) c.rotate(plat.tilt * 34f, plat.x, top)

        // contact shadow
        art.drawShadow(c, plat.x, top + h + 10f, w * 1.05f, h * 2.6f, 0.30f * alpha)

        val bodyColor = when (plat.kind) {
            PlatKind.FRAGILE -> ColorX.lerp(pal.platBody, 0xFF7A4038.toInt(), 0.55f)
            PlatKind.CRUMBLE -> ColorX.lerp(pal.platBody, 0xFF8A6A4A.toInt(), 0.35f)
            else -> pal.platBody
        }
        val topColor = when (plat.kind) {
            PlatKind.FRAGILE -> ColorX.lerp(pal.platTop, 0xFFB5695C.toInt(), 0.5f)
            PlatKind.CRUMBLE -> ColorX.lerp(pal.platTop, 0xFFC49A6A.toInt(), 0.3f)
            else -> pal.platTop
        }

        p.reset(); p.isAntiAlias = true

        // body + lit top face
        p.color = ColorX.withAlpha(pal.platShade, alpha)
        r.set(left, top + h * 0.35f, right, top + h)
        c.drawRoundRect(r, h * 0.38f, h * 0.38f, p)
        p.color = ColorX.withAlpha(bodyColor, alpha)
        r.set(left, top + h * 0.12f, right, top + h * 0.86f)
        c.drawRoundRect(r, h * 0.36f, h * 0.36f, p)
        ink.color = ColorX.withAlpha(0xFF080A0F.toInt(), alpha * 0.75f)
        ink.strokeWidth = 5f
        r.set(left, top, right, top + h)
        c.drawRoundRect(r, h * 0.38f, h * 0.38f, ink)
        p.color = ColorX.withAlpha(topColor, alpha)
        r.set(left, top, right, top + h * 0.46f)
        c.drawRoundRect(r, h * 0.3f, h * 0.3f, p)
        p.color = ColorX.withAlpha(ColorX.tint(topColor, 0.45f), alpha * 0.6f)
        r.set(left + w * 0.06f, top + h * 0.06f, right - w * 0.06f, top + h * 0.2f)
        c.drawRoundRect(r, h * 0.1f, h * 0.1f, p)

        // end caps: a slightly darker block at each end reads as thickness
        p.color = ColorX.withAlpha(ColorX.shade(bodyColor, 0.82f), alpha * 0.9f)
        r.set(left, top + h * 0.18f, left + h * 0.5f, top + h * 0.92f)
        c.drawRoundRect(r, h * 0.2f, h * 0.2f, p)
        r.set(right - h * 0.5f, top + h * 0.18f, right, top + h * 0.92f)
        c.drawRoundRect(r, h * 0.2f, h * 0.2f, p)
        // specular line along the very top
        p.color = ColorX.withAlpha(0xFFFFFFFF.toInt(), alpha * 0.22f)
        r.set(left + w * 0.1f, top + h * 0.04f, right - w * 0.22f, top + h * 0.12f)
        c.drawRoundRect(r, h * 0.06f, h * 0.06f, p)
        surfaceDetail(c, plat, top, left, right, w, h, skin, alpha, pal)

        when (skin) {
            0 -> grassTufts(c, plat, top, left, right, pal, alpha)
            1 -> moss(c, plat, top, left, right, pal, alpha)
            2 -> cloudPuffs(c, plat, top, left, right, w, h, alpha)
            3 -> crystal(c, plat, top, left, right, w, h, pal, alpha)
            else -> craters(c, plat, top, left, right, w, h, pal, alpha)
        }

        when (plat.kind) {
            PlatKind.CRUMBLE -> cracks(c, plat, top, left, right, w, h, alpha, 0.45f)
            PlatKind.FRAGILE -> {
                cracks(c, plat, top, left, right, w, h, alpha, 0.9f)
                p.color = ColorX.withAlpha(0xFFE07A5F.toInt(), alpha * 0.85f)
                p.style = Paint.Style.STROKE
                p.strokeWidth = 3.5f
                r.set(left + 2f, top + 2f, right - 2f, top + h - 2f)
                c.drawRoundRect(r, h * 0.3f, h * 0.3f, p)
                p.style = Paint.Style.FILL
            }
            PlatKind.SLIDER -> sliderMarks(c, plat, top, left, right, h, alpha)
            PlatKind.HOVER -> {
                art.drawGlow(c, plat.x, top + h + 16f, w * 0.7f, pal.platAccent, 0.35f * alpha)
                p.color = ColorX.withAlpha(pal.platAccent, alpha * 0.8f)
                for (i in 0 until 3) {
                    val fx = plat.x + (i - 1) * w * 0.26f
                    val bob = sin(plat.phase * 1.6f + i) * 5f
                    c.drawCircle(fx, top + h + 16f + bob, 5f, p)
                }
            }
        }

        when (plat.boost) {
            Boost.SPRING -> spring(c, plat, top, alpha)
            Boost.TRAMPOLINE -> trampoline(c, plat, top, alpha)
        }

        if (plat.rescue) {
            art.drawGlow(c, plat.x, top + h * 0.5f, w * 0.8f, 0xFF7BE3A0.toInt(), 0.45f * alpha)
        }

        c.restore()
    }

    /**
     * Close-up texture on the plank face: grain and nail heads on wood, speckle and cracks on
     * rock, facets on crystal, a dusting of sparkle on cloud. Two or three strokes each, but it
     * is the difference between a coloured bar and a drawn object.
     */
    private fun surfaceDetail(
        c: Canvas, plat: Platform, top: Float, left: Float, right: Float,
        w: Float, h: Float, skin: Int, a: Float, pal: BiomePalette
    ) {
        when (skin) {
            0, 1 -> {
                // wood grain
                ink.color = ColorX.withAlpha(ColorX.shade(pal.platBody, 0.62f), a * 0.55f)
                ink.strokeWidth = 2.2f
                for (i in 0 until 2) {
                    val gy = top + h * (0.42f + i * 0.24f)
                    path.reset()
                    path.moveTo(left + w * 0.1f, gy)
                    path.quadTo(plat.x, gy + (if (i == 0) 3f else -3f), right - w * 0.1f, gy)
                    c.drawPath(path, ink)
                }
                // nail heads
                p.color = ColorX.withAlpha(0xFF6E6152.toInt(), a * 0.8f)
                c.drawCircle(left + h * 0.5f, top + h * 0.55f, h * 0.09f, p)
                c.drawCircle(right - h * 0.5f, top + h * 0.55f, h * 0.09f, p)
            }
            2 -> {
                // cloud sparkle
                p.color = ColorX.withAlpha(0xFFFFFFFF.toInt(), a * 0.75f)
                for (i in 0 until 3) {
                    val sx = left + w * (0.22f + i * 0.28f)
                    val sr = 2.4f + Hash.f(plat.seed + i, 271) * 2.2f
                    c.drawCircle(sx, top + h * 0.52f, sr, p)
                }
            }
            3 -> {
                // crystal facets
                ink.color = ColorX.withAlpha(0xFFFFFFFF.toInt(), a * 0.45f)
                ink.strokeWidth = 2.4f
                for (i in 0 until 3) {
                    val fx = left + w * (0.24f + i * 0.26f)
                    c.drawLine(fx, top + h * 0.12f, fx - h * 0.35f, top + h * 0.9f, ink)
                }
            }
            else -> {
                // rock speckle and a hairline crack
                p.color = ColorX.withAlpha(ColorX.shade(pal.platShade, 0.75f), a * 0.85f)
                for (i in 0 until 6) {
                    val sx = left + w * (0.12f + Hash.f(plat.seed + i, 277) * 0.76f)
                    val sy = top + h * (0.3f + Hash.f(plat.seed + i, 281) * 0.5f)
                    c.drawCircle(sx, sy, 1.8f + Hash.f(plat.seed + i, 283) * 2.4f, p)
                }
                ink.color = ColorX.withAlpha(0xFF000000.toInt(), a * 0.35f)
                ink.strokeWidth = 2f
                path.reset()
                path.moveTo(left + w * 0.3f, top + h * 0.25f)
                path.lineTo(left + w * 0.42f, top + h * 0.6f)
                path.lineTo(left + w * 0.36f, top + h * 0.9f)
                c.drawPath(path, ink)
            }
        }
        ink.color = 0xCC080A0F.toInt()
        ink.strokeWidth = 5f
    }

    /** Which platform dressing suits a band: grass, moss, snow/cloud, crystal or rock. */
    private fun skinFor(style: Int): Int = when (style) {
        BandStyle.HILLS -> 0
        BandStyle.TREES, BandStyle.KELP -> 1
        BandStyle.CLOUDS, BandStyle.PEAKS -> 2
        BandStyle.AURORA, BandStyle.REEF -> 3
        else -> 4
    }

    private fun grassTufts(c: Canvas, plat: Platform, top: Float, left: Float, right: Float, pal: BiomePalette, a: Float) {
        p.color = ColorX.withAlpha(pal.platAccent, a)
        val n = ((right - left) / 34f).toInt().coerceIn(2, 9)
        for (i in 0 until n) {
            val x = left + 14f + i * ((right - left - 24f) / (n - 1).coerceAtLeast(1))
            val hgt = 12f + Hash.f(plat.seed + i, 211) * 16f
            path.reset()
            path.moveTo(x - 7f, top + 3f)
            path.quadTo(x - 1f, top - hgt * 0.7f, x + 5f, top - hgt)
            path.quadTo(x + 2f, top - hgt * 0.35f, x + 7f, top + 3f)
            path.close()
            c.drawPath(path, p)
        }
    }

    private fun moss(c: Canvas, plat: Platform, top: Float, left: Float, right: Float, pal: BiomePalette, a: Float) {
        p.color = ColorX.withAlpha(pal.platAccent, a * 0.9f)
        val n = ((right - left) / 42f).toInt().coerceIn(2, 8)
        for (i in 0 until n) {
            val x = left + 18f + i * ((right - left - 30f) / (n - 1).coerceAtLeast(1))
            val rr = 8f + Hash.f(plat.seed + i, 223) * 8f
            c.drawCircle(x, top + 2f, rr, p)
        }
        p.color = ColorX.withAlpha(0xFF8FD26B.toInt(), a * 0.7f)
        c.drawCircle(left + 20f, top - 2f, 6f, p)
        c.drawCircle(right - 24f, top - 1f, 5f, p)
    }

    private fun cloudPuffs(c: Canvas, plat: Platform, top: Float, left: Float, right: Float, w: Float, h: Float, a: Float) {
        p.color = ColorX.withAlpha(0xFFFFFFFF.toInt(), a * 0.95f)
        val n = ((right - left) / 46f).toInt().coerceIn(2, 8)
        for (i in 0 until n) {
            val x = left + 20f + i * ((right - left - 40f) / (n - 1).coerceAtLeast(1))
            val rr = 13f + Hash.f(plat.seed + i, 233) * 9f
            c.drawCircle(x, top + h * 0.28f, rr, p)
        }
        art.draw(c, art.softGlow, plat.x, top + h * 0.5f, w * 1.25f, h * 4f, a * 0.22f, 0xFFFFFFFF.toInt())
    }

    private fun crystal(c: Canvas, plat: Platform, top: Float, left: Float, right: Float, w: Float, h: Float, pal: BiomePalette, a: Float) {
        art.drawGlow(c, plat.x, top + h * 0.4f, w * 0.75f, pal.platAccent, 0.35f * a)
        p.color = ColorX.withAlpha(ColorX.tint(pal.platAccent, 0.4f), a * 0.85f)
        val n = 3
        for (i in 0 until n) {
            val x = left + w * (0.22f + i * 0.28f)
            val hgt = 14f + Hash.f(plat.seed + i, 241) * 18f
            path.reset()
            path.moveTo(x - 9f, top + 3f)
            path.lineTo(x, top - hgt)
            path.lineTo(x + 9f, top + 3f)
            path.close()
            c.drawPath(path, p)
        }
        p.color = ColorX.withAlpha(0xFFFFFFFF.toInt(), a * 0.5f)
        r.set(left + w * 0.08f, top + h * 0.08f, left + w * 0.42f, top + h * 0.2f)
        c.drawRoundRect(r, 6f, 6f, p)
    }

    private fun craters(c: Canvas, plat: Platform, top: Float, left: Float, right: Float, w: Float, h: Float, pal: BiomePalette, a: Float) {
        p.color = ColorX.withAlpha(pal.platShade, a * 0.9f)
        for (i in 0 until 3) {
            val x = left + w * (0.2f + Hash.f(plat.seed + i, 251) * 0.6f)
            val rr = 5f + Hash.f(plat.seed + i, 257) * 6f
            c.drawCircle(x, top + h * 0.3f, rr, p)
        }
        p.color = ColorX.withAlpha(pal.platAccent, a * 0.75f)
        p.style = Paint.Style.STROKE
        p.strokeWidth = 3f
        r.set(left + 3f, top + 2f, right - 3f, top + h * 0.6f)
        c.drawRoundRect(r, h * 0.3f, h * 0.3f, p)
        p.style = Paint.Style.FILL
    }

    private fun cracks(c: Canvas, plat: Platform, top: Float, left: Float, right: Float, w: Float, h: Float, a: Float, strength: Float) {
        p.color = ColorX.withAlpha(0xFF23150F.toInt(), a * strength * 0.8f)
        p.style = Paint.Style.STROKE
        p.strokeWidth = 3f
        for (i in 0 until 3) {
            val x = left + w * (0.25f + i * 0.25f + Hash.range(plat.seed + i, 261, -0.06f, 0.06f))
            path.reset()
            path.moveTo(x, top + 2f)
            path.lineTo(x + w * 0.05f, top + h * 0.45f)
            path.lineTo(x - w * 0.03f, top + h * 0.95f)
            c.drawPath(path, p)
        }
        p.style = Paint.Style.FILL
    }

    private fun sliderMarks(c: Canvas, plat: Platform, top: Float, left: Float, right: Float, h: Float, a: Float) {
        val dir = if (plat.vx >= 0f) 1f else -1f
        p.color = ColorX.withAlpha(0xFFFFFFFF.toInt(), a * 0.45f)
        for (i in 0 until 2) {
            val x = plat.x + dir * (18f + i * 16f)
            path.reset()
            path.moveTo(x - dir * 7f, top + h * 0.22f)
            path.lineTo(x, top + h * 0.45f)
            path.lineTo(x - dir * 7f, top + h * 0.68f)
            p.style = Paint.Style.STROKE
            p.strokeWidth = 4f
            p.strokeCap = Paint.Cap.ROUND
            c.drawPath(path, p)
        }
        p.style = Paint.Style.FILL
        // motion streak behind it
        p.color = ColorX.withAlpha(0xFFFFFFFF.toInt(), a * 0.14f)
        val sx = if (dir > 0f) left - 34f else right
        r.set(sx, top + h * 0.2f, sx + 34f, top + h * 0.8f)
        c.drawRoundRect(r, 8f, 8f, p)
    }

    private fun spring(c: Canvas, plat: Platform, top: Float, a: Float) {
        val compress = plat.boostAnim
        val height = 44f * (1f - compress * 0.62f)
        val x = plat.x
        p.reset(); p.isAntiAlias = true
        p.style = Paint.Style.STROKE
        p.strokeWidth = 7f
        p.strokeCap = Paint.Cap.ROUND
        p.color = ColorX.withAlpha(0xFFD8DEE9.toInt(), a)
        val coils = 3
        path.reset()
        for (i in 0..coils * 2) {
            val t = i / (coils * 2f)
            val yy = top - height * t
            val xx = x + (if (i % 2 == 0) -13f else 13f)
            if (i == 0) path.moveTo(xx, yy) else path.lineTo(xx, yy)
        }
        c.drawPath(path, p)
        p.style = Paint.Style.FILL
        p.color = ColorX.withAlpha(0xFFE8595B.toInt(), a)
        r.set(x - 21f, top - height - 12f, x + 21f, top - height + 4f)
        c.drawRoundRect(r, 7f, 7f, p)
        p.color = ColorX.withAlpha(0xFFFFFFFF.toInt(), a * 0.4f)
        r.set(x - 15f, top - height - 9f, x + 2f, top - height - 4f)
        c.drawRoundRect(r, 3f, 3f, p)
    }

    private fun trampoline(c: Canvas, plat: Platform, top: Float, a: Float) {
        val dip = plat.boostAnim * 22f
        val x = plat.x
        val w = (plat.w * 0.62f).coerceAtLeast(70f)
        p.reset(); p.isAntiAlias = true
        p.color = ColorX.withAlpha(0xFF6B4A2F.toInt(), a)
        p.style = Paint.Style.STROKE
        p.strokeWidth = 8f
        c.drawLine(x - w * 0.5f, top - 42f, x - w * 0.42f, top + 4f, p)
        c.drawLine(x + w * 0.5f, top - 42f, x + w * 0.42f, top + 4f, p)
        p.style = Paint.Style.FILL
        p.color = ColorX.withAlpha(0xFF3B6FD6.toInt(), a)
        path.reset()
        path.moveTo(x - w * 0.55f, top - 44f)
        path.quadTo(x, top - 44f + dip * 2f, x + w * 0.55f, top - 44f)
        path.quadTo(x, top - 30f + dip * 2f, x - w * 0.55f, top - 44f)
        path.close()
        c.drawPath(path, p)
        p.color = ColorX.withAlpha(0xFF7FB2FF.toInt(), a * 0.7f)
        path.reset()
        path.moveTo(x - w * 0.45f, top - 44f)
        path.quadTo(x, top - 40f + dip * 1.6f, x + w * 0.45f, top - 44f)
        c.drawPath(path, p)
    }

    // -----------------------------------------------------------------------------------
    // pick-ups
    // -----------------------------------------------------------------------------------

    fun drawPickup(c: Canvas, pk: Pickup, pal: BiomePalette, viewTop: Float, time: Float) {
        val sy = viewTop - pk.y
        if (sy < -140f || sy > Tuning.VIEW_H + 140f) return
        val bob = sin(time * 2.6f + pk.t * 3f) * 7f
        val y = sy + bob
        when (pk.kind) {
            PickupKind.COIN -> coin(c, pk.x, y, 30f, time + pk.t, 0xFFF2C14E.toInt(), 0xFFB98F25.toInt())
            PickupKind.BONE -> boneCoin(c, pk.x, y, time + pk.t)
            PickupKind.PROPELLER -> propellerIcon(c, pk.x, y, time)
            PickupKind.JETPACK -> jetpackIcon(c, pk.x, y)
            PickupKind.ROCKET -> rocketIcon(c, pk.x, y, time)
            PickupKind.SHIELD -> shieldIcon(c, pk.x, y, time)
            PickupKind.MAGNET -> magnetIcon(c, pk.x, y)
        }
    }

    private fun coin(c: Canvas, x: Float, y: Float, rad: Float, t: Float, face: Int, edge: Int) {
        val spin = cos(t * 3.4f)
        val w = rad * abs(spin).coerceAtLeast(0.14f)
        art.drawGlow(c, x, y, rad * 2.6f, face, 0.35f)
        p.reset(); p.isAntiAlias = true
        p.color = edge
        r.set(x - w - 3f, y - rad, x + w + 3f, y + rad)
        c.drawOval(r, p)
        ink.strokeWidth = 4f
        ink.color = 0x99080A0F.toInt()
        c.drawOval(r, ink)
        p.color = face
        r.set(x - w, y - rad + 3f, x + w, y + rad - 3f)
        c.drawOval(r, p)
        if (w > rad * 0.45f) {
            // paw print on the face
            p.color = ColorX.withAlpha(edge, 0.85f)
            val s = w / rad
            c.drawCircle(x, y + rad * 0.16f, rad * 0.30f * s, p)
            c.drawCircle(x - rad * 0.30f * s, y - rad * 0.22f, rad * 0.14f * s, p)
            c.drawCircle(x, y - rad * 0.38f, rad * 0.14f * s, p)
            c.drawCircle(x + rad * 0.30f * s, y - rad * 0.22f, rad * 0.14f * s, p)
        }
        p.color = ColorX.withAlpha(0xFFFFFFFF.toInt(), 0.5f)
        r.set(x - w * 0.55f, y - rad * 0.7f, x - w * 0.1f, y - rad * 0.1f)
        c.drawOval(r, p)

        // a shine sweeping across the face, so gold reads as metal rather than yellow
        val sweep = ((t * 0.7f) % 1.6f) - 0.3f
        if (sweep in 0f..1f && w > rad * 0.3f) {
            c.save()
            r.set(x - w, y - rad, x + w, y + rad)
            c.clipRect(r.left, r.top, r.right, r.bottom)
            p.color = ColorX.withAlpha(0xFFFFFFFF.toInt(), 0.32f)
            c.save()
            c.rotate(24f, x, y)
            r.set(x - w + w * 2f * sweep - 3f, y - rad * 1.4f, x - w + w * 2f * sweep + 5f, y + rad * 1.4f)
            c.drawRect(r, p)
            c.restore()
            c.restore()
        }
    }

    private fun boneCoin(c: Canvas, x: Float, y: Float, t: Float) {
        val spin = cos(t * 2.6f)
        art.drawGlow(c, x, y, 96f, 0xFFFFE9A8.toInt(), 0.45f)
        c.save()
        c.translate(x, y)
        c.scale(abs(spin).coerceAtLeast(0.2f), 1f)
        c.rotate(sin(t * 1.4f) * 10f)
        p.reset(); p.isAntiAlias = true
        p.color = 0xFFF7F3E4.toInt()
        r.set(-30f, -9f, 30f, 9f)
        c.drawRoundRect(r, 9f, 9f, p)
        c.drawCircle(-30f, -10f, 11f, p)
        c.drawCircle(-30f, 10f, 11f, p)
        c.drawCircle(30f, -10f, 11f, p)
        c.drawCircle(30f, 10f, 11f, p)
        p.color = ColorX.withAlpha(0xFFB9A98A.toInt(), 0.5f)
        r.set(-26f, 1f, 26f, 8f)
        c.drawRoundRect(r, 5f, 5f, p)
        c.restore()
    }

    private fun pedestal(c: Canvas, x: Float, y: Float, color: Int) {
        art.drawGlow(c, x, y, 78f, color, 0.4f)
        p.reset(); p.isAntiAlias = true
        p.color = ColorX.withAlpha(color, 0.25f)
        c.drawCircle(x, y, 40f, p)
    }

    private fun propellerIcon(c: Canvas, x: Float, y: Float, t: Float) {
        pedestal(c, x, y, 0xFF57C4E5.toInt())
        p.color = 0xFF3FA9E0.toInt()
        r.set(x - 30f, y - 10f, x + 30f, y + 30f)
        c.drawArc(r, 180f, 180f, true, p)
        p.color = 0xFF2E86B5.toInt()
        r.set(x - 33f, y + 14f, x + 33f, y + 26f)
        c.drawRoundRect(r, 6f, 6f, p)
        val spin = cos(t * 12f)
        p.color = 0xFFE8EDF5.toInt()
        val w = 40f * abs(spin).coerceAtLeast(0.12f)
        r.set(x - w, y - 26f, x + w, y - 15f)
        c.drawRoundRect(r, 5f, 5f, p)
        p.color = 0xFF9AA6B8.toInt()
        c.drawCircle(x, y - 20f, 6f, p)
    }

    private fun jetpackIcon(c: Canvas, x: Float, y: Float) {
        pedestal(c, x, y, 0xFFE05E4A.toInt())
        p.color = 0xFFCED6E2.toInt()
        r.set(x - 26f, y - 28f, x - 2f, y + 26f)
        c.drawRoundRect(r, 11f, 11f, p)
        r.set(x + 2f, y - 28f, x + 26f, y + 26f)
        c.drawRoundRect(r, 11f, 11f, p)
        p.color = 0xFFE05E4A.toInt()
        r.set(x - 24f, y - 25f, x - 4f, y - 13f)
        c.drawRoundRect(r, 5f, 5f, p)
        r.set(x + 4f, y - 25f, x + 24f, y - 13f)
        c.drawRoundRect(r, 5f, 5f, p)
        p.color = 0xFFFFB347.toInt()
        path.reset()
        path.moveTo(x - 20f, y + 26f)
        path.lineTo(x - 14f, y + 44f)
        path.lineTo(x - 8f, y + 26f)
        path.close()
        path.moveTo(x + 8f, y + 26f)
        path.lineTo(x + 14f, y + 44f)
        path.lineTo(x + 20f, y + 26f)
        path.close()
        c.drawPath(path, p)
    }

    private fun rocketIcon(c: Canvas, x: Float, y: Float, t: Float) {
        pedestal(c, x, y, 0xFFFFB347.toInt())
        p.color = 0xFFEFEFF4.toInt()
        r.set(x - 16f, y - 34f, x + 16f, y + 22f)
        c.drawRoundRect(r, 16f, 12f, p)
        p.color = 0xFFD8453B.toInt()
        path.reset()
        path.moveTo(x - 16f, y - 22f)
        path.lineTo(x, y - 48f)
        path.lineTo(x + 16f, y - 22f)
        path.close()
        path.moveTo(x - 16f, y + 6f)
        path.lineTo(x - 30f, y + 30f)
        path.lineTo(x - 16f, y + 24f)
        path.close()
        path.moveTo(x + 16f, y + 6f)
        path.lineTo(x + 30f, y + 30f)
        path.lineTo(x + 16f, y + 24f)
        path.close()
        c.drawPath(path, p)
        p.color = 0xFF57C4E5.toInt()
        c.drawCircle(x, y - 12f, 8f, p)
        val flick = 0.7f + 0.3f * sin(t * 24f)
        p.color = 0xFFFFB347.toInt()
        path.reset()
        path.moveTo(x - 10f, y + 22f)
        path.quadTo(x, y + 22f + 30f * flick, x + 10f, y + 22f)
        path.close()
        c.drawPath(path, p)
    }

    private fun shieldIcon(c: Canvas, x: Float, y: Float, t: Float) {
        pedestal(c, x, y, 0xFF8FD3F4.toInt())
        p.color = ColorX.withAlpha(0xFFBDEBFF.toInt(), 0.45f)
        c.drawCircle(x, y, 32f, p)
        p.style = Paint.Style.STROKE
        p.strokeWidth = 6f
        p.color = 0xFFEAF7FF.toInt()
        c.drawCircle(x, y, 32f, p)
        p.style = Paint.Style.FILL
        p.color = ColorX.withAlpha(0xFFFFFFFF.toInt(), 0.75f)
        c.drawCircle(x - 11f, y - 12f, 7f, p)
    }

    private fun magnetIcon(c: Canvas, x: Float, y: Float) {
        pedestal(c, x, y, 0xFFE8595B.toInt())
        p.style = Paint.Style.STROKE
        p.strokeWidth = 16f
        p.color = 0xFFE8595B.toInt()
        r.set(x - 24f, y - 26f, x + 24f, y + 22f)
        c.drawArc(r, 180f, 180f, false, p)
        p.style = Paint.Style.FILL
        p.color = 0xFFE8EDF5.toInt()
        r.set(x - 32f, y - 2f, x - 16f, y + 24f)
        c.drawRect(r, p)
        r.set(x + 16f, y - 2f, x + 32f, y + 24f)
        c.drawRect(r, p)
    }

    // -----------------------------------------------------------------------------------
    // hazards
    // -----------------------------------------------------------------------------------

    fun drawEnemy(c: Canvas, e: Enemy, pal: BiomePalette, viewTop: Float, time: Float) {
        val sy = viewTop - e.y
        if (sy < -220f || sy > Tuning.VIEW_H + 220f) return
        val fade = if (e.dying) clamp01(1f - e.dieT / 0.55f) else 1f
        c.save()
        c.translate(e.x, sy)
        if (e.dying) {
            c.rotate(e.dieT * 400f)
            c.scale(1f + e.dieT, 1f - e.dieT * 0.4f)
        }
        when (e.kind) {
            EnemyKind.BEE -> bee(c, e, time, fade)
            EnemyKind.CROW -> crow(c, e, time, fade)
            EnemyKind.STORM -> storm(c, e, time, fade)
            else -> rift(c, e, time, fade)
        }
        c.restore()
    }

    private fun bee(c: Canvas, e: Enemy, t: Float, a: Float) {
        val flap = sin(t * 40f + e.phase)
        p.reset(); p.isAntiAlias = true
        p.color = ColorX.withAlpha(0xFFE8F4FF.toInt(), a * 0.6f)
        c.save(); c.scale(1f, 0.4f + 0.6f * abs(flap))
        r.set(-46f, -58f, -4f, -20f); c.drawOval(r, p)
        r.set(4f, -58f, 46f, -20f); c.drawOval(r, p)
        c.restore()

        p.color = ColorX.withAlpha(0xFFF2C14E.toInt(), a)
        r.set(-40f, -28f, 40f, 30f)
        c.drawRoundRect(r, 28f, 28f, p)
        p.color = ColorX.withAlpha(0xFF2A2B32.toInt(), a)
        for (i in 0 until 2) {
            r.set(-2f + i * 20f, -27f, 12f + i * 20f, 29f)
            c.drawRect(r, p)
        }
        p.color = ColorX.withAlpha(0xFF2A2B32.toInt(), a)
        path.reset()
        path.moveTo(40f, 0f); path.lineTo(58f, 4f); path.lineTo(40f, 10f); path.close()
        c.drawPath(path, p)
        // face
        p.color = ColorX.withAlpha(0xFFFFFFFF.toInt(), a)
        c.drawCircle(-22f, -8f, 9f, p)
        c.drawCircle(-2f, -8f, 9f, p)
        p.color = ColorX.withAlpha(0xFF15161C.toInt(), a)
        c.drawCircle(-20f, -7f, 4.5f, p)
        c.drawCircle(0f, -7f, 4.5f, p)
        p.style = Paint.Style.STROKE
        p.strokeWidth = 3.5f
        c.drawLine(-32f, -20f, -16f, -15f, p)
        c.drawLine(8f, -15f, -8f, -20f, p)
        p.style = Paint.Style.FILL
    }

    private fun crow(c: Canvas, e: Enemy, t: Float, a: Float) {
        val flap = sin(t * 14f + e.phase)
        val dir = if (e.facing >= 0f) 1f else -1f
        c.scale(dir, 1f)
        p.reset(); p.isAntiAlias = true
        p.color = ColorX.withAlpha(0xFF23252D.toInt(), a)
        r.set(-52f, -22f, 44f, 30f)
        c.drawOval(r, p)
        c.drawCircle(34f, -10f, 26f, p)
        // wings
        p.color = ColorX.withAlpha(0xFF15161B.toInt(), a)
        path.reset()
        path.moveTo(-8f, -6f)
        path.quadTo(-30f, -30f - flap * 34f, -66f, -10f - flap * 16f)
        path.quadTo(-34f, 10f, -8f, 14f)
        path.close()
        c.drawPath(path, p)
        // tail
        path.reset()
        path.moveTo(-44f, 2f); path.lineTo(-76f, 18f); path.lineTo(-44f, 22f); path.close()
        c.drawPath(path, p)
        // beak + eye
        p.color = ColorX.withAlpha(0xFFF2A03C.toInt(), a)
        path.reset()
        path.moveTo(52f, -14f); path.lineTo(80f, -6f); path.lineTo(52f, 2f); path.close()
        c.drawPath(path, p)
        p.color = ColorX.withAlpha(0xFFFFFFFF.toInt(), a)
        c.drawCircle(40f, -18f, 8f, p)
        p.color = ColorX.withAlpha(0xFF0D0E12.toInt(), a)
        c.drawCircle(42f, -17f, 4f, p)
    }

    private fun storm(c: Canvas, e: Enemy, t: Float, a: Float) {
        art.drawGlow(c, 0f, 0f, 150f, 0xFF6C7BD6.toInt(), 0.3f * a)
        p.reset(); p.isAntiAlias = true
        p.color = ColorX.withAlpha(0xFF4A5170.toInt(), a)
        c.drawCircle(-44f, 4f, 36f, p)
        c.drawCircle(0f, -16f, 46f, p)
        c.drawCircle(44f, 4f, 34f, p)
        r.set(-60f, -8f, 60f, 34f)
        c.drawRoundRect(r, 24f, 24f, p)
        p.color = ColorX.withAlpha(0xFF2F3550.toInt(), a)
        r.set(-58f, 14f, 58f, 36f)
        c.drawRoundRect(r, 18f, 18f, p)
        // angry eyes
        p.color = ColorX.withAlpha(0xFFFFFFFF.toInt(), a)
        c.drawCircle(-18f, 0f, 9f, p)
        c.drawCircle(18f, 0f, 9f, p)
        p.color = ColorX.withAlpha(0xFF15161C.toInt(), a)
        c.drawCircle(-17f, 1f, 4.5f, p)
        c.drawCircle(19f, 1f, 4.5f, p)
        // lightning
        val zap = ((t * 3f).toInt() % 3) == 0
        if (zap) {
            p.color = ColorX.withAlpha(0xFFFFE07A.toInt(), a * (0.6f + 0.4f * sin(t * 40f)))
            path.reset()
            path.moveTo(-8f, 32f)
            path.lineTo(-22f, 62f)
            path.lineTo(-4f, 58f)
            path.lineTo(-14f, 92f)
            path.lineTo(20f, 52f)
            path.lineTo(2f, 54f)
            path.lineTo(14f, 32f)
            path.close()
            c.drawPath(path, p)
        }
    }

    private fun rift(c: Canvas, e: Enemy, t: Float, a: Float) {
        art.drawGlow(c, 0f, 0f, 220f, 0xFF7B4BD8.toInt(), 0.45f * a)
        p.reset(); p.isAntiAlias = true
        for (i in 0 until 4) {
            val k = 1f - i * 0.22f
            p.color = ColorX.withAlpha(ColorX.lerp(0xFF7B4BD8.toInt(), 0xFF07040F.toInt(), i / 3f), a)
            c.save()
            c.rotate(t * (28f + i * 22f))
            r.set(-92f * k, -70f * k, 92f * k, 70f * k)
            c.drawOval(r, p)
            c.restore()
        }
        p.color = ColorX.withAlpha(0xFF000000.toInt(), a)
        c.drawCircle(0f, 0f, 30f, p)
        p.style = Paint.Style.STROKE
        p.strokeWidth = 4f
        p.color = ColorX.withAlpha(0xFFCBB7FF.toInt(), a * 0.7f)
        c.save(); c.rotate(-t * 60f)
        r.set(-70f, -52f, 70f, 52f)
        c.drawArc(r, 20f, 200f, false, p)
        c.restore()
        p.style = Paint.Style.FILL
    }
}
