package com.blacklab.buddybounce.render

import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import com.blacklab.buddybounce.game.Hash
import com.blacklab.buddybounce.game.MathX.clamp01
import com.blacklab.buddybounce.game.Tuning
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.sin

/**
 * Sky, parallax layers and the per-biome flourishes. Everything repeats on a band so the
 * backdrop is infinite, and consecutive biomes cross-fade into each other.
 */
class Backdrop(private val art: Art) {

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val path = Path()
    private val rect = RectF()

    private var skyShader: Shader? = null
    private var skyKeyBiome = -1
    private var skyKeyBlend = -1f
    private var skyKeyHeight = -1f

    fun draw(
        c: Canvas,
        worldW: Float,
        camY: Float,
        time: Float,
        biome: Int,
        blend: Float
    ) {
        val pal = Palettes.get(biome)
        val next = Palettes.get(biome + 1)
        val h = Tuning.VIEW_H

        drawSky(c, worldW, h, biome, blend, pal, next)
        drawStars(c, worldW, camY, time, lerpF(pal.starAlpha, next.starAlpha, blend))
        drawCelestial(c, worldW, camY, time, biome, blend, pal, next)

        // Far / mid / near flourishes, cross-faded between the two adjacent biomes.
        drawFlourish(c, worldW, camY, time, biome, pal, 1f - blend)
        if (blend > 0.004f) drawFlourish(c, worldW, camY, time, biome + 1, next, blend)

        drawClouds(c, worldW, camY, time, lerpF(pal.cloudAlpha, next.cloudAlpha, blend), pal, next, blend)
    }

    private fun lerpF(a: Float, b: Float, t: Float): Float = a + (b - a) * clamp01(t)

    private fun drawSky(
        c: Canvas, worldW: Float, h: Float, biome: Int, blend: Float,
        pal: BiomePalette, next: BiomePalette
    ) {
        val quantised = (blend * 24f).toInt() / 24f
        if (skyShader == null || skyKeyBiome != biome || skyKeyBlend != quantised || skyKeyHeight != h) {
            skyShader = LinearGradient(
                0f, 0f, 0f, h,
                intArrayOf(
                    ColorX.lerp(pal.skyTop, next.skyTop, quantised),
                    ColorX.lerp(pal.skyMid, next.skyMid, quantised),
                    ColorX.lerp(pal.skyLow, next.skyLow, quantised)
                ),
                floatArrayOf(0f, 0.55f, 1f),
                Shader.TileMode.CLAMP
            )
            skyKeyBiome = biome
            skyKeyBlend = quantised
            skyKeyHeight = h
        }
        paint.reset()
        paint.isAntiAlias = true
        paint.shader = skyShader
        c.drawRect(0f, 0f, worldW, h, paint)
        paint.shader = null
    }

    private fun drawStars(c: Canvas, worldW: Float, camY: Float, time: Float, alpha: Float) {
        if (alpha <= 0.01f) return
        val p = 0.06f
        val band = Tuning.VIEW_H * 1.5f
        val i0 = floor((camY * p) / band).toInt()
        paint.reset()
        paint.isAntiAlias = true
        paint.style = Paint.Style.FILL
        for (k in 0..2) {
            val idx = i0 + k
            val layerY = idx * band
            val baseScreenY = Tuning.VIEW_H - (layerY - camY * p)
            for (i in 0 until 46) {
                val sx = Hash.f(idx * 97 + i, 11) * worldW
                val sy = baseScreenY - Hash.f(idx * 97 + i, 23) * band
                if (sy < -40f || sy > Tuning.VIEW_H + 40f) continue
                val tw = 0.55f + 0.45f * sin(time * (1.1f + Hash.f(i, 31) * 2.2f) + Hash.f(i, 41) * 6.28f)
                val r = 2.2f + Hash.f(idx * 97 + i, 53) * 4.6f
                paint.color = ColorX.withAlpha(0xFFFFFFFF.toInt(), alpha * tw * (0.5f + Hash.f(i, 61) * 0.5f))
                c.drawCircle(sx, sy, r, paint)
                if (r > 5.4f) {
                    art.draw(c, art.glow, sx, sy, r * 9f, r * 9f, alpha * tw * 0.35f)
                }
            }
        }
    }

