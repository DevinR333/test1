package com.blacklab.buddybounce.render

import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import com.blacklab.buddybounce.game.Hash
import com.blacklab.buddybounce.game.Tuning
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.sin

/**
 * The per-band scenery that is specific to one world.
 *
 * [Backdrop] holds the styles that several worlds legitimately share - clouds, auroras, nebulae,
 * generic peaks and trees. Everything in here exists because a band was wearing art that did not
 * belong to it: the jungle's "Root Floor" was drawing the treetop canopy, the bakery floor and
 * the gumdrop hills were the same picture, the back alley and the skyline were the same towers,
 * and four bands across the desert, the ocean and the sweet shop were a couple of clouds and
 * nothing else. A band whose name promises roots should draw roots.
 *
 * Every function here is built the same way, in three depths, because that is what was missing:
 *
 *   FAR   a silhouette high in the frame, so the top of the screen is not bare sky
 *   MID   the horizon row, which is what reads as distance
 *   NEAR  something at the foot of the band, behind where the platforms actually are
 *
 * A band that only fills one of the three looks empty however much work went into that one
 * layer, which is exactly the "missing background elements" complaint.
 */
internal class BandArt(private val art: Art) {

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val ink = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val path = Path()
    private val rect = RectF()

    private companion object {
        /** How far above the top of the screen a ground line fades out over. */
        const val GROUND_FADE = 260f
    }

    // -----------------------------------------------------------------------------------
    // shared scaffolding - same contract as Backdrop's, kept local so this file stands alone
    // -----------------------------------------------------------------------------------

    /**
     * Runs a repeating parallax band and hands each repeat's base screen Y to [body], FARTHEST
     * FIRST. See Backdrop.band: counting the repeats upward paints the most distant one last,
     * which sheets it over everything nearer.
     */
    private inline fun band(camY: Float, p: Float, height: Float, body: (idx: Int, baseY: Float) -> Unit) {
        val i0 = floor((camY * p) / height).toInt()
        for (k in 2 downTo 0) {
            val idx = i0 + k
            val baseY = Tuning.VIEW_H - (idx * height - camY * p)
            if (baseY < -height * 1.6f || baseY > Tuning.VIEW_H + height) continue
            body(idx, baseY)
        }
    }


    /**
     * Where a downward fill should end so its bottom edge never shows.
     *
     * Ground, water, rock and buildings are all drawn as a silhouette filled DOWNWARD from its
     * own line, and every one of them stopped at a fixed depth. Whenever that depth happened to
     * land inside the frame you got a hard horizontal rule straight across the screen with sky
     * underneath it - the seams in the reef, the lava ridge, the mountains, the city towers and
     * the desert buttes were all this one thing.
     *
     * Pushing the bottom past the frame costs nothing: whatever is nearer is painted after and
     * covers it, and below a horizon there is supposed to be more of the same rather than sky.
     *
     * A fill that is entirely ABOVE the screen is left alone - it is invisible either way, and
     * stretching it down would drop a slab of it over the whole frame.
     */
    private fun deep(y: Float): Float = if (y <= 0f) y else maxOf(y, Tuning.VIEW_H + 400f)

    /**
     * The repeat nearest the viewer - the one whose ground line is lowest on screen.
     *
     * Most of a band is scenery and repeating it up the sky is the whole point. A few things are
     * not: a road, a sea. Drawn once per repeat you get a road hanging in mid-air every screen
     * and a fresh horizon every screen, each with a hard line along the top of it. Those belong
     * to the ground you are actually above, so they are drawn for this repeat only.
     */
    private fun nearestIdx(camY: Float, p: Float, height: Float): Int =
        floor((camY * p) / height).toInt()

    /** Ground: a soft ridge at [footY] filled well past the bottom, so objects are rooted. */
    private fun ground(c: Canvas, worldW: Float, footY: Float, depth: Float, amp: Float, color: Int, key: Int) {
        // A ground line above the top of the screen has nothing to show but its own fill, and
        // that fill runs to the bottom of the frame. The band's repeats tile seamlessly, so the
        // repeat above the one you are looking at painted the whole screen in soil, the one
        // above that painted over it, and the sky never appeared at all - which is what made
        // the jungle's root floor and the bakery floor read as flat dark slabs with a couple of
        // ferns on them. Above the screen the ground is simply not drawn, fading out over the
        // last stretch so it does not pop in.
        val vis = ((footY + GROUND_FADE) / GROUND_FADE).coerceIn(0f, 1f)
        if (vis <= 0.01f) return
        path.reset()
        path.moveTo(-60f, footY + amp)
        val lobes = 5
        val step = (worldW + 120f) / lobes
        var x = -60f
        for (i in 0..lobes) {
            val crest = footY - amp * (0.35f + Hash.f(key * 37 + i, 149) * 0.9f)
            path.quadTo(x + step * 0.5f, crest, x + step, footY + amp * (0.1f + Hash.f(key * 41 + i, 151) * 0.5f))
            x += step
        }
        path.lineTo(worldW + 60f, deep(footY + depth))
        path.lineTo(-60f, deep(footY + depth))
        path.close()
        paint.color = ColorX.scaleAlpha(color, vis)
        c.drawPath(path, paint)
    }

    /** A row of rounded lobes filled downward - the generic far hill/mound row. */
    private fun mounds(c: Canvas, worldW: Float, baseY: Float, h: Float, color: Int, key: Int, lobes: Int) {
        val footY = baseY
        // A ground line above the top of the screen has nothing to show but its own fill, and
        // that fill runs to the bottom of the frame. The band's repeats tile seamlessly, so the
        // repeat above the one you are looking at painted the whole screen in soil, the one
        // above that painted over it, and the sky never appeared at all - which is what made
        // the jungle's root floor and the bakery floor read as flat dark slabs with a couple of
        // ferns on them. Above the screen the ground is simply not drawn, fading out over the
        // last stretch so it does not pop in.
        val vis = ((footY + GROUND_FADE) / GROUND_FADE).coerceIn(0f, 1f)
        if (vis <= 0.01f) return
        path.reset()
        path.moveTo(-40f, baseY + h)
        val step = (worldW + 80f) / lobes
        var x = -40f
        for (i in 0..lobes) {
            val peak = baseY - h * (0.35f + Hash.f(key * 31 + i, 71) * 0.6f)
            path.quadTo(x + step * 0.5f, peak, x + step, baseY + h * 0.2f)
            x += step
        }
        path.lineTo(worldW + 40f, deep(baseY + h * 1.6f))
        path.lineTo(-40f, deep(baseY + h * 1.6f))
        path.close()
        paint.color = ColorX.scaleAlpha(color, vis)
        c.drawPath(path, paint)
    }

    /** A five-pointed star/flower head, used for blossoms and sprinkles alike. */
    private fun petals(c: Canvas, cx: Float, cy: Float, r: Float, n: Int, spin: Float) {
        for (i in 0 until n) {
            val a = spin + i * (6.2832f / n)
            c.drawCircle(cx + cos(a) * r * 0.72f, cy + sin(a) * r * 0.72f, r * 0.52f, paint)
        }
    }

    // -----------------------------------------------------------------------------------
    // Backyard Skies - the ground band
    // -----------------------------------------------------------------------------------

    /**
     * The garden itself: a hedge and fence line, a kennel, sunflowers and a washing line, with
     * the neighbourhood roofs behind. This is the very first thing anyone ever sees, and it was
     * two anonymous green humps.
     */
    fun yardLow(c: Canvas, worldW: Float, camY: Float, pal: BiomePalette, alpha: Float, time: Float) {
        paint.reset(); paint.isAntiAlias = true
        val h = Tuning.VIEW_H * 1.0f

        // FAR - the roofs over the fence, and a couple of kites
        band(camY, 0.09f, h) { idx, baseY ->
            val roofY = baseY - h * 0.52f
            paint.color = ColorX.withAlpha(ColorX.tint(pal.farInk, 0.45f), alpha * 0.45f)
            var x = -80f
            var i = 0
            while (x < worldW + 80f) {
                val key = idx * 71 + i
                val w = 200f + Hash.f(key, 301) * 260f
                val hh = 150f + Hash.f(key, 303) * 190f
                rect.set(x, roofY - hh * 0.45f, x + w, deep(roofY + 340f))
                c.drawRect(rect, paint)
                path.reset()
                path.moveTo(x - 18f, roofY - hh * 0.45f)
                path.lineTo(x + w * 0.5f, roofY - hh)
                path.lineTo(x + w + 18f, roofY - hh * 0.45f)
                path.close()
                c.drawPath(path, paint)
                x += w + 40f
                i++
            }
            // kites on strings, high up where there was nothing at all
            for (k in 0 until 2) {
                val key = idx * 17 + k
                val kx = Hash.f(key, 307) * worldW
                val ky = baseY - h * (0.86f + Hash.f(key, 311) * 0.22f) + sin(time * 0.6f + k) * 26f
                paint.color = ColorX.withAlpha(if (k == 0) pal.rimInk else pal.accentInk, alpha * 0.8f)
                path.reset()
                path.moveTo(kx, ky - 46f)
                path.lineTo(kx + 34f, ky)
                path.lineTo(kx, ky + 54f)
                path.lineTo(kx - 34f, ky)
                path.close()
                c.drawPath(path, paint)
                ink.color = ColorX.withAlpha(0xFFFFFFFF.toInt(), alpha * 0.28f)
                ink.strokeWidth = 3f
                path.reset()
                path.moveTo(kx, ky + 54f)
                path.quadTo(kx - 70f, ky + 200f, kx - 30f, ky + 340f)
                c.drawPath(path, ink)
            }
        }

        // MID - the hedge row
        band(camY, 0.15f, h) { idx, baseY ->
            mounds(
                c, worldW, baseY - h * 0.2f, 300f,
                ColorX.withAlpha(ColorX.shade(pal.midInk, 1.05f), alpha * 0.8f), idx, 4
            )
        }

        // NEAR - fence, kennel, sunflowers, washing line
        band(camY, 0.24f, h) { idx, baseY ->
            val footY = baseY + h * 0.06f
            ground(c, worldW, footY, h, h * 0.035f, ColorX.withAlpha(pal.nearInk, alpha * 0.95f), idx * 3)

            // picket fence
            val fenceTop = footY - 250f
            paint.color = ColorX.withAlpha(ColorX.tint(pal.platTop, 0.35f), alpha * 0.9f)
            var x = -30f
            var i = 0
            while (x < worldW + 30f) {
                path.reset()
                path.moveTo(x, fenceTop + 34f)
                path.lineTo(x + 26f, fenceTop)
                path.lineTo(x + 52f, fenceTop + 34f)
                path.lineTo(x + 52f, footY + 20f)
                path.lineTo(x, footY + 20f)
                path.close()
                c.drawPath(path, paint)
                x += 78f
                i++
            }
            // the two rails, so it reads as a fence and not a row of stakes
            paint.color = ColorX.withAlpha(ColorX.shade(pal.platBody, 1.1f), alpha * 0.85f)
            rect.set(-30f, fenceTop + 86f, worldW + 30f, fenceTop + 116f)
            c.drawRect(rect, paint)
            rect.set(-30f, footY - 96f, worldW + 30f, footY - 66f)
            c.drawRect(rect, paint)

            // the kennel, on whichever side the hash says
            val kx = if (Hash.f(idx, 313) > 0.5f) worldW * 0.16f else worldW * 0.78f
            val kw = 260f
            paint.color = ColorX.withAlpha(pal.platBody, alpha * 0.95f)
            rect.set(kx - kw * 0.5f, footY - 230f, kx + kw * 0.5f, footY + 10f)
            c.drawRect(rect, paint)
            paint.color = ColorX.withAlpha(pal.platShade, alpha * 0.95f)
            path.reset()
            path.moveTo(kx - kw * 0.62f, footY - 226f)
            path.lineTo(kx, footY - 350f)
            path.lineTo(kx + kw * 0.62f, footY - 226f)
            path.close()
            c.drawPath(path, paint)
            paint.color = ColorX.withAlpha(0xFF201410.toInt(), alpha * 0.8f)
            rect.set(kx - 66f, footY - 176f, kx + 66f, footY + 10f)
            c.drawRoundRect(rect, 66f, 66f, paint)

            // sunflowers along the fence
            for (k in 0 until 5) {
                val key = idx * 23 + k
                val sx = Hash.f(key, 317) * worldW
                val sh = 260f + Hash.f(key, 319) * 190f
                val lean = sin(time * 0.5f + k) * 16f
                ink.color = ColorX.withAlpha(ColorX.shade(pal.midInk, 0.9f), alpha * 0.9f)
                ink.strokeWidth = 12f
                path.reset()
                path.moveTo(sx, footY)
                path.quadTo(sx + lean, footY - sh * 0.55f, sx + lean * 1.8f, footY - sh)
                c.drawPath(path, ink)
                paint.color = ColorX.withAlpha(pal.rimInk, alpha * 0.95f)
                petals(c, sx + lean * 1.8f, footY - sh, 52f, 8, k * 0.7f)
                paint.color = ColorX.withAlpha(ColorX.shade(pal.platShade, 0.8f), alpha * 0.95f)
                c.drawCircle(sx + lean * 1.8f, footY - sh, 26f, paint)
            }

            // a washing line with a couple of pegged towels
            ink.color = ColorX.withAlpha(0xFFFFFFFF.toInt(), alpha * 0.35f)
            ink.strokeWidth = 4f
            path.reset()
            path.moveTo(-20f, footY - 470f)
            path.quadTo(worldW * 0.5f, footY - 400f, worldW + 20f, footY - 470f)
            c.drawPath(path, ink)
            for (k in 0 until 3) {
                val key = idx * 29 + k
                val tx = (0.2f + k * 0.3f) * worldW
                val sag = footY - 470f + (1f - kotlin.math.abs(tx / worldW - 0.5f) * 2f) * 70f
                paint.color = ColorX.withAlpha(
                    if (k % 2 == 0) pal.accentInk else ColorX.tint(pal.rimInk, 0.4f), alpha * 0.75f
                )
                val sway = sin(time * 0.9f + k) * 10f
                rect.set(tx - 44f + sway, sag, tx + 44f + sway, sag + 150f + Hash.f(key, 323) * 60f)
                c.drawRoundRect(rect, 10f, 10f, paint)
            }
        }
    }

