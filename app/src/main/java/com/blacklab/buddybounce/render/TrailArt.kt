package com.blacklab.buddybounce.render

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import com.blacklab.buddybounce.data.Trails
import com.blacklab.buddybounce.game.MathX.clamp01
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.round
import kotlin.math.sin

/**
 * One drawing per trail. Forty of them, and no two share a shape.
 *
 * [Fx] owns the particles and their motion; this owns what they look like. Every function gets
 * the same contract:
 *
 * * the origin is where the particle is, and the caller has applied nothing else;
 * * `k` runs 1 at birth down to 0 at death, so a shape can shrink, swell, rotate or break apart
 *   off that one number;
 * * `col` is already blended from the trail's hot colour to its cool one by age, and `accent`
 *   is the trail's third colour for cores, bands and spatter.
 *
 * Shapes stay small and readable at gameplay scale - the whole point of a trail is that it reads
 * in peripheral vision while you are looking at a platform somewhere else.
 */
class TrailArt(private val art: Art) {

    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val path = Path()
    private val r = RectF()

    fun draw(
        c: Canvas, style: Int, x: Float, y: Float, size: Float, rot: Float,
        col: Int, accent: Int, k: Float
    ) {
        val a = clamp01(k)
        c.save()
        c.translate(x, y)
        when (style) {
            Trails.Style.FLAME -> flame(c, size, col, accent, a)
            Trails.Style.EMBER -> ember(c, size, rot, col, accent, a)
            Trails.Style.MAGMA -> magma(c, size, col, accent, a)
            Trails.Style.SOLAR -> solar(c, size, rot, col, accent, a)
            Trails.Style.PHOENIX -> phoenix(c, size, rot, col, accent, a)

            Trails.Style.WAVE -> wave(c, size, rot, col, accent, a)
            Trails.Style.BUBBLE -> bubble(c, size, col, accent, a)
            Trails.Style.SPLASH -> splash(c, size, rot, col, accent, a)
            Trails.Style.TIDE -> tide(c, size, rot, col, accent, a)
            Trails.Style.ABYSS -> abyss(c, size, col, accent, a)

            Trails.Style.LEAF -> leaf(c, size, rot, col, accent, a)
            Trails.Style.PETAL -> petal(c, size, rot, col, accent, a)
            Trails.Style.CLOVER -> clover(c, size, rot, col, accent, a)
            Trails.Style.POLLEN -> pollen(c, size, col, accent, a)
            Trails.Style.VINE -> vine(c, size, rot, col, accent, a)
            Trails.Style.MUD -> mud(c, size, rot, col, accent, a)

            Trails.Style.SNOW -> snow(c, size, rot, col, accent, a)
            Trails.Style.FROST -> frost(c, size, rot, col, accent, a)
            Trails.Style.STORM -> storm(c, size, col, accent, a)
            Trails.Style.CLOUD -> cloud(c, size, col, accent, a)
            Trails.Style.MIST -> mist(c, size, col, a)

            Trails.Style.RAINBOW -> rainbow(c, size, rot, col, accent, a)
            Trails.Style.STAR -> star(c, size, rot, col, accent, a)
            Trails.Style.COMET -> comet(c, size, rot, col, accent, a)
            Trails.Style.NEBULA -> nebula(c, size, rot, col, accent, a)
            Trails.Style.AURORA -> aurora(c, size, col, accent, a)
            Trails.Style.VOID -> voidHole(c, size, col, accent, a)
            Trails.Style.MOON -> moon(c, size, rot, col, accent, a)

            Trails.Style.NEON -> neon(c, size, rot, col, accent, a)
            Trails.Style.PIXEL -> pixel(c, x, y, size, col, a)
            Trails.Style.GLITCH -> glitch(c, size, rot, col, accent, a)
            Trails.Style.CIRCUIT -> circuit(c, size, rot, col, accent, a)
            Trails.Style.LASER -> laser(c, size, rot, col, accent, a)
            Trails.Style.HOLOGRAM -> hologram(c, size, col, accent, a)

            Trails.Style.BONE -> bone(c, size, rot, col, accent, a)
            Trails.Style.PAW -> paw(c, size, rot, col, accent, a)
            Trails.Style.GUM -> gum(c, size, col, accent, a)
            Trails.Style.CONFETTI -> confetti(c, size, rot, col, accent, a)
            Trails.Style.HEART -> heart(c, size, rot, col, accent, a)
            Trails.Style.BLAST -> blast(c, size, rot, col, accent, a)
            Trails.Style.GLORY -> glory(c, size, rot, col, accent, a)
            else -> note(c, size, rot, col, accent, a)
        }
        c.restore()
    }

    // -- paint helpers ---------------------------------------------------------------------

    private fun fill(color: Int, a: Float) {
        p.reset(); p.isAntiAlias = true
        p.style = Paint.Style.FILL
        p.color = ColorX.withAlpha(color, a)
    }

    private fun line(color: Int, a: Float, w: Float) {
        p.reset(); p.isAntiAlias = true
        p.style = Paint.Style.STROKE
        p.strokeCap = Paint.Cap.ROUND
        p.strokeJoin = Paint.Join.ROUND
        p.strokeWidth = w
        p.color = ColorX.withAlpha(color, a)
    }

    private fun glow(c: Canvas, size: Float, color: Int, a: Float) =
        art.draw(c, art.softGlow, 0f, 0f, size, size, a, color)

    // =====================================================================================
    // fire
    // =====================================================================================

