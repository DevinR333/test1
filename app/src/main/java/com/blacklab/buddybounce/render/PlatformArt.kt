package com.blacklab.buddybounce.render

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import com.blacklab.buddybounce.game.Hash
import com.blacklab.buddybounce.game.Platform
import kotlin.math.sin

/**
 * The thing Buddy actually lands on - one design per world, not one design per palette.
 *
 * The first pass drew a single rounded slab everywhere and changed its colours per biome, with
 * five "skins" shared out between ten worlds. It showed: the jungle and the yard were the same
 * green block, the graveyard and the neon city were the same grey one, and nothing you stood on
 * told you where you were. A platform is the single object on screen the player looks at most,
 * so each world gets its own OBJECT here - a fence plank, a lashed raft, a circuit tile, an ice
 * shelf, a lava-veined slab, a sandstone ledge, a mossy log, a chocolate bar, a coffin lid, a
 * marble step. Different silhouette, different construction, different surface.
 *
 * Each body is drawn into the rectangle (left, top) - (right, top + h) and must fill it: the
 * collision box is that rectangle regardless of what is painted, so a design that visibly falls
 * short of its own footprint would read as landing on thin air. Width varies a lot run to run,
 * so nothing here may assume a fixed number of segments - everything is derived from w.
 *
 * Colours still come from the band's [BiomePalette], so a world's own altitude bands continue to
 * shift as the player climbs; it is the construction that is fixed per world.
 */
object PlatformArt {

    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val ink = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val path = Path()
    private val r = RectF()

    fun draw(
        c: Canvas, fauna: Int, plat: Platform, pal: BiomePalette,
        left: Float, right: Float, top: Float, w: Float, h: Float,
        body: Int, topCol: Int, a: Float
    ) {
        p.reset(); p.isAntiAlias = true
        ink.color = ColorX.withAlpha(0xFF080A0F.toInt(), a * 0.75f)
        ink.strokeWidth = 5f
        when (fauna) {
            Fauna.YARD -> plank(c, plat, pal, left, right, top, w, h, body, topCol, a)
            Fauna.OCEAN -> raft(c, plat, pal, left, right, top, w, h, body, topCol, a)
            Fauna.NEON -> circuit(c, plat, pal, left, right, top, w, h, body, topCol, a)
            Fauna.FROST -> iceShelf(c, plat, pal, left, right, top, w, h, body, topCol, a)
            Fauna.EMBER -> basalt(c, plat, pal, left, right, top, w, h, body, topCol, a)
            Fauna.DESERT -> sandstone(c, plat, pal, left, right, top, w, h, body, topCol, a)
            Fauna.JUNGLE -> log(c, plat, pal, left, right, top, w, h, body, topCol, a)
            Fauna.CANDY -> chocolate(c, plat, pal, left, right, top, w, h, body, topCol, a)
            Fauna.HAUNT -> coffin(c, plat, pal, left, right, top, w, h, body, topCol, a)
            else -> marble(c, plat, pal, left, right, top, w, h, body, topCol, a)
        }
    }

    // -----------------------------------------------------------------------------------
    // shared helpers
    // -----------------------------------------------------------------------------------

    /** Fills the footprint and outlines it, so every design starts from the same solid base. */
    private fun slab(c: Canvas, l: Float, t: Float, rt: Float, b: Float, rad: Float, col: Int, a: Float) {
        p.color = ColorX.withAlpha(col, a)
        r.set(l, t, rt, b)
        c.drawRoundRect(r, rad, rad, p)
    }

    private fun outline(c: Canvas, l: Float, t: Float, rt: Float, b: Float, rad: Float) {
        r.set(l, t, rt, b)
        c.drawRoundRect(r, rad, rad, ink)
    }

    /** The lit strip along the very top edge that tells the player where the surface is. */
    private fun litEdge(c: Canvas, l: Float, t: Float, rt: Float, h: Float, col: Int, a: Float, strength: Float = 0.24f) {
        p.color = ColorX.withAlpha(ColorX.tint(col, 0.5f), a * strength)
        r.set(l + h * 0.22f, t + h * 0.05f, rt - h * 0.4f, t + h * 0.15f)
        c.drawRoundRect(r, h * 0.06f, h * 0.06f, p)
    }

    // -----------------------------------------------------------------------------------
    // the ten
    // -----------------------------------------------------------------------------------