    // -----------------------------------------------------------------------------------
    // Overgrown
    // -----------------------------------------------------------------------------------

    /**
     * The jungle floor: buttress roots, trunks going up out of frame, ferns and a leaf-litter
     * floor. The band was drawing the treetop canopy, which is the art for the band four
     * screens above it - the ground level had leaves hanging in mid-air and no ground.
     */
    fun roots(c: Canvas, worldW: Float, camY: Float, pal: BiomePalette, alpha: Float, time: Float) {
        paint.reset(); paint.isAntiAlias = true
        val h = Tuning.VIEW_H * 0.95f

        // FAR - a wall of haze-blue trunks receding into the forest
        band(camY, 0.1f, h) { idx, baseY ->
            paint.color = ColorX.withAlpha(ColorX.tint(pal.farInk, 0.5f), alpha * 0.4f)
            var x = -40f
            var i = 0
            while (x < worldW + 40f) {
                val key = idx * 83 + i
                val w = 34f + Hash.f(key, 331) * 54f
                rect.set(x, baseY - h * 1.35f, x + w, deep(baseY + h * 0.4f))
                c.drawRect(rect, paint)
                // wide gaps: packed shoulder to shoulder this rank was a flat wash, not a forest
                x += w + 130f + Hash.f(key, 337) * 170f
                i++
            }
        }

        // MID - the middle rank of trunks, with vines wound round them
        band(camY, 0.19f, h) { idx, baseY ->
            for (i in 0 until 3) {
                val key = idx * 89 + i
                // spaced into thirds rather than placed at random: four trunks up to 180 wide,
                // dropped anywhere across 900 units, kept landing on each other and closing the
                // band into a solid slab with no sky left between the trees
                val tx = (i / 3f + 0.02f + Hash.f(key, 341) * 0.16f) * worldW
                val tw = 66f + Hash.f(key, 343) * 56f
                paint.color = ColorX.withAlpha(ColorX.shade(pal.midInk, 0.9f), alpha * 0.7f)
                rect.set(tx, baseY - h * 1.4f, tx + tw, deep(baseY + h * 0.2f))
                c.drawRect(rect, paint)
                ink.color = ColorX.withAlpha(pal.rimInk, alpha * 0.45f)
                ink.strokeWidth = 9f
                path.reset()
                path.moveTo(tx - 14f, baseY - h * 0.1f)
                var y = baseY - h * 0.1f
                var side = 1f
                while (y > baseY - h * 1.3f) {
                    path.quadTo(tx + tw * 0.5f + side * (tw * 0.9f), y - 80f, tx + tw * 0.5f, y - 160f)
                    side = -side
                    y -= 160f
                }
                c.drawPath(path, ink)
            }
        }

        // NEAR - buttress roots spreading onto the litter floor, plus ferns
        band(camY, 0.3f, h) { idx, baseY ->
            val footY = baseY + h * 0.08f
            ground(
                c, worldW, footY, h, h * 0.03f,
                ColorX.withAlpha(ColorX.shade(pal.nearInk, 0.8f), alpha * 0.95f), idx * 5
            )
            for (i in 0 until 2) {
                val key = idx * 97 + i
                // two trees, spaced into their own halves of the screen. Three placed at random
                // and flaring 1.35x their own width to each side covered the whole frame, so the
                // band read as one dark slab instead of trunks you can see between.
                val tx = (i * 0.5f + 0.06f + Hash.f(key, 347) * 0.24f) * worldW
                val tw = 92f + Hash.f(key, 349) * 62f
                // the trunk
                paint.color = ColorX.withAlpha(pal.nearInk, alpha * 0.95f)
                rect.set(tx, footY - h * 1.5f, tx + tw, footY - 40f)
                c.drawRect(rect, paint)
                // three buttress fins flaring out to the floor
                for (fin in -1..1) {
                    if (fin == 0) continue
                    path.reset()
                    path.moveTo(tx + tw * 0.5f, footY - 520f)
                    path.quadTo(
                        tx + tw * 0.5f + fin * tw * 0.7f, footY - 250f,
                        tx + tw * 0.5f + fin * tw * 0.95f, footY + 20f
                    )
                    path.lineTo(tx + tw * 0.5f + fin * tw * 0.18f, footY + 20f)
                    path.close()
                    c.drawPath(path, paint)
                }
                // the lit edge down one side of the trunk
                paint.color = ColorX.withAlpha(ColorX.tint(pal.nearInk, 0.3f), alpha * 0.5f)
                rect.set(tx + tw * 0.74f, footY - h * 1.5f, tx + tw, footY - 40f)
                c.drawRect(rect, paint)
            }
            // ferns along the floor
            for (i in 0 until 7) {
                val key = idx * 101 + i
                val fx = Hash.f(key, 353) * worldW
                val fh = 130f + Hash.f(key, 359) * 130f
                paint.color = ColorX.withAlpha(ColorX.tint(pal.midInk, 0.15f), alpha * 0.85f)
                for (frond in 0 until 5) {
                    val a = -1.35f + frond * 0.68f + sin(time * 0.6f + i) * 0.06f
                    path.reset()
                    path.moveTo(fx, footY + 10f)
                    path.quadTo(
                        fx + sin(a) * fh * 0.6f, footY - fh * 0.75f,
                        fx + sin(a) * fh * 1.15f, footY - fh * (0.55f + cos(a) * 0.5f)
                    )
                    path.quadTo(fx + sin(a) * fh * 0.4f, footY - fh * 0.45f, fx, footY + 10f)
                    path.close()
                    c.drawPath(path, paint)
                }
            }
        }
    }

    /**
     * The flowering layer between the canopy and the mist: garlands of blossom strung between the
     * high branches, big epiphyte blooms, and petals coming down. It was plain green hills.
     */
    fun flowers(c: Canvas, worldW: Float, camY: Float, pal: BiomePalette, alpha: Float, time: Float) {
        paint.reset(); paint.isAntiAlias = true
        val h = Tuning.VIEW_H * 0.9f

        // FAR - soft blossom masses high in the frame
        band(camY, 0.11f, h) { idx, baseY ->
            for (i in 0 until 4) {
                val key = idx * 61 + i
                art.draw(
                    c, art.softGlow,
                    Hash.f(key, 361) * worldW, baseY - h * (0.75f + Hash.f(key, 367) * 0.45f),
                    520f + Hash.f(key, 373) * 340f, 360f + Hash.f(key, 379) * 240f,
                    alpha * 0.3f, if (i % 2 == 0) pal.rimInk else pal.accentInk
                )
            }
        }

        // MID - garlands slung across the band, flowers hanging off them
        band(camY, 0.18f, h) { idx, baseY ->
            for (row in 0 until 3) {
                val key = idx * 67 + row
                val yy = baseY - h * (0.25f + row * 0.28f)
                val sag = 90f + Hash.f(key, 383) * 120f
                ink.color = ColorX.withAlpha(ColorX.shade(pal.midInk, 0.85f), alpha * 0.7f)
                ink.strokeWidth = 8f
                path.reset()
                path.moveTo(-30f, yy)
                path.quadTo(worldW * 0.5f, yy + sag, worldW + 30f, yy - 30f)
                c.drawPath(path, ink)
                for (k in 0 until 7) {
                    val t = (k + 0.5f) / 7f
                    // point on the quadratic, so the blooms sit ON the garland
                    val bx = (1 - t) * (1 - t) * -30f + 2 * (1 - t) * t * (worldW * 0.5f) + t * t * (worldW + 30f)
                    val by = (1 - t) * (1 - t) * yy + 2 * (1 - t) * t * (yy + sag) + t * t * (yy - 30f)
                    val drop = 40f + Hash.f(key * 13 + k, 389) * 70f
                    ink.strokeWidth = 5f
                    path.reset()
                    path.moveTo(bx, by)
                    path.lineTo(bx + sin(time * 0.7f + k) * 8f, by + drop)
                    c.drawPath(path, ink)
                    paint.color = ColorX.withAlpha(
                        if ((k + row) % 3 == 0) pal.accentInk else pal.rimInk, alpha * 0.9f
                    )
                    petals(c, bx + sin(time * 0.7f + k) * 8f, by + drop + 26f, 30f, 5, k * 0.9f)
                    paint.color = ColorX.withAlpha(ColorX.tint(pal.platTop, 0.5f), alpha * 0.9f)
                    c.drawCircle(bx + sin(time * 0.7f + k) * 8f, by + drop + 26f, 11f, paint)
                }
            }
        }

        // NEAR - a flowering bank at the foot of the band, and petals drifting past
        band(camY, 0.27f, h) { idx, baseY ->
            val footY = baseY + h * 0.05f
            ground(
                c, worldW, footY, h, h * 0.045f,
                ColorX.withAlpha(ColorX.shade(pal.nearInk, 0.95f), alpha * 0.9f), idx * 11
            )
            for (i in 0 until 9) {
                val key = idx * 71 + i
                val bx = Hash.f(key, 397) * worldW
                val by = footY - 20f - Hash.f(key, 401) * 130f
                val r = 26f + Hash.f(key, 409) * 26f
                paint.color = ColorX.withAlpha(
                    if (i % 3 == 0) pal.rimInk else if (i % 3 == 1) pal.accentInk else ColorX.tint(pal.rimInk, 0.45f),
                    alpha * 0.9f
                )
                petals(c, bx, by, r, 5, i * 1.3f)
                paint.color = ColorX.withAlpha(ColorX.tint(pal.platTop, 0.55f), alpha * 0.9f)
                c.drawCircle(bx, by, r * 0.36f, paint)
            }
            // falling petals, drifting across the whole band
            for (i in 0 until 14) {
                val key = idx * 73 + i
                val px = (Hash.f(key, 419) * worldW + sin(time * 0.4f + i) * 60f + time * 18f) % (worldW + 80f) - 40f
                val py = baseY - ((Hash.f(key, 421) * h + time * 46f) % h)
                paint.color = ColorX.withAlpha(if (i % 2 == 0) pal.rimInk else pal.accentInk, alpha * 0.55f)
                rect.set(px - 13f, py - 7f, px + 13f, py + 7f)
                c.drawOval(rect, paint)
            }
        }
    }

    // -----------------------------------------------------------------------------------
    // Deep Blue
    // -----------------------------------------------------------------------------------

