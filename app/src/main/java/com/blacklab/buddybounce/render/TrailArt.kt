package com.blacklab.buddybounce.render

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import com.blacklab.buddybounce.data.Trails
import com.blacklab.buddybounce.game.MathX.clamp01
import kotlin.math.cos
import kotlin.math.sin

/**
 * Draws one trail particle in whichever of the fourteen styles it belongs to.
 *
 * [Fx] owns the particles and their motion; this owns the look. `k` runs 1 -> 0 over a
 * particle's life, so every style can fade and shrink off the same number, and the colour is
 * already blended from the trail's hot to its cool by the caller.
 *
 * Deliberately restrained: trails live about a third of a second and the shapes are small,
 * because the point is a flourish behind Buddy, not clutter over the platforms.
 */
class TrailArt(private val art: Art) {

    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val path = Path()
    private val r = RectF()

    fun draw(c: Canvas, style: Int, x: Float, y: Float, size: Float, rot: Float, color: Int, k: Float) {
        val a = clamp01(k)
        when (style) {
            Trails.Style.EMBER -> ember(c, x, y, size, color, a)
            Trails.Style.BUBBLE -> bubble(c, x, y, size, color, a)
            Trails.Style.PETAL -> petal(c, x, y, size, rot, color, a)
            Trails.Style.STAR -> star(c, x, y, size, rot, color, a)
            Trails.Style.RIBBON -> ribbon(c, x, y, size, rot, color, a)
            Trails.Style.SMOKE -> smoke(c, x, y, size, color, a)
            Trails.Style.FLAKE -> flake(c, x, y, size, rot, color, a)
            Trails.Style.BOLT -> bolt(c, x, y, size, rot, color, a)
            Trails.Style.PAW -> paw(c, x, y, size, rot, color, a)
            Trails.Style.NOTE -> note(c, x, y, size, rot, color, a)
            Trails.Style.HEART -> heart(c, x, y, size, color, a)
            Trails.Style.PIXEL -> pixel(c, x, y, size, color, a)
            Trails.Style.ORB -> orb(c, x, y, size, color, a)
            else -> drop(c, x, y, size, rot, color, a)
        }
    }

    private fun fill(color: Int, a: Float) {
        p.reset(); p.isAntiAlias = true
        p.style = Paint.Style.FILL
        p.color = ColorX.withAlpha(color, a)
    }

    // -- styles ---------------------------------------------------------------------------

    /** A mote that shrinks as it cools, with a hot white pinprick while it is young. */
    private fun ember(c: Canvas, x: Float, y: Float, s: Float, color: Int, a: Float) {
        art.draw(c, art.softGlow, x, y, s * 2.4f, s * 2.4f, a * 0.5f, color)
        fill(color, a * 0.95f)
        c.drawCircle(x, y, s * 0.42f * (0.45f + 0.55f * a), p)
        if (a > 0.55f) {
            fill(0xFFFFF6DC.toInt(), (a - 0.55f) * 2f)
            c.drawCircle(x, y, s * 0.18f, p)
        }
    }

    /** A hollow ring that swells, thins and is gone. */
    private fun bubble(c: Canvas, x: Float, y: Float, s: Float, color: Int, a: Float) {
        val rad = s * (0.35f + (1f - a) * 0.75f)
        p.reset(); p.isAntiAlias = true
        p.style = Paint.Style.STROKE
        p.strokeWidth = s * 0.16f * a + 1.2f
        p.color = ColorX.withAlpha(color, a * 0.85f)
        c.drawCircle(x, y, rad, p)
        p.style = Paint.Style.FILL
        p.color = ColorX.withAlpha(0xFFFFFFFF.toInt(), a * 0.5f)   // highlight
        c.drawCircle(x - rad * 0.34f, y - rad * 0.34f, rad * 0.2f, p)
    }

    /** A flat oval tumbling end over end - a leaf, a petal, a clover. */
    private fun petal(c: Canvas, x: Float, y: Float, s: Float, rot: Float, color: Int, a: Float) {
        fill(color, a)
        c.save(); c.translate(x, y); c.rotate(rot * 57.29578f)
        // the squash makes it read as a flat thing seen edge-on partway through the tumble
        c.scale(1f, 0.35f + 0.65f * kotlin.math.abs(cos(rot)))
        r.set(-s * 0.55f, -s * 0.32f, s * 0.55f, s * 0.32f)
        c.drawOval(r, p)
        c.restore()
    }