    /** A tongue of fire: broad at the base, pinched to a tip, with a paler core inside it. */
    private fun flame(c: Canvas, s: Float, col: Int, accent: Int, a: Float) {
        glow(c, s * 2.6f, col, a * 0.45f)
        val h = s * (0.5f + 0.5f * a)
        fill(col, a)
        path.reset()
        path.moveTo(0f, h * 0.7f)
        path.cubicTo(-s * 0.45f, h * 0.3f, -s * 0.3f, -h * 0.3f, 0f, -h)
        path.cubicTo(s * 0.3f, -h * 0.3f, s * 0.45f, h * 0.3f, 0f, h * 0.7f)
        path.close()
        c.drawPath(path, p)
        // the white-hot heart, which lags the tip
        fill(accent, a * 0.85f)
        path.reset()
        path.moveTo(0f, h * 0.5f)
        path.cubicTo(-s * 0.2f, h * 0.2f, -s * 0.14f, -h * 0.1f, 0f, -h * 0.45f)
        path.cubicTo(s * 0.14f, -h * 0.1f, s * 0.2f, h * 0.2f, 0f, h * 0.5f)
        path.close()
        c.drawPath(path, p)
    }

    /** A chunk of char - angular, opaque, with the fire showing through its cracks. */
    private fun ember(c: Canvas, s: Float, rot: Float, col: Int, accent: Int, a: Float) {
        c.rotate(rot * 57.29578f)
        val h = s * 0.34f
        fill(col, a)
        path.reset()
        path.moveTo(-h, -h * 0.55f)
        path.lineTo(-h * 0.35f, -h)
        path.lineTo(h * 0.8f, -h * 0.6f)
        path.lineTo(h, h * 0.45f)
        path.lineTo(h * 0.1f, h)
        path.lineTo(-h * 0.85f, h * 0.5f)
        path.close()
        c.drawPath(path, p)
        // glowing splits - brightest while it is young, gone as it cools
        line(accent, a * a, h * 0.3f)
        c.drawLine(-h * 0.55f, -h * 0.35f, h * 0.2f, h * 0.15f, p)
        c.drawLine(h * 0.15f, -h * 0.5f, h * 0.45f, h * 0.05f, p)
    }

    /** A heavy drop of melt: sagging, with a dark skin forming over the top of it. */
    private fun magma(c: Canvas, s: Float, col: Int, accent: Int, a: Float) {
        glow(c, s * 2.2f, accent, a * 0.4f)
        val w = s * 0.34f
        val h = s * (0.34f + (1f - a) * 0.34f)    // it stretches as it falls
        fill(accent, a)
        path.reset()
        path.moveTo(0f, -h)
        path.cubicTo(w, -h * 0.4f, w, h * 0.6f, 0f, h)
        path.cubicTo(-w, h * 0.6f, -w, -h * 0.4f, 0f, -h)
        path.close()
        c.drawPath(path, p)
        // the crust, which spreads over it as it cools
        fill(col, (1f - a) * 0.85f)
        r.set(-w * 0.8f, -h * 0.75f, w * 0.8f, h * 0.1f)
        c.drawOval(r, p)
    }

    /** A small sun: a bright disc with a corona of spikes turning around it. */
    private fun solar(c: Canvas, s: Float, rot: Float, col: Int, accent: Int, a: Float) {
        glow(c, s * 3.4f, col, a * 0.55f)
        c.rotate(rot * 34f)
        fill(col, a * 0.9f)
        for (i in 0 until 8) {
            c.save(); c.rotate(i * 45f)
            path.reset()
            path.moveTo(-s * 0.08f, -s * 0.24f)
            path.lineTo(0f, -s * (0.44f + 0.14f * sin(rot * 3f + i.toFloat())))
            path.lineTo(s * 0.08f, -s * 0.24f)
            path.close()
            c.drawPath(path, p)
            c.restore()
        }
        fill(col, a)
        c.drawCircle(0f, 0f, s * 0.26f, p)
        fill(accent, a * 0.8f)
        c.drawCircle(0f, 0f, s * 0.13f, p)
    }

    /** A feather whose trailing edge has caught. */
    private fun phoenix(c: Canvas, s: Float, rot: Float, col: Int, accent: Int, a: Float) {
        glow(c, s * 2.4f, col, a * 0.4f)
        c.rotate(rot * 57.29578f)
        fill(col, a)
        path.reset()
        path.moveTo(0f, -s * 0.55f)
        path.cubicTo(s * 0.3f, -s * 0.2f, s * 0.26f, s * 0.3f, 0f, s * 0.55f)
        path.cubicTo(-s * 0.26f, s * 0.3f, -s * 0.3f, -s * 0.2f, 0f, -s * 0.55f)
        path.close()
        c.drawPath(path, p)
        // barbs along the shaft
        line(accent, a * 0.65f, 1.6f)
        for (i in 0 until 4) {
            val fy = -s * 0.32f + i * s * 0.22f
            c.drawLine(0f, fy, s * 0.2f, fy + s * 0.1f, p)
            c.drawLine(0f, fy, -s * 0.2f, fy + s * 0.1f, p)
        }
        line(accent, a, 2.2f)
        c.drawLine(0f, -s * 0.55f, 0f, s * 0.55f, p)
    }

    // =====================================================================================
    // water
    // =====================================================================================

    /** A breaking crest: the curl of the wave with foam along its lip. */
    private fun wave(c: Canvas, s: Float, rot: Float, col: Int, accent: Int, a: Float) {
        c.rotate(rot * 57.29578f)
        fill(col, a * 0.9f)
        path.reset()
        path.moveTo(-s * 0.7f, s * 0.18f)
        path.cubicTo(-s * 0.3f, -s * 0.34f, s * 0.2f, -s * 0.4f, s * 0.62f, -s * 0.02f)
        path.cubicTo(s * 0.3f, -s * 0.14f, -s * 0.1f, s * 0.06f, -s * 0.7f, s * 0.18f)
        path.close()
        c.drawPath(path, p)
        // foam on the lip
        fill(accent, a * 0.75f)
        c.drawCircle(s * 0.52f, -s * 0.06f, s * 0.11f, p)
        c.drawCircle(s * 0.3f, -s * 0.18f, s * 0.08f, p)
        c.drawCircle(s * 0.1f, -s * 0.2f, s * 0.06f, p)
    }