    /** Backyard: a fence plank. Square-cut ends, visible grain, two nail heads. */
    private fun plank(
        c: Canvas, plat: Platform, pal: BiomePalette, left: Float, right: Float, top: Float,
        w: Float, h: Float, body: Int, topCol: Int, a: Float
    ) {
        slab(c, left, top + h * 0.10f, right, top + h, h * 0.14f, ColorX.shade(body, 0.7f), a)
        slab(c, left, top, right, top + h * 0.82f, h * 0.14f, body, a)
        // the sawn top face
        p.color = ColorX.withAlpha(topCol, a)
        r.set(left, top, right, top + h * 0.40f)
        c.drawRoundRect(r, h * 0.13f, h * 0.13f, p)

        // grain: long shallow lines running the length of the board
        p.color = ColorX.withAlpha(ColorX.shade(body, 0.74f), a * 0.7f)
        for (i in 0 until 3) {
            val gy = top + h * (0.48f + i * 0.16f)
            val inset = h * (0.3f + Hash.f(plat.seed + i, 61) * 0.8f)
            r.set(left + inset, gy, right - inset * 1.3f, gy + h * 0.055f)
            c.drawRoundRect(r, h * 0.03f, h * 0.03f, p)
        }
        // nail heads, one in from each end
        p.color = ColorX.withAlpha(0xFF6B7180.toInt(), a * 0.9f)
        c.drawCircle(left + h * 0.55f, top + h * 0.56f, h * 0.10f, p)
        c.drawCircle(right - h * 0.55f, top + h * 0.56f, h * 0.10f, p)
        litEdge(c, left, top, right, h, topCol, a)
        outline(c, left, top, right, top + h, h * 0.14f)
    }

    /** Ocean: three lashed driftwood logs, wet on top, with rope bindings near the ends. */
    private fun raft(
        c: Canvas, plat: Platform, pal: BiomePalette, left: Float, right: Float, top: Float,
        w: Float, h: Float, body: Int, topCol: Int, a: Float
    ) {
        slab(c, left, top + h * 0.2f, right, top + h, h * 0.3f, ColorX.shade(body, 0.62f), a)
        // logs run ACROSS the platform, so the raft reads as built rather than cast
        val n = (w / (h * 1.05f)).toInt().coerceIn(3, 14)
        val lw = w / n
        for (i in 0 until n) {
            val lx = left + i * lw
            val tint = 0.86f + Hash.f(plat.seed + i, 71) * 0.24f
            p.color = ColorX.withAlpha(ColorX.shade(body, tint), a)
            r.set(lx + lw * 0.06f, top, lx + lw * 0.94f, top + h * 0.9f)
            c.drawRoundRect(r, lw * 0.3f, lw * 0.3f, p)
            p.color = ColorX.withAlpha(ColorX.shade(topCol, tint), a)
            r.set(lx + lw * 0.06f, top, lx + lw * 0.94f, top + h * 0.34f)
            c.drawRoundRect(r, lw * 0.28f, lw * 0.28f, p)
        }
        // the rope lashing them together
        ink.strokeWidth = h * 0.13f
        ink.color = ColorX.withAlpha(0xFFC9A96A.toInt(), a * 0.95f)
        c.drawLine(left + h * 0.3f, top + h * 0.62f, right - h * 0.3f, top + h * 0.62f, ink)
        ink.color = ColorX.withAlpha(0xFF8A7146.toInt(), a * 0.8f)
        ink.strokeWidth = h * 0.06f
        c.drawLine(left + h * 0.3f, top + h * 0.70f, right - h * 0.3f, top + h * 0.70f, ink)
        // barnacles clinging to one end
        p.color = ColorX.withAlpha(ColorX.tint(pal.platAccent, 0.3f), a * 0.85f)
        for (i in 0 until 3) {
            val bx = left + h * (0.4f + i * 0.5f) + Hash.f(plat.seed + i, 73) * h * 0.3f
            c.drawCircle(bx, top + h * 0.86f, h * (0.09f + Hash.f(plat.seed + i, 79) * 0.06f), p)
        }
        ink.strokeWidth = 5f
        ink.color = ColorX.withAlpha(0xFF080A0F.toInt(), a * 0.75f)
        outline(c, left, top, right, top + h, h * 0.3f)
    }