    /**
     * Just under the surface: the underside of the water with light coming through it, shoals of
     * fish, and a pale sandbar below. It was two clouds, which underwater read as nothing.
     */
    fun shallows(c: Canvas, worldW: Float, camY: Float, pal: BiomePalette, alpha: Float, time: Float) {
        paint.reset(); paint.isAntiAlias = true
        val h = Tuning.VIEW_H * 0.9f

        // FAR - the rippling ceiling of the sea, and the shafts under it
        band(camY, 0.1f, h) { idx, baseY ->
            val surf = baseY - h * 0.92f
            // The lit water under the surface. Its underside is the rippled surface line and is
            // meant to be seen; its TOP was a straight cut at surf - 0.6h, which ruled a line
            // across the whole screen with darker water above it. It fades out instead.
            val lit = ColorX.withAlpha(ColorX.tint(pal.farInk, 0.55f), alpha * 0.5f)
            paint.shader = LinearGradient(
                0f, surf - h * 0.6f, 0f, surf - h * 0.12f,
                intArrayOf(ColorX.withAlpha(lit, 0f), lit), null, Shader.TileMode.CLAMP
            )
            path.reset()
            path.moveTo(-40f, surf)
            var x = -40f
            var k = 0
            while (x < worldW + 60f) {
                val span = 150f + Hash.f(idx * 31 + k, 431) * 130f
                path.quadTo(x + span * 0.5f, surf + 46f + sin(time * 1.1f + k) * 16f, x + span, surf)
                x += span
                k++
            }
            path.lineTo(x, surf - h * 0.6f)
            path.lineTo(-40f, surf - h * 0.6f)
            path.close()
            c.drawPath(path, paint)
            paint.shader = null
            for (i in 0 until 5) {
                val key = idx * 37 + i
                val sx = Hash.f(key, 433) * worldW
                paint.color = ColorX.withAlpha(ColorX.tint(pal.rimInk, 0.4f), alpha * 0.16f)
                path.reset()
                path.moveTo(sx - 60f, surf)
                path.lineTo(sx + 60f, surf)
                path.lineTo(sx + 250f + Hash.f(key, 439) * 160f, surf + h * 1.1f)
                path.lineTo(sx + 60f + Hash.f(key, 443) * 160f, surf + h * 1.1f)
                path.close()
                c.drawPath(path, paint)
            }
        }

        // MID - shoals, each fish a little wedge, all pointing the same way
        band(camY, 0.2f, h) { idx, baseY ->
            for (shoal in 0 until 3) {
                val key = idx * 41 + shoal
                val sx = Hash.f(key, 449) * worldW
                val sy = baseY - h * (0.3f + Hash.f(key, 457) * 0.5f)
                val dir = if (Hash.f(key, 461) > 0.5f) 1f else -1f
                // the shoals sit against a very pale sandbar: lightening them loses them
                paint.color = ColorX.withAlpha(
                    ColorX.readable(
                        if (shoal % 2 == 0) pal.accentInk else ColorX.shade(pal.midInk, 0.8f),
                        pal.skyMid, 0.2f
                    ),
                    alpha * 0.92f
                )
                for (i in 0 until 11) {
                    val fx = sx + ((i % 4) * 78f + Hash.f(key * 7 + i, 463) * 40f) * dir +
                        sin(time * 0.8f + i) * 12f
                    val fy = sy + (i / 4) * 62f + Hash.f(key * 7 + i, 467) * 26f
                    val s = 17f + Hash.f(key * 7 + i, 479) * 10f
                    path.reset()
                    path.moveTo(fx + dir * s * 1.7f, fy)
                    path.quadTo(fx, fy - s * 0.75f, fx - dir * s * 1.2f, fy)
                    path.quadTo(fx, fy + s * 0.75f, fx + dir * s * 1.7f, fy)
                    path.close()
                    c.drawPath(path, paint)
                    path.reset()
                    path.moveTo(fx - dir * s * 1.2f, fy)
                    path.lineTo(fx - dir * s * 2.1f, fy - s * 0.8f)
                    path.lineTo(fx - dir * s * 2.1f, fy + s * 0.8f)
                    path.close()
                    c.drawPath(path, paint)
                }
            }
        }

        // NEAR - the pale sandbar and its ripples, with a few sea fans standing on it
        band(camY, 0.28f, h) { idx, baseY ->
            val footY = baseY + h * 0.1f
            // the sandbar. Tinted a quarter of the way to white it was a cream wall that
            // everything standing on it disappeared into.
            ground(
                c, worldW, footY, h, h * 0.05f,
                ColorX.withAlpha(ColorX.readable(pal.platTop, pal.skyLow, 0.14f), alpha * 0.9f),
                idx * 13
            )
            ink.color = ColorX.withAlpha(ColorX.tint(pal.rimInk, 0.5f), alpha * 0.28f)
            ink.strokeWidth = 6f
            for (i in 0 until 5) {
                val ry = footY + 20f + i * 46f
                path.reset()
                path.moveTo(-30f, ry)
                var x = -30f
                var k = 0
                while (x < worldW + 40f) {
                    val span = 180f + Hash.f(idx * 17 + i * 5 + k, 487) * 120f
                    path.quadTo(x + span * 0.5f, ry - 18f, x + span, ry)
                    x += span
                    k++
                }
                c.drawPath(path, ink)
            }
            for (i in 0 until 5) {
                val key = idx * 19 + i
                val fx = Hash.f(key, 491) * worldW
                val fh = 130f + Hash.f(key, 499) * 150f
                val lean = sin(time * 0.5f + i) * 22f
                paint.color = ColorX.withAlpha(
                    if (i % 2 == 0) pal.midInk else pal.farInk, alpha * 0.75f
                )
                path.reset()
                path.moveTo(fx - 12f, footY)
                path.quadTo(fx - fh * 0.5f + lean, footY - fh * 0.7f, fx + lean, footY - fh)
                path.quadTo(fx + fh * 0.5f + lean, footY - fh * 0.7f, fx + 12f, footY)
                path.close()
                c.drawPath(path, paint)
            }
        }
    }

    /**
     * Out of the water at last: the sea below with islands and sails on it, gulls, and towering
     * cumulus. The top band of the ocean was two flat clouds and empty blue.
     */
    fun seaSky(c: Canvas, worldW: Float, camY: Float, pal: BiomePalette, alpha: Float, time: Float) {
        paint.reset(); paint.isAntiAlias = true
        val h = Tuning.VIEW_H * 1.0f

        // FAR - big cumulus towers, stacked lobes rather than one flat puff
        band(camY, 0.09f, h) { idx, baseY ->
            for (i in 0 until 3) {
                val key = idx * 43 + i
                val cx = Hash.f(key, 503) * (worldW + 400f) - 200f
                val cy = baseY - h * (0.62f + Hash.f(key, 509) * 0.5f)
                val s = 220f + Hash.f(key, 521) * 200f
                paint.color = ColorX.withAlpha(0xFFFFFFFF.toInt(), alpha * 0.5f)
                c.drawCircle(cx, cy, s, paint)
                c.drawCircle(cx - s * 0.85f, cy + s * 0.45f, s * 0.68f, paint)
                c.drawCircle(cx + s * 0.9f, cy + s * 0.4f, s * 0.6f, paint)
                c.drawCircle(cx + s * 0.15f, cy - s * 0.72f, s * 0.58f, paint)
                paint.color = ColorX.withAlpha(ColorX.tint(pal.farInk, 0.35f), alpha * 0.32f)
                rect.set(cx - s * 1.5f, cy + s * 0.55f, cx + s * 1.5f, cy + s * 1.05f)
                c.drawOval(rect, paint)
            }
        }

        // MID - gulls
        band(camY, 0.16f, h) { idx, baseY ->
            ink.color = ColorX.withAlpha(0xFFFFFFFF.toInt(), alpha * 0.65f)
            for (i in 0 until 6) {
                val key = idx * 47 + i
                val gx = (Hash.f(key, 523) * worldW + time * (14f + Hash.f(key, 541) * 18f)) % (worldW + 120f) - 60f
                val gy = baseY - h * (0.2f + Hash.f(key, 547) * 0.55f)
                val s = 22f + Hash.f(key, 557) * 20f
                val flap = sin(time * 2.2f + i) * 0.35f
                ink.strokeWidth = 5f
                path.reset()
                path.moveTo(gx - s * 1.6f, gy + s * flap)
                path.quadTo(gx - s * 0.7f, gy - s * 0.6f, gx, gy)
                path.quadTo(gx + s * 0.7f, gy - s * 0.6f, gx + s * 1.6f, gy + s * flap)
                c.drawPath(path, ink)
            }
        }

        // NEAR - the sea surface, islands and a sail or two. There is one sea, so this is drawn
        // for the nearest repeat only; once per repeat stacked a fresh horizon every screen,
        // each one a hard line with the islands of the repeat behind it cut off along it.
        val nearestSea = nearestIdx(camY, 0.24f, h)
        band(camY, 0.24f, h) { idx, baseY ->
            if (idx != nearestSea) return@band
            val seaY = baseY - h * 0.02f
            // islands sitting ON the waterline
            for (i in 0 until 3) {
                val key = idx * 53 + i
                val ix = Hash.f(key, 563) * (worldW + 300f) - 150f
                val iw = 220f + Hash.f(key, 569) * 320f
                val ih = 90f + Hash.f(key, 571) * 150f
                paint.color = ColorX.withAlpha(ColorX.shade(pal.midInk, 0.9f), alpha * 0.7f)
                path.reset()
                path.moveTo(ix - iw, seaY)
                path.quadTo(ix - iw * 0.3f, seaY - ih, ix, seaY - ih * 0.85f)
                path.quadTo(ix + iw * 0.4f, seaY - ih * 0.6f, ix + iw, seaY)
                path.close()
                c.drawPath(path, paint)
                // a palm on the bigger ones
                if (iw > 380f) {
                    ink.color = ColorX.withAlpha(ColorX.shade(pal.nearInk, 0.8f), alpha * 0.7f)
                    ink.strokeWidth = 9f
                    path.reset()
                    path.moveTo(ix, seaY - ih * 0.8f)
                    path.quadTo(ix + 20f, seaY - ih * 1.5f, ix + 44f, seaY - ih * 2.1f)
                    c.drawPath(path, ink)
                    paint.color = ColorX.withAlpha(pal.midInk, alpha * 0.8f)
                    petals(c, ix + 44f, seaY - ih * 2.1f, 60f, 6, 0.4f)
                }
            }
            // sails
            for (i in 0 until 2) {
                val key = idx * 59 + i
                val sx = (Hash.f(key, 577) * worldW + time * 9f) % (worldW + 200f) - 100f
                val s = 60f + Hash.f(key, 587) * 50f
                paint.color = ColorX.withAlpha(0xFFFFFFFF.toInt(), alpha * 0.75f)
                path.reset()
                path.moveTo(sx, seaY - s * 2.1f)
                path.lineTo(sx + s * 0.85f, seaY - 6f)
                path.lineTo(sx - s * 0.2f, seaY - 6f)
                path.close()
                c.drawPath(path, paint)
            }
            // the water itself, filling everything below the line
            paint.color = ColorX.withAlpha(pal.nearInk, alpha * 0.9f)
            rect.set(-40f, seaY, worldW + 40f, deep(seaY + h))
            c.drawRect(rect, paint)
            ink.color = ColorX.withAlpha(0xFFFFFFFF.toInt(), alpha * 0.22f)
            ink.strokeWidth = 7f
            for (i in 0 until 7) {
                val key = idx * 61 + i
                val wy = seaY + 26f + Hash.f(key, 593) * h * 0.35f
                val wx = Hash.f(key, 599) * worldW + sin(time * 0.8f + i) * 22f
                path.reset()
                path.moveTo(wx - 70f, wy)
                path.quadTo(wx, wy - 14f, wx + 70f, wy)
                c.drawPath(path, ink)
            }
        }
    }

    // -----------------------------------------------------------------------------------
    // Dust Run
    // -----------------------------------------------------------------------------------

    /** Stratified buttes: flat-topped mesas in banded rock, not generic snow-capped peaks. */
    fun mesa(c: Canvas, worldW: Float, camY: Float, pal: BiomePalette, alpha: Float, time: Float) {
        paint.reset(); paint.isAntiAlias = true
        val h = Tuning.VIEW_H * 1.1f

        // FAR - a pale rim of distant mesas near the top of the frame
        band(camY, 0.08f, h) { idx, baseY ->
            paint.color = ColorX.withAlpha(ColorX.tint(pal.farInk, 0.45f), alpha * 0.4f)
            var x = -60f
            var i = 0
            while (x < worldW + 60f) {
                val key = idx * 67 + i
                val w = 260f + Hash.f(key, 601) * 300f
                val top = baseY - h * (0.72f + Hash.f(key, 607) * 0.34f)
                path.reset()
                path.moveTo(x, deep(baseY + h))
                path.lineTo(x + w * 0.16f, top)
                path.lineTo(x + w * 0.84f, top)
                path.lineTo(x + w, deep(baseY + h))
                path.close()
                c.drawPath(path, paint)
                x += w * 0.8f
                i++
            }
        }

        // MID+NEAR - the big buttes, each one banded with its rock strata
        band(camY, 0.17f, h) { idx, baseY ->
            val footY = baseY + h * 0.22f
            var x = -140f
            var i = 0
            while (x < worldW + 140f) {
                val key = idx * 71 + i
                val w = 240f + Hash.f(key, 613) * 260f
                val tall = h * (0.26f + Hash.f(key, 617) * 0.28f)
                val top = footY - tall
                // body
                paint.color = ColorX.withAlpha(pal.midInk, alpha * 0.9f)
                path.reset()
                path.moveTo(x, footY)
                path.lineTo(x + w * 0.11f, top)
                path.lineTo(x + w * 0.89f, top)
                path.lineTo(x + w, footY)
                path.close()
                c.drawPath(path, paint)
                // strata - alternating lighter courses across the butte
                val layers = 5
                for (l in 0 until layers) {
                    if (l % 2 == 1) continue
                    val t0 = l / layers.toFloat()
                    val t1 = (l + 0.6f) / layers
                    val y0 = top + tall * t0
                    val y1 = top + tall * t1
                    val in0 = 0.11f * (1f - t0)
                    val in1 = 0.11f * (1f - t1)
                    paint.color = ColorX.withAlpha(
                        ColorX.tint(pal.midInk, 0.18f + Hash.f(key * 5 + l, 619) * 0.16f), alpha * 0.85f
                    )
                    path.reset()
                    path.moveTo(x + w * in0, y0)
                    path.lineTo(x + w * (1f - in0), y0)
                    path.lineTo(x + w * (1f - in1), y1)
                    path.lineTo(x + w * in1, y1)
                    path.close()
                    c.drawPath(path, paint)
                }
                // the shaded face
                paint.color = ColorX.withAlpha(ColorX.shade(pal.midInk, 0.7f), alpha * 0.5f)
                path.reset()
                path.moveTo(x + w * 0.72f, top)
                path.lineTo(x + w * 0.89f, top)
                path.lineTo(x + w, footY)
                path.lineTo(x + w * 0.8f, footY)
                path.close()
                c.drawPath(path, paint)
                // spaced apart, not overlapping: shoulder to shoulder they tiled into one
                // unbroken red wall with no horizon anywhere in the band
                x += w + 120f + Hash.f(key, 631) * 260f
                i++
            }
            // the canyon floor and the boulders that rolled off the buttes
            ground(c, worldW, footY, h, h * 0.03f, ColorX.withAlpha(pal.nearInk, alpha * 0.95f), idx * 3)
            for (k in 0 until 6) {
                val key = idx * 73 + k
                val bx = Hash.f(key, 641) * worldW
                val br = 26f + Hash.f(key, 643) * 46f
                paint.color = ColorX.withAlpha(ColorX.shade(pal.nearInk, 1.2f), alpha * 0.85f)
                rect.set(bx - br, footY - br * 0.9f, bx + br, footY + br * 0.5f)
                c.drawRoundRect(rect, br * 0.5f, br * 0.5f, paint)
            }
            // a lone saguaro or two, for scale
            for (k in 0 until 2) {
                val key = idx * 79 + k
                if (Hash.f(key, 647) < 0.45f) continue
                val cx = Hash.f(key, 653) * worldW
                val ch = 200f + Hash.f(key, 659) * 160f
                paint.color = ColorX.withAlpha(ColorX.shade(pal.farInk, 0.85f), alpha * 0.9f)
                rect.set(cx - 26f, footY - ch, cx + 26f, footY + 10f)
                c.drawRoundRect(rect, 26f, 26f, paint)
                rect.set(cx - 96f, footY - ch * 0.66f, cx - 44f, footY - ch * 0.2f)
                c.drawRoundRect(rect, 26f, 26f, paint)
                rect.set(cx - 96f, footY - ch * 0.66f, cx + 4f, footY - ch * 0.56f)
                c.drawRoundRect(rect, 26f, 26f, paint)
                rect.set(cx + 48f, footY - ch * 0.78f, cx + 100f, footY - ch * 0.34f)
                c.drawRoundRect(rect, 26f, 26f, paint)
                rect.set(cx - 4f, footY - ch * 0.78f, cx + 100f, footY - ch * 0.68f)
                c.drawRoundRect(rect, 26f, 26f, paint)
            }
        }
    }