    /** A sphere of air that swells, thins, and finally breaks into a ring of fragments. */
    private fun bubble(c: Canvas, s: Float, col: Int, accent: Int, a: Float) {
        val rad = s * (0.24f + (1f - a) * 0.34f)
        if (a > 0.22f) {
            line(col, a * 0.9f, s * 0.07f * a + 1.2f)
            c.drawCircle(0f, 0f, rad, p)
            fill(accent, a * 0.5f)
            c.drawCircle(-rad * 0.36f, -rad * 0.36f, rad * 0.2f, p)
        } else {
            // the pop: six fragments flying off where the skin was
            val burst = (0.22f - a) / 0.22f
            fill(col, a * 4f)
            for (i in 0 until 6) {
                val ang = i * 1.047f
                c.drawCircle(
                    cos(ang) * rad * (1f + burst * 0.7f),
                    sin(ang) * rad * (1f + burst * 0.7f),
                    s * 0.05f, p
                )
            }
        }
    }

    /** One fat droplet, plus the smaller ones it threw off. */
    private fun splash(c: Canvas, s: Float, rot: Float, col: Int, accent: Int, a: Float) {
        c.rotate(rot * 57.29578f)
        fill(col, a)
        path.reset()
        path.moveTo(0f, -s * 0.46f)
        path.quadTo(s * 0.26f, 0f, 0f, s * 0.32f)
        path.quadTo(-s * 0.26f, 0f, 0f, -s * 0.46f)
        path.close()
        c.drawPath(path, p)
        fill(accent, a * 0.5f)
        c.drawCircle(-s * 0.07f, s * 0.06f, s * 0.07f, p)
        // satellites, thrown further as it ages
        fill(col, a * 0.75f)
        val d = s * (0.3f + (1f - a) * 0.4f)
        c.drawCircle(d, -d * 0.5f, s * 0.08f, p)
        c.drawCircle(-d * 0.8f, -d * 0.7f, s * 0.06f, p)
        c.drawCircle(d * 0.4f, d * 0.7f, s * 0.05f, p)
    }

    /** A flat ripple: a long thin line that spreads outward and thins to nothing. */
    private fun tide(c: Canvas, s: Float, rot: Float, col: Int, accent: Int, a: Float) {
        c.rotate(rot * 57.29578f)
        val len = s * (0.6f + (1f - a) * 0.9f)
        line(col, a * 0.85f, s * 0.1f * a + 1f)
        path.reset()
        path.moveTo(-len, 0f)
        path.quadTo(0f, -s * 0.16f, len, 0f)
        c.drawPath(path, p)
        line(accent, a * 0.5f, s * 0.05f * a + 0.8f)
        path.reset()
        path.moveTo(-len * 0.7f, s * 0.12f)
        path.quadTo(0f, s * 0.24f, len * 0.7f, s * 0.12f)
        c.drawPath(path, p)
    }

    /** Something dark with a light on it, from a long way down. */
    private fun abyss(c: Canvas, s: Float, col: Int, accent: Int, a: Float) {
        glow(c, s * 3.0f, accent, a * 0.45f)
        fill(col, a * 0.95f)
        c.drawCircle(0f, 0f, s * 0.3f, p)
        // the pulsing band around its middle
        line(accent, a * (0.55f + 0.45f * sin(a * 12f)), s * 0.07f)
        r.set(-s * 0.34f, -s * 0.12f, s * 0.34f, s * 0.12f)
        c.drawOval(r, p)
        fill(accent, a * 0.9f)
        c.drawCircle(s * 0.12f, -s * 0.1f, s * 0.05f, p)
    }

    // =====================================================================================
    // nature
    // =====================================================================================

    /** A leaf: pointed, veined, with a serrated edge and a stem. */
    private fun leaf(c: Canvas, s: Float, rot: Float, col: Int, accent: Int, a: Float) {
        c.rotate(rot * 57.29578f)
        c.scale(1f, 0.4f + 0.6f * abs(cos(rot)))   // tumbling, so it goes edge-on
        fill(col, a)
        path.reset()
        path.moveTo(0f, -s * 0.5f)
        path.cubicTo(s * 0.34f, -s * 0.24f, s * 0.3f, s * 0.24f, 0f, s * 0.5f)
        path.cubicTo(-s * 0.3f, s * 0.24f, -s * 0.34f, -s * 0.24f, 0f, -s * 0.5f)
        path.close()
        c.drawPath(path, p)
        // midrib and side veins
        line(accent, a * 0.8f, 1.5f)
        c.drawLine(0f, -s * 0.5f, 0f, s * 0.5f, p)
        for (i in 0 until 3) {
            val vy = -s * 0.22f + i * s * 0.22f
            c.drawLine(0f, vy, s * 0.2f, vy + s * 0.1f, p)
            c.drawLine(0f, vy, -s * 0.2f, vy + s * 0.1f, p)
        }
        // stem
        line(accent, a, 2f)
        c.drawLine(0f, s * 0.5f, 0f, s * 0.66f, p)
    }

    /** A blossom petal: curved, with the notch at its wide end. */
    private fun petal(c: Canvas, s: Float, rot: Float, col: Int, accent: Int, a: Float) {
        c.rotate(rot * 57.29578f)
        c.scale(0.45f + 0.55f * abs(sin(rot * 0.8f)), 1f)
        fill(col, a)
        path.reset()
        path.moveTo(0f, s * 0.42f)                                  // the stem end
        path.cubicTo(s * 0.34f, s * 0.1f, s * 0.3f, -s * 0.3f, s * 0.1f, -s * 0.42f)
        path.quadTo(0f, -s * 0.3f, -s * 0.1f, -s * 0.42f)           // the notch
        path.cubicTo(-s * 0.3f, -s * 0.3f, -s * 0.34f, s * 0.1f, 0f, s * 0.42f)
        path.close()
        c.drawPath(path, p)
        fill(accent, a * 0.6f)
        r.set(-s * 0.1f, -s * 0.24f, s * 0.12f, s * 0.2f)
        c.drawOval(r, p)
    }