    /** Neon: a dark circuit tile with a lit trace running through it and nodes at the corners. */
    private fun circuit(
        c: Canvas, plat: Platform, pal: BiomePalette, left: Float, right: Float, top: Float,
        w: Float, h: Float, body: Int, topCol: Int, a: Float
    ) {
        slab(c, left, top, right, top + h, h * 0.16f, ColorX.shade(body, 0.45f), a)
        slab(c, left + h * 0.1f, top + h * 0.08f, right - h * 0.1f, top + h * 0.86f, h * 0.12f,
            ColorX.shade(body, 0.7f), a)

        // the trace: a stepped line, the way a real board routes round its components
        ink.strokeWidth = h * 0.09f
        ink.color = ColorX.withAlpha(pal.platAccent, a * 0.95f)
        path.reset()
        val segs = (w / (h * 1.6f)).toInt().coerceIn(2, 9)
        var x = left + h * 0.4f
        val step = (w - h * 0.8f) / segs
        path.moveTo(x, top + h * 0.6f)
        for (i in 0 until segs) {
            val hi = i % 2 == 0
            path.lineTo(x + step * 0.45f, top + h * (if (hi) 0.6f else 0.34f))
            path.lineTo(x + step * 0.55f, top + h * (if (hi) 0.34f else 0.6f))
            x += step
        }
        c.drawPath(path, ink)
        // solder nodes
        p.color = ColorX.withAlpha(ColorX.tint(pal.platAccent, 0.6f), a)
        c.drawCircle(left + h * 0.4f, top + h * 0.6f, h * 0.11f, p)
        c.drawCircle(x, top + h * (if (segs % 2 == 0) 0.6f else 0.6f), h * 0.11f, p)

        // the lit landing surface, the one part that is unmistakably a floor
        p.color = ColorX.withAlpha(pal.platAccent, a * 0.9f)
        r.set(left + h * 0.2f, top, right - h * 0.2f, top + h * 0.15f)
        c.drawRoundRect(r, h * 0.07f, h * 0.07f, p)
        p.color = ColorX.withAlpha(ColorX.tint(topCol, 0.7f), a * 0.55f)
        r.set(left + h * 0.34f, top + h * 0.02f, right - h * 0.5f, top + h * 0.09f)
        c.drawRoundRect(r, h * 0.04f, h * 0.04f, p)
        ink.strokeWidth = 5f
        ink.color = ColorX.withAlpha(0xFF080A0F.toInt(), a * 0.75f)
        outline(c, left, top, right, top + h, h * 0.16f)
    }

    /** Frozen: a shelf of ice with fracture lines through it and icicles hanging beneath. */
    private fun iceShelf(
        c: Canvas, plat: Platform, pal: BiomePalette, left: Float, right: Float, top: Float,
        w: Float, h: Float, body: Int, topCol: Int, a: Float
    ) {
        // icicles first, so the shelf sits over their roots
        p.color = ColorX.withAlpha(ColorX.tint(body, 0.25f), a * 0.9f)
        val spikes = (w / (h * 0.9f)).toInt().coerceIn(2, 12)
        for (i in 0 until spikes) {
            val sx = left + h * 0.4f + i * ((w - h * 0.8f) / (spikes - 1).coerceAtLeast(1))
            val len = h * (0.3f + Hash.f(plat.seed + i, 83) * 0.75f)
            path.reset()
            path.moveTo(sx - h * 0.13f, top + h * 0.7f)
            path.lineTo(sx, top + h + len)
            path.lineTo(sx + h * 0.13f, top + h * 0.7f)
            path.close()
            c.drawPath(path, p)
        }

        slab(c, left, top, right, top + h, h * 0.2f, ColorX.withAlpha(body, 0.92f).let { body }, a * 0.93f)
        // the packed snow lying on top
        p.color = ColorX.withAlpha(0xFFF6FBFF.toInt(), a * 0.9f)
        path.reset()
        path.moveTo(left, top + h * 0.34f)
        var sx = left
        val lobes = (w / (h * 1.4f)).toInt().coerceIn(2, 10)
        val lw = w / lobes
        for (i in 0 until lobes) {
            path.quadTo(sx + lw * 0.5f, top - h * (0.02f + Hash.f(plat.seed + i, 89) * 0.16f), sx + lw, top + h * 0.2f)
            sx += lw
        }
        path.lineTo(right, top + h * 0.42f)
        path.lineTo(left, top + h * 0.42f)
        path.close()
        c.drawPath(path, p)

        // fractures: straight, angular, nothing like a crack in rock
        ink.strokeWidth = h * 0.05f
        ink.color = ColorX.withAlpha(0xFFFFFFFF.toInt(), a * 0.45f)
        for (i in 0 until 3) {
            val fx = left + w * (0.2f + i * 0.3f) + Hash.f(plat.seed + i, 97) * w * 0.1f
            path.reset()
            path.moveTo(fx, top + h * 0.45f)
            path.lineTo(fx + h * 0.2f, top + h * 0.72f)
            path.lineTo(fx - h * 0.1f, top + h * 0.95f)
            c.drawPath(path, ink)
        }
        ink.strokeWidth = 5f
        ink.color = ColorX.withAlpha(0xFF0A1420.toInt(), a * 0.6f)
        outline(c, left, top, right, top + h, h * 0.2f)
    }

