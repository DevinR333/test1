package com.blacklab.buddybounce.render

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import com.blacklab.buddybounce.data.Trails
import com.blacklab.buddybounce.game.Hash
import com.blacklab.buddybounce.game.MathX.clamp01
import com.blacklab.buddybounce.game.Pool
import com.blacklab.buddybounce.game.Poolable
import kotlin.math.cos
import kotlin.math.sin

/**
 * Particles and floating score pops. All in world coordinates; the renderer converts on draw.
 * Pooled, so a long run never allocates.
 */
class Fx {

    object Kind {
        const val DUST = 0
        const val SHARD = 1
        const val SPARK = 2
        const val TRAIL = 3
        const val RING = 4
        const val FEATHER = 5
        /** An equipped cosmetic trail. [P.style] picks which of the 14 looks to draw. */
        const val COSMETIC = 6
    }

    class P : Poolable {
        override var alive = false
        var x = 0f; var y = 0f
        var vx = 0f; var vy = 0f
        var life = 0f; var maxLife = 1f
        var size = 10f
        var color = 0xFFFFFFFF.toInt()
        var kind = Kind.DUST
        var rot = 0f; var spin = 0f
        var gravity = -1400f
        var drag = 2.4f
        /** The colour a cosmetic trail particle fades TO as it dies. */
        var color2 = 0xFFFFFFFF.toInt()
        /** Which [com.blacklab.buddybounce.data.Trails.Style] to draw, for [Kind.COSMETIC]. */
        var style = 0

        override fun reset() {
            x = 0f; y = 0f; vx = 0f; vy = 0f; life = 0f; maxLife = 1f
            size = 10f; color = 0xFFFFFFFF.toInt(); kind = Kind.DUST
            rot = 0f; spin = 0f; gravity = -1400f; drag = 2.4f
            color2 = 0xFFFFFFFF.toInt(); style = 0
        }
    }

    class Pop : Poolable {
        override var alive = false
        var x = 0f; var y = 0f
        var life = 0f; var maxLife = 1f
        var text = ""
        var color = 0xFFFFFFFF.toInt()
        var size = 46f

        override fun reset() {
            x = 0f; y = 0f; life = 0f; maxLife = 1f; text = ""; color = 0xFFFFFFFF.toInt(); size = 46f
        }
    }

    val parts = Pool { P() }
    val pops = Pool { Pop() }

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rect = RectF()
    private var seed = 0

    // Built on first draw and rebuilt whenever the Art cache is (i.e. on a resize).
    private var trailArt: TrailArt? = null
    private var trailArtFor: Art? = null

    private fun rnd(lo: Float, hi: Float): Float {
        seed++
        return Hash.range(seed, 9173, lo, hi)
    }

    fun clear() {
        parts.clear(); pops.clear()
    }

    fun dust(x: Float, y: Float, count: Int, color: Int, power: Float = 1f) {
        for (i in 0 until count) {
            val p = parts.obtain()
            p.kind = Kind.DUST
            p.x = x + rnd(-26f, 26f); p.y = y + rnd(-4f, 10f)
            p.vx = rnd(-210f, 210f) * power
            p.vy = rnd(60f, 260f) * power
            p.gravity = -900f
            p.drag = 3.4f
            p.size = rnd(12f, 26f)
            p.maxLife = rnd(0.30f, 0.55f); p.life = p.maxLife
            p.color = color
        }
    }

    fun shards(x: Float, y: Float, width: Float, color: Int) {
        for (i in 0 until 9) {
            val p = parts.obtain()
            p.kind = Kind.SHARD
            p.x = x + rnd(-width * 0.5f, width * 0.5f)
            p.y = y + rnd(-10f, 8f)
            p.vx = rnd(-320f, 320f)
            p.vy = rnd(40f, 320f)
            p.gravity = -2100f
            p.drag = 0.6f
            p.size = rnd(12f, 26f)
            p.rot = rnd(0f, 6.28f); p.spin = rnd(-9f, 9f)
            p.maxLife = rnd(0.5f, 0.9f); p.life = p.maxLife
            p.color = color
        }
    }