    /** A four-point sparkle: two crossed tapers plus a core. */
    private fun star(c: Canvas, x: Float, y: Float, s: Float, rot: Float, color: Int, a: Float) {
        fill(color, a)
        c.save(); c.translate(x, y); c.rotate(rot * 28f)
        val long = s * 0.85f * (0.4f + 0.6f * a)
        val short = s * 0.2f
        path.reset()
        path.moveTo(0f, -long); path.lineTo(short, 0f)
        path.lineTo(0f, long); path.lineTo(-short, 0f)
        path.close()
        c.drawPath(path, p)
        path.reset()
        path.moveTo(-long, 0f); path.lineTo(0f, -short)
        path.lineTo(long, 0f); path.lineTo(0f, short)
        path.close()
        c.drawPath(path, p)
        c.restore()
        fill(0xFFFFFFFF.toInt(), a * 0.75f)
        c.drawCircle(x, y, s * 0.14f, p)
    }

    /** A soft streak stretched along the direction it was thrown. */
    private fun ribbon(c: Canvas, x: Float, y: Float, s: Float, rot: Float, color: Int, a: Float) {
        c.save(); c.translate(x, y); c.rotate(rot * 57.29578f)
        art.draw(c, art.softGlow, 0f, 0f, s * 3.4f, s * 1.5f, a * 0.55f, color)
        fill(color, a * 0.9f)
        r.set(-s * 0.9f, -s * 0.24f * a - 1f, s * 0.9f, s * 0.24f * a + 1f)
        c.drawRoundRect(r, s * 0.24f, s * 0.24f, p)
        c.restore()
    }

    /** A puff that keeps expanding as it thins out. */
    private fun smoke(c: Canvas, x: Float, y: Float, s: Float, color: Int, a: Float) {
        val grow = 0.6f + (1f - a) * 1.1f
        art.draw(c, art.softGlow, x, y, s * 3.2f * grow, s * 3.2f * grow, a * 0.4f, color)
        fill(color, a * 0.28f)
        c.drawCircle(x, y, s * 0.6f * grow, p)
        c.drawCircle(x - s * 0.3f * grow, y + s * 0.2f * grow, s * 0.4f * grow, p)
    }

    /** Six spokes with a dot in the middle: a snow crystal, drifting. */
    private fun flake(c: Canvas, x: Float, y: Float, s: Float, rot: Float, color: Int, a: Float) {
        p.reset(); p.isAntiAlias = true
        p.style = Paint.Style.STROKE
        p.strokeCap = Paint.Cap.ROUND
        p.strokeWidth = s * 0.14f + 1f
        p.color = ColorX.withAlpha(color, a)
        c.save(); c.translate(x, y); c.rotate(rot * 57.29578f)
        val len = s * 0.52f
        for (i in 0 until 3) {
            val ang = i * 1.0472f            // 60 degrees
            val dx = cos(ang) * len
            val dy = sin(ang) * len
            c.drawLine(-dx, -dy, dx, dy, p)
        }
        c.restore()
        p.style = Paint.Style.FILL
        c.drawCircle(x, y, s * 0.13f, p)
    }

    /** A short jagged shard - lightning, a circuit trace, a laser fragment. */
    private fun bolt(c: Canvas, x: Float, y: Float, s: Float, rot: Float, color: Int, a: Float) {
        art.draw(c, art.softGlow, x, y, s * 2.4f, s * 2.4f, a * 0.4f, color)
        fill(color, a)
        c.save(); c.translate(x, y); c.rotate(rot * 57.29578f)
        path.reset()
        path.moveTo(-s * 0.15f, -s * 0.6f)
        path.lineTo(s * 0.22f, -s * 0.1f)
        path.lineTo(s * 0.02f, -s * 0.06f)
        path.lineTo(s * 0.2f, s * 0.6f)
        path.lineTo(-s * 0.2f, s * 0.05f)
        path.lineTo(0f, 0f)
        path.close()
        c.drawPath(path, p)
        c.restore()
    }