    /** Three lobes and a stem. Three. He checked. */
    private fun clover(c: Canvas, s: Float, rot: Float, col: Int, accent: Int, a: Float) {
        c.rotate(rot * 40f)
        line(accent, a, 2f)
        c.drawLine(0f, s * 0.1f, s * 0.06f, s * 0.5f, p)
        fill(col, a)
        for (i in 0 until 3) {
            c.save(); c.rotate(i * 120f)
            r.set(-s * 0.17f, -s * 0.42f, s * 0.17f, -s * 0.04f)
            c.drawOval(r, p)
            c.restore()
        }
        fill(accent, a * 0.7f)
        c.drawCircle(0f, 0f, s * 0.06f, p)
    }

    /** A fuzzy mote in a soft halo - the thing that makes him sneeze. */
    private fun pollen(c: Canvas, s: Float, col: Int, accent: Int, a: Float) {
        glow(c, s * 1.9f, col, a * 0.5f)
        fill(col, a * 0.9f)
        c.drawCircle(0f, 0f, s * 0.15f, p)
        // the fuzz
        line(col, a * 0.7f, 1.3f)
        for (i in 0 until 6) {
            val ang = i * 1.047f
            c.drawLine(
                cos(ang) * s * 0.13f, sin(ang) * s * 0.13f,
                cos(ang) * s * 0.26f, sin(ang) * s * 0.26f, p
            )
        }
        fill(accent, a * 0.8f)
        c.drawCircle(-s * 0.04f, -s * 0.04f, s * 0.06f, p)
    }

    /** A tendril, curling as it grows. */
    private fun vine(c: Canvas, s: Float, rot: Float, col: Int, accent: Int, a: Float) {
        c.rotate(rot * 30f)
        line(col, a, 2.6f)
        path.reset()
        path.moveTo(-s * 0.4f, s * 0.24f)
        path.cubicTo(-s * 0.1f, s * 0.1f, s * 0.24f, -s * 0.1f, s * 0.14f, -s * 0.3f)
        path.cubicTo(s * 0.06f, -s * 0.44f, -s * 0.1f, -s * 0.36f, -s * 0.04f, -s * 0.22f)
        c.drawPath(path, p)
        // a little pair of leaves off the stem
        fill(accent, a * 0.9f)
        r.set(-s * 0.3f, s * 0.02f, -s * 0.12f, s * 0.16f)
        c.drawOval(r, p)
        r.set(s * 0.04f, -s * 0.08f, s * 0.22f, s * 0.04f)
        c.drawOval(r, p)
    }

    /** A splat, with spatter round it. Somebody is getting a bath. */
    private fun mud(c: Canvas, s: Float, rot: Float, col: Int, accent: Int, a: Float) {
        c.rotate(rot * 57.29578f)
        fill(col, a)
        path.reset()
        path.moveTo(-s * 0.3f, -s * 0.12f)
        path.cubicTo(-s * 0.2f, -s * 0.34f, s * 0.16f, -s * 0.34f, s * 0.26f, -s * 0.1f)
        path.cubicTo(s * 0.4f, s * 0.06f, s * 0.14f, s * 0.3f, -s * 0.06f, s * 0.24f)
        path.cubicTo(-s * 0.28f, s * 0.2f, -s * 0.38f, s * 0.04f, -s * 0.3f, -s * 0.12f)
        path.close()
        c.drawPath(path, p)
        fill(accent, a * 0.85f)
        val d = s * (0.32f + (1f - a) * 0.22f)
        c.drawCircle(d, -d * 0.6f, s * 0.07f, p)
        c.drawCircle(-d * 0.9f, d * 0.4f, s * 0.055f, p)
        c.drawCircle(d * 0.2f, d * 0.8f, s * 0.045f, p)
    }

    // =====================================================================================
    // weather
    // =====================================================================================

    /** A six-spoke crystal, each spoke with a pair of branchlets. */
    private fun snow(c: Canvas, s: Float, rot: Float, col: Int, accent: Int, a: Float) {
        c.rotate(rot * 57.29578f)
        line(col, a, s * 0.06f + 0.9f)
        val len = s * 0.4f
        for (i in 0 until 6) {
            c.save(); c.rotate(i * 60f)
            c.drawLine(0f, 0f, 0f, -len, p)
            c.drawLine(0f, -len * 0.55f, -len * 0.22f, -len * 0.78f, p)
            c.drawLine(0f, -len * 0.55f, len * 0.22f, -len * 0.78f, p)
            c.restore()
        }
        fill(accent, a)
        c.drawCircle(0f, 0f, s * 0.07f, p)
    }

    /** A flat hexagonal shard of ice, drawn as an outline so it reads as glass. */
    private fun frost(c: Canvas, s: Float, rot: Float, col: Int, accent: Int, a: Float) {
        c.rotate(rot * 45f)
        val rad = s * 0.34f
        path.reset()
        for (i in 0 until 6) {
            val ang = i * 1.047f
            val px = cos(ang) * rad
            val py = sin(ang) * rad * 0.82f
            if (i == 0) path.moveTo(px, py) else path.lineTo(px, py)
        }
        path.close()
        fill(col, a * 0.3f)
        c.drawPath(path, p)
        line(col, a, 1.8f)
        c.drawPath(path, p)
        line(accent, a * 0.7f, 1.2f)
        c.drawLine(-rad * 0.5f, 0f, rad * 0.5f, 0f, p)
    }