    fun sparkle(x: Float, y: Float, color: Int, count: Int = 10, power: Float = 1f) {
        for (i in 0 until count) {
            val p = parts.obtain()
            p.kind = Kind.SPARK
            val a = rnd(0f, 6.283f)
            val sp = rnd(120f, 460f) * power
            p.x = x; p.y = y
            p.vx = cos(a) * sp; p.vy = sin(a) * sp
            p.gravity = -260f
            p.drag = 2.2f
            p.size = rnd(7f, 17f)
            p.maxLife = rnd(0.35f, 0.7f); p.life = p.maxLife
            p.color = color
        }
    }

    fun ring(x: Float, y: Float, color: Int, size: Float) {
        val p = parts.obtain()
        p.kind = Kind.RING
        p.x = x; p.y = y
        p.vx = 0f; p.vy = 0f
        p.gravity = 0f; p.drag = 0f
        p.size = size
        p.maxLife = 0.45f; p.life = p.maxLife
        p.color = color
    }

    fun feathers(x: Float, y: Float, color: Int) {
        for (i in 0 until 7) {
            val p = parts.obtain()
            p.kind = Kind.FEATHER
            p.x = x + rnd(-20f, 20f); p.y = y + rnd(-16f, 16f)
            p.vx = rnd(-240f, 240f); p.vy = rnd(-40f, 240f)
            p.gravity = -420f
            p.drag = 1.6f
            p.size = rnd(14f, 24f)
            p.rot = rnd(0f, 6.28f); p.spin = rnd(-6f, 6f)
            p.maxLife = rnd(0.7f, 1.2f); p.life = p.maxLife
            p.color = color
        }
    }

    fun trail(x: Float, y: Float, color: Int, size: Float) {
        val p = parts.obtain()
        p.kind = Kind.TRAIL
        p.x = x + rnd(-14f, 14f); p.y = y
        p.vx = rnd(-70f, 70f); p.vy = rnd(-380f, -120f)
        p.gravity = 0f
        p.drag = 1.1f
        p.size = size * rnd(0.7f, 1.3f)
        p.maxLife = rnd(0.25f, 0.5f); p.life = p.maxLife
        p.color = color
    }