    /**
     * A wall of blowing sand: streaked dust sheets, grit racing across, tumbleweed, and the
     * buried tops of rocks barely showing through. It was two cloud puffs.
     */
    fun sandstorm(c: Canvas, worldW: Float, camY: Float, pal: BiomePalette, alpha: Float, time: Float) {
        paint.reset(); paint.isAntiAlias = true
        val h = Tuning.VIEW_H * 0.95f

        // FAR - the dust wall itself, long stretched lobes right across the top
        band(camY, 0.1f, h) { idx, baseY ->
            for (row in 0 until 3) {
                val key = idx * 83 + row
                val yy = baseY - h * (0.45f + row * 0.3f)
                // A sheet of blowing dust has a crest but no underside. Filled flat it stopped
                // dead at yy + 200 and ruled a hard line across the whole screen; fading it out
                // over the same distance keeps the sheet exactly as it was and loses the edge.
                val col = ColorX.withAlpha(
                    ColorX.tint(pal.farInk, 0.2f + row * 0.18f), alpha * (0.4f - row * 0.08f)
                )
                paint.shader = LinearGradient(
                    0f, yy - 30f, 0f, yy + 200f,
                    intArrayOf(col, ColorX.withAlpha(col, 0f)), null, Shader.TileMode.CLAMP
                )
                path.reset()
                path.moveTo(-60f, yy + 200f)
                var x = -60f
                var k = 0
                while (x < worldW + 80f) {
                    val span = 200f + Hash.f(key * 11 + k, 661) * 220f
                    val rise = 120f + Hash.f(key * 11 + k, 673) * 190f
                    path.quadTo(x + span * 0.5f, yy - rise + sin(time * 0.5f + k) * 18f, x + span, yy)
                    x += span
                    k++
                }
                path.lineTo(x, yy + 200f)
                path.close()
                c.drawPath(path, paint)
                paint.shader = null
            }
        }

        // MID - grit streaks tearing across the frame
        band(camY, 0.22f, h) { idx, baseY ->
            ink.color = ColorX.withAlpha(ColorX.tint(pal.midInk, 0.35f), alpha * 0.4f)
            for (i in 0 until 14) {
                val key = idx * 89 + i
                val sy = baseY - Hash.f(key, 677) * h
                val len = 120f + Hash.f(key, 683) * 260f
                val sx = (Hash.f(key, 691) * (worldW + 400f) + time * (220f + Hash.f(key, 701) * 260f)) %
                    (worldW + 400f) - 200f
                ink.strokeWidth = 4f + Hash.f(key, 709) * 7f
                path.reset()
                path.moveTo(sx, sy)
                path.quadTo(sx + len * 0.5f, sy - 14f, sx + len, sy)
                c.drawPath(path, ink)
            }
        }

        // NEAR - drifted sand, half-buried rocks, and tumbleweed bowling past
        band(camY, 0.3f, h) { idx, baseY ->
            val footY = baseY + h * 0.08f
            ground(
                c, worldW, footY, h, h * 0.06f,
                ColorX.withAlpha(ColorX.tint(pal.nearInk, 0.1f), alpha * 0.9f), idx * 7
            )
            for (i in 0 until 5) {
                val key = idx * 97 + i
                val rx = Hash.f(key, 719) * worldW
                val rw = 70f + Hash.f(key, 727) * 110f
                paint.color = ColorX.withAlpha(ColorX.shade(pal.midInk, 0.85f), alpha * 0.8f)
                rect.set(rx - rw, footY - rw * 0.62f, rx + rw, footY + rw * 0.5f)
                c.drawOval(rect, paint)
            }
            // and a second, nearer drift over the rocks so they read as half buried
            ground(
                c, worldW, footY + 54f, h, h * 0.045f,
                ColorX.withAlpha(ColorX.tint(pal.nearInk, 0.3f), alpha * 0.95f), idx * 7 + 1
            )
            for (i in 0 until 3) {
                val key = idx * 101 + i
                val tw = 54f + Hash.f(key, 733) * 40f
                val tx = (Hash.f(key, 739) * (worldW + 300f) + time * (150f + Hash.f(key, 743) * 120f)) %
                    (worldW + 300f) - 150f
                val ty = footY - tw - kotlin.math.abs(sin(time * 2.4f + i)) * 90f
                ink.color = ColorX.withAlpha(ColorX.shade(pal.platShade, 1.15f), alpha * 0.85f)
                ink.strokeWidth = 5f
                for (s in 0 until 6) {
                    val a = time * 2.4f + s * 0.52f + i
                    path.reset()
                    path.moveTo(tx + cos(a) * tw, ty + sin(a) * tw)
                    path.quadTo(tx + cos(a + 1.6f) * tw * 0.3f, ty + sin(a + 1.6f) * tw * 0.3f,
                        tx + cos(a + 3.1f) * tw, ty + sin(a + 3.1f) * tw)
                    c.drawPath(path, ink)
                }
            }
        }
    }

    /**
     * The mirage band: a false lake shimmering on the flats with palms and a far caravan, the
     * whole thing sitting under a heat haze. It was the backyard's green hills in sand colours.
     */
    fun oasis(c: Canvas, worldW: Float, camY: Float, pal: BiomePalette, alpha: Float, time: Float) {
        paint.reset(); paint.isAntiAlias = true
        val h = Tuning.VIEW_H * 1.0f

        // FAR - a wobbling heat line where the sky meets the flats
        band(camY, 0.09f, h) { idx, baseY ->
            val hz = baseY - h * 0.78f
            paint.color = ColorX.withAlpha(ColorX.tint(pal.farInk, 0.4f), alpha * 0.35f)
            for (row in 0 until 4) {
                val yy = hz + row * 34f
                path.reset()
                path.moveTo(-40f, yy)
                var x = -40f
                var k = 0
                while (x < worldW + 60f) {
                    val span = 210f + Hash.f(idx * 13 + row * 5 + k, 751) * 150f
                    path.quadTo(x + span * 0.5f, yy + sin(time * 1.4f + k + row) * 16f - 12f, x + span, yy)
                    x += span
                    k++
                }
                path.lineTo(x, yy + 18f)
                path.lineTo(-40f, yy + 18f)
                path.close()
                c.drawPath(path, paint)
            }
        }

        // MID - the caravan strung out along the horizon
        band(camY, 0.15f, h) { idx, baseY ->
            val hz = baseY - h * 0.42f
            paint.color = ColorX.withAlpha(ColorX.shade(pal.midInk, 0.65f), alpha * 0.55f)
            for (i in 0 until 4) {
                val key = idx * 103 + i
                val cx = (Hash.f(key, 757) * worldW + time * 6f) % (worldW + 160f) - 80f
                val s = 34f + Hash.f(key, 761) * 16f
                // a camel: body, two humps, neck, legs
                rect.set(cx - s * 1.4f, hz - s * 0.9f, cx + s * 1.1f, hz - s * 0.2f)
                c.drawRoundRect(rect, s * 0.35f, s * 0.35f, paint)
                c.drawCircle(cx - s * 0.5f, hz - s * 1.05f, s * 0.42f, paint)
                c.drawCircle(cx + s * 0.4f, hz - s * 1.05f, s * 0.38f, paint)
                ink.color = paint.color
                ink.strokeWidth = s * 0.26f
                path.reset()
                path.moveTo(cx + s * 1.0f, hz - s * 0.6f)
                path.quadTo(cx + s * 1.7f, hz - s * 1.5f, cx + s * 1.5f, hz - s * 1.9f)
                c.drawPath(path, ink)
                for (l in 0 until 4) {
                    val lx = cx - s * 1.1f + l * s * 0.72f
                    path.reset()
                    path.moveTo(lx, hz - s * 0.25f)
                    path.lineTo(lx + sin(time * 3f + l) * s * 0.18f, hz)
                    c.drawPath(path, ink)
                }
            }
        }

        // NEAR - the false lake, the palms round it, and the flats it lies on
        band(camY, 0.23f, h) { idx, baseY ->
            val footY = baseY + h * 0.02f
            // the flats are sand; the green belongs to the oasis, not to the whole band
            ground(
                c, worldW, footY, h, h * 0.035f,
                ColorX.withAlpha(ColorX.lerp(pal.nearInk, pal.platTop, 0.65f), alpha * 0.92f), idx * 3
            )
            // the green ring of growth around the water, and nowhere else
            paint.color = ColorX.withAlpha(pal.midInk, alpha * 0.8f)
            rect.set(
                worldW * 0.5f - worldW * 0.42f, footY - 150f,
                worldW * 0.5f + worldW * 0.42f, footY + 190f
            )
            c.drawOval(rect, paint)

            val lakeY = footY - 40f
            val lakeW = worldW * (0.5f + Hash.f(idx, 769) * 0.35f)
            val lakeX = Hash.f(idx, 773) * (worldW - lakeW * 0.4f)
            paint.color = ColorX.withAlpha(ColorX.tint(pal.rimInk, 0.35f), alpha * 0.45f)
            rect.set(lakeX - lakeW * 0.5f, lakeY - 54f, lakeX + lakeW * 0.5f, lakeY + 54f)
            c.drawOval(rect, paint)
            ink.color = ColorX.withAlpha(0xFFFFFFFF.toInt(), alpha * 0.35f)
            ink.strokeWidth = 5f
            for (i in 0 until 5) {
                val wy = lakeY - 34f + i * 17f
                val ww = lakeW * 0.5f * (1f - kotlin.math.abs(i - 2) * 0.22f)
                path.reset()
                path.moveTo(lakeX - ww, wy)
                path.quadTo(lakeX, wy + sin(time * 1.6f + i) * 8f - 5f, lakeX + ww, wy)
                c.drawPath(path, ink)
            }

            for (i in 0 until 4) {
                val key = idx * 107 + i
                val px = Hash.f(key, 787) * worldW
                val ph = 280f + Hash.f(key, 797) * 230f
                val lean = sin(time * 0.35f + i) * 24f
                ink.color = ColorX.withAlpha(ColorX.shade(pal.platShade, 1.1f), alpha * 0.9f)
                ink.strokeWidth = 18f
                path.reset()
                path.moveTo(px, footY)
                path.quadTo(px + lean * 0.6f, footY - ph * 0.55f, px + lean, footY - ph)
                c.drawPath(path, ink)
                paint.color = ColorX.withAlpha(pal.midInk, alpha * 0.9f)
                for (frond in 0 until 7) {
                    val a = -2.6f + frond * 0.42f
                    path.reset()
                    path.moveTo(px + lean, footY - ph)
                    path.quadTo(
                        px + lean + cos(a) * 110f, footY - ph + sin(a) * 90f - 30f,
                        px + lean + cos(a) * 190f, footY - ph + sin(a) * 150f + 26f
                    )
                    path.quadTo(px + lean + cos(a) * 100f, footY - ph + sin(a) * 60f, px + lean, footY - ph)
                    path.close()
                    c.drawPath(path, paint)
                }
                paint.color = ColorX.withAlpha(ColorX.shade(pal.accentInk, 0.9f), alpha * 0.9f)
                for (d in 0 until 3) {
                    c.drawCircle(px + lean + (d - 1) * 26f, footY - ph + 34f, 13f, paint)
                }
            }
        }
    }

    // -----------------------------------------------------------------------------------
    // Frozen Peaks
    // -----------------------------------------------------------------------------------