    /** Emberfall: cooled basalt, cracked open with the lava still glowing through the seams. */
    private fun basalt(
        c: Canvas, plat: Platform, pal: BiomePalette, left: Float, right: Float, top: Float,
        w: Float, h: Float, body: Int, topCol: Int, a: Float
    ) {
        // the glow escaping the seams, under the rock so it reads as coming from inside
        p.color = ColorX.withAlpha(0xFFFF6A1E.toInt(), a * 0.5f)
        r.set(left - h * 0.1f, top + h * 0.3f, right + h * 0.1f, top + h * 1.15f)
        c.drawRoundRect(r, h * 0.3f, h * 0.3f, p)

        slab(c, left, top, right, top + h, h * 0.1f, ColorX.shade(body, 0.55f), a)
        // broken into angular blocks rather than one smooth bar
        val n = (w / (h * 1.3f)).toInt().coerceIn(2, 10)
        val bw = w / n
        for (i in 0 until n) {
            val bx = left + i * bw
            val lift = Hash.f(plat.seed + i, 101) * h * 0.14f
            p.color = ColorX.withAlpha(ColorX.shade(body, 0.72f + Hash.f(plat.seed + i, 103) * 0.3f), a)
            path.reset()
            path.moveTo(bx + bw * 0.04f, top + h * 0.12f + lift)
            path.lineTo(bx + bw * 0.96f, top + lift)
            path.lineTo(bx + bw * 0.96f, top + h * 0.9f)
            path.lineTo(bx + bw * 0.04f, top + h * 0.92f)
            path.close()
            c.drawPath(path, p)
            // the hot seam between this block and the next
            if (i < n - 1) {
                p.color = ColorX.withAlpha(0xFFFFC24A.toInt(), a * 0.85f)
                r.set(bx + bw * 0.94f, top + h * 0.1f, bx + bw * 1.06f, top + h * 0.88f)
                c.drawRoundRect(r, h * 0.04f, h * 0.04f, p)
            }
        }
        // the crust on top, lighter where it has cooled hardest
        p.color = ColorX.withAlpha(ColorX.shade(topCol, 0.9f), a)
        r.set(left, top, right, top + h * 0.26f)
        c.drawRoundRect(r, h * 0.09f, h * 0.09f, p)
        // loose embers sitting in the crust
        p.color = ColorX.withAlpha(0xFFFFE08A.toInt(), a * (0.5f + 0.4f * sin(plat.phase * 2.4f)))
        for (i in 0 until 3) {
            c.drawCircle(left + w * (0.2f + i * 0.3f), top + h * 0.15f, h * 0.05f, p)
        }
        outline(c, left, top, right, top + h, h * 0.1f)
    }