    /** A small dark cloud with a bolt coming out from under it. */
    private fun storm(c: Canvas, s: Float, col: Int, accent: Int, a: Float) {
        fill(col, a * 0.95f)
        c.drawCircle(-s * 0.18f, -s * 0.1f, s * 0.16f, p)
        c.drawCircle(s * 0.02f, -s * 0.18f, s * 0.19f, p)
        c.drawCircle(s * 0.2f, -s * 0.1f, s * 0.15f, p)
        r.set(-s * 0.3f, -s * 0.14f, s * 0.32f, s * 0.04f)
        c.drawRoundRect(r, s * 0.09f, s * 0.09f, p)
        // the bolt strikes for the first half of the particle's life only
        if (a > 0.45f) {
            fill(accent, (a - 0.45f) * 1.8f)
            path.reset()
            path.moveTo(-s * 0.02f, s * 0.02f)
            path.lineTo(-s * 0.16f, s * 0.3f)
            path.lineTo(-s * 0.02f, s * 0.26f)
            path.lineTo(-s * 0.08f, s * 0.54f)
            path.lineTo(s * 0.18f, s * 0.2f)
            path.lineTo(s * 0.03f, s * 0.24f)
            path.lineTo(s * 0.12f, s * 0.02f)
            path.close()
            c.drawPath(path, p)
        }
    }

    /** Three lumps of cumulus, swelling as it drifts. */
    private fun cloud(c: Canvas, s: Float, col: Int, accent: Int, a: Float) {
        val g = 0.8f + (1f - a) * 0.5f
        glow(c, s * 2.2f * g, col, a * 0.3f)
        fill(col, a * 0.55f)
        c.drawCircle(-s * 0.2f * g, s * 0.04f, s * 0.2f * g, p)
        c.drawCircle(s * 0.04f * g, -s * 0.1f * g, s * 0.26f * g, p)
        c.drawCircle(s * 0.26f * g, s * 0.04f, s * 0.18f * g, p)
        fill(accent, a * 0.35f)
        c.drawCircle(-s * 0.06f * g, -s * 0.18f * g, s * 0.14f * g, p)
    }

    /** A low band of fog, stretching sideways as it thins out. */
    private fun mist(c: Canvas, s: Float, col: Int, a: Float) {
        val w = s * (0.5f + (1f - a) * 1.1f)
        val h = s * 0.16f * a + 1.5f
        glow(c, s * 2.4f, col, a * 0.22f)
        fill(col, a * 0.28f)
        r.set(-w, -h, w, h)
        c.drawRoundRect(r, h, h, p)
        r.set(-w * 0.6f, -h * 1.7f, w * 0.5f, h * 0.4f)
        c.drawRoundRect(r, h, h, p)
    }

    // =====================================================================================
    // light and space
    // =====================================================================================

    /** All of it at once: a banded arc, hot on the outside, cool on the inside. */
    private fun rainbow(c: Canvas, s: Float, rot: Float, col: Int, accent: Int, a: Float) {
        c.rotate(rot * 57.29578f)
        val bands = 5
        for (i in 0 until bands) {
            val t = i / (bands - 1f)
            // the trail's three colours, mixed across the arc
            val band = if (t < 0.5f) ColorX.lerp(col, accent, t * 2f)
            else ColorX.lerp(accent, 0xFF4A9BFF.toInt(), (t - 0.5f) * 2f)
            line(band, a * 0.9f, s * 0.09f)
            val rad = s * (0.42f - i * 0.07f)
            r.set(-rad, -rad * 0.72f, rad, rad * 0.72f)
            c.drawArc(r, 200f, 140f, false, p)
        }
    }

    /** A five-pointed star, turning. */
    private fun star(c: Canvas, s: Float, rot: Float, col: Int, accent: Int, a: Float) {
        glow(c, s * 2.0f, col, a * 0.35f)
        c.rotate(rot * 40f)
        val outer = s * 0.42f * (0.6f + 0.4f * a)
        val inner = outer * 0.42f
        path.reset()
        for (i in 0 until 10) {
            val rad = if (i % 2 == 0) outer else inner
            val ang = i * 0.6283f - 1.5708f
            val px = cos(ang) * rad
            val py = sin(ang) * rad
            if (i == 0) path.moveTo(px, py) else path.lineTo(px, py)
        }
        path.close()
        fill(col, a)
        c.drawPath(path, p)
        fill(accent, a * 0.7f)
        c.drawCircle(0f, 0f, inner * 0.5f, p)
    }

    /** A bright head dragging a tail behind it, lying along the direction of travel. */
    private fun comet(c: Canvas, s: Float, rot: Float, col: Int, accent: Int, a: Float) {
        c.rotate(rot * 57.29578f)
        val len = s * (0.7f + a * 0.7f)
        fill(accent, a * 0.5f)
        path.reset()
        path.moveTo(s * 0.18f, -s * 0.15f)
        path.lineTo(-len, 0f)
        path.lineTo(s * 0.18f, s * 0.15f)
        path.close()
        c.drawPath(path, p)
        glow(c, s * 1.6f, col, a * 0.6f)
        fill(col, a)
        c.drawCircle(s * 0.14f, 0f, s * 0.17f, p)
    }

    /** Two overlapping clouds of gas, with the odd star lit inside them. */
    private fun nebula(c: Canvas, s: Float, rot: Float, col: Int, accent: Int, a: Float) {
        val g = 0.85f + (1f - a) * 0.4f
        glow(c, s * 2.8f * g, col, a * 0.35f)
        fill(col, a * 0.26f)
        c.save(); c.rotate(rot * 20f)
        r.set(-s * 0.46f * g, -s * 0.26f * g, s * 0.3f * g, s * 0.3f * g)
        c.drawOval(r, p)
        r.set(-s * 0.22f * g, -s * 0.34f * g, s * 0.46f * g, s * 0.2f * g)
        c.drawOval(r, p)
        c.restore()
        fill(accent, a * 0.9f)
        c.drawCircle(-s * 0.16f, -s * 0.06f, 1.7f, p)
        c.drawCircle(s * 0.14f, -s * 0.16f, 1.4f, p)
        c.drawCircle(s * 0.06f, s * 0.14f, 1.2f, p)
    }