    /**
     * One particle of an equipped cosmetic trail.
     *
     * Short on purpose. The user asked for trails that fade out quickly "as to not make a lot of
     * stuff on screen", so the lifetimes here top out around a third of a second - long enough
     * to read as a ribbon behind a moving dog, short enough that it never hides a platform.
     */
    fun cosmetic(x: Float, y: Float, style: Int, hot: Int, cool: Int, vx: Float, vy: Float) {
        val p = parts.obtain()
        p.kind = Kind.COSMETIC
        p.style = style
        p.x = x + rnd(-13f, 13f)
        p.y = y + rnd(-11f, 11f)
        p.color = hot
        p.color2 = cool

        // Each style gets its own drift so they do not all behave like the same puff. The
        // velocity handed in is Buddy's own, lightly inherited so the ribbon lags behind him.
        val inheritX = vx * 0.13f
        val inheritY = vy * 0.10f
        when (style) {
            Trails.Style.EMBER -> {
                p.vx = inheritX + rnd(-70f, 70f); p.vy = inheritY + rnd(90f, 250f)
                p.gravity = 90f; p.drag = 1.5f
                p.size = rnd(13f, 23f); p.maxLife = rnd(0.24f, 0.38f)
            }
            Trails.Style.BUBBLE -> {
                p.vx = inheritX + rnd(-60f, 60f); p.vy = inheritY + rnd(70f, 200f)
                p.gravity = 40f; p.drag = 1.9f
                p.size = rnd(16f, 30f); p.maxLife = rnd(0.26f, 0.42f)
            }
            Trails.Style.PETAL -> {
                p.vx = inheritX + rnd(-130f, 130f); p.vy = inheritY + rnd(-110f, 40f)
                p.gravity = -330f; p.drag = 1.1f
                p.size = rnd(18f, 30f); p.maxLife = rnd(0.30f, 0.45f)
                p.rot = rnd(0f, 6.283f); p.spin = rnd(-7f, 7f)
            }
            Trails.Style.STAR -> {
                p.vx = inheritX + rnd(-90f, 90f); p.vy = inheritY + rnd(-40f, 90f)
                p.gravity = -60f; p.drag = 2.6f
                p.size = rnd(16f, 28f); p.maxLife = rnd(0.22f, 0.36f)
                p.rot = rnd(0f, 6.283f); p.spin = rnd(-3f, 3f)
            }
            Trails.Style.RIBBON -> {
                p.vx = inheritX * 1.6f; p.vy = inheritY * 1.6f + rnd(-40f, 40f)
                p.gravity = 0f; p.drag = 3.0f
                p.size = rnd(20f, 32f); p.maxLife = rnd(0.24f, 0.36f)
                // lie along the direction of travel
                p.rot = kotlin.math.atan2(-vy, vx)
                p.spin = 0f
            }
            Trails.Style.SMOKE -> {
                p.vx = inheritX + rnd(-50f, 50f); p.vy = inheritY + rnd(20f, 110f)
                p.gravity = 30f; p.drag = 2.2f
                p.size = rnd(18f, 30f); p.maxLife = rnd(0.30f, 0.45f)
            }
            Trails.Style.FLAKE -> {
                p.vx = inheritX + rnd(-100f, 100f); p.vy = inheritY + rnd(-90f, 20f)
                p.gravity = -260f; p.drag = 1.4f
                p.size = rnd(16f, 26f); p.maxLife = rnd(0.30f, 0.45f)
                p.rot = rnd(0f, 6.283f); p.spin = rnd(-4f, 4f)
            }
            Trails.Style.BOLT -> {
                p.vx = inheritX + rnd(-120f, 120f); p.vy = inheritY + rnd(-60f, 60f)
                p.gravity = 0f; p.drag = 4.2f
                p.size = rnd(18f, 30f); p.maxLife = rnd(0.16f, 0.28f)
                p.rot = rnd(0f, 6.283f); p.spin = 0f
            }
            Trails.Style.PAW -> {
                // Paw prints stay where they were set down - no drift at all.
                p.vx = 0f; p.vy = 0f
                p.gravity = 0f; p.drag = 0f
                p.size = rnd(24f, 32f); p.maxLife = rnd(0.32f, 0.45f)
                p.rot = rnd(-0.5f, 0.5f); p.spin = 0f
            }
            Trails.Style.NOTE, Trails.Style.HEART -> {
                p.vx = inheritX + rnd(-60f, 60f); p.vy = inheritY + rnd(70f, 190f)
                p.gravity = 60f; p.drag = 1.7f
                p.size = rnd(20f, 30f); p.maxLife = rnd(0.30f, 0.45f)
                p.rot = rnd(0f, 6.283f); p.spin = rnd(-2f, 2f)
            }
            Trails.Style.PIXEL -> {
                p.vx = inheritX + rnd(-80f, 80f); p.vy = inheritY + rnd(-50f, 80f)
                p.gravity = 0f; p.drag = 3.6f
                p.size = rnd(18f, 30f); p.maxLife = rnd(0.18f, 0.30f)
            }
            Trails.Style.ORB -> {
                p.vx = inheritX + rnd(-50f, 50f); p.vy = inheritY + rnd(-30f, 60f)
                p.gravity = -40f; p.drag = 2.4f
                p.size = rnd(17f, 27f); p.maxLife = rnd(0.24f, 0.38f)
            }
            else -> {   // DROP
                p.vx = inheritX + rnd(-90f, 90f); p.vy = inheritY + rnd(-60f, 60f)
                p.gravity = -900f; p.drag = 0.9f
                p.size = rnd(16f, 26f); p.maxLife = rnd(0.26f, 0.40f)
                p.rot = rnd(-0.4f, 0.4f); p.spin = rnd(-2f, 2f)
            }
        }
        p.life = p.maxLife
    }

    fun pop(x: Float, y: Float, text: String, color: Int, size: Float = 46f) {
        val t = pops.obtain()
        t.x = x; t.y = y; t.text = text; t.color = color; t.size = size
        t.maxLife = 1.0f; t.life = t.maxLife
    }