    /** Dust Run: a sandstone ledge, layered in strata, with sand spilling off the lip. */
    private fun sandstone(
        c: Canvas, plat: Platform, pal: BiomePalette, left: Float, right: Float, top: Float,
        w: Float, h: Float, body: Int, topCol: Int, a: Float
    ) {
        slab(c, left, top, right, top + h, h * 0.22f, ColorX.shade(body, 0.72f), a)
        // strata: horizontal bands of slightly different stone, the whole point of sandstone
        val layers = 4
        for (i in 0 until layers) {
            val t0 = top + h * (0.14f + i * 0.2f)
            val shade = 0.82f + ((i * 37) % 5) * 0.07f
            p.color = ColorX.withAlpha(ColorX.shade(body, shade), a)
            r.set(left + h * 0.04f * i, t0, right - h * 0.05f * (layers - i), t0 + h * 0.2f)
            c.drawRoundRect(r, h * 0.06f, h * 0.06f, p)
        }
        // the wind-rounded top
        p.color = ColorX.withAlpha(topCol, a)
        path.reset()
        path.moveTo(left, top + h * 0.3f)
        path.quadTo(left + w * 0.25f, top - h * 0.06f, left + w * 0.55f, top + h * 0.04f)
        path.quadTo(right - w * 0.15f, top + h * 0.12f, right, top + h * 0.26f)
        path.lineTo(right, top + h * 0.34f)
        path.lineTo(left, top + h * 0.38f)
        path.close()
        c.drawPath(path, p)
        // sand running off the near lip
        p.color = ColorX.withAlpha(ColorX.tint(topCol, 0.45f), a * 0.6f)
        for (i in 0 until 4) {
            val sx = left + w * (0.15f + i * 0.22f) + Hash.f(plat.seed + i, 107) * w * 0.08f
            r.set(sx, top + h * 0.95f, sx + h * 0.07f, top + h * (1.1f + Hash.f(plat.seed + i, 109) * 0.5f))
            c.drawRoundRect(r, h * 0.035f, h * 0.035f, p)
        }
        litEdge(c, left, top, right, h, topCol, a, 0.3f)
        outline(c, left, top, right, top + h, h * 0.22f)
    }

    /** Jungle: a fallen log, round in section, wrapped in vine and sprouting leaves. */
    private fun log(
        c: Canvas, plat: Platform, pal: BiomePalette, left: Float, right: Float, top: Float,
        w: Float, h: Float, body: Int, topCol: Int, a: Float
    ) {
        // a full-height cylinder: the roundness is what separates it from every other world
        slab(c, left, top, right, top + h, h * 0.5f, ColorX.shade(body, 0.62f), a)
        p.color = ColorX.withAlpha(body, a)
        r.set(left, top + h * 0.06f, right, top + h * 0.8f)
        c.drawRoundRect(r, h * 0.4f, h * 0.4f, p)
        p.color = ColorX.withAlpha(ColorX.tint(body, 0.2f), a * 0.8f)
        r.set(left + h * 0.3f, top + h * 0.1f, right - h * 0.3f, top + h * 0.34f)
        c.drawRoundRect(r, h * 0.14f, h * 0.14f, p)

        // cut end grain, so it reads as a log and not a pipe
        p.color = ColorX.withAlpha(ColorX.shade(body, 0.8f), a)
        r.set(right - h * 0.42f, top + h * 0.04f, right, top + h * 0.92f)
        c.drawRoundRect(r, h * 0.2f, h * 0.2f, p)
        ink.strokeWidth = h * 0.045f
        ink.color = ColorX.withAlpha(ColorX.shade(body, 0.6f), a * 0.9f)
        r.set(right - h * 0.34f, top + h * 0.2f, right - h * 0.06f, top + h * 0.76f)
        c.drawOval(r, ink)
        r.set(right - h * 0.26f, top + h * 0.34f, right - h * 0.14f, top + h * 0.62f)
        c.drawOval(r, ink)

        // moss along the top, and a couple of leaves off the back
        p.color = ColorX.withAlpha(topCol, a * 0.95f)
        path.reset()
        path.moveTo(left + h * 0.1f, top + h * 0.24f)
        var mx = left + h * 0.1f
        val bumps = (w / (h * 0.8f)).toInt().coerceIn(3, 16)
        val bw = (w - h * 0.5f) / bumps
        for (i in 0 until bumps) {
            path.quadTo(mx + bw * 0.5f, top - h * (0.02f + Hash.f(plat.seed + i, 113) * 0.14f), mx + bw, top + h * 0.16f)
            mx += bw
        }
        path.lineTo(mx, top + h * 0.3f)
        path.lineTo(left + h * 0.1f, top + h * 0.34f)
        path.close()
        c.drawPath(path, p)
        p.color = ColorX.withAlpha(ColorX.shade(pal.platAccent, 0.9f), a * 0.9f)
        for (i in 0 until 2) {
            val lx = left + w * (0.25f + i * 0.4f)
            path.reset()
            path.moveTo(lx, top + h * 0.06f)
            path.quadTo(lx + h * 0.34f, top - h * 0.34f, lx + h * 0.06f, top - h * 0.46f)
            path.quadTo(lx - h * 0.22f, top - h * 0.24f, lx, top + h * 0.06f)
            path.close()
            c.drawPath(path, p)
        }
        ink.strokeWidth = 5f
        ink.color = ColorX.withAlpha(0xFF080A0F.toInt(), a * 0.75f)
        outline(c, left, top, right, top + h, h * 0.5f)
    }