    /** A hanging curtain of light with a rippled lower edge. */
    private fun aurora(c: Canvas, s: Float, col: Int, accent: Int, a: Float) {
        glow(c, s * 2.2f, col, a * 0.3f)
        val w = s * 0.4f
        val h = s * 0.62f * a
        fill(col, a * 0.5f)
        path.reset()
        path.moveTo(-w, -h)
        path.lineTo(w, -h)
        path.cubicTo(w * 0.5f, h * 0.5f, w * 0.4f, h * 0.2f, w * 0.2f, h)
        path.cubicTo(0f, h * 0.4f, -w * 0.3f, h * 0.8f, -w * 0.6f, h * 0.3f)
        path.close()
        c.drawPath(path, p)
        // the bright hem along the top
        fill(accent, a * 0.7f)
        r.set(-w, -h, w, -h + s * 0.09f)
        c.drawRect(r, p)
    }

    /** A hole: dark inside, bright at the rim, pulling itself shut. */
    private fun voidHole(c: Canvas, s: Float, col: Int, accent: Int, a: Float) {
        val rad = s * 0.34f * a                    // it collapses rather than fading
        glow(c, s * 2.0f, col, a * 0.4f)
        fill(0xFF000000.toInt(), a)
        c.drawCircle(0f, 0f, rad, p)
        line(col, a, s * 0.07f)
        c.drawCircle(0f, 0f, rad * 1.18f, p)
        line(accent, a * 0.6f, s * 0.03f)
        c.drawCircle(0f, 0f, rad * 1.45f, p)
    }

    /** A crescent, cut from a disc. */
    private fun moon(c: Canvas, s: Float, rot: Float, col: Int, accent: Int, a: Float) {
        glow(c, s * 1.8f, col, a * 0.35f)
        c.rotate(rot * 25f)
        val rad = s * 0.3f
        path.reset()
        path.addCircle(0f, 0f, rad, Path.Direction.CW)
        path.addCircle(rad * 0.52f, -rad * 0.2f, rad * 0.88f, Path.Direction.CCW)
        fill(col, a)
        c.drawPath(path, p)
        fill(accent, a * 0.55f)
        c.drawCircle(-rad * 0.46f, rad * 0.24f, rad * 0.14f, p)
    }

    // =====================================================================================
    // neon and tech
    // =====================================================================================

    /** A segment of glass tubing with a white core, the way a neon sign actually looks. */
    private fun neon(c: Canvas, s: Float, rot: Float, col: Int, accent: Int, a: Float) {
        c.rotate(rot * 57.29578f)
        val len = s * 0.5f
        glow(c, s * 2.0f, col, a * 0.5f)
        line(col, a * 0.85f, s * 0.24f)
        c.drawLine(-len, 0f, len, 0f, p)
        line(accent, a * 0.95f, s * 0.08f)
        c.drawLine(-len, 0f, len, 0f, p)
    }

    /** A hard square, snapped to a coarse grid and never rotated. */
    private fun pixel(c: Canvas, worldX: Float, worldY: Float, s: Float, col: Int, a: Float) {
        // Snapping happens in world space, so the grid stays put while the camera moves.
        val grid = 11f
        val ox = round(worldX / grid) * grid - worldX
        val oy = round(worldY / grid) * grid - worldY
        val half = s * 0.22f * (0.5f + 0.5f * a)
        fill(col, a)
        r.set(ox - half, oy - half, ox + half, oy + half)
        c.drawRect(r, p)
    }

    /** The three channels pulling apart, the way a broken frame does. */
    private fun glitch(c: Canvas, s: Float, rot: Float, col: Int, accent: Int, a: Float) {
        val split = s * 0.1f * (1f - a) + s * 0.04f
        val jitter = sin(rot * 40f) * s * 0.06f
        val w = s * 0.3f
        val h = s * 0.12f
        fill(col, a * 0.75f)
        r.set(-w - split, -h + jitter, w - split, h + jitter)
        c.drawRect(r, p)
        fill(accent, a * 0.75f)
        r.set(-w + split, -h - jitter, w + split, h - jitter)
        c.drawRect(r, p)
        fill(0xFFFFFFFF.toInt(), a * 0.5f)
        r.set(-w * 0.6f, -h * 0.4f, w * 0.6f, h * 0.4f)
        c.drawRect(r, p)
    }

    /** A right-angled trace with a via soldered at the corner. */
    private fun circuit(c: Canvas, s: Float, rot: Float, col: Int, accent: Int, a: Float) {
        c.rotate(round(rot * 2f) * 90f)          // traces only run at right angles
        line(col, a, s * 0.08f)
        path.reset()
        path.moveTo(-s * 0.34f, -s * 0.2f)
        path.lineTo(s * 0.06f, -s * 0.2f)
        path.lineTo(s * 0.06f, s * 0.3f)
        c.drawPath(path, p)
        fill(col, a)
        c.drawCircle(s * 0.06f, -s * 0.2f, s * 0.1f, p)
        fill(accent, a * 0.9f)
        c.drawCircle(s * 0.06f, -s * 0.2f, s * 0.045f, p)
    }

    /** A thin beam with a flare where it is brightest. */
    private fun laser(c: Canvas, s: Float, rot: Float, col: Int, accent: Int, a: Float) {
        c.rotate(rot * 57.29578f)
        val len = s * 0.75f
        glow(c, s * 1.6f, col, a * 0.5f)
        line(col, a * 0.8f, s * 0.1f)
        c.drawLine(-len, 0f, len, 0f, p)
        line(accent, a, s * 0.035f)
        c.drawLine(-len, 0f, len, 0f, p)
        // the lens flare: a cross at the head
        line(accent, a * 0.7f, s * 0.03f)
        c.drawLine(len - s * 0.12f, -s * 0.16f, len - s * 0.12f, s * 0.16f, p)
    }