    private fun drawCelestial(
        c: Canvas, worldW: Float, camY: Float, time: Float, biome: Int, blend: Float,
        pal: BiomePalette, next: BiomePalette
    ) {
        val p = 0.035f
        val band = Tuning.VIEW_H * 4.2f
        val i0 = floor((camY * p) / band).toInt()
        for (k in 0..1) {
            val idx = i0 + k
            val layerY = idx * band
            val sy = Tuning.VIEW_H - (layerY - camY * p) - band * 0.62f
            if (sy < -600f || sy > Tuning.VIEW_H + 600f) continue
            val sx = worldW * (0.18f + Hash.f(idx, 7) * 0.64f)
            val col = ColorX.lerp(pal.sunColor, next.sunColor, blend)
            val r = 150f + Hash.f(idx, 9) * 60f
            art.drawGlow(c, sx, sy, r * 3.4f, col, 0.28f)
            paint.reset(); paint.isAntiAlias = true
            paint.color = ColorX.withAlpha(col, 0.92f)
            c.drawCircle(sx, sy, r, paint)
            // Crater / limb shading so it reads as a body rather than a flat disc.
            paint.color = ColorX.withAlpha(ColorX.shade(col, 0.88f), 0.5f)
            c.drawCircle(sx + r * 0.28f, sy + r * 0.22f, r * 0.72f, paint)
            val ringed = Hash.f(idx, 13) > 0.72f
            if (ringed) {
                paint.style = Paint.Style.STROKE
                paint.strokeWidth = 16f
                paint.color = ColorX.withAlpha(col, 0.45f)
                rect.set(sx - r * 1.9f, sy - r * 0.55f, sx + r * 1.9f, sy + r * 0.55f)
                c.save()
                c.rotate(-16f, sx, sy)
                c.drawOval(rect, paint)
                c.restore()
                paint.style = Paint.Style.FILL
            }
        }
    }

    private fun drawClouds(
        c: Canvas, worldW: Float, camY: Float, time: Float, alpha: Float,
        pal: BiomePalette, next: BiomePalette, blend: Float
    ) {
        if (alpha <= 0.02f) return
        val tintColor = ColorX.lerp(pal.haze, next.haze, blend)
        // Two layers at different depths.
        cloudLayer(c, worldW, camY, 0.22f, Tuning.VIEW_H * 0.95f, 520f, alpha * 0.55f, tintColor, 3, time * 6f)
        cloudLayer(c, worldW, camY, 0.46f, Tuning.VIEW_H * 1.25f, 880f, alpha * 0.8f, ColorX.tint(tintColor, 0.5f), 2, time * 11f)
    }

    private fun cloudLayer(
        c: Canvas, worldW: Float, camY: Float, p: Float, band: Float, size: Float,
        alpha: Float, tint: Int, perBand: Int, drift: Float
    ) {
        val i0 = floor((camY * p) / band).toInt()
        for (k in 0..2) {
            val idx = i0 + k
            val layerY = idx * band
            val baseScreenY = Tuning.VIEW_H - (layerY - camY * p)
            for (i in 0 until perBand) {
                val key = idx * 131 + i
                val w = size * (0.7f + Hash.f(key, 3) * 0.9f)
                val hgt = w * 0.52f
                val sx = ((Hash.f(key, 5) * (worldW + w * 2f) + drift * (0.3f + Hash.f(key, 17))) %
                    (worldW + w * 2f)) - w
                val sy = baseScreenY - Hash.f(key, 19) * band
                if (sy < -hgt || sy > Tuning.VIEW_H + hgt) continue
                art.draw(c, art.cloud, sx, sy, w, hgt, alpha * (0.6f + Hash.f(key, 29) * 0.4f), tint)
            }
        }
    }

    // -----------------------------------------------------------------------------------
    // per-biome flourishes
    // -----------------------------------------------------------------------------------

    private fun drawFlourish(
        c: Canvas, worldW: Float, camY: Float, time: Float, biome: Int,
        pal: BiomePalette, alpha: Float
    ) {
        if (alpha <= 0.01f) return
        when (biome % Palettes.list.size) {
            0 -> drawHills(c, worldW, camY, pal, alpha)
            1 -> drawTrees(c, worldW, camY, pal, alpha)
            2 -> drawCloudBanks(c, worldW, camY, pal, alpha, time)
            3 -> drawAurora(c, worldW, camY, pal, alpha, time)
            else -> drawNebula(c, worldW, camY, pal, alpha, time)
        }
    }

    private fun drawHills(c: Canvas, worldW: Float, camY: Float, pal: BiomePalette, alpha: Float) {
        val p = 0.14f
        val band = Tuning.VIEW_H * 1.1f
        val i0 = floor((camY * p) / band).toInt()
        paint.reset(); paint.isAntiAlias = true
        for (k in 0..2) {
            val idx = i0 + k
            val baseY = Tuning.VIEW_H - (idx * band - camY * p)
            if (baseY < -band || baseY > Tuning.VIEW_H + band) continue
            // far ridge
            hillRow(c, worldW, baseY, 210f, ColorX.withAlpha(ColorX.tint(pal.farShape, 0.35f), alpha * 0.55f), idx, 3)
            // near ridge with hedges
            hillRow(c, worldW, baseY + 120f, 300f, ColorX.withAlpha(pal.midShape, alpha * 0.75f), idx + 500, 2)
        }
    }