    /** Sugar Rush: a bar of chocolate, scored into squares, with icing drizzled over it. */
    private fun chocolate(
        c: Canvas, plat: Platform, pal: BiomePalette, left: Float, right: Float, top: Float,
        w: Float, h: Float, body: Int, topCol: Int, a: Float
    ) {
        slab(c, left, top, right, top + h, h * 0.12f, ColorX.shade(body, 0.6f), a)
        // the squares, each with its own bevel - the one unmistakable chocolate cue
        val n = (w / (h * 0.95f)).toInt().coerceIn(2, 12)
        val sw = w / n
        for (i in 0 until n) {
            val sx = left + i * sw
            p.color = ColorX.withAlpha(body, a)
            r.set(sx + sw * 0.05f, top + h * 0.05f, sx + sw * 0.95f, top + h * 0.82f)
            c.drawRoundRect(r, h * 0.07f, h * 0.07f, p)
            p.color = ColorX.withAlpha(ColorX.tint(body, 0.28f), a * 0.85f)
            r.set(sx + sw * 0.12f, top + h * 0.1f, sx + sw * 0.88f, top + h * 0.3f)
            c.drawRoundRect(r, h * 0.05f, h * 0.05f, p)
            p.color = ColorX.withAlpha(ColorX.shade(body, 0.72f), a * 0.8f)
            r.set(sx + sw * 0.12f, top + h * 0.64f, sx + sw * 0.88f, top + h * 0.78f)
            c.drawRoundRect(r, h * 0.05f, h * 0.05f, p)
        }
        // icing drizzled along the top, sagging between the squares
        p.color = ColorX.withAlpha(0xFFFFF3F8.toInt(), a * 0.92f)
        path.reset()
        path.moveTo(left, top + h * 0.02f)
        path.lineTo(right, top + h * 0.02f)
        var dx = right
        val drips = (w / (h * 1.2f)).toInt().coerceIn(2, 9)
        val dw = w / drips
        for (i in 0 until drips) {
            path.quadTo(dx - dw * 0.5f, top + h * (0.18f + Hash.f(plat.seed + i, 127) * 0.32f), dx - dw, top + h * 0.1f)
            dx -= dw
        }
        path.close()
        c.drawPath(path, p)
        // a glossy catch-light on the icing
        p.color = ColorX.withAlpha(0xFFFFFFFF.toInt(), a * 0.5f)
        r.set(left + w * 0.1f, top - h * 0.02f, right - w * 0.3f, top + h * 0.06f)
        c.drawRoundRect(r, h * 0.04f, h * 0.04f, p)
        outline(c, left, top, right, top + h, h * 0.12f)
    }