    /** A projected panel, cut through by scanlines. */
    private fun hologram(c: Canvas, s: Float, col: Int, accent: Int, a: Float) {
        val w = s * 0.3f
        val h = s * 0.34f
        fill(col, a * 0.32f)
        r.set(-w, -h, w, h)
        c.drawRect(r, p)
        line(col, a * 0.85f, 1.6f)
        c.drawRect(r, p)
        // scanlines march upward through it
        fill(accent, a * 0.5f)
        val step = s * 0.1f
        var y = -h + ((a * 4f) % 1f) * step
        while (y < h) {
            r.set(-w, y, w, y + step * 0.28f)
            c.drawRect(r, p)
            y += step
        }
    }

    // =====================================================================================
    // treats and nonsense
    // =====================================================================================

    /** An actual bone: shaft plus four knuckles. */
    private fun bone(c: Canvas, s: Float, rot: Float, col: Int, accent: Int, a: Float) {
        c.rotate(rot * 57.29578f)
        val len = s * 0.26f
        val k = s * 0.1f
        fill(col, a)
        r.set(-len, -k * 0.55f, len, k * 0.55f)
        c.drawRoundRect(r, k * 0.4f, k * 0.4f, p)
        c.drawCircle(-len, -k * 0.6f, k, p)
        c.drawCircle(-len, k * 0.6f, k, p)
        c.drawCircle(len, -k * 0.6f, k, p)
        c.drawCircle(len, k * 0.6f, k, p)
        fill(accent, a * 0.55f)
        r.set(-len * 0.7f, -k * 0.3f, len * 0.7f, -k * 0.05f)
        c.drawRect(r, p)
    }

    /** A paw print: main pad plus four toes. */
    private fun paw(c: Canvas, s: Float, rot: Float, col: Int, accent: Int, a: Float) {
        c.rotate(rot * 20f)
        fill(col, a * 0.9f)
        r.set(-s * 0.22f, -s * 0.06f, s * 0.22f, s * 0.28f)
        c.drawOval(r, p)
        val toe = s * 0.1f
        c.drawCircle(-s * 0.2f, -s * 0.18f, toe, p)
        c.drawCircle(-s * 0.06f, -s * 0.26f, toe, p)
        c.drawCircle(s * 0.08f, -s * 0.26f, toe, p)
        c.drawCircle(s * 0.21f, -s * 0.17f, toe, p)
        fill(accent, a * 0.45f)
        r.set(-s * 0.12f, 0f, s * 0.12f, s * 0.18f)
        c.drawOval(r, p)
    }

    /** Chewing gum: a bubble that inflates, then a sticky splat where it burst. */
    private fun gum(c: Canvas, s: Float, col: Int, accent: Int, a: Float) {
        if (a > 0.3f) {
            val rad = s * (0.1f + (1f - a) * 0.4f)     // inflating
            fill(col, a * 0.85f)
            c.drawCircle(0f, 0f, rad, p)
            fill(accent, a * 0.45f)
            c.drawCircle(-rad * 0.34f, -rad * 0.34f, rad * 0.24f, p)
        } else {
            // burst: a flat blob with a couple of strings off it
            fill(col, a * 3f)
            r.set(-s * 0.3f, -s * 0.1f, s * 0.3f, s * 0.12f)
            c.drawOval(r, p)
            line(col, a * 2.4f, s * 0.05f)
            c.drawLine(-s * 0.28f, 0f, -s * 0.4f, -s * 0.12f, p)
            c.drawLine(s * 0.26f, 0f, s * 0.4f, -s * 0.08f, p)
        }
    }

    /** A rectangle of paper with a twist through it. */
    private fun confetti(c: Canvas, s: Float, rot: Float, col: Int, accent: Int, a: Float) {
        c.rotate(rot * 57.29578f)
        val w = s * 0.14f
        val h = s * 0.26f
        val twist = sin(rot * 3f)
        // the twist makes one half read as the paper's back side, which is the darker colour
        fill(col, a)
        path.reset()
        path.moveTo(-w, -h)
        path.quadTo(w * twist, -h * 0.4f, w, -h * 0.1f)
        path.lineTo(w, h * 0.1f)
        path.quadTo(w * twist, h * 0.4f, -w, h)
        path.close()
        c.drawPath(path, p)
        fill(accent, a * (0.3f + 0.4f * abs(twist)))
        r.set(-w * 0.5f, -h * 0.3f, w * 0.5f, h * 0.3f)
        c.drawRect(r, p)
    }

    /** Two lobes and a point. */
    private fun heart(c: Canvas, s: Float, rot: Float, col: Int, accent: Int, a: Float) {
        c.rotate(sin(rot) * 12f)
        val k = s * 0.24f
        fill(col, a)
        c.drawCircle(-k * 0.46f, -k * 0.3f, k * 0.52f, p)
        c.drawCircle(k * 0.46f, -k * 0.3f, k * 0.52f, p)
        path.reset()
        path.moveTo(-k * 0.95f, -k * 0.14f)
        path.lineTo(0f, k)
        path.lineTo(k * 0.95f, -k * 0.14f)
        path.close()
        c.drawPath(path, p)
        fill(accent, a * 0.6f)
        c.drawCircle(-k * 0.42f, -k * 0.42f, k * 0.17f, p)
    }

    /** A quaver: head, stem and flag. */
    private fun note(c: Canvas, s: Float, rot: Float, col: Int, accent: Int, a: Float) {
        c.rotate(sin(rot) * 14f)
        fill(col, a)
        c.save(); c.rotate(-18f)
        r.set(-s * 0.26f, -s * 0.02f, s * 0.04f, s * 0.2f)
        c.drawOval(r, p)
        c.restore()
        r.set(-s * 0.02f, -s * 0.44f, s * 0.05f, s * 0.12f)
        c.drawRect(r, p)
        path.reset()
        path.moveTo(s * 0.05f, -s * 0.44f)
        path.quadTo(s * 0.32f, -s * 0.3f, s * 0.18f, -s * 0.06f)
        path.quadTo(s * 0.24f, -s * 0.26f, s * 0.05f, -s * 0.28f)
        path.close()
        c.drawPath(path, p)
        fill(accent, a * 0.5f)
        c.drawCircle(-s * 0.13f, s * 0.07f, s * 0.05f, p)
    }