    /** The snowfield floor: deep drifts, buried fence posts, frozen scrub, a far white range. */
    fun snowfield(c: Canvas, worldW: Float, camY: Float, pal: BiomePalette, alpha: Float, time: Float) {
        paint.reset(); paint.isAntiAlias = true
        val h = Tuning.VIEW_H * 1.05f

        // FAR - the range, pale and flat, high in the frame
        band(camY, 0.08f, h) { idx, baseY ->
            paint.color = ColorX.withAlpha(ColorX.tint(pal.farInk, 0.55f), alpha * 0.4f)
            path.reset()
            path.moveTo(-60f, deep(baseY + h))
            var x = -60f
            val step = worldW / 3f
            var i = 0
            while (x < worldW + 60f) {
                path.lineTo(x + step * 0.5f, baseY - h * (0.5f + Hash.f(idx * 23 + i, 801) * 0.45f))
                path.lineTo(x + step, baseY - h * 0.1f)
                x += step
                i++
            }
            path.lineTo(worldW + 60f, deep(baseY + h))
            path.close()
            c.drawPath(path, paint)
        }

        // MID - a rolling drift line with scrub poking out of it
        band(camY, 0.16f, h) { idx, baseY ->
            mounds(
                c, worldW, baseY - h * 0.1f, 280f,
                ColorX.withAlpha(ColorX.tint(pal.midInk, 0.45f), alpha * 0.75f), idx, 4
            )
            paint.color = ColorX.withAlpha(ColorX.shade(pal.nearInk, 0.75f), alpha * 0.6f)
            for (i in 0 until 8) {
                val key = idx * 29 + i
                val sx = Hash.f(key, 809) * worldW
                val sy = baseY - h * 0.08f
                val sh = 40f + Hash.f(key, 811) * 60f
                ink.color = paint.color
                ink.strokeWidth = 6f
                for (b in 0 until 3) {
                    path.reset()
                    path.moveTo(sx, sy)
                    path.quadTo(sx + (b - 1) * 26f, sy - sh * 0.6f, sx + (b - 1) * 44f, sy - sh)
                    c.drawPath(path, ink)
                }
            }
        }

        // NEAR - the drift you stand on, fence posts, and snow falling in front
        band(camY, 0.26f, h) { idx, baseY ->
            val footY = baseY + h * 0.04f
            ground(
                c, worldW, footY, h, h * 0.07f,
                ColorX.withAlpha(ColorX.tint(pal.midInk, 0.78f), alpha * 0.95f), idx * 5
            )
            // a buried fence: posts of dwindling height, wire sagging between them
            val postTop = footY - 190f
            paint.color = ColorX.withAlpha(ColorX.shade(pal.platShade, 1.05f), alpha * 0.9f)
            var x = -20f
            var i = 0
            while (x < worldW + 40f) {
                val key = idx * 31 + i
                val drop = Hash.f(key, 821) * 90f
                rect.set(x - 15f, postTop + drop, x + 15f, footY + 30f)
                c.drawRect(rect, paint)
                // the cap of snow that settled on it
                paint.color = ColorX.withAlpha(0xFFFFFFFF.toInt(), alpha * 0.8f)
                rect.set(x - 21f, postTop + drop - 16f, x + 21f, postTop + drop + 12f)
                c.drawRoundRect(rect, 12f, 12f, paint)
                paint.color = ColorX.withAlpha(ColorX.shade(pal.platShade, 1.05f), alpha * 0.9f)
                x += 250f
                i++
            }
            ink.color = ColorX.withAlpha(ColorX.shade(pal.platShade, 1.3f), alpha * 0.55f)
            ink.strokeWidth = 4f
            for (wire in 0 until 2) {
                val wy = postTop + 60f + wire * 62f
                path.reset()
                path.moveTo(-20f, wy)
                var wx = -20f
                while (wx < worldW + 40f) {
                    path.quadTo(wx + 125f, wy + 40f, wx + 250f, wy)
                    wx += 250f
                }
                c.drawPath(path, ink)
            }
            // a final drift in front, so the posts sit IN the snow
            ground(
                c, worldW, footY + 70f, h, h * 0.05f,
                ColorX.withAlpha(0xFFFFFFFF.toInt(), alpha * 0.75f), idx * 5 + 1
            )
            paint.color = ColorX.withAlpha(0xFFFFFFFF.toInt(), alpha * 0.6f)
            for (i in 0 until 18) {
                val key = idx * 37 + i
                val fx = (Hash.f(key, 823) * worldW + sin(time * 0.7f + i) * 36f)
                val fy = baseY - ((Hash.f(key, 827) * h + time * 40f) % h)
                c.drawCircle(fx, fy, 3f + Hash.f(key, 829) * 4f, paint)
            }
        }
    }

    // -----------------------------------------------------------------------------------
    // Sugar Rush
    // -----------------------------------------------------------------------------------

    /** The bakery: tiered cakes, a rolling pin, cookies and an icing floor. */
    fun bakery(c: Canvas, worldW: Float, camY: Float, pal: BiomePalette, alpha: Float, time: Float) {
        paint.reset(); paint.isAntiAlias = true
        val h = Tuning.VIEW_H * 1.0f

        // FAR - macarons and doughnuts floating high, so the sky is not blank pink
        band(camY, 0.1f, h) { idx, baseY ->
            for (i in 0 until 5) {
                val key = idx * 41 + i
                val cx = Hash.f(key, 831) * worldW
                val cy = baseY - h * (0.62f + Hash.f(key, 839) * 0.45f) + sin(time * 0.6f + i) * 20f
                val r = 44f + Hash.f(key, 853) * 46f
                if (i % 2 == 0) {
                    paint.color = ColorX.withAlpha(ColorX.tint(pal.farInk, 0.3f), alpha * 0.55f)
                    rect.set(cx - r, cy - r * 0.95f, cx + r, cy + r * 0.95f)
                    c.drawOval(rect, paint)
                    paint.color = ColorX.withAlpha(pal.skyLow, alpha * 0.5f)
                    c.drawCircle(cx, cy, r * 0.34f, paint)
                    paint.color = ColorX.withAlpha(0xFFFFFFFF.toInt(), alpha * 0.45f)
                    path.reset()
                    path.moveTo(cx - r, cy - r * 0.2f)
                    var x = cx - r
                    var k = 0
                    while (x < cx + r) {
                        path.quadTo(x + r * 0.22f, cy - r * 0.62f, x + r * 0.44f, cy - r * 0.2f)
                        x += r * 0.44f
                        k++
                    }
                    path.lineTo(cx + r, cy + r * 0.3f)
                    path.lineTo(cx - r, cy + r * 0.3f)
                    path.close()
                    c.drawPath(path, paint)
                } else {
                    paint.color = ColorX.withAlpha(ColorX.tint(pal.midInk, 0.3f), alpha * 0.5f)
                    rect.set(cx - r, cy - r * 0.6f, cx + r, cy + r * 0.6f)
                    c.drawRoundRect(rect, r * 0.5f, r * 0.5f, paint)
                    paint.color = ColorX.withAlpha(ColorX.shade(pal.midInk, 0.75f), alpha * 0.5f)
                    rect.set(cx - r, cy - r * 0.02f, cx + r, cy + r * 0.14f)
                    c.drawRect(rect, paint)
                }
            }
        }

        // MID - a shelf of tiered cakes
        band(camY, 0.18f, h) { idx, baseY ->
            val shelfY = baseY - h * 0.22f
            paint.color = ColorX.withAlpha(ColorX.shade(pal.platBody, 1.0f), alpha * 0.75f)
            rect.set(-40f, shelfY, worldW + 40f, shelfY + 46f)
            c.drawRect(rect, paint)
            for (i in 0 until 4) {
                val key = idx * 43 + i
                val cx = (i + 0.5f) * worldW / 4f + Hash.f(key, 857) * 90f - 45f
                val cw = 130f + Hash.f(key, 859) * 90f
                val tiers = 2 + (Hash.f(key, 863) * 2f).toInt()
                var y = shelfY
                for (t in 0 until tiers) {
                    val w = cw * (1f - t * 0.24f)
                    val th = 78f - t * 12f
                    paint.color = ColorX.withAlpha(
                        if (t % 2 == 0) ColorX.tint(pal.nearInk, 0.2f) else pal.midInk, alpha * 0.9f
                    )
                    rect.set(cx - w, y - th, cx + w, y)
                    c.drawRoundRect(rect, 14f, 14f, paint)
                    // icing running over the lip of each tier
                    paint.color = ColorX.withAlpha(0xFFFFFFFF.toInt(), alpha * 0.7f)
                    path.reset()
                    path.moveTo(cx - w, y - th)
                    path.lineTo(cx + w, y - th)
                    path.lineTo(cx + w, y - th + 16f)
                    var x = cx + w
                    var k = 0
                    while (x > cx - w) {
                        val drop = 12f + Hash.f(key * 7 + k, 877) * 26f
                        path.quadTo(x - 22f, y - th + 16f + drop, x - 44f, y - th + 16f)
                        x -= 44f
                        k++
                    }
                    path.lineTo(cx - w, y - th)
                    path.close()
                    c.drawPath(path, paint)
                    y -= th
                }
                // a cherry on top
                paint.color = ColorX.withAlpha(pal.rimInk, alpha * 0.95f)
                c.drawCircle(cx, y - 24f, 22f, paint)
            }
        }

        // NEAR - the icing floor, scattered cookies and a rolling pin
        band(camY, 0.27f, h) { idx, baseY ->
            val footY = baseY + h * 0.05f
            ground(
                c, worldW, footY, h, h * 0.035f,
                ColorX.withAlpha(ColorX.tint(pal.nearInk, 0.35f), alpha * 0.95f), idx * 7
            )
            // sugar dusting on the floor
            paint.color = ColorX.withAlpha(0xFFFFFFFF.toInt(), alpha * 0.35f)
            for (i in 0 until 22) {
                val key = idx * 47 + i
                c.drawCircle(
                    Hash.f(key, 881) * worldW, footY + 14f + Hash.f(key, 883) * 110f,
                    3f + Hash.f(key, 887) * 5f, paint
                )
            }
            // cookies, on their edges and flat
            for (i in 0 until 6) {
                val key = idx * 53 + i
                val cx = Hash.f(key, 907) * worldW
                val r = 52f + Hash.f(key, 911) * 40f
                paint.color = ColorX.withAlpha(ColorX.shade(pal.platBody, 1.05f), alpha * 0.9f)
                c.drawCircle(cx, footY - r * 0.55f, r, paint)
                paint.color = ColorX.withAlpha(ColorX.shade(pal.platShade, 0.75f), alpha * 0.9f)
                for (chip in 0 until 5) {
                    val a = Hash.f(key * 11 + chip, 919) * 6.2832f
                    val d = Hash.f(key * 11 + chip, 929) * r * 0.62f
                    c.drawCircle(cx + cos(a) * d, footY - r * 0.55f + sin(a) * d, r * 0.15f, paint)
                }
            }
            // the rolling pin
            val rx = Hash.f(idx, 937) * worldW
            paint.color = ColorX.withAlpha(ColorX.tint(pal.platTop, 0.3f), alpha * 0.95f)
            rect.set(rx - 200f, footY - 108f, rx + 200f, footY - 40f)
            c.drawRoundRect(rect, 34f, 34f, paint)
            rect.set(rx - 268f, footY - 88f, rx - 196f, footY - 60f)
            c.drawRoundRect(rect, 14f, 14f, paint)
            rect.set(rx + 196f, footY - 88f, rx + 268f, footY - 60f)
            c.drawRoundRect(rect, 14f, 14f, paint)
        }
    }

    /** Twisted liquorice towers, lollipops on sticks and candy canes leaning across. */
    fun liquorice(c: Canvas, worldW: Float, camY: Float, pal: BiomePalette, alpha: Float, time: Float) {
        paint.reset(); paint.isAntiAlias = true
        val h = Tuning.VIEW_H * 1.1f

        // FAR - lollipops on long sticks, heads up in the sky
        band(camY, 0.1f, h) { idx, baseY ->
            for (i in 0 until 4) {
                val key = idx * 59 + i
                val lx = Hash.f(key, 941) * worldW
                val lh = h * (0.5f + Hash.f(key, 947) * 0.5f)
                val r = 90f + Hash.f(key, 953) * 80f
                ink.color = ColorX.withAlpha(0xFFFFFFFF.toInt(), alpha * 0.4f)
                ink.strokeWidth = 16f
                path.reset()
                path.moveTo(lx, baseY + h * 0.3f)
                path.lineTo(lx, baseY - lh)
                c.drawPath(path, ink)
                // a spiral head: rings alternating the band's two brightest colours
                for (ring in 4 downTo 0) {
                    paint.color = ColorX.withAlpha(
                        if (ring % 2 == 0) pal.farInk else ColorX.tint(pal.rimInk, 0.35f), alpha * 0.55f
                    )
                    c.drawCircle(lx, baseY - lh - r, r * (1f - ring * 0.18f), paint)
                }
            }
        }

        // MID+NEAR - the twisted liquorice towers themselves
        band(camY, 0.19f, h) { idx, baseY ->
            val footY = baseY + h * 0.2f
            var x = -100f
            var i = 0
            while (x < worldW + 100f) {
                val key = idx * 61 + i
                val w = 82f + Hash.f(key, 967) * 78f
                val tall = h * (0.34f + Hash.f(key, 971) * 0.36f)
                val twist = 0.7f + Hash.f(key, 977) * 1.1f
                // the tower is a stack of slabs, each offset by a sine so it corkscrews
                val slabs = 11
                for (s in slabs - 1 downTo 0) {
                    val t = s / (slabs - 1f)
                    val sy = footY - tall * t
                    val off = sin(t * 6.2832f * twist + idx) * w * 0.34f
                    paint.color = ColorX.withAlpha(
                        if (s % 2 == 0) ColorX.shade(pal.nearInk, 0.65f) else ColorX.tint(pal.midInk, 0.25f),
                        alpha * 0.92f
                    )
                    rect.set(x + off, sy - tall / slabs * 1.25f, x + off + w, sy + 6f)
                    c.drawRoundRect(rect, w * 0.25f, w * 0.25f, paint)
                }
                x += w + 190f + Hash.f(key, 983) * 220f
                i++
            }
            // the liquorice floor. Shaded to 0.7 it was a near-black slab under half the band;
            // liquorice is dark, but it is not a hole in the screen.
            ground(
                c, worldW, footY, h, h * 0.03f,
                ColorX.withAlpha(ColorX.lerp(pal.nearInk, pal.midInk, 0.45f), alpha * 0.95f), idx * 3
            )
            // candy canes leaning out of the floor
            for (k in 0 until 4) {
                val key = idx * 67 + k
                val cx = Hash.f(key, 991) * worldW
                val ch = 250f + Hash.f(key, 997) * 200f
                val lean = (Hash.f(key, 1009) - 0.5f) * 90f
                ink.strokeCap = Paint.Cap.BUTT
                for (pass in 0 until 2) {
                    ink.color = ColorX.withAlpha(
                        if (pass == 0) 0xFFFFFFFF.toInt() else pal.rimInk, alpha * 0.95f
                    )
                    ink.strokeWidth = if (pass == 0) 34f else 12f
                    path.reset()
                    path.moveTo(cx, footY + 10f)
                    path.lineTo(cx + lean, footY - ch * 0.72f)
                    path.quadTo(cx + lean + 10f, footY - ch, cx + lean - 80f, footY - ch * 0.94f)
                    c.drawPath(path, ink)
                    if (pass == 0) continue
                    // the stripes: short dashes across the cane
                    for (s in 0 until 7) {
                        val t = s / 7f
                        val sx = cx + lean * t
                        val sy = footY + 10f - ch * 0.72f * t
                        ink.strokeWidth = 34f
                        path.reset()
                        path.moveTo(sx - 16f, sy + 10f)
                        path.lineTo(sx + 16f, sy - 10f)
                        c.drawPath(path, ink)
                    }
                }
                ink.strokeCap = Paint.Cap.ROUND
            }
        }
    }