    fun update(dt: Float) {
        for (p in parts.items) {
            if (!p.alive) continue
            p.life -= dt
            if (p.life <= 0f) { p.alive = false; continue }
            p.vy += p.gravity * dt
            if (p.drag > 0f) {
                val k = 1f - p.drag * dt
                val d = if (k < 0f) 0f else k
                p.vx *= d; p.vy *= d
            }
            p.x += p.vx * dt
            p.y += p.vy * dt
            p.rot += p.spin * dt
        }
        for (t in pops.items) {
            if (!t.alive) continue
            t.life -= dt
            if (t.life <= 0f) { t.alive = false; continue }
            t.y += 150f * dt
        }
        parts.sweep(); pops.sweep()
    }

    fun draw(c: Canvas, art: Art, viewTop: Float) {
        if (trailArtFor !== art) {
            trailArt = TrailArt(art)
            trailArtFor = art
        }
        paint.reset()
        paint.isAntiAlias = true
        for (p in parts.items) {
            if (!p.alive) continue
            val t = clamp01(p.life / p.maxLife)
            val sy = viewTop - p.y
            when (p.kind) {
                Kind.DUST -> {
                    paint.color = ColorX.withAlpha(p.color, t * 0.55f)
                    c.drawCircle(p.x, sy, p.size * (1.4f - t * 0.5f), paint)
                }
                Kind.SHARD -> {
                    paint.color = ColorX.withAlpha(p.color, t)
                    c.save()
                    c.translate(p.x, sy)
                    c.rotate(p.rot * 57.29578f)
                    rect.set(-p.size * 0.5f, -p.size * 0.32f, p.size * 0.5f, p.size * 0.32f)
                    c.drawRoundRect(rect, 3f, 3f, paint)
                    c.restore()
                }
                Kind.SPARK -> {
                    art.draw(c, art.spark, p.x, sy, p.size * 3.2f, p.size * 3.2f, t * 0.9f, p.color)
                    paint.color = ColorX.withAlpha(0xFFFFFFFF.toInt(), t * 0.8f)
                    c.drawCircle(p.x, sy, p.size * 0.32f, paint)
                }
                Kind.TRAIL -> {
                    art.draw(c, art.softGlow, p.x, sy, p.size * 2.6f, p.size * 2.6f, t * 0.55f, p.color)
                }
                Kind.RING -> {
                    paint.style = Paint.Style.STROKE
                    paint.strokeWidth = 8f * t
                    paint.color = ColorX.withAlpha(p.color, t * 0.8f)
                    c.drawCircle(p.x, sy, p.size * (1.6f - t), paint)
                    paint.style = Paint.Style.FILL
                }
                Kind.COSMETIC -> {
                    // Born hot, dies cool: the blend is what makes one colour pair read as fire
                    // and another as surf, using the very same shapes.
                    val col = ColorX.lerp(p.color2, p.color, t)
                    trailArt?.draw(c, p.style, p.x, sy, p.size, p.rot, col, t)
                }
                Kind.FEATHER -> {
                    paint.color = ColorX.withAlpha(p.color, t)
                    c.save()
                    c.translate(p.x, sy)
                    c.rotate(p.rot * 57.29578f)
                    rect.set(-p.size * 0.28f, -p.size * 0.5f, p.size * 0.28f, p.size * 0.5f)
                    c.drawOval(rect, paint)
                    c.restore()
                }
            }
        }
    }

    /** Pops are drawn after the world, with the caller's text paint. */
    fun drawPops(c: Canvas, textPaint: Paint, viewTop: Float) {
        for (t in pops.items) {
            if (!t.alive) continue
            val k = clamp01(t.life / t.maxLife)
            val rise = (1f - k) * 90f
            val scale = if (k > 0.82f) 1f + (k - 0.82f) * 2.4f else 1f
            textPaint.textSize = t.size * scale
            textPaint.color = ColorX.withAlpha(0xFF000000.toInt(), k * 0.35f)
            c.drawText(t.text, t.x + 3f, viewTop - t.y - rise + 3f, textPaint)
            textPaint.color = ColorX.withAlpha(t.color, k)
            c.drawText(t.text, t.x, viewTop - t.y - rise, textPaint)
        }
    }
}