    private fun hillRow(c: Canvas, worldW: Float, baseY: Float, h: Float, color: Int, key: Int, lobes: Int) {
        path.reset()
        path.moveTo(-40f, baseY + h)
        val step = worldW / lobes
        var x = -40f
        for (i in 0..lobes) {
            val peak = baseY - h * (0.45f + Hash.f(key * 31 + i, 71) * 0.55f)
            val cx = x + step * 0.5f
            path.quadTo(cx, peak, x + step, baseY + h * 0.2f)
            x += step
        }
        path.lineTo(worldW + 40f, baseY + h * 1.6f)
        path.lineTo(-40f, baseY + h * 1.6f)
        path.close()
        paint.color = color
        c.drawPath(path, paint)
    }

    private fun drawTrees(c: Canvas, worldW: Float, camY: Float, pal: BiomePalette, alpha: Float) {
        val p = 0.26f
        val band = Tuning.VIEW_H * 0.8f
        val i0 = floor((camY * p) / band).toInt()
        paint.reset(); paint.isAntiAlias = true
        for (k in 0..2) {
            val idx = i0 + k
            val baseY = Tuning.VIEW_H - (idx * band - camY * p)
            if (baseY < -band * 1.5f || baseY > Tuning.VIEW_H + band) continue
            val fromLeft = Hash.f(idx, 83) > 0.5f
            val trunkX = if (fromLeft) -30f else worldW + 30f
            val dir = if (fromLeft) 1f else -1f
            // trunk
            paint.color = ColorX.withAlpha(ColorX.shade(pal.nearShape, 0.7f), alpha * 0.9f)
            rect.set(
                minOf(trunkX, trunkX + dir * 120f), baseY - band * 1.2f,
                maxOf(trunkX, trunkX + dir * 120f), baseY + band * 0.4f
            )
            c.drawRect(rect, paint)
            // canopy lobes
            paint.color = ColorX.withAlpha(pal.midShape, alpha * 0.85f)
            for (i in 0 until 5) {
                val cx = trunkX + dir * (100f + Hash.f(idx * 17 + i, 91) * 320f)
                val cy = baseY - band * (0.15f + Hash.f(idx * 17 + i, 93) * 0.95f)
                val r = 130f + Hash.f(idx * 17 + i, 97) * 150f
                c.drawCircle(cx, cy, r, paint)
            }
            paint.color = ColorX.withAlpha(ColorX.tint(pal.farShape, 0.25f), alpha * 0.55f)
            for (i in 0 until 3) {
                val cx = trunkX + dir * (140f + Hash.f(idx * 19 + i, 101) * 260f)
                val cy = baseY - band * (0.35f + Hash.f(idx * 19 + i, 103) * 0.8f)
                c.drawCircle(cx, cy, 90f + Hash.f(idx * 19 + i, 107) * 90f, paint)
            }
        }
    }

    private fun drawCloudBanks(c: Canvas, worldW: Float, camY: Float, pal: BiomePalette, alpha: Float, time: Float) {
        val p = 0.17f
        val band = Tuning.VIEW_H * 0.85f
        val i0 = floor((camY * p) / band).toInt()
        for (k in 0..2) {
            val idx = i0 + k
            val baseY = Tuning.VIEW_H - (idx * band - camY * p)
            if (baseY < -band || baseY > Tuning.VIEW_H + band) continue
            for (i in 0 until 2) {
                val key = idx * 37 + i
                val w = worldW * (0.55f + Hash.f(key, 111) * 0.8f)
                val sx = Hash.f(key, 113) * worldW
                val sy = baseY - Hash.f(key, 117) * band
                art.draw(c, art.cloud, sx, sy, w, w * 0.45f, alpha * 0.5f, ColorX.withAlpha(pal.farShape, 1f))
            }
        }
    }