    /** Candyfloss: spun pink masses on paper cones, gumballs and a fall of sprinkles. */
    fun candyfloss(c: Canvas, worldW: Float, camY: Float, pal: BiomePalette, alpha: Float, time: Float) {
        paint.reset(); paint.isAntiAlias = true
        val h = Tuning.VIEW_H * 0.9f

        // FAR - big soft floss masses
        band(camY, 0.1f, h) { idx, baseY ->
            for (i in 0 until 4) {
                val key = idx * 71 + i
                val cx = Hash.f(key, 1013) * (worldW + 300f) - 150f
                val cy = baseY - h * (0.55f + Hash.f(key, 1019) * 0.55f)
                val s = 200f + Hash.f(key, 1021) * 180f
                paint.color = ColorX.withAlpha(
                    if (i % 2 == 0) ColorX.tint(pal.farInk, 0.35f) else ColorX.tint(pal.midInk, 0.3f),
                    alpha * 0.45f
                )
                for (lobe in 0 until 7) {
                    val a = lobe * 0.9f + idx
                    c.drawCircle(cx + cos(a) * s * 0.62f, cy + sin(a) * s * 0.42f, s * 0.5f, paint)
                }
                paint.color = ColorX.withAlpha(0xFFFFFFFF.toInt(), alpha * 0.2f)
                c.drawCircle(cx - s * 0.2f, cy - s * 0.25f, s * 0.4f, paint)
            }
        }

        // MID - floss on paper cones, hanging from above like pink trees
        band(camY, 0.18f, h) { idx, baseY ->
            for (i in 0 until 4) {
                val key = idx * 73 + i
                val cx = Hash.f(key, 1031) * worldW
                val cy = baseY - h * (0.25f + Hash.f(key, 1033) * 0.4f)
                val s = 120f + Hash.f(key, 1039) * 90f
                paint.color = ColorX.withAlpha(ColorX.tint(pal.platTop, 0.2f), alpha * 0.85f)
                path.reset()
                path.moveTo(cx - s * 0.28f, cy)
                path.lineTo(cx + s * 0.28f, cy)
                path.lineTo(cx, cy + s * 1.5f)
                path.close()
                c.drawPath(path, paint)
                paint.color = ColorX.withAlpha(pal.rimInk, alpha * 0.85f)
                for (lobe in 0 until 6) {
                    val a = lobe * 1.05f
                    c.drawCircle(cx + cos(a) * s * 0.55f, cy - s * 0.2f + sin(a) * s * 0.4f, s * 0.48f, paint)
                }
            }
        }

        // NEAR - gumballs bobbing along the bottom, and sprinkles raining through
        band(camY, 0.26f, h) { idx, baseY ->
            for (i in 0 until 7) {
                val key = idx * 79 + i
                val gx = Hash.f(key, 1049) * worldW
                val gy = baseY - h * 0.02f - Hash.f(key, 1051) * h * 0.2f + sin(time * 0.8f + i) * 22f
                val r = 44f + Hash.f(key, 1061) * 40f
                paint.color = ColorX.withAlpha(
                    when (i % 4) {
                        0 -> pal.rimInk
                        1 -> pal.accentInk
                        2 -> pal.midInk
                        else -> ColorX.tint(pal.farInk, 0.3f)
                    },
                    alpha * 0.9f
                )
                c.drawCircle(gx, gy, r, paint)
                paint.color = ColorX.withAlpha(0xFFFFFFFF.toInt(), alpha * 0.4f)
                c.drawCircle(gx - r * 0.3f, gy - r * 0.35f, r * 0.26f, paint)
            }
            for (i in 0 until 24) {
                val key = idx * 83 + i
                val sx = Hash.f(key, 1063) * worldW + sin(time * 0.5f + i) * 26f
                val sy = baseY - ((Hash.f(key, 1069) * h + time * 60f) % h)
                paint.color = ColorX.withAlpha(
                    when (i % 4) {
                        0 -> pal.rimInk
                        1 -> pal.accentInk
                        2 -> 0xFFFFFFFF.toInt()
                        else -> pal.midInk
                    },
                    alpha * 0.8f
                )
                rect.set(sx - 5f, sy - 15f, sx + 5f, sy + 15f)
                c.drawRoundRect(rect, 5f, 5f, paint)
            }
        }
    }

    // -----------------------------------------------------------------------------------
    // Neon City
    // -----------------------------------------------------------------------------------

    /** Street level: a brick back wall, fire escapes, dumpsters, signage and a wet road. */
    fun alley(c: Canvas, worldW: Float, camY: Float, pal: BiomePalette, alpha: Float, time: Float) {
        paint.reset(); paint.isAntiAlias = true
        val h = Tuning.VIEW_H * 1.0f

        // FAR - the tops of the buildings the alley runs between
        band(camY, 0.1f, h) { idx, baseY ->
            paint.color = ColorX.withAlpha(ColorX.shade(pal.nearInk, 1.35f), alpha * 0.55f)
            var x = -60f
            var i = 0
            while (x < worldW + 60f) {
                val key = idx * 89 + i
                val w = 200f + Hash.f(key, 1087) * 220f
                val hh = h * (0.55f + Hash.f(key, 1091) * 0.6f)
                rect.set(x, baseY - hh, x + w, deep(baseY + h))
                c.drawRect(rect, paint)
                x += w + 20f
                i++
            }
        }

        // MID - the wall itself, with its fire escape and its windows
        val nearestAlley = nearestIdx(camY, 0.22f, h)
        band(camY, 0.22f, h) { idx, baseY ->
            val wallTop = baseY - h * 0.95f
            val footY = baseY + h * 0.08f
            // The wall of the repeat behind this one is the same wall, so where this one starts
            // there should be nothing to see. At 95% opaque and a straight top edge there was: a
            // rule across the screen, with the dumpsters and blocks of the repeat behind it
            // sliced off along it. Fading the top in over a couple of brick courses hides the
            // join and leaves the wall itself exactly as it was.
            val brick = ColorX.withAlpha(pal.nearInk, alpha * 0.95f)
            paint.shader = LinearGradient(
                0f, wallTop, 0f, wallTop + 190f,
                intArrayOf(ColorX.withAlpha(brick, 0f), brick), null, Shader.TileMode.CLAMP
            )
            rect.set(-40f, wallTop, worldW + 40f, footY)
            c.drawRect(rect, paint)
            paint.shader = null
            // courses of brick
            paint.color = ColorX.withAlpha(ColorX.shade(pal.nearInk, 1.15f), alpha * 0.5f)
            var by = wallTop
            var row = 0
            while (by < footY) {
                var bx = -40f + (row % 2) * 60f
                while (bx < worldW + 40f) {
                    rect.set(bx, by, bx + 108f, by + 46f)
                    c.drawRect(rect, paint)
                    bx += 120f
                }
                by += 58f
                row++
            }
            // windows, some lit
            for (i in 0 until 8) {
                val key = idx * 97 + i
                val wx = Hash.f(key, 1093) * (worldW - 140f) + 20f
                val wy = wallTop + 120f + Hash.f(key, 1097) * (footY - wallTop - 420f)
                val lit = Hash.f(key, 1103) > 0.5f
                paint.color = ColorX.withAlpha(
                    if (lit) pal.accentInk else ColorX.shade(pal.nearInk, 0.5f),
                    alpha * (if (lit) 0.7f else 0.9f)
                )
                rect.set(wx, wy, wx + 110f, wy + 150f)
                c.drawRect(rect, paint)
            }
            // fire escape: platforms and the zigzag ladder between them
            val fx = Hash.f(idx, 1109) * (worldW - 400f) + 60f
            ink.color = ColorX.withAlpha(ColorX.shade(pal.platShade, 1.2f), alpha * 0.9f)
            ink.strokeWidth = 9f
            for (lvl in 0 until 3) {
                val ly = footY - 420f - lvl * 330f
                if (ly < wallTop) continue
                rect.set(fx, ly, fx + 330f, ly + 16f)
                paint.color = ColorX.withAlpha(ColorX.shade(pal.platShade, 1.05f), alpha * 0.9f)
                c.drawRect(rect, paint)
                for (rail in 0 until 7) {
                    path.reset()
                    path.moveTo(fx + rail * 55f, ly)
                    path.lineTo(fx + rail * 55f, ly - 90f)
                    c.drawPath(path, ink)
                }
                path.reset()
                path.moveTo(fx, ly - 90f)
                path.lineTo(fx + 330f, ly - 90f)
                c.drawPath(path, ink)
                path.reset()
                path.moveTo(fx + (if (lvl % 2 == 0) 40f else 290f), ly + 16f)
                path.lineTo(fx + (if (lvl % 2 == 0) 290f else 40f), ly + 330f)
                c.drawPath(path, ink)
            }
            // a vertical neon sign bolted to the wall
            val sx = if (fx > worldW * 0.5f) worldW * 0.12f else worldW * 0.78f
            paint.color = ColorX.withAlpha(ColorX.shade(pal.platShade, 0.6f), alpha * 0.9f)
            rect.set(sx - 46f, footY - 780f, sx + 46f, footY - 300f)
            c.drawRoundRect(rect, 12f, 12f, paint)
            val flicker = 0.55f + 0.45f * sin(time * 3.1f + idx)
            paint.color = ColorX.withAlpha(pal.rimInk, alpha * 0.9f * flicker)
            for (k in 0 until 4) {
                rect.set(sx - 28f, footY - 740f + k * 112f, sx + 28f, footY - 660f + k * 112f)
                c.drawRoundRect(rect, 10f, 10f, paint)
            }
            art.drawGlow(c, sx, footY - 540f, 260f, pal.rimInk, alpha * 0.35f * flicker)

            // NEAR - the road, the dumpsters and the puddle. Only for the repeat you are
            // actually standing above: every repeat drew its own, so a dark band of roadway
            // crossed the screen once a screen with the bins of the repeat behind it sliced
            // off along the top edge.
            if (idx != nearestAlley) return@band
            paint.color = ColorX.withAlpha(ColorX.shade(pal.nearInk, 0.55f), alpha * 0.95f)
            rect.set(-40f, footY, worldW + 40f, deep(footY + h))
            c.drawRect(rect, paint)
            for (i in 0 until 2) {
                val key = idx * 101 + i
                val dx = Hash.f(key, 1117) * (worldW - 260f) + 60f
                paint.color = ColorX.withAlpha(ColorX.shade(pal.midInk, 0.8f), alpha * 0.95f)
                rect.set(dx - 130f, footY - 175f, dx + 130f, footY + 10f)
                c.drawRoundRect(rect, 12f, 12f, paint)
                paint.color = ColorX.withAlpha(ColorX.shade(pal.midInk, 1.15f), alpha * 0.95f)
                rect.set(dx - 142f, footY - 200f, dx + 142f, footY - 168f)
                c.drawRoundRect(rect, 10f, 10f, paint)
            }
            // rubbish bags
            paint.color = ColorX.withAlpha(0xFF12141C.toInt(), alpha * 0.85f)
            for (i in 0 until 4) {
                val key = idx * 103 + i
                val gx = Hash.f(key, 1123) * worldW
                val gr = 40f + Hash.f(key, 1129) * 28f
                c.drawCircle(gx, footY - gr * 0.6f, gr, paint)
            }
            // the puddle, with the sign reflected in it
            val px = Hash.f(idx, 1151) * (worldW - 300f) + 150f
            paint.color = ColorX.withAlpha(ColorX.tint(pal.skyLow, 0.1f), alpha * 0.4f)
            rect.set(px - 230f, footY + 60f, px + 230f, footY + 132f)
            c.drawOval(rect, paint)
            paint.color = ColorX.withAlpha(pal.rimInk, alpha * 0.28f * flicker)
            rect.set(px - 40f, footY + 70f, px + 40f, footY + 124f)
            c.drawOval(rect, paint)
        }
    }