    /** Hollow Hill: a coffin lid on trestles, cobwebbed at the corners and chipped at the edge. */
    private fun coffin(
        c: Canvas, plat: Platform, pal: BiomePalette, left: Float, right: Float, top: Float,
        w: Float, h: Float, body: Int, topCol: Int, a: Float
    ) {
        slab(c, left, top + h * 0.14f, right, top + h, h * 0.08f, ColorX.shade(body, 0.55f), a)
        // the lid: tapered, wider at the shoulder end, which is the whole silhouette
        p.color = ColorX.withAlpha(body, a)
        path.reset()
        path.moveTo(left + w * 0.02f, top + h * 0.26f)
        path.lineTo(left + w * 0.26f, top + h * 0.02f)
        path.lineTo(right - w * 0.06f, top + h * 0.06f)
        path.lineTo(right, top + h * 0.3f)
        path.lineTo(right - w * 0.04f, top + h * 0.88f)
        path.lineTo(left + w * 0.04f, top + h * 0.84f)
        path.close()
        c.drawPath(path, p)
        p.color = ColorX.withAlpha(topCol, a * 0.9f)
        path.reset()
        path.moveTo(left + w * 0.03f, top + h * 0.26f)
        path.lineTo(left + w * 0.26f, top + h * 0.03f)
        path.lineTo(right - w * 0.06f, top + h * 0.07f)
        path.lineTo(right, top + h * 0.3f)
        path.close()
        c.drawPath(path, p)

        // the seam down the middle of the lid, and its two plates
        ink.strokeWidth = h * 0.05f
        ink.color = ColorX.withAlpha(ColorX.shade(body, 0.45f), a * 0.9f)
        c.drawLine(left + w * 0.05f, top + h * 0.52f, right - w * 0.05f, top + h * 0.54f, ink)
        p.color = ColorX.withAlpha(0xFF8A8FA0.toInt(), a * 0.8f)
        c.drawCircle(left + w * 0.2f, top + h * 0.53f, h * 0.08f, p)
        c.drawCircle(right - w * 0.2f, top + h * 0.55f, h * 0.08f, p)

        // cobweb in one corner
        ink.strokeWidth = h * 0.035f
        ink.color = ColorX.withAlpha(0xFFD8DEE9.toInt(), a * 0.4f)
        val cx = right - h * 0.1f
        val cy = top + h * 0.1f
        for (i in 0 until 3) {
            val ang = 0.6f + i * 0.55f
            c.drawLine(cx, cy, cx - h * 0.7f * kotlin.math.cos(ang), cy + h * 0.7f * sin(ang), ink)
        }
        path.reset()
        path.moveTo(cx - h * 0.12f, cy + h * 0.32f)
        path.quadTo(cx - h * 0.3f, cy + h * 0.22f, cx - h * 0.4f, cy + h * 0.05f)
        c.drawPath(path, ink)

        ink.strokeWidth = 5f
        ink.color = ColorX.withAlpha(0xFF080A0F.toInt(), a * 0.75f)
        outline(c, left, top, right, top + h, h * 0.08f)
    }

    /** Heaven: a marble step with a gilded edge, resting on cloud rather than on anything. */
    private fun marble(
        c: Canvas, plat: Platform, pal: BiomePalette, left: Float, right: Float, top: Float,
        w: Float, h: Float, body: Int, topCol: Int, a: Float
    ) {
        // cloud underneath, so it floats instead of hanging
        p.color = ColorX.withAlpha(0xFFFFFFFF.toInt(), a * 0.5f)
        val puffs = (w / (h * 1.1f)).toInt().coerceIn(2, 10)
        for (i in 0 until puffs) {
            val px = left + h * 0.3f + i * ((w - h * 0.6f) / (puffs - 1).coerceAtLeast(1))
            c.drawCircle(px, top + h * 0.92f, h * (0.3f + Hash.f(plat.seed + i, 131) * 0.22f), p)
        }

        slab(c, left, top, right, top + h * 0.9f, h * 0.16f, ColorX.tint(body, 0.35f), a)
        // veining: a few thin, wandering lines, nothing like a crack
        ink.strokeWidth = h * 0.04f
        ink.color = ColorX.withAlpha(ColorX.shade(body, 0.62f), a * 0.55f)
        for (i in 0 until 2) {
            val vx = left + w * (0.25f + i * 0.4f)
            path.reset()
            path.moveTo(vx - h * 0.4f, top + h * 0.28f)
            path.quadTo(vx, top + h * 0.5f, vx + h * 0.3f, top + h * 0.36f)
            path.quadTo(vx + h * 0.6f, top + h * 0.24f, vx + h * 0.9f, top + h * 0.46f)
            c.drawPath(path, ink)
        }
        // the gilded lip and a bright tread
        p.color = ColorX.withAlpha(0xFFE8C26A.toInt(), a * 0.95f)
        r.set(left, top, right, top + h * 0.16f)
        c.drawRoundRect(r, h * 0.07f, h * 0.07f, p)
        p.color = ColorX.withAlpha(0xFFFFF6DC.toInt(), a * 0.75f)
        r.set(left + w * 0.06f, top + h * 0.02f, right - w * 0.2f, top + h * 0.08f)
        c.drawRoundRect(r, h * 0.03f, h * 0.03f, p)
        p.color = ColorX.withAlpha(0xFFE8C26A.toInt(), a * 0.7f)
        r.set(left, top + h * 0.74f, right, top + h * 0.88f)
        c.drawRoundRect(r, h * 0.06f, h * 0.06f, p)

        ink.strokeWidth = 4f
        ink.color = ColorX.withAlpha(0xFF6B5A2E.toInt(), a * 0.5f)
        outline(c, left, top, right, top + h * 0.9f, h * 0.16f)
    }
}