    /**
     * A beam segment with the shock ring still expanding off it.
     *
     * The look is a charged energy wave: a white-hot core, a blue sheath around it that thins as
     * it ages, and a ring perpendicular to the beam that swells outward and fades - the pressure
     * front running away from the shot. It lies along the direction of travel (STREAK motion),
     * so climbing leaves a beam pointing back down at where he came from.
     */
    private fun blast(c: Canvas, s: Float, rot: Float, col: Int, accent: Int, a: Float) {
        c.rotate(rot * 57.29578f)
        glow(c, s * 3.6f, col, a * 0.6f)

        // the sheath, fattest at the head and tapering back down the beam
        val len = s * 0.95f
        val fat = s * 0.3f * (0.45f + 0.55f * a)
        fill(col, a * 0.75f)
        path.reset()
        path.moveTo(len, 0f)
        path.cubicTo(len * 0.3f, -fat, -len * 0.4f, -fat * 0.7f, -len, 0f)
        path.cubicTo(-len * 0.4f, fat * 0.7f, len * 0.3f, fat, len, 0f)
        path.close()
        c.drawPath(path, p)

        // the white-hot core
        fill(accent, a * 0.95f)
        path.reset()
        path.moveTo(len * 0.92f, 0f)
        path.cubicTo(len * 0.2f, -fat * 0.36f, -len * 0.4f, -fat * 0.26f, -len * 0.9f, 0f)
        path.cubicTo(-len * 0.4f, fat * 0.26f, len * 0.2f, fat * 0.36f, len * 0.92f, 0f)
        path.close()
        c.drawPath(path, p)

        // the shock ring, swelling and thinning as the particle ages
        val ring = s * (0.22f + (1f - a) * 0.72f)
        line(accent, a * a * 0.85f, s * 0.09f * a + 1f)
        r.set(-s * 0.14f, -ring, s * 0.14f, ring)
        c.drawOval(r, p)
    }

    /**
     * Glory Beam: a shaft of golden light standing upright, brightest at its core, with motes
     * riding up it and a soft flare where it meets the ground.
     *
     * It is the only trail built from a straight vertical - everything else in the catalogue
     * tumbles, drifts or streaks - which is what makes a column of it read as light falling on
     * him rather than as sparks coming off him.
     */
    private fun glory(c: Canvas, size: Float, rot: Float, col: Int, accent: Int, a: Float) {
        val hgt = size * 3.4f
        val halfW = size * 0.62f

        // the outer shaft, widening as it falls
        p.reset(); p.isAntiAlias = true
        p.color = ColorX.withAlpha(accent, a * 0.30f)
        path.reset()
        path.moveTo(-halfW * 0.34f, -hgt)
        path.lineTo(halfW * 0.34f, -hgt)
        path.lineTo(halfW, hgt * 0.30f)
        path.lineTo(-halfW, hgt * 0.30f)
        path.close()
        c.drawPath(path, p)

        // the core, narrower and near-white
        p.color = ColorX.withAlpha(col, a * 0.72f)
        path.reset()
        path.moveTo(-halfW * 0.17f, -hgt)
        path.lineTo(halfW * 0.17f, -hgt)
        path.lineTo(halfW * 0.44f, hgt * 0.24f)
        path.lineTo(-halfW * 0.44f, hgt * 0.24f)
        path.close()
        c.drawPath(path, p)
        p.color = ColorX.withAlpha(0xFFFFFFFF.toInt(), a * 0.55f)
        path.reset()
        path.moveTo(-halfW * 0.07f, -hgt * 0.94f)
        path.lineTo(halfW * 0.07f, -hgt * 0.94f)
        path.lineTo(halfW * 0.18f, hgt * 0.18f)
        path.lineTo(-halfW * 0.18f, hgt * 0.18f)
        path.close()
        c.drawPath(path, p)

        // motes riding up the beam, each a four-point sparkle rather than a dot
        for (i in 0 until 5) {
            val t = ((rot * 0.16f + i * 0.2f) % 1f)
            val my = hgt * 0.24f - t * hgt * 1.15f
            val mx = sin(rot * 1.6f + i * 2.1f) * halfW * 0.5f * (1f - t)
            val ms = size * (0.30f - t * 0.16f)
            if (ms <= 0f) continue
            p.color = ColorX.withAlpha(0xFFFFFFFF.toInt(), a * (1f - t) * 0.9f)
            path.reset()
            path.moveTo(mx, my - ms)
            path.quadTo(mx + ms * 0.22f, my - ms * 0.22f, mx + ms, my)
            path.quadTo(mx + ms * 0.22f, my + ms * 0.22f, mx, my + ms)
            path.quadTo(mx - ms * 0.22f, my + ms * 0.22f, mx - ms, my)
            path.quadTo(mx - ms * 0.22f, my - ms * 0.22f, mx, my - ms)
            path.close()
            c.drawPath(path, p)
        }

        // the flare where it lands
        p.color = ColorX.withAlpha(accent, a * 0.42f)
        r.set(-halfW * 1.25f, hgt * 0.16f, halfW * 1.25f, hgt * 0.40f)
        c.drawOval(r, p)
        p.color = ColorX.withAlpha(0xFFFFFFFF.toInt(), a * 0.34f)
        r.set(-halfW * 0.52f, hgt * 0.21f, halfW * 0.52f, hgt * 0.33f)
        c.drawOval(r, p)
    }
}