    private fun drawAurora(c: Canvas, worldW: Float, camY: Float, pal: BiomePalette, alpha: Float, time: Float) {
        val p = 0.12f
        val band = Tuning.VIEW_H * 1.2f
        val i0 = floor((camY * p) / band).toInt()
        paint.reset(); paint.isAntiAlias = true
        paint.style = Paint.Style.STROKE
        paint.strokeCap = Paint.Cap.ROUND
        for (k in 0..2) {
            val idx = i0 + k
            val baseY = Tuning.VIEW_H - (idx * band - camY * p)
            if (baseY < -band || baseY > Tuning.VIEW_H + band) continue
            for (ribbon in 0 until 3) {
                val key = idx * 53 + ribbon
                val yy = baseY - Hash.f(key, 121) * band
                val hue = if (ribbon % 2 == 0) pal.farShape else pal.midShape
                val amp = 90f + Hash.f(key, 123) * 120f
                val speed = 0.35f + Hash.f(key, 127) * 0.5f
                path.reset()
                var x = -60f
                var first = true
                while (x < worldW + 60f) {
                    val yOff = sin(x * 0.0045f + time * speed + Hash.f(key, 131) * 6f) * amp
                    if (first) { path.moveTo(x, yy + yOff); first = false } else path.lineTo(x, yy + yOff)
                    x += 46f
                }
                for (pass in 0 until 3) {
                    paint.strokeWidth = 34f + pass * 46f
                    paint.color = ColorX.withAlpha(hue, alpha * (0.22f - pass * 0.06f))
                    c.drawPath(path, paint)
                }
            }
        }
        paint.style = Paint.Style.FILL
    }

    private fun drawNebula(c: Canvas, worldW: Float, camY: Float, pal: BiomePalette, alpha: Float, time: Float) {
        val p = 0.1f
        val band = Tuning.VIEW_H * 1.35f
        val i0 = floor((camY * p) / band).toInt()
        for (k in 0..2) {
            val idx = i0 + k
            val baseY = Tuning.VIEW_H - (idx * band - camY * p)
            if (baseY < -band || baseY > Tuning.VIEW_H + band) continue
            for (i in 0 until 3) {
                val key = idx * 71 + i
                val sx = Hash.f(key, 141) * worldW
                val sy = baseY - Hash.f(key, 143) * band
                val r = 320f + Hash.f(key, 147) * 420f
                val col = if (i % 2 == 0) pal.farShape else pal.midShape
                art.draw(c, art.softGlow, sx, sy, r * 2f, r * 1.5f, alpha * 0.30f, col)
            }
        }
    }

    /**
     * The backyard the run starts in: grass, fence, a sunflower or two and Buddy's house.
     * Only drawn while the bottom of the world is still on screen.
     */
    fun drawGround(c: Canvas, worldW: Float, viewTop: Float, groundY: Float) {
        val sy = viewTop - groundY
        if (sy < -40f || sy > Tuning.VIEW_H + 600f) return
        paint.reset(); paint.isAntiAlias = true

        // fence
        val fenceTop = sy - 250f
        paint.color = 0xFFE4E0D2.toInt()
        var fx = 20f
        while (fx < worldW) {
            rect.set(fx, fenceTop, fx + 46f, sy + 40f)
            c.drawRoundRect(rect, 10f, 10f, paint)
            path.reset()
            path.moveTo(fx, fenceTop)
            path.lineTo(fx + 23f, fenceTop - 34f)
            path.lineTo(fx + 46f, fenceTop)
            path.close()
            c.drawPath(path, paint)
            fx += 78f
        }
        paint.color = 0xFFCFC9B6.toInt()
        rect.set(0f, fenceTop + 70f, worldW, fenceTop + 100f)
        c.drawRect(rect, paint)
        rect.set(0f, fenceTop + 170f, worldW, fenceTop + 200f)
        c.drawRect(rect, paint)

        // dog house
        val hx = worldW * 0.5f - 150f
        paint.color = 0xFF8E5F35.toInt()
        rect.set(hx, sy - 175f, hx + 300f, sy + 10f)
        c.drawRoundRect(rect, 16f, 16f, paint)
        paint.color = 0xFFB4463A.toInt()
        path.reset()
        path.moveTo(hx - 34f, sy - 165f)
        path.lineTo(hx + 150f, sy - 300f)
        path.lineTo(hx + 334f, sy - 165f)
        path.close()
        c.drawPath(path, paint)
        paint.color = 0xFF3A2A1E.toInt()
        rect.set(hx + 98f, sy - 120f, hx + 202f, sy + 10f)
        c.drawRoundRect(rect, 52f, 52f, paint)

        // grass
        paint.color = 0xFF63A662.toInt()
        c.drawRect(0f, sy, worldW, Tuning.VIEW_H + 200f, paint)
        paint.color = 0xFF7FC25C.toInt()
        var gx = 0f
        var i = 0
        while (gx < worldW) {
            val h = 26f + Hash.f(i, 151) * 34f
            path.reset()
            path.moveTo(gx, sy + 6f)
            path.quadTo(gx + 10f, sy - h * 0.6f, gx + 20f + Hash.f(i, 153) * 10f, sy - h)
            path.quadTo(gx + 18f, sy - h * 0.4f, gx + 26f, sy + 6f)
            path.close()
            c.drawPath(path, paint)
            gx += 34f
            i++
        }
    }
}