    /** Up among the roof furniture: water tanks, AC units, aerials, vents and a pigeon or two. */
    fun rooftops(c: Canvas, worldW: Float, camY: Float, pal: BiomePalette, alpha: Float, time: Float) {
        paint.reset(); paint.isAntiAlias = true
        val h = Tuning.VIEW_H * 1.05f

        // FAR - the towers behind, lit windows only
        band(camY, 0.11f, h) { idx, baseY ->
            var x = -60f
            var i = 0
            while (x < worldW + 60f) {
                val key = idx * 107 + i
                val w = 150f + Hash.f(key, 1153) * 190f
                val hh = h * (0.45f + Hash.f(key, 1163) * 0.75f)
                paint.color = ColorX.withAlpha(ColorX.shade(pal.nearInk, 1.3f), alpha * 0.6f)
                rect.set(x, baseY - hh, x + w, deep(baseY + h))
                c.drawRect(rect, paint)
                paint.color = ColorX.withAlpha(pal.accentInk, alpha * 0.28f)
                val cols = ((w - 30f) / 40f).toInt().coerceAtLeast(1)
                for (cx in 0 until cols) {
                    for (ry in 0 until 10) {
                        if (Hash.f(key * 17 + cx * 7 + ry, 1171) < 0.55f) continue
                        rect.set(
                            x + 18f + cx * 40f, baseY - hh + 50f + ry * 62f,
                            x + 40f + cx * 40f, baseY - hh + 82f + ry * 62f
                        )
                        c.drawRect(rect, paint)
                    }
                }
                x += w + 26f
                i++
            }
        }

        // MID+NEAR - the roof you are actually above, with its clutter
        band(camY, 0.27f, h) { idx, baseY ->
            val roofY = baseY + h * 0.02f
            paint.color = ColorX.withAlpha(ColorX.shade(pal.nearInk, 0.7f), alpha * 0.95f)
            rect.set(-40f, roofY, worldW + 40f, deep(roofY + h))
            c.drawRect(rect, paint)
            // the parapet
            paint.color = ColorX.withAlpha(ColorX.shade(pal.nearInk, 1.1f), alpha * 0.95f)
            rect.set(-40f, roofY - 46f, worldW + 40f, roofY + 16f)
            c.drawRect(rect, paint)

            // water tank on legs
            val tx = Hash.f(idx, 1181) * (worldW - 300f) + 150f
            paint.color = ColorX.withAlpha(ColorX.shade(pal.platShade, 1.1f), alpha * 0.95f)
            for (leg in 0 until 4) {
                rect.set(tx - 130f + leg * 86f, roofY - 200f, tx - 110f + leg * 86f, roofY)
                c.drawRect(rect, paint)
            }
            paint.color = ColorX.withAlpha(ColorX.shade(pal.midInk, 0.85f), alpha * 0.95f)
            rect.set(tx - 150f, roofY - 480f, tx + 150f, roofY - 190f)
            c.drawRoundRect(rect, 24f, 24f, paint)
            paint.color = ColorX.withAlpha(ColorX.shade(pal.midInk, 0.6f), alpha * 0.95f)
            path.reset()
            path.moveTo(tx - 170f, roofY - 470f)
            path.lineTo(tx, roofY - 580f)
            path.lineTo(tx + 170f, roofY - 470f)
            path.close()
            c.drawPath(path, paint)
            // the hoop bands round it
            ink.color = ColorX.withAlpha(ColorX.shade(pal.platShade, 1.25f), alpha * 0.8f)
            ink.strokeWidth = 8f
            for (hoop in 0 until 3) {
                path.reset()
                path.moveTo(tx - 150f, roofY - 430f + hoop * 82f)
                path.lineTo(tx + 150f, roofY - 430f + hoop * 82f)
                c.drawPath(path, ink)
            }

            // AC units and vents
            for (i in 0 until 4) {
                val key = idx * 109 + i
                val ax = Hash.f(key, 1187) * worldW
                val aw = 80f + Hash.f(key, 1193) * 70f
                paint.color = ColorX.withAlpha(ColorX.shade(pal.platShade, 0.95f), alpha * 0.95f)
                rect.set(ax - aw, roofY - aw * 0.95f, ax + aw, roofY + 6f)
                c.drawRoundRect(rect, 10f, 10f, paint)
                ink.color = ColorX.withAlpha(ColorX.shade(pal.platShade, 1.4f), alpha * 0.7f)
                ink.strokeWidth = 5f
                for (fin in 0 until 4) {
                    path.reset()
                    path.moveTo(ax - aw * 0.7f, roofY - aw * 0.75f + fin * aw * 0.18f)
                    path.lineTo(ax + aw * 0.7f, roofY - aw * 0.75f + fin * aw * 0.18f)
                    c.drawPath(path, ink)
                }
                // the fan, turning
                paint.color = ColorX.withAlpha(ColorX.shade(pal.platShade, 1.5f), alpha * 0.5f)
                for (blade in 0 until 3) {
                    val a = time * 4f + blade * 2.1f + i
                    rect.set(
                        ax - aw * 0.1f, roofY - aw * 0.5f - aw * 0.32f,
                        ax + aw * 0.1f, roofY - aw * 0.5f + aw * 0.32f
                    )
                    c.drawCircle(
                        ax + cos(a) * aw * 0.26f, roofY - aw * 0.5f + sin(a) * aw * 0.26f,
                        aw * 0.14f, paint
                    )
                }
            }

            // aerials and a satellite dish
            ink.color = ColorX.withAlpha(ColorX.shade(pal.platShade, 1.3f), alpha * 0.85f)
            ink.strokeWidth = 6f
            for (i in 0 until 3) {
                val key = idx * 113 + i
                val ax = Hash.f(key, 1201) * worldW
                val ah = 220f + Hash.f(key, 1213) * 240f
                path.reset()
                path.moveTo(ax, roofY)
                path.lineTo(ax, roofY - ah)
                c.drawPath(path, ink)
                for (arm in 0 until 4) {
                    val ay = roofY - ah + arm * ah * 0.16f
                    val aw = 60f - arm * 10f
                    path.reset()
                    path.moveTo(ax - aw, ay)
                    path.lineTo(ax + aw, ay)
                    c.drawPath(path, ink)
                }
                // a red aircraft light on the tallest
                if (i == 0) {
                    paint.color = ColorX.withAlpha(0xFFFF4B4B.toInt(), alpha * (0.4f + 0.6f * sin(time * 2.4f)))
                    c.drawCircle(ax, roofY - ah - 14f, 12f, paint)
                }
            }
            val dx = Hash.f(idx, 1217) * worldW
            paint.color = ColorX.withAlpha(ColorX.shade(pal.platShade, 1.15f), alpha * 0.9f)
            rect.set(dx - 90f, roofY - 200f, dx + 90f, roofY - 20f)
            c.drawArc(rect, 200f, 250f, true, paint)
            ink.strokeWidth = 7f
            path.reset()
            path.moveTo(dx, roofY - 110f)
            path.lineTo(dx + 60f, roofY - 150f)
            c.drawPath(path, ink)
        }
    }

    // -----------------------------------------------------------------------------------
    // Hollow Hill
    // -----------------------------------------------------------------------------------

    /** Bare gnarled trees clawing upward out of fog, with crows on the branches. */
    fun deadWood(c: Canvas, worldW: Float, camY: Float, pal: BiomePalette, alpha: Float, time: Float) {
        paint.reset(); paint.isAntiAlias = true
        val h = Tuning.VIEW_H * 0.95f

        // FAR - a thicket of thin pale trunks
        band(camY, 0.12f, h) { idx, baseY ->
            ink.color = ColorX.withAlpha(ColorX.tint(pal.farInk, 0.35f), alpha * 0.35f)
            var x = -40f
            var i = 0
            while (x < worldW + 40f) {
                val key = idx * 127 + i
                ink.strokeWidth = 8f + Hash.f(key, 1223) * 12f
                path.reset()
                path.moveTo(x, baseY + h * 0.3f)
                path.quadTo(x + 30f, baseY - h * 0.4f, x + 10f, baseY - h * (0.9f + Hash.f(key, 1229) * 0.4f))
                c.drawPath(path, ink)
                x += 70f + Hash.f(key, 1231) * 90f
                i++
            }
        }

        // MID+NEAR - the big trees, branching, with crows and a fog bank at their feet
        band(camY, 0.24f, h) { idx, baseY ->
            val footY = baseY + h * 0.06f
            ground(
                c, worldW, footY, h, h * 0.03f,
                ColorX.withAlpha(ColorX.shade(pal.nearInk, 0.7f), alpha * 0.9f), idx * 3
            )
            for (i in 0 until 3) {
                val key = idx * 131 + i
                val tx = Hash.f(key, 1237) * (worldW + 200f) - 100f
                val tall = h * (0.7f + Hash.f(key, 1249) * 0.55f)
                ink.color = ColorX.withAlpha(pal.midInk, alpha * 0.92f)
                // trunk
                ink.strokeWidth = 46f
                path.reset()
                path.moveTo(tx, footY + 10f)
                path.quadTo(tx + 34f, footY - tall * 0.5f, tx - 14f, footY - tall)
                c.drawPath(path, ink)
                // three orders of branch, each thinner
                for (b in 0 until 5) {
                    val t = 0.42f + b * 0.12f
                    val by = footY - tall * t
                    val bxs = tx + (t - 0.5f) * 30f
                    val dir = if (b % 2 == 0) 1f else -1f
                    ink.strokeWidth = 24f - b * 2.5f
                    path.reset()
                    path.moveTo(bxs, by)
                    path.quadTo(
                        bxs + dir * 130f, by - 70f,
                        bxs + dir * (200f + Hash.f(key * 7 + b, 1259) * 130f), by - 150f
                    )
                    c.drawPath(path, ink)
                    ink.strokeWidth = 11f - b * 1.2f
                    for (tw in 0 until 2) {
                        path.reset()
                        path.moveTo(bxs + dir * 160f, by - 110f)
                        path.quadTo(
                            bxs + dir * 220f, by - 200f - tw * 60f,
                            bxs + dir * (250f + tw * 60f), by - 260f - tw * 70f
                        )
                        c.drawPath(path, ink)
                    }
                }
                // a crow, shuffling
                val crowX = tx + 190f
                val crowY = footY - tall * 0.66f - 150f
                paint.color = ColorX.withAlpha(0xFF0C0C12.toInt(), alpha * 0.9f)
                rect.set(crowX - 34f, crowY - 20f, crowX + 30f, crowY + 20f)
                c.drawOval(rect, paint)
                c.drawCircle(crowX + 28f, crowY - 22f, 18f, paint)
                path.reset()
                path.moveTo(crowX + 42f, crowY - 26f)
                path.lineTo(crowX + 70f, crowY - 18f)
                path.lineTo(crowX + 42f, crowY - 12f)
                path.close()
                c.drawPath(path, paint)
                path.reset()
                path.moveTo(crowX - 30f, crowY + 6f)
                path.lineTo(crowX - 78f, crowY + 26f + sin(time * 1.3f + i) * 6f)
                path.lineTo(crowX - 26f, crowY + 20f)
                path.close()
                c.drawPath(path, paint)
                paint.color = ColorX.withAlpha(pal.rimInk, alpha * 0.9f)
                c.drawCircle(crowX + 32f, crowY - 24f, 5f, paint)
            }
            // low fog, lying between the trunks
            for (i in 0 until 4) {
                val key = idx * 137 + i
                art.draw(
                    c, art.cloud,
                    (Hash.f(key, 1277) * worldW + time * 7f) % (worldW + 300f) - 150f,
                    footY - 40f - Hash.f(key, 1279) * 130f,
                    worldW * (0.5f + Hash.f(key, 1283) * 0.5f), 210f,
                    alpha * 0.32f, ColorX.tint(pal.haze, 0.35f)
                )
            }
        }
    }