    /** A paw print: main pad plus four toes, fading where it was set down. */
    private fun paw(c: Canvas, x: Float, y: Float, s: Float, rot: Float, color: Int, a: Float) {
        fill(color, a * 0.9f)
        c.save(); c.translate(x, y); c.rotate(rot * 20f)
        r.set(-s * 0.3f, -s * 0.1f, s * 0.3f, s * 0.36f)
        c.drawOval(r, p)
        val toe = s * 0.13f
        c.drawCircle(-s * 0.26f, -s * 0.24f, toe, p)
        c.drawCircle(-s * 0.08f, -s * 0.34f, toe, p)
        c.drawCircle(s * 0.11f, -s * 0.34f, toe, p)
        c.drawCircle(s * 0.28f, -s * 0.22f, toe, p)
        c.restore()
    }

    /** A quaver: stem, flag and head. */
    private fun note(c: Canvas, x: Float, y: Float, s: Float, rot: Float, color: Int, a: Float) {
        fill(color, a)
        c.save(); c.translate(x, y); c.rotate(sin(rot) * 14f)
        r.set(-s * 0.34f, s * 0.02f, s * 0.14f, s * 0.42f)
        c.drawOval(r, p)
        r.set(s * 0.04f, -s * 0.62f, s * 0.16f, s * 0.22f)
        c.drawRect(r, p)
        path.reset()
        path.moveTo(s * 0.16f, -s * 0.62f)
        path.quadTo(s * 0.52f, -s * 0.44f, s * 0.3f, -s * 0.1f)
        path.quadTo(s * 0.4f, -s * 0.4f, s * 0.16f, -s * 0.42f)
        path.close()
        c.drawPath(path, p)
        c.restore()
    }

    /** Two lobes and a point. */
    private fun heart(c: Canvas, x: Float, y: Float, s: Float, color: Int, a: Float) {
        fill(color, a)
        c.save(); c.translate(x, y)
        val k = s * 0.5f
        c.drawCircle(-k * 0.46f, -k * 0.3f, k * 0.5f, p)
        c.drawCircle(k * 0.46f, -k * 0.3f, k * 0.5f, p)
        path.reset()
        path.moveTo(-k * 0.92f, -k * 0.16f)
        path.lineTo(0f, k * 0.92f)
        path.lineTo(k * 0.92f, -k * 0.16f)
        path.close()
        c.drawPath(path, p)
        c.restore()
    }

    /** A hard square, snapped to a coarse grid and never rotated. Reads as digital. */
    private fun pixel(c: Canvas, x: Float, y: Float, s: Float, color: Int, a: Float) {
        val grid = 9f
        val gx = kotlin.math.round(x / grid) * grid
        val gy = kotlin.math.round(y / grid) * grid
        val half = s * 0.3f * (0.45f + 0.55f * a)
        fill(color, a)
        r.set(gx - half, gy - half, gx + half, gy + half)
        c.drawRect(r, p)
    }

    /** A soft ball of light with a bright centre. */
    private fun orb(c: Canvas, x: Float, y: Float, s: Float, color: Int, a: Float) {
        art.draw(c, art.softGlow, x, y, s * 3.0f, s * 3.0f, a * 0.6f, color)
        fill(color, a * 0.8f)
        c.drawCircle(x, y, s * 0.38f, p)
        fill(0xFFFFFFFF.toInt(), a * 0.55f)
        c.drawCircle(x, y, s * 0.16f, p)
    }

    /** A teardrop, point trailing the way it came. */
    private fun drop(c: Canvas, x: Float, y: Float, s: Float, rot: Float, color: Int, a: Float) {
        fill(color, a)
        c.save(); c.translate(x, y); c.rotate(rot * 57.29578f)
        path.reset()
        path.moveTo(0f, -s * 0.62f)
        path.quadTo(s * 0.36f, 0f, 0f, s * 0.44f)
        path.quadTo(-s * 0.36f, 0f, 0f, -s * 0.62f)
        path.close()
        c.drawPath(path, p)
        c.restore()
        fill(0xFFFFFFFF.toInt(), a * 0.4f)
        c.drawCircle(x - s * 0.1f, y + s * 0.08f, s * 0.1f, p)
    }
}
