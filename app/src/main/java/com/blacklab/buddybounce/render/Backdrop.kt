package com.blacklab.buddybounce.render

import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import com.blacklab.buddybounce.game.Hash
import com.blacklab.buddybounce.game.MathX.clamp01
import com.blacklab.buddybounce.game.Tuning
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.sin

/**
 * Sky, parallax layers and the per-biome flourishes. Everything repeats on a band so the
 * backdrop is infinite, consecutive biomes cross-fade, and each scene gets its own silhouettes.
 */
class Backdrop(private val art: Art) {

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val ink = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val path = Path()
    private val rect = RectF()

    private var skyShader: Shader? = null
    private var skyKeyBiome = -1
    private var skyKeyBlend = -1f
    private var skyKeyHeight = -1f

    private var vignette: Shader? = null
    private var vignetteW = -1f

    fun draw(c: Canvas, worldW: Float, camY: Float, time: Float, biome: Int, blend: Float) {
        val pal = Palettes.get(biome)
        val next = Palettes.get(biome + 1)
        val h = Tuning.VIEW_H

        drawSky(c, worldW, h, biome, blend, pal, next)
        drawStars(c, worldW, camY, time, lerpF(pal.starAlpha, next.starAlpha, blend))
        drawCelestial(c, worldW, camY, biome, blend, pal, next)

        drawFlourish(c, worldW, camY, time, pal, 1f - blend)
        if (blend > 0.004f) drawFlourish(c, worldW, camY, time, next, blend)

        drawClouds(c, worldW, camY, time, lerpF(pal.cloudAlpha, next.cloudAlpha, blend), pal, next, blend)
    }

    /** A soft darkening at the edges - cheap, and it stops the screen looking like flat paper. */
    fun drawVignette(c: Canvas, worldW: Float, strength: Float) {
        if (strength <= 0.01f) return
        val h = Tuning.VIEW_H
        if (vignette == null || vignetteW != worldW) {
            vignette = RadialGradient(
                worldW * 0.5f, h * 0.5f, maxOf(worldW, h) * 0.72f,
                intArrayOf(0x00000000, 0x00000000, 0x66000000),
                floatArrayOf(0f, 0.58f, 1f),
                Shader.TileMode.CLAMP
            )
            vignetteW = worldW
        }
        paint.reset()
        paint.shader = vignette
        paint.alpha = (strength * 255f).toInt().coerceIn(0, 255)
        c.drawRect(0f, 0f, worldW, h, paint)
        paint.shader = null
        paint.alpha = 255
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
        for (k in 0..2) {
            val idx = i0 + k
            val baseScreenY = Tuning.VIEW_H - (idx * band - camY * p)
            for (i in 0 until 46) {
                val sx = Hash.f(idx * 97 + i, 11) * worldW
                val sy = baseScreenY - Hash.f(idx * 97 + i, 23) * band
                if (sy < -40f || sy > Tuning.VIEW_H + 40f) continue
                val tw = 0.55f + 0.45f * sin(time * (1.1f + Hash.f(i, 31) * 2.2f) + Hash.f(i, 41) * 6.28f)
                val r = 2.6f + Hash.f(idx * 97 + i, 53) * 5.4f
                paint.color = ColorX.withAlpha(0xFFFFFFFF.toInt(), alpha * tw * (0.5f + Hash.f(i, 61) * 0.5f))
                c.drawCircle(sx, sy, r, paint)
                if (r > 6.4f) art.draw(c, art.glow, sx, sy, r * 9f, r * 9f, alpha * tw * 0.35f)
            }
        }
    }