    /** The bell tower band: gothic spires, arched windows, the bell, and bats round it. */
    fun belfry(c: Canvas, worldW: Float, camY: Float, pal: BiomePalette, alpha: Float, time: Float) {
        paint.reset(); paint.isAntiAlias = true
        val h = Tuning.VIEW_H * 1.1f

        // FAR - a saw of distant rooftops and small spires
        band(camY, 0.09f, h) { idx, baseY ->
            paint.color = ColorX.withAlpha(ColorX.tint(pal.farInk, 0.3f), alpha * 0.4f)
            var x = -60f
            var i = 0
            while (x < worldW + 60f) {
                val key = idx * 139 + i
                val w = 150f + Hash.f(key, 1289) * 170f
                val top = baseY - h * (0.5f + Hash.f(key, 1291) * 0.4f)
                rect.set(x, top, x + w, deep(baseY + h))
                c.drawRect(rect, paint)
                path.reset()
                path.moveTo(x - 10f, top)
                path.lineTo(x + w * 0.5f, top - 130f - Hash.f(key, 1297) * 150f)
                path.lineTo(x + w + 10f, top)
                path.close()
                c.drawPath(path, paint)
                x += w + 30f
                i++
            }
        }

        // MID+NEAR - the tower itself
        band(camY, 0.2f, h) { idx, baseY ->
            val footY = baseY + h * 0.25f
            val tx = Hash.f(idx, 1301) * (worldW - 420f) + 210f
            val tw = 230f
            val top = footY - h * 0.95f

            paint.color = ColorX.withAlpha(pal.midInk, alpha * 0.95f)
            rect.set(tx - tw, top, tx + tw, footY)
            c.drawRect(rect, paint)
            // the spire
            path.reset()
            path.moveTo(tx - tw - 36f, top)
            path.lineTo(tx, top - 420f)
            path.lineTo(tx + tw + 36f, top)
            path.close()
            c.drawPath(path, paint)
            // the lit face
            paint.color = ColorX.withAlpha(ColorX.tint(pal.midInk, 0.2f), alpha * 0.5f)
            rect.set(tx - tw, top, tx - tw * 0.45f, footY)
            c.drawRect(rect, paint)
            // the arched belfry opening, with the bell hanging in it
            // an arch: round at the head, straight down the sides. A fully rounded rect the
            // width of the tower is just a black pill sitting on it.
            paint.color = ColorX.withAlpha(0xFF0A0A12.toInt(), alpha * 0.9f)
            path.reset()
            path.moveTo(tx - tw * 0.46f, top + 500f)
            path.lineTo(tx - tw * 0.46f, top + 250f)
            path.quadTo(tx, top + 60f, tx + tw * 0.46f, top + 250f)
            path.lineTo(tx + tw * 0.46f, top + 500f)
            path.close()
            c.drawPath(path, paint)
            val swing = sin(time * 1.1f) * 0.16f
            paint.color = ColorX.withAlpha(ColorX.tint(pal.rimInk, 0.15f), alpha * 0.9f)
            path.reset()
            path.moveTo(tx + swing * 200f - 78f, top + 400f)
            path.quadTo(tx + swing * 120f, top + 170f, tx + swing * 200f + 78f, top + 400f)
            path.close()
            c.drawPath(path, paint)
            rect.set(tx + swing * 200f - 92f, top + 386f, tx + swing * 200f + 92f, top + 428f)
            c.drawRoundRect(rect, 16f, 16f, paint)
            // the clock face lower down
            paint.color = ColorX.withAlpha(ColorX.tint(pal.platTop, 0.4f), alpha * 0.9f)
            c.drawCircle(tx, top + 720f, 108f, paint)
            ink.color = ColorX.withAlpha(0xFF14141E.toInt(), alpha * 0.9f)
            ink.strokeWidth = 12f
            path.reset(); path.moveTo(tx, top + 720f); path.lineTo(tx, top + 720f - 74f); c.drawPath(path, ink)
            path.reset(); path.moveTo(tx, top + 720f); path.lineTo(tx + 52f, top + 720f + 30f); c.drawPath(path, ink)
            // arched windows down the shaft
            paint.color = ColorX.withAlpha(0xFF0A0A12.toInt(), alpha * 0.8f)
            for (w in 0 until 3) {
                val wy = top + 900f + w * 240f
                if (wy > footY - 120f) break
                path.reset()
                path.moveTo(tx - 46f, wy + 170f)
                path.lineTo(tx - 46f, wy + 60f)
                path.quadTo(tx, wy - 24f, tx + 46f, wy + 60f)
                path.lineTo(tx + 46f, wy + 170f)
                path.close()
                c.drawPath(path, paint)
            }
            // the ground the tower stands on
            ground(
                c, worldW, footY, h, h * 0.03f,
                ColorX.withAlpha(ColorX.shade(pal.nearInk, 0.8f), alpha * 0.95f), idx * 3
            )
            // bats wheeling round it
            ink.color = ColorX.withAlpha(0xFF0C0C14.toInt(), alpha * 0.85f)
            for (i in 0 until 9) {
                val key = idx * 149 + i
                val a = time * (0.5f + Hash.f(key, 1303) * 0.6f) + i * 0.7f
                val rr = 230f + Hash.f(key, 1307) * 330f
                val bx = tx + cos(a) * rr
                val by = top + 380f + sin(a) * rr * 0.4f
                val s = 17f + Hash.f(key, 1319) * 12f
                val flap = sin(time * 6f + i) * 0.5f
                ink.strokeWidth = 5f
                path.reset()
                path.moveTo(bx - s * 1.7f, by + s * flap)
                path.quadTo(bx - s * 0.8f, by - s * 0.7f, bx, by)
                path.quadTo(bx + s * 0.8f, by - s * 0.7f, bx + s * 1.7f, by + s * flap)
                c.drawPath(path, ink)
            }
        }
    }

    // -----------------------------------------------------------------------------------
    // Emberfall
    // -----------------------------------------------------------------------------------

    /** Floating cinder islands, falling ash and the glow of the fires far below. */
    fun emberSky(c: Canvas, worldW: Float, camY: Float, pal: BiomePalette, alpha: Float, time: Float) {
        paint.reset(); paint.isAntiAlias = true
        val h = Tuning.VIEW_H * 1.0f

        // FAR - the glow from below, pushing up into the smoke
        band(camY, 0.09f, h) { idx, baseY ->
            for (i in 0 until 3) {
                val key = idx * 151 + i
                art.draw(
                    c, art.softGlow,
                    Hash.f(key, 1321) * worldW, baseY - h * (0.1f + Hash.f(key, 1327) * 0.35f),
                    700f + Hash.f(key, 1361) * 500f, 420f + Hash.f(key, 1367) * 300f,
                    alpha * 0.26f, pal.farInk
                )
            }
        }

        // MID - cinder islands, cracked and glowing along the underside
        band(camY, 0.18f, h) { idx, baseY ->
            for (i in 0 until 4) {
                val key = idx * 157 + i
                val cx = Hash.f(key, 1373) * (worldW + 300f) - 150f
                val cy = baseY - h * (0.2f + Hash.f(key, 1381) * 0.75f) + sin(time * 0.35f + i) * 26f
                val cw = 180f + Hash.f(key, 1399) * 220f
                paint.color = ColorX.withAlpha(pal.nearInk, alpha * 0.92f)
                path.reset()
                path.moveTo(cx - cw, cy)
                path.quadTo(cx - cw * 0.4f, cy - cw * 0.42f, cx + cw * 0.2f, cy - cw * 0.3f)
                path.quadTo(cx + cw * 0.9f, cy - cw * 0.22f, cx + cw, cy + cw * 0.04f)
                path.quadTo(cx + cw * 0.3f, cy + cw * 0.62f, cx - cw * 0.5f, cy + cw * 0.3f)
                path.close()
                c.drawPath(path, paint)
                // the cracks, lit from inside
                ink.color = ColorX.withAlpha(pal.rimInk, alpha * 0.8f)
                ink.strokeWidth = 7f
                for (k in 0 until 3) {
                    val sx = cx - cw * 0.6f + k * cw * 0.55f
                    path.reset()
                    path.moveTo(sx, cy - cw * 0.2f)
                    path.lineTo(sx + 30f, cy + cw * 0.05f)
                    path.lineTo(sx - 14f, cy + cw * 0.24f)
                    c.drawPath(path, ink)
                }
                art.drawGlow(c, cx, cy + cw * 0.3f, cw * 0.9f, pal.rimInk, alpha * 0.3f)
                // stalactite shards under it
                // stubbier and in the island's own colour: long near-black spikes read as a row
                // of teeth hanging under a rock rather than as the rock's broken underside
                paint.color = ColorX.withAlpha(ColorX.shade(pal.nearInk, 0.86f), alpha * 0.9f)
                for (k in 0 until 3) {
                    val sx = cx - cw * 0.45f + k * cw * 0.45f
                    path.reset()
                    path.moveTo(sx - 34f, cy + cw * 0.2f)
                    path.lineTo(sx + 34f, cy + cw * 0.16f)
                    path.lineTo(sx, cy + cw * (0.28f + Hash.f(key * 5 + k, 1409) * 0.16f))
                    path.close()
                    c.drawPath(path, paint)
                }
            }
        }

        // NEAR - ash falling through, and embers rising past it
        band(camY, 0.3f, h) { idx, baseY ->
            for (i in 0 until 16) {
                val key = idx * 163 + i
                val ax = Hash.f(key, 1423) * worldW + sin(time * 0.5f + i) * 40f
                val ay = baseY - ((Hash.f(key, 1427) * h - time * 50f) % h + h) % h
                paint.color = ColorX.withAlpha(ColorX.tint(pal.haze, 0.5f), alpha * 0.45f)
                c.drawCircle(ax, ay, 5f + Hash.f(key, 1429) * 8f, paint)
            }
            for (i in 0 until 12) {
                val key = idx * 167 + i
                val ex = Hash.f(key, 1433) * worldW + sin(time * 1.1f + i) * 26f
                val ey = baseY - ((Hash.f(key, 1439) * h + time * 130f) % h)
                paint.color = ColorX.withAlpha(pal.rimInk, alpha * (0.4f + 0.5f * Hash.f(key, 1447)))
                c.drawCircle(ex, ey, 4f + Hash.f(key, 1451) * 7f, paint)
            }
        }
    }

    // -----------------------------------------------------------------------------------
    // Heaven
    // -----------------------------------------------------------------------------------

    /** The choir: ranks of organ pipes, stained arches and drifting harps. */
    fun choir(c: Canvas, worldW: Float, camY: Float, pal: BiomePalette, alpha: Float, time: Float) {
        paint.reset(); paint.isAntiAlias = true
        val h = Tuning.VIEW_H * 1.05f

        // FAR - tall stained-glass arches, glowing
        band(camY, 0.1f, h) { idx, baseY ->
            var x = -60f
            var i = 0
            while (x < worldW + 60f) {
                val key = idx * 173 + i
                val w = 170f + Hash.f(key, 1453) * 120f
                val hh = h * (0.55f + Hash.f(key, 1459) * 0.45f)
                paint.color = ColorX.withAlpha(ColorX.tint(pal.farInk, 0.3f), alpha * 0.3f)
                rect.set(x, baseY - hh, x + w, baseY + h * 0.5f)
                c.drawRoundRect(rect, w * 0.5f, w * 0.5f, paint)
                paint.color = ColorX.withAlpha(pal.rimInk, alpha * 0.2f)
                rect.set(x + w * 0.18f, baseY - hh + w * 0.3f, x + w * 0.82f, baseY + h * 0.4f)
                c.drawRoundRect(rect, w * 0.32f, w * 0.32f, paint)
                x += w + 60f
                i++
            }
        }

        // MID+NEAR - the pipe ranks, standing on a plinth
        band(camY, 0.21f, h) { idx, baseY ->
            val footY = baseY + h * 0.1f
            paint.color = ColorX.withAlpha(ColorX.tint(pal.midInk, 0.25f), alpha * 0.9f)
            rect.set(-40f, footY, worldW + 40f, deep(footY + h))
            c.drawRect(rect, paint)
            paint.color = ColorX.withAlpha(ColorX.tint(pal.platTop, 0.4f), alpha * 0.9f)
            rect.set(-40f, footY - 40f, worldW + 40f, footY + 26f)
            c.drawRect(rect, paint)

            var x = -40f
            var i = 0
            while (x < worldW + 60f) {
                val key = idx * 179 + i
                // pipes come in ranks that rise and fall, so the silhouette is a wave
                val rank = 0.35f + 0.65f * kotlin.math.abs(sin(i * 0.55f + idx * 0.7f))
                val w = 44f + Hash.f(key, 1471) * 26f
                val tall = h * 0.72f * rank
                paint.color = ColorX.withAlpha(
                    if (i % 3 == 0) ColorX.readable(pal.rimInk, pal.skyMid, 0.17f)
                    else ColorX.readable(ColorX.shade(pal.midInk, 0.8f), pal.skyMid, 0.2f),
                    alpha * 0.95f
                )
                rect.set(x, footY - tall, x + w, footY - 20f)
                c.drawRoundRect(rect, w * 0.5f, w * 0.2f, paint)
                // the lit edge down one side, and the mouth as the slot it actually is - it was
                // a downward triangle, and a rank of those reads as teeth, not as pipework
                paint.color = ColorX.withAlpha(0xFFFFFFFF.toInt(), alpha * 0.4f)
                rect.set(x + w * 0.1f, footY - tall + w * 0.2f, x + w * 0.34f, footY - 60f)
                c.drawRoundRect(rect, w * 0.12f, w * 0.12f, paint)
                paint.color = ColorX.withAlpha(ColorX.shade(pal.midInk, 0.5f), alpha * 0.55f)
                rect.set(x + w * 0.22f, footY - tall * 0.34f, x + w * 0.78f, footY - tall * 0.34f + w * 0.3f)
                c.drawRoundRect(rect, w * 0.14f, w * 0.14f, paint)
                x += w + 16f
                i++
            }

            // harps drifting in front of the ranks
            for (k in 0 until 2) {
                val key = idx * 181 + k
                val hx = Hash.f(key, 1481) * worldW
                val hy = footY - h * (0.35f + Hash.f(key, 1483) * 0.35f) + sin(time * 0.5f + k) * 30f
                val s = 110f + Hash.f(key, 1487) * 60f
                ink.color = ColorX.withAlpha(ColorX.tint(pal.rimInk, 0.3f), alpha * 0.85f)
                ink.strokeWidth = 14f
                path.reset()
                path.moveTo(hx - s * 0.5f, hy + s)
                path.quadTo(hx - s * 0.9f, hy - s * 0.6f, hx + s * 0.35f, hy - s)
                c.drawPath(path, ink)
                path.reset()
                path.moveTo(hx - s * 0.5f, hy + s)
                path.lineTo(hx + s * 0.35f, hy - s)
                c.drawPath(path, ink)
                ink.strokeWidth = 3f
                ink.color = ColorX.withAlpha(0xFFFFFFFF.toInt(), alpha * 0.5f)
                for (str in 1 until 6) {
                    val t = str / 6f
                    path.reset()
                    path.moveTo(hx - s * 0.5f + (s * 0.85f) * t, hy + s - (s * 2f) * t)
                    path.lineTo(hx - s * 0.78f + (s * 1.0f) * t, hy - s * 0.5f - (s * 0.4f) * t)
                    c.drawPath(path, ink)
                }
            }

            // motes of light rising off the pipes
            paint.color = ColorX.withAlpha(0xFFFFFFFF.toInt(), alpha * 0.5f)
            for (i in 0 until 16) {
                val key = idx * 191 + i
                val mx = Hash.f(key, 1489) * worldW + sin(time * 0.8f + i) * 22f
                val my = footY - ((Hash.f(key, 1493) * h + time * 55f) % h)
                c.drawCircle(mx, my, 4f + Hash.f(key, 1499) * 6f, paint)
            }
        }
    }
}