    private fun drawCelestial(
        c: Canvas, worldW: Float, camY: Float, biome: Int, blend: Float,
        pal: BiomePalette, next: BiomePalette
    ) {
        val p = 0.035f
        val band = Tuning.VIEW_H * 4.2f
        val i0 = floor((camY * p) / band).toInt()
        for (k in 0..1) {
            val idx = i0 + k
            val sy = Tuning.VIEW_H - (idx * band - camY * p) - band * 0.62f
            if (sy < -700f || sy > Tuning.VIEW_H + 700f) continue
            val sx = worldW * (0.18f + Hash.f(idx, 7) * 0.64f)
            val col = ColorX.lerp(pal.sunColor, next.sunColor, blend)
            val r = 170f + Hash.f(idx, 9) * 70f
            art.drawGlow(c, sx, sy, r * 3.4f, col, 0.26f)
            paint.reset(); paint.isAntiAlias = true
            paint.color = ColorX.withAlpha(col, 0.92f)
            c.drawCircle(sx, sy, r, paint)
            paint.color = ColorX.withAlpha(ColorX.shade(col, 0.88f), 0.5f)
            c.drawCircle(sx + r * 0.28f, sy + r * 0.22f, r * 0.72f, paint)
            paint.color = ColorX.withAlpha(ColorX.shade(col, 0.8f), 0.4f)
            c.drawCircle(sx - r * 0.32f, sy - r * 0.3f, r * 0.2f, paint)
            if (Hash.f(idx, 13) > 0.72f) {
                paint.style = Paint.Style.STROKE
                paint.strokeWidth = 18f
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
        cloudLayer(c, worldW, camY, 0.22f, Tuning.VIEW_H * 0.95f, 700f, alpha * 0.5f, tintColor, 3, time * 6f)
        cloudLayer(c, worldW, camY, 0.46f, Tuning.VIEW_H * 1.25f, 1180f, alpha * 0.78f, ColorX.tint(tintColor, 0.5f), 2, time * 11f)
    }

    private fun cloudLayer(
        c: Canvas, worldW: Float, camY: Float, p: Float, band: Float, size: Float,
        alpha: Float, tint: Int, perBand: Int, drift: Float
    ) {
        val i0 = floor((camY * p) / band).toInt()
        for (k in 0..2) {
            val idx = i0 + k
            val baseScreenY = Tuning.VIEW_H - (idx * band - camY * p)
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

    private fun drawFlourish(c: Canvas, worldW: Float, camY: Float, time: Float, pal: BiomePalette, alpha: Float) {
        if (alpha <= 0.01f) return
        when (pal.style) {
            BandStyle.HILLS -> hills(c, worldW, camY, pal, alpha)
            BandStyle.TREES -> trees(c, worldW, camY, pal, alpha)
            BandStyle.CLOUDS -> cloudBanks(c, worldW, camY, pal, alpha)
            BandStyle.AURORA -> aurora(c, worldW, camY, pal, alpha, time)
            BandStyle.NEBULA -> nebula(c, worldW, camY, pal, alpha)
            BandStyle.KELP -> kelp(c, worldW, camY, pal, alpha, time)
            BandStyle.REEF -> reef(c, worldW, camY, pal, alpha, time)
            BandStyle.CITY -> city(c, worldW, camY, pal, alpha, time)
            BandStyle.PEAKS -> peaks(c, worldW, camY, pal, alpha)
            else -> lava(c, worldW, camY, pal, alpha, time)
        }
    }

    /** Runs a repeating parallax band and hands each repeat's base screen Y to [body]. */
    private inline fun band(camY: Float, p: Float, height: Float, body: (idx: Int, baseY: Float) -> Unit) {
        val i0 = floor((camY * p) / height).toInt()
        for (k in 0..2) {
            val idx = i0 + k
            val baseY = Tuning.VIEW_H - (idx * height - camY * p)
            if (baseY < -height * 1.6f || baseY > Tuning.VIEW_H + height) continue
            body(idx, baseY)
        }
    }

    private fun hills(c: Canvas, worldW: Float, camY: Float, pal: BiomePalette, alpha: Float) {
        paint.reset(); paint.isAntiAlias = true
        band(camY, 0.14f, Tuning.VIEW_H * 1.1f) { idx, baseY ->
            hillRow(c, worldW, baseY, 330f, ColorX.withAlpha(ColorX.tint(pal.farShape, 0.35f), alpha * 0.55f), idx, 3)
            hillRow(c, worldW, baseY + 190f, 470f, ColorX.withAlpha(pal.midShape, alpha * 0.75f), idx + 500, 2)
        }
    }

    private fun hillRow(c: Canvas, worldW: Float, baseY: Float, h: Float, color: Int, key: Int, lobes: Int) {
        path.reset()
        path.moveTo(-40f, baseY + h)
        val step = worldW / lobes
        var x = -40f
        for (i in 0..lobes) {
            val peak = baseY - h * (0.45f + Hash.f(key * 31 + i, 71) * 0.55f)
            path.quadTo(x + step * 0.5f, peak, x + step, baseY + h * 0.2f)
            x += step
        }
        path.lineTo(worldW + 40f, baseY + h * 1.6f)
        path.lineTo(-40f, baseY + h * 1.6f)
        path.close()
        paint.color = color
        c.drawPath(path, paint)
    }

    private fun trees(c: Canvas, worldW: Float, camY: Float, pal: BiomePalette, alpha: Float) {
        paint.reset(); paint.isAntiAlias = true
        val h = Tuning.VIEW_H * 0.8f
        band(camY, 0.26f, h) { idx, baseY ->
            val fromLeft = Hash.f(idx, 83) > 0.5f
            val trunkX = if (fromLeft) -30f else worldW + 30f
            val dir = if (fromLeft) 1f else -1f
            paint.color = ColorX.withAlpha(ColorX.shade(pal.nearShape, 0.7f), alpha * 0.9f)
            rect.set(
                minOf(trunkX, trunkX + dir * 150f), baseY - h * 1.2f,
                maxOf(trunkX, trunkX + dir * 150f), baseY + h * 0.4f
            )
            c.drawRect(rect, paint)
            paint.color = ColorX.withAlpha(pal.midShape, alpha * 0.85f)
            for (i in 0 until 5) {
                val cx = trunkX + dir * (140f + Hash.f(idx * 17 + i, 91) * 420f)
                val cy = baseY - h * (0.15f + Hash.f(idx * 17 + i, 93) * 0.95f)
                c.drawCircle(cx, cy, 180f + Hash.f(idx * 17 + i, 97) * 200f, paint)
            }
            paint.color = ColorX.withAlpha(ColorX.tint(pal.farShape, 0.25f), alpha * 0.5f)
            for (i in 0 until 3) {
                val cx = trunkX + dir * (190f + Hash.f(idx * 19 + i, 101) * 340f)
                val cy = baseY - h * (0.35f + Hash.f(idx * 19 + i, 103) * 0.8f)
                c.drawCircle(cx, cy, 120f + Hash.f(idx * 19 + i, 107) * 120f, paint)
            }
        }
    }

    private fun cloudBanks(c: Canvas, worldW: Float, camY: Float, pal: BiomePalette, alpha: Float) {
        val h = Tuning.VIEW_H * 0.85f
        band(camY, 0.17f, h) { idx, baseY ->
            for (i in 0 until 2) {
                val key = idx * 37 + i
                val w = worldW * (0.55f + Hash.f(key, 111) * 0.8f)
                art.draw(
                    c, art.cloud, Hash.f(key, 113) * worldW, baseY - Hash.f(key, 117) * h,
                    w, w * 0.45f, alpha * 0.5f, pal.farShape
                )
            }
        }
    }

    private fun aurora(c: Canvas, worldW: Float, camY: Float, pal: BiomePalette, alpha: Float, time: Float) {
        paint.reset(); paint.isAntiAlias = true
        paint.style = Paint.Style.STROKE
        paint.strokeCap = Paint.Cap.ROUND
        val h = Tuning.VIEW_H * 1.2f
        band(camY, 0.12f, h) { idx, baseY ->
            for (ribbon in 0 until 3) {
                val key = idx * 53 + ribbon
                val yy = baseY - Hash.f(key, 121) * h
                val hue = if (ribbon % 2 == 0) pal.farShape else pal.midShape
                val amp = 130f + Hash.f(key, 123) * 170f
                val speed = 0.35f + Hash.f(key, 127) * 0.5f
                path.reset()
                var x = -60f
                var first = true
                while (x < worldW + 60f) {
                    val yOff = sin(x * 0.0035f + time * speed + Hash.f(key, 131) * 6f) * amp
                    if (first) { path.moveTo(x, yy + yOff); first = false } else path.lineTo(x, yy + yOff)
                    x += 52f
                }
                for (pass in 0 until 3) {
                    paint.strokeWidth = 46f + pass * 62f
                    paint.color = ColorX.withAlpha(hue, alpha * (0.22f - pass * 0.06f))
                    c.drawPath(path, paint)
                }
            }
        }
        paint.style = Paint.Style.FILL
    }

    private fun nebula(c: Canvas, worldW: Float, camY: Float, pal: BiomePalette, alpha: Float) {
        val h = Tuning.VIEW_H * 1.35f
        band(camY, 0.1f, h) { idx, baseY ->
            for (i in 0 until 3) {
                val key = idx * 71 + i
                val r = 440f + Hash.f(key, 147) * 520f
                art.draw(
                    c, art.softGlow, Hash.f(key, 141) * worldW, baseY - Hash.f(key, 143) * h,
                    r * 2f, r * 1.5f, alpha * 0.30f, if (i % 2 == 0) pal.farShape else pal.midShape
                )
            }
        }
    }

    private fun kelp(c: Canvas, worldW: Float, camY: Float, pal: BiomePalette, alpha: Float, time: Float) {
        paint.reset(); paint.isAntiAlias = true
        ink.style = Paint.Style.STROKE
        val h = Tuning.VIEW_H * 0.95f
        band(camY, 0.2f, h) { idx, baseY ->
            for (i in 0 until 5) {
                val key = idx * 29 + i
                val x = Hash.f(key, 151) * worldW
                val sway = sin(time * (0.5f + Hash.f(key, 153) * 0.5f) + key) * 60f
                val col = if (i % 2 == 0) pal.farShape else pal.midShape
                ink.color = ColorX.withAlpha(col, alpha * 0.75f)
                ink.strokeWidth = 26f + Hash.f(key, 157) * 22f
                path.reset()
                path.moveTo(x, baseY + h * 0.4f)
                path.cubicTo(
                    x + sway * 0.5f, baseY, x - sway * 0.6f, baseY - h * 0.5f,
                    x + sway, baseY - h * 1.1f
                )
                c.drawPath(path, ink)
                // fronds
                paint.color = ColorX.withAlpha(ColorX.tint(col, 0.2f), alpha * 0.5f)
                for (k in 0 until 4) {
                    val t = 0.2f + k * 0.2f
                    c.drawCircle(x + sway * t, baseY + h * 0.4f - h * 1.5f * t, 26f, paint)
                }
            }
        }
        ink.style = Paint.Style.STROKE
    }

    private fun reef(c: Canvas, worldW: Float, camY: Float, pal: BiomePalette, alpha: Float, time: Float) {
        paint.reset(); paint.isAntiAlias = true
        val h = Tuning.VIEW_H * 0.9f
        band(camY, 0.16f, h) { idx, baseY ->
            // rocky shelf
            path.reset()
            path.moveTo(-40f, baseY + h * 0.5f)
            var x = -40f
            val step = worldW / 4f
            for (i in 0..4) {
                path.quadTo(
                    x + step * 0.5f, baseY - h * (0.1f + Hash.f(idx * 13 + i, 161) * 0.35f),
                    x + step, baseY + h * 0.1f
                )
                x += step
            }
            path.lineTo(worldW + 40f, baseY + h)
            path.lineTo(-40f, baseY + h)
            path.close()
            paint.color = ColorX.withAlpha(pal.nearShape, alpha * 0.8f)
            c.drawPath(path, paint)

            // coral fans and tube corals
            for (i in 0 until 6) {
                val key = idx * 41 + i
                val cx = Hash.f(key, 163) * worldW
                val cy = baseY - Hash.f(key, 167) * h * 0.45f
                val col = if (i % 2 == 0) pal.farShape else pal.midShape
                paint.color = ColorX.withAlpha(col, alpha * 0.85f)
                if (i % 3 == 0) {
                    for (k in 0 until 5) {
                        val a = -1.2f + k * 0.6f
                        c.drawCircle(cx + sin(a) * 46f, cy - cos(a) * 46f, 26f, paint)
                    }
                } else {
                    for (k in 0 until 3) {
                        rect.set(cx - 16f + k * 18f, cy - 70f - k * 12f, cx + 2f + k * 18f, cy + 20f)
                        c.drawRoundRect(rect, 12f, 12f, paint)
                    }
                }
            }

            // drifting bubbles
            paint.color = ColorX.withAlpha(0xFFFFFFFF.toInt(), alpha * 0.28f)
            for (i in 0 until 9) {
                val key = idx * 7 + i
                val bx = Hash.f(key, 171) * worldW
                val by = baseY - ((time * 60f * (0.4f + Hash.f(key, 173))) % h)
                c.drawCircle(bx, by, 8f + Hash.f(key, 177) * 14f, paint)
            }
        }
    }

    private fun city(c: Canvas, worldW: Float, camY: Float, pal: BiomePalette, alpha: Float, time: Float) {
        paint.reset(); paint.isAntiAlias = true
        val h = Tuning.VIEW_H * 1.05f
        // far towers
        band(camY, 0.12f, h) { idx, baseY ->
            paint.color = ColorX.withAlpha(ColorX.shade(pal.nearShape, 1.25f), alpha * 0.7f)
            var x = -60f
            var i = 0
            while (x < worldW + 60f) {
                val key = idx * 97 + i
                val w = 120f + Hash.f(key, 181) * 170f
                val hh = h * (0.35f + Hash.f(key, 183) * 0.75f)
                rect.set(x, baseY - hh, x + w, baseY + h * 0.6f)
                c.drawRect(rect, paint)
                x += w + 24f
                i++
            }
        }
        // near towers with windows
        band(camY, 0.3f, h) { idx, baseY ->
            var x = -80f
            var i = 0
            while (x < worldW + 80f) {
                val key = idx * 61 + i
                val w = 150f + Hash.f(key, 191) * 210f
                val hh = h * (0.4f + Hash.f(key, 193) * 0.85f)
                paint.color = ColorX.withAlpha(pal.nearShape, alpha * 0.95f)
                rect.set(x, baseY - hh, x + w, baseY + h * 0.6f)
                c.drawRect(rect, paint)
                // a neon sign band
                paint.color = ColorX.withAlpha(if (i % 2 == 0) pal.farShape else pal.midShape, alpha * 0.85f)
                rect.set(x + 16f, baseY - hh + 30f, x + w - 16f, baseY - hh + 54f)
                c.drawRect(rect, paint)
                // windows
                val cols = ((w - 40f) / 44f).toInt().coerceAtLeast(1)
                val rows = ((hh - 110f) / 54f).toInt().coerceAtLeast(1)
                for (cx in 0 until cols) {
                    for (ry in 0 until rows) {
                        val lit = Hash.f(key * 131 + cx * 17 + ry, 197)
                        if (lit < 0.42f) continue
                        val flicker = if (lit > 0.95f) (0.4f + 0.6f * sin(time * 4f + cx + ry)) else 1f
                        paint.color = ColorX.withAlpha(pal.platAccent, alpha * 0.55f * flicker)
                        rect.set(
                            x + 24f + cx * 44f, baseY - hh + 76f + ry * 54f,
                            x + 48f + cx * 44f, baseY - hh + 108f + ry * 54f
                        )
                        c.drawRect(rect, paint)
                    }
                }
                x += w + 30f
                i++
            }
        }
    }

    private fun peaks(c: Canvas, worldW: Float, camY: Float, pal: BiomePalette, alpha: Float) {
        paint.reset(); paint.isAntiAlias = true
        val h = Tuning.VIEW_H * 1.15f
        band(camY, 0.11f, h) { idx, baseY ->
            for (layer in 0 until 2) {
                val depth = if (layer == 0) 0.55f else 0.95f
                val col = if (layer == 0) ColorX.tint(pal.farShape, 0.3f) else pal.midShape
                paint.color = ColorX.withAlpha(col, alpha * depth)
                path.reset()
                path.moveTo(-60f, baseY + h * 0.7f)
                var x = -60f
                val step = worldW / (2 + layer)
                var i = 0
                while (x < worldW + 60f) {
                    val peakH = h * (0.45f + Hash.f(idx * 23 + i + layer * 7, 201) * 0.5f)
                    path.lineTo(x + step * 0.5f, baseY + h * 0.7f - peakH)
                    path.lineTo(x + step, baseY + h * 0.7f - peakH * 0.25f)
                    x += step
                    i++
                }
                path.lineTo(worldW + 60f, baseY + h * 1.4f)
                path.lineTo(-60f, baseY + h * 1.4f)
                path.close()
                c.drawPath(path, paint)
                // snow caps
                if (layer == 1) {
                    paint.color = ColorX.withAlpha(0xFFFFFFFF.toInt(), alpha * 0.55f)
                    x = -60f
                    i = 0
                    while (x < worldW + 60f) {
                        val peakH = h * (0.45f + Hash.f(idx * 23 + i + 7, 201) * 0.5f)
                        path.reset()
                        path.moveTo(x + step * 0.5f, baseY + h * 0.7f - peakH)
                        path.lineTo(x + step * 0.5f + 60f, baseY + h * 0.7f - peakH * 0.78f)
                        path.lineTo(x + step * 0.5f - 60f, baseY + h * 0.7f - peakH * 0.78f)
                        path.close()
                        c.drawPath(path, paint)
                        x += step
                        i++
                    }
                }
            }
        }
    }

    private fun lava(c: Canvas, worldW: Float, camY: Float, pal: BiomePalette, alpha: Float, time: Float) {
        paint.reset(); paint.isAntiAlias = true
        val h = Tuning.VIEW_H * 1.0f
        band(camY, 0.15f, h) { idx, baseY ->
            // dark basalt ridge
            paint.color = ColorX.withAlpha(pal.nearShape, alpha * 0.95f)
            path.reset()
            path.moveTo(-50f, baseY + h * 0.6f)
            var x = -50f
            val step = worldW / 3f
            var i = 0
            while (x < worldW + 50f) {
                path.lineTo(x + step * 0.5f, baseY + h * 0.6f - h * (0.2f + Hash.f(idx * 19 + i, 211) * 0.42f))
                path.lineTo(x + step, baseY + h * 0.55f)
                x += step
                i++
            }
            path.lineTo(worldW + 50f, baseY + h * 1.4f)
            path.lineTo(-50f, baseY + h * 1.4f)
            path.close()
            c.drawPath(path, paint)

            // glowing cracks
            for (k in 0 until 3) {
                val gx = Hash.f(idx * 31 + k, 213) * worldW
                val gy = baseY + h * 0.35f - Hash.f(idx * 31 + k, 217) * h * 0.3f
                art.drawGlow(c, gx, gy, 260f, pal.farShape, alpha * 0.5f)
            }

            // rising embers
            for (k in 0 until 14) {
                val key = idx * 11 + k
                val ex = Hash.f(key, 221) * worldW
                val ey = baseY - ((time * 120f * (0.3f + Hash.f(key, 223))) % h)
                paint.color = ColorX.withAlpha(pal.farShape, alpha * (0.35f + 0.4f * Hash.f(key, 227)))
                c.drawCircle(ex, ey, 4f + Hash.f(key, 229) * 7f, paint)
            }
        }
    }

    // -----------------------------------------------------------------------------------
    // the ground a run starts on
    // -----------------------------------------------------------------------------------

    fun drawGround(c: Canvas, worldW: Float, viewTop: Float, groundY: Float, scene: Scene) {
        val sy = viewTop - groundY
        if (sy < -80f || sy > Tuning.VIEW_H + 900f) return
        paint.reset(); paint.isAntiAlias = true
        ink.strokeWidth = 6f
        ink.color = 0x55000000

        when (scene.groundStyle) {
            GroundStyle.YARD -> yardGround(c, worldW, sy)
            GroundStyle.SEABED -> seabedGround(c, worldW, sy)
            GroundStyle.STREET -> streetGround(c, worldW, sy)
            GroundStyle.SNOW -> snowGround(c, worldW, sy)
            else -> ashGround(c, worldW, sy)
        }
    }

    private fun slab(c: Canvas, worldW: Float, sy: Float, top: Int, body: Int) {
        paint.color = top
        c.drawRect(0f, sy, worldW, sy + 46f, paint)
        paint.color = body
        c.drawRect(0f, sy + 46f, worldW, Tuning.VIEW_H + 400f, paint)
    }

    private fun yardGround(c: Canvas, worldW: Float, sy: Float) {
        // fence
        val fenceTop = sy - 330f
        paint.color = 0xFFE4E0D2.toInt()
        var fx = 20f
        while (fx < worldW) {
            rect.set(fx, fenceTop, fx + 58f, sy + 40f)
            c.drawRoundRect(rect, 12f, 12f, paint)
            path.reset()
            path.moveTo(fx, fenceTop)
            path.lineTo(fx + 29f, fenceTop - 44f)
            path.lineTo(fx + 58f, fenceTop)
            path.close()
            c.drawPath(path, paint)
            fx += 98f
        }
        paint.color = 0xFFCFC9B6.toInt()
        c.drawRect(0f, fenceTop + 90f, worldW, fenceTop + 128f, paint)
        c.drawRect(0f, fenceTop + 220f, worldW, fenceTop + 258f, paint)

        // dog house
        val hx = worldW * 0.5f - 190f
        paint.color = 0xFF8E5F35.toInt()
        rect.set(hx, sy - 225f, hx + 380f, sy + 10f)
        c.drawRoundRect(rect, 18f, 18f, paint)
        paint.color = 0xFFB4463A.toInt()
        path.reset()
        path.moveTo(hx - 42f, sy - 212f)
        path.lineTo(hx + 190f, sy - 382f)
        path.lineTo(hx + 422f, sy - 212f)
        path.close()
        c.drawPath(path, paint)
        paint.color = 0xFF3A2A1E.toInt()
        rect.set(hx + 124f, sy - 155f, hx + 256f, sy + 10f)
        c.drawRoundRect(rect, 66f, 66f, paint)
        paint.color = 0xFFF2C14E.toInt()
        rect.set(hx + 150f, sy - 250f, hx + 230f, sy - 228f)
        c.drawRoundRect(rect, 8f, 8f, paint)

        slab(c, worldW, sy, 0xFF7FC25C.toInt(), 0xFF4E8442.toInt())
        // grass blades
        paint.color = 0xFF93D46B.toInt()
        var gx = 0f
        var i = 0
        while (gx < worldW) {
            val h = 30f + Hash.f(i, 151) * 42f
            path.reset()
            path.moveTo(gx, sy + 8f)
            path.quadTo(gx + 12f, sy - h * 0.6f, gx + 24f + Hash.f(i, 153) * 12f, sy - h)
            path.quadTo(gx + 22f, sy - h * 0.4f, gx + 32f, sy + 8f)
            path.close()
            c.drawPath(path, paint)
            gx += 42f
            i++
        }
    }

    private fun seabedGround(c: Canvas, worldW: Float, sy: Float) {
        slab(c, worldW, sy, 0xFFD9C79A.toInt(), 0xFFA89263.toInt())
        paint.color = 0xFF8C7A52.toInt()
        for (i in 0 until 14) {
            val x = Hash.f(i, 231) * worldW
            c.drawCircle(x, sy + 16f + Hash.f(i, 233) * 30f, 12f + Hash.f(i, 237) * 22f, paint)
        }
        // weed and a sunken treasure chest
        paint.color = 0xFF3E8A5A.toInt()
        for (i in 0 until 10) {
            val x = Hash.f(i, 241) * worldW
            path.reset()
            path.moveTo(x, sy + 6f)
            path.quadTo(x + 26f, sy - 80f, x + 6f, sy - 150f)
            path.quadTo(x + 34f, sy - 80f, x + 22f, sy + 6f)
            path.close()
            c.drawPath(path, paint)
        }
        val cx = worldW * 0.5f
        paint.color = 0xFF7A5230.toInt()
        rect.set(cx - 90f, sy - 96f, cx + 90f, sy + 6f)
        c.drawRoundRect(rect, 12f, 12f, paint)
        paint.color = 0xFFF2C14E.toInt()
        rect.set(cx - 90f, sy - 54f, cx + 90f, sy - 36f)
        c.drawRect(rect, paint)
    }

    private fun streetGround(c: Canvas, worldW: Float, sy: Float) {
        slab(c, worldW, sy, 0xFF3A3F55.toInt(), 0xFF23262F.toInt())
        paint.color = 0xFFF2C14E.toInt()
        var x = 20f
        while (x < worldW) {
            rect.set(x, sy + 92f, x + 90f, sy + 108f)
            c.drawRect(rect, paint)
            x += 190f
        }
        // kerb, hydrant, neon puddle glow
        paint.color = 0xFF4E566E.toInt()
        c.drawRect(0f, sy - 14f, worldW, sy + 6f, paint)
        val hx = worldW * 0.3f
        paint.color = 0xFFD8453B.toInt()
        rect.set(hx - 24f, sy - 112f, hx + 24f, sy + 4f)
        c.drawRoundRect(rect, 14f, 14f, paint)
        rect.set(hx - 42f, sy - 84f, hx + 42f, sy - 58f)
        c.drawRoundRect(rect, 10f, 10f, paint)
        art.drawGlow(c, worldW * 0.68f, sy + 40f, 320f, 0xFFFF3CAC.toInt(), 0.35f)
        art.drawGlow(c, worldW * 0.24f, sy + 70f, 260f, 0xFF4FE8FF.toInt(), 0.28f)
    }

    private fun snowGround(c: Canvas, worldW: Float, sy: Float) {
        slab(c, worldW, sy, 0xFFFFFFFF.toInt(), 0xFFD6E6F2.toInt())
        paint.color = 0xFFEAF4FF.toInt()
        for (i in 0 until 8) {
            val x = Hash.f(i, 251) * worldW
            c.drawCircle(x, sy + 10f, 60f + Hash.f(i, 253) * 70f, paint)
        }
        // pines and a snowdog
        paint.color = 0xFF2E5A4A.toInt()
        for (i in 0 until 6) {
            val x = Hash.f(i, 257) * worldW
            path.reset()
            path.moveTo(x - 50f, sy - 6f)
            path.lineTo(x, sy - 220f)
            path.lineTo(x + 50f, sy - 6f)
            path.close()
            c.drawPath(path, paint)
        }
        val cx = worldW * 0.5f
        paint.color = 0xFFFFFFFF.toInt()
        c.drawCircle(cx, sy - 40f, 56f, paint)
        c.drawCircle(cx, sy - 118f, 40f, paint)
        paint.color = 0xFFF2A03C.toInt()
        path.reset()
        path.moveTo(cx + 30f, sy - 122f)
        path.lineTo(cx + 74f, sy - 112f)
        path.lineTo(cx + 30f, sy - 106f)
        path.close()
        c.drawPath(path, paint)
    }

    private fun ashGround(c: Canvas, worldW: Float, sy: Float) {
        slab(c, worldW, sy, 0xFF4A2C22.toInt(), 0xFF2A1410.toInt())
        // lava seams
        for (i in 0 until 5) {
            val x = Hash.f(i, 261) * worldW
            art.drawGlow(c, x, sy + 50f, 220f, 0xFFFF5A1E.toInt(), 0.5f)
            paint.color = 0xFFFF7A2E.toInt()
            path.reset()
            path.moveTo(x - 70f, sy + 40f)
            path.lineTo(x - 10f, sy + 20f)
            path.lineTo(x + 40f, sy + 60f)
            path.lineTo(x + 90f, sy + 34f)
            path.lineTo(x + 90f, sy + 62f)
            path.lineTo(x - 70f, sy + 74f)
            path.close()
            c.drawPath(path, paint)
        }
        paint.color = 0xFF1A0C0A.toInt()
        for (i in 0 until 7) {
            val x = Hash.f(i, 263) * worldW
            path.reset()
            path.moveTo(x - 44f, sy + 4f)
            path.lineTo(x, sy - 150f - Hash.f(i, 267) * 90f)
            path.lineTo(x + 44f, sy + 4f)
            path.close()
            c.drawPath(path, paint)
        }
    }
}
