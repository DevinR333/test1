package com.blacklab.buddybounce.render

import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import com.blacklab.buddybounce.game.Flight
import com.blacklab.buddybounce.game.MathX.clamp01
import kotlin.math.cos
import kotlin.math.sin

/**
 * Buddy's proportions, in the local space every part of him is drawn in:
 * the origin is the paw line, negative Y is up, and the sprite is about 128 wu tall.
 */
object BuddyGeom {
    const val BODY_CY = -52f
    const val BODY_W = 96f
    const val BODY_H = 80f
    const val HEAD_CY = -100f
    const val HEAD_R = 45f
    const val MUZZLE_CY = -88f
    const val MUZZLE_W = 54f
    const val MUZZLE_H = 38f
    const val NECK_CY = -70f
    const val EAR_X = 36f
    const val EAR_Y = -116f
    const val TAIL_X = -46f
    const val TAIL_Y = -64f

    // A black lab never reads as pure black on screen: it is a very dark blue-grey with a
    // strong sheen on every upward-facing surface.
    const val FUR_DARK = 0xFF121318.toInt()
    const val FUR_BASE = 0xFF23252D.toInt()
    const val FUR_LIGHT = 0xFF383C49.toInt()
    const val FUR_SHEEN = 0xFF525A6B.toInt()
    const val MUZZLE_COLOR = 0xFF2E313B.toInt()
    const val NOSE_COLOR = 0xFF0C0D11.toInt()
    const val IRIS = 0xFFC98A33.toInt()
    const val TONGUE = 0xFFE87284.toInt()
}

/** One frame of Buddy: everything the renderer needs in order to pose him. */
class Pose {
    var squash = 0f        // +1 stretched (rising), -1 squashed (landing)
    var lean = 0f          // -1..1 steering lean
    var earFlap = 0f
    var tail = 0f          // wag phase in radians
    var blink = 0f
    var mouth = 0f
    var facing = 1f
    var flight = Flight.NONE
    var flightT = 0f
    var shield = 0f        // remaining seconds, 0 = none
    var invuln = 0f
    var hurt = 0f
    var spin = 0f          // death tumble, radians
    var dead = false
    var time = 0f

    fun reset(): Pose {
        squash = 0f; lean = 0f; earFlap = 0f; tail = 0f; blink = 0f; mouth = 0f
        facing = 1f; flight = Flight.NONE; flightT = 0f; shield = 0f; invuln = 0f
        hurt = 0f; spin = 0f; dead = false; time = 0f
        return this
    }
}

/**
 * Draws Buddy. Everything is vector work on the canvas rather than a sprite sheet, so he can
 * squash, stretch, lean, flap and wag continuously, and every outfit composes on top of the
 * same rig.
 */
class BuddyArt(private val art: Art) {

    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val path = Path()
    private val rect = RectF()

    private val bodyShader: Shader = LinearGradient(
        0f, BuddyGeom.BODY_CY - BuddyGeom.BODY_H * 0.5f,
        0f, BuddyGeom.BODY_CY + BuddyGeom.BODY_H * 0.5f,
        intArrayOf(BuddyGeom.FUR_LIGHT, BuddyGeom.FUR_BASE, BuddyGeom.FUR_DARK),
        floatArrayOf(0f, 0.45f, 1f),
        Shader.TileMode.CLAMP
    )

    private val headShader: Shader = LinearGradient(
        0f, BuddyGeom.HEAD_CY - BuddyGeom.HEAD_R,
        0f, BuddyGeom.HEAD_CY + BuddyGeom.HEAD_R,
        intArrayOf(BuddyGeom.FUR_LIGHT, BuddyGeom.FUR_BASE, BuddyGeom.FUR_DARK),
        floatArrayOf(0f, 0.5f, 1f),
        Shader.TileMode.CLAMP
    )

    /**
     * @param cx      screen x of Buddy's centre line
     * @param pawY    screen y of his paws
     * @param scale   1 = gameplay size; menus draw him larger
     * @param rim     rim-light colour, taken from the current biome
     */
    fun draw(c: Canvas, cx: Float, pawY: Float, scale: Float, pose: Pose, outfit: String, rim: Int) {
        c.save()
        c.translate(cx, pawY)
        if (scale != 1f) c.scale(scale, scale)
        if (pose.spin != 0f) c.rotate(Math.toDegrees(pose.spin.toDouble()).toFloat(), 0f, BuddyGeom.BODY_CY)
        c.rotate(pose.lean * 7f)

        val sx = 1f - 0.16f * pose.squash
        val sy = 1f + 0.20f * pose.squash
        c.scale(sx, sy)

        val headX = pose.facing * 5f
        val spread = (if (pose.squash < 0f) -pose.squash else 0f) * 14f + 4f

        drawFlightBack(c, pose)
        OutfitArt.drawBack(c, outfit, pose, headX, rim)
        drawTail(c, pose)
        drawLegs(c, pose, spread, back = true)
        drawBody(c, pose, rim)
        drawLegs(c, pose, spread, back = false)
        drawEar(c, pose, -1f, back = true)
        drawEar(c, pose, 1f, back = true)
        drawHead(c, pose, headX, rim)
        drawFace(c, pose, headX)
        drawEar(c, pose, if (pose.facing > 0f) 1f else -1f, back = false)
        OutfitArt.drawFront(c, outfit, pose, headX, rim)
        drawFlightFront(c, pose, headX)

        if (pose.hurt > 0.01f) {
            p.reset(); p.isAntiAlias = true
            p.color = ColorX.withAlpha(0xFFFF6B6B.toInt(), pose.hurt * 0.5f)
            c.drawCircle(0f, BuddyGeom.BODY_CY, 74f, p)
        }

        c.restore()

        if (pose.shield > 0f || pose.invuln > 0f) drawShield(c, cx, pawY, scale, pose, rim)
    }

    // -------------------------------------------------------------------------------------

    private fun drawBody(c: Canvas, pose: Pose, rim: Int) {
        val w = BuddyGeom.BODY_W * 0.5f
        val h = BuddyGeom.BODY_H * 0.5f
        val cy = BuddyGeom.BODY_CY

        path.reset()
        path.moveTo(0f, cy - h)
        path.cubicTo(w * 0.95f, cy - h, w * 1.02f, cy - h * 0.15f, w * 0.90f, cy + h * 0.6f)
        path.cubicTo(w * 0.82f, cy + h * 1.05f, -w * 0.82f, cy + h * 1.05f, -w * 0.90f, cy + h * 0.6f)
        path.cubicTo(-w * 1.02f, cy - h * 0.15f, -w * 0.95f, cy - h, 0f, cy - h)
        path.close()

        p.reset(); p.isAntiAlias = true
        p.shader = bodyShader
        c.drawPath(path, p)
        p.shader = null

        // chest sheen + the little white patch labs so often have
        p.color = ColorX.withAlpha(BuddyGeom.FUR_SHEEN, 0.30f)
        rect.set(-w * 0.55f, cy - h * 0.55f, w * 0.55f, cy + h * 0.15f)
        c.drawOval(rect, p)
        p.color = ColorX.withAlpha(0xFFFFFFFF.toInt(), 0.10f)
        rect.set(-14f, cy + h * 0.05f, 14f, cy + h * 0.72f)
        c.drawOval(rect, p)

        // rim light along the shoulder
        p.style = Paint.Style.STROKE
        p.strokeWidth = 5f
        p.strokeCap = Paint.Cap.ROUND
        p.color = ColorX.withAlpha(rim, 0.35f)
        path.reset()
        path.moveTo(-w * 0.86f, cy + h * 0.15f)
        path.cubicTo(-w * 0.98f, cy - h * 0.5f, -w * 0.45f, cy - h * 0.98f, 0f, cy - h * 0.95f)
        c.drawPath(path, p)
        p.style = Paint.Style.FILL
    }

    private fun drawLegs(c: Canvas, pose: Pose, spread: Float, back: Boolean) {
        val cy = BuddyGeom.BODY_CY + BuddyGeom.BODY_H * 0.18f
        val legW = if (back) 20f else 23f
        val baseX = if (back) 30f else 17f
        val color = if (back) BuddyGeom.FUR_DARK else BuddyGeom.FUR_BASE
        val dangle = clamp01(pose.squash) * 6f

        p.reset(); p.isAntiAlias = true
        for (side in 0 until 2) {
            val dir = if (side == 0) -1f else 1f
            val x = dir * (baseX + spread * 0.55f)
            val bottom = 0f + dangle
            p.color = color
            rect.set(x - legW * 0.5f, cy, x + legW * 0.5f, bottom)
            c.drawRoundRect(rect, legW * 0.5f, legW * 0.5f, p)
            // paw
            p.color = if (back) ColorX.shade(color, 1.15f) else BuddyGeom.FUR_LIGHT
            rect.set(x - legW * 0.62f, bottom - legW * 0.62f, x + legW * 0.62f, bottom + 3f)
            c.drawRoundRect(rect, legW * 0.5f, legW * 0.5f, p)
            if (!back) {
                p.color = ColorX.withAlpha(0xFF6B5A66.toInt(), 0.55f)
                rect.set(x - legW * 0.32f, bottom - legW * 0.34f, x + legW * 0.32f, bottom - 1f)
                c.drawOval(rect, p)
            }
        }
    }

    private fun drawHead(c: Canvas, pose: Pose, headX: Float, rim: Int) {
        val cy = BuddyGeom.HEAD_CY
        val r = BuddyGeom.HEAD_R

        p.reset(); p.isAntiAlias = true
        c.save()
        c.translate(headX, 0f)

        path.reset()
        path.moveTo(0f, cy - r)
        path.cubicTo(r * 0.98f, cy - r * 0.96f, r * 1.06f, cy - r * 0.1f, r * 0.86f, cy + r * 0.52f)
        path.cubicTo(r * 0.6f, cy + r * 1.0f, -r * 0.6f, cy + r * 1.0f, -r * 0.86f, cy + r * 0.52f)
        path.cubicTo(-r * 1.06f, cy - r * 0.1f, -r * 0.98f, cy - r * 0.96f, 0f, cy - r)
        path.close()
        p.shader = headShader
        c.drawPath(path, p)
        p.shader = null

        // top-of-head sheen
        p.color = ColorX.withAlpha(BuddyGeom.FUR_SHEEN, 0.38f)
        rect.set(-r * 0.62f, cy - r * 0.92f, r * 0.62f, cy - r * 0.18f)
        c.drawOval(rect, p)

        // rim light
        p.style = Paint.Style.STROKE
        p.strokeWidth = 5f
        p.strokeCap = Paint.Cap.ROUND
        p.color = ColorX.withAlpha(rim, 0.55f)
        path.reset()
        path.moveTo(-r * 0.92f, cy - r * 0.1f)
        path.cubicTo(-r * 0.92f, cy - r * 0.86f, -r * 0.3f, cy - r * 1.02f, r * 0.28f, cy - r * 0.96f)
        c.drawPath(path, p)
        p.style = Paint.Style.FILL

        c.restore()
    }

    private fun drawFace(c: Canvas, pose: Pose, headX: Float) {
        val cy = BuddyGeom.HEAD_CY
        val r = BuddyGeom.HEAD_R
        c.save()
        c.translate(headX, 0f)
        p.reset(); p.isAntiAlias = true

        // muzzle
        val mx = pose.facing * 4f
        p.color = BuddyGeom.MUZZLE_COLOR
        rect.set(
            mx - BuddyGeom.MUZZLE_W * 0.5f, BuddyGeom.MUZZLE_CY - BuddyGeom.MUZZLE_H * 0.34f,
            mx + BuddyGeom.MUZZLE_W * 0.5f, BuddyGeom.MUZZLE_CY + BuddyGeom.MUZZLE_H * 0.78f
        )
        c.drawRoundRect(rect, BuddyGeom.MUZZLE_W * 0.42f, BuddyGeom.MUZZLE_H * 0.5f, p)

        // mouth / tongue
        if (pose.mouth > 0.03f) {
            p.color = 0xFF17181D.toInt()
            rect.set(mx - 17f, BuddyGeom.MUZZLE_CY + 2f, mx + 17f, BuddyGeom.MUZZLE_CY + 8f + 22f * pose.mouth)
            c.drawRoundRect(rect, 12f, 12f, p)
            p.color = BuddyGeom.TONGUE
            rect.set(mx - 10f, BuddyGeom.MUZZLE_CY + 8f, mx + 10f, BuddyGeom.MUZZLE_CY + 8f + 20f * pose.mouth)
            c.drawRoundRect(rect, 10f, 10f, p)
        } else {
            p.style = Paint.Style.STROKE
            p.strokeWidth = 3.4f
            p.strokeCap = Paint.Cap.ROUND
            p.color = 0xFF14151A.toInt()
            path.reset()
            path.moveTo(mx - 13f, BuddyGeom.MUZZLE_CY + 6f)
            path.quadTo(mx - 6f, BuddyGeom.MUZZLE_CY + 13f, mx, BuddyGeom.MUZZLE_CY + 6f)
            path.quadTo(mx + 6f, BuddyGeom.MUZZLE_CY + 13f, mx + 13f, BuddyGeom.MUZZLE_CY + 6f)
            c.drawPath(path, p)
            p.style = Paint.Style.FILL
        }

        // nose
        p.color = BuddyGeom.NOSE_COLOR
        rect.set(mx - 15f, BuddyGeom.MUZZLE_CY - 15f, mx + 15f, BuddyGeom.MUZZLE_CY + 4f)
        c.drawRoundRect(rect, 10f, 9f, p)
        p.color = ColorX.withAlpha(0xFFFFFFFF.toInt(), 0.22f)
        rect.set(mx - 9f, BuddyGeom.MUZZLE_CY - 12f, mx + 1f, BuddyGeom.MUZZLE_CY - 7f)
        c.drawOval(rect, p)

        // eyes
        val eyeY = cy - r * 0.22f
        for (side in 0 until 2) {
            val dir = if (side == 0) -1f else 1f
            val ex = dir * 18f + pose.facing * 2f
            p.color = 0xFF0D0E12.toInt()
            c.drawCircle(ex, eyeY, 12.5f, p)
            if (pose.dead) {
                p.style = Paint.Style.STROKE
                p.strokeWidth = 4f
                p.strokeCap = Paint.Cap.ROUND
                p.color = 0xFFE7E9F2.toInt()
                c.drawLine(ex - 7f, eyeY - 7f, ex + 7f, eyeY + 7f, p)
                c.drawLine(ex + 7f, eyeY - 7f, ex - 7f, eyeY + 7f, p)
                p.style = Paint.Style.FILL
                continue
            }
            p.color = BuddyGeom.IRIS
            c.drawCircle(ex, eyeY + 0.5f, 8.2f, p)
            p.color = 0xFF0A0B0E.toInt()
            c.drawCircle(ex + pose.facing * 1.6f, eyeY + 1f, 4.6f, p)
            p.color = ColorX.withAlpha(0xFFFFFFFF.toInt(), 0.95f)
            c.drawCircle(ex - 3.6f, eyeY - 4.2f, 2.9f, p)

            // eyelid
            if (pose.blink > 0.02f) {
                p.color = BuddyGeom.FUR_BASE
                rect.set(ex - 14f, eyeY - 15f, ex + 14f, eyeY - 15f + 30f * pose.blink)
                c.drawRect(rect, p)
            }
        }

        // brow ridges - a couple of strokes do a lot for expression
        p.style = Paint.Style.STROKE
        p.strokeWidth = 4f
        p.strokeCap = Paint.Cap.ROUND
        p.color = ColorX.withAlpha(BuddyGeom.FUR_SHEEN, 0.5f)
        val browLift = clamp01(pose.squash) * 3f
        c.drawLine(-27f, eyeY - 16f - browLift, -10f, eyeY - 20f - browLift, p)
        c.drawLine(27f, eyeY - 16f - browLift, 10f, eyeY - 20f - browLift, p)
        p.style = Paint.Style.FILL

        c.restore()
    }

    private fun drawEar(c: Canvas, pose: Pose, side: Float, back: Boolean) {
        // The near ear is drawn again on top of the head so it reads as flopping forward.
        if (!back && side * pose.facing < 0f) return
        val rootX = side * BuddyGeom.EAR_X
        val rootY = BuddyGeom.EAR_Y + 14f
        val flap = pose.earFlap * 34f + pose.lean * -10f * side
        val idle = sin(pose.time * 2.1f + side) * 2.5f

        c.save()
        c.translate(rootX, rootY)
        c.rotate(side * (12f + flap) + idle)

        p.reset(); p.isAntiAlias = true
        p.color = if (back) BuddyGeom.FUR_DARK else BuddyGeom.FUR_BASE
        path.reset()
        path.moveTo(0f, 0f)
        path.cubicTo(side * 30f, 6f, side * 34f, 52f, side * 20f, 74f)
        path.cubicTo(side * 10f, 88f, -side * 10f, 80f, -side * 12f, 54f)
        path.cubicTo(-side * 14f, 28f, -side * 8f, 6f, 0f, 0f)
        path.close()
        c.drawPath(path, p)

        p.color = ColorX.withAlpha(BuddyGeom.FUR_SHEEN, if (back) 0.16f else 0.28f)
        path.reset()
        path.moveTo(side * 2f, 6f)
        path.cubicTo(side * 22f, 14f, side * 24f, 46f, side * 14f, 62f)
        path.cubicTo(side * 6f, 48f, side * 2f, 24f, side * 2f, 6f)
        path.close()
        c.drawPath(path, p)

        c.restore()
    }

    private fun drawTail(c: Canvas, pose: Pose) {
        val wag = sin(pose.tail) * 26f
        c.save()
        c.translate(BuddyGeom.TAIL_X * pose.facing, BuddyGeom.TAIL_Y)
        c.rotate(wag - 18f * pose.facing)

        p.reset(); p.isAntiAlias = true
        p.color = BuddyGeom.FUR_DARK
        p.style = Paint.Style.STROKE
        p.strokeCap = Paint.Cap.ROUND
        p.strokeWidth = 19f
        path.reset()
        path.moveTo(0f, 0f)
        path.quadTo(-pose.facing * 34f, -16f, -pose.facing * 52f, -46f)
        c.drawPath(path, p)
        p.strokeWidth = 11f
        p.color = ColorX.withAlpha(BuddyGeom.FUR_LIGHT, 0.75f)
        c.drawPath(path, p)
        p.style = Paint.Style.FILL
        c.restore()
    }

    // ---- flight rigs -----------------------------------------------------------------------

    private fun drawFlightBack(c: Canvas, pose: Pose) {
        when (pose.flight) {
            Flight.JETPACK -> {
                p.reset(); p.isAntiAlias = true
                p.color = 0xFFB8C0CC.toInt()
                rect.set(-64f, BuddyGeom.BODY_CY - 34f, -22f, BuddyGeom.BODY_CY + 40f)
                c.drawRoundRect(rect, 18f, 18f, p)
                rect.set(22f, BuddyGeom.BODY_CY - 34f, 64f, BuddyGeom.BODY_CY + 40f)
                c.drawRoundRect(rect, 18f, 18f, p)
                p.color = 0xFFE05E4A.toInt()
                rect.set(-60f, BuddyGeom.BODY_CY - 30f, -26f, BuddyGeom.BODY_CY - 12f)
                c.drawRoundRect(rect, 8f, 8f, p)
                rect.set(26f, BuddyGeom.BODY_CY - 30f, 60f, BuddyGeom.BODY_CY - 12f)
                c.drawRoundRect(rect, 8f, 8f, p)
                drawFlame(c, -43f, BuddyGeom.BODY_CY + 44f, 30f, 1f, pose.time)
                drawFlame(c, 43f, BuddyGeom.BODY_CY + 44f, 30f, 1f, pose.time + 0.6f)
            }
            Flight.ROCKET -> {
                p.reset(); p.isAntiAlias = true
                p.color = 0xFFEFEFF4.toInt()
                rect.set(-30f, BuddyGeom.BODY_CY - 46f, 30f, BuddyGeom.BODY_CY + 78f)
                c.drawRoundRect(rect, 30f, 22f, p)
                p.color = 0xFFD8453B.toInt()
                path.reset()
                path.moveTo(-30f, BuddyGeom.BODY_CY - 20f)
                path.lineTo(0f, BuddyGeom.BODY_CY - 74f)
                path.lineTo(30f, BuddyGeom.BODY_CY - 20f)
                path.close()
                c.drawPath(path, p)
                path.reset()
                path.moveTo(-30f, BuddyGeom.BODY_CY + 34f)
                path.lineTo(-56f, BuddyGeom.BODY_CY + 84f)
                path.lineTo(-30f, BuddyGeom.BODY_CY + 72f)
                path.close()
                path.moveTo(30f, BuddyGeom.BODY_CY + 34f)
                path.lineTo(56f, BuddyGeom.BODY_CY + 84f)
                path.lineTo(30f, BuddyGeom.BODY_CY + 72f)
                path.close()
                c.drawPath(path, p)
                drawFlame(c, 0f, BuddyGeom.BODY_CY + 84f, 56f, 1.6f, pose.time)
            }
        }
    }

    private fun drawFlightFront(c: Canvas, pose: Pose, headX: Float) {
        if (pose.flight != Flight.PROPELLER) return
        val cy = BuddyGeom.HEAD_CY - BuddyGeom.HEAD_R * 0.92f
        c.save()
        c.translate(headX, 0f)
        p.reset(); p.isAntiAlias = true

        // cap
        p.color = 0xFF3FA9E0.toInt()
        rect.set(-40f, cy - 6f, 40f, cy + 34f)
        c.drawArc(rect, 180f, 180f, true, p)
        p.color = 0xFF2E86B5.toInt()
        rect.set(-44f, cy + 22f, 44f, cy + 34f)
        c.drawRoundRect(rect, 6f, 6f, p)

        // spinning blades - a squashed ellipse reads as rotation at speed
        val spin = cos(pose.time * 34f)
        p.color = 0xFFE8EDF5.toInt()
        rect.set(-56f * spin, cy - 26f, 56f * spin, cy - 14f)
        if (rect.left > rect.right) rect.set(rect.right, rect.top, rect.left, rect.bottom)
        c.drawRoundRect(rect, 6f, 6f, p)
        p.color = 0xFF9AA6B8.toInt()
        c.drawCircle(0f, cy - 20f, 7f, p)
        p.color = ColorX.withAlpha(0xFFFFFFFF.toInt(), 0.25f)
        rect.set(-58f, cy - 30f, 58f, cy - 10f)
        c.drawOval(rect, p)
        c.restore()
    }

    private fun drawFlame(c: Canvas, x: Float, y: Float, size: Float, power: Float, t: Float) {
        val flicker = 0.75f + 0.25f * sin(t * 33f + x)
        art.draw(c, art.softGlow, x, y + size * 0.6f, size * 3.4f, size * 4.2f, 0.45f * power, 0xFFFF9A3C.toInt())
        p.reset(); p.isAntiAlias = true
        p.color = 0xFFFFB347.toInt()
        path.reset()
        path.moveTo(x - size * 0.5f, y)
        path.quadTo(x, y + size * 2.3f * flicker * power, x + size * 0.5f, y)
        path.quadTo(x, y + size * 0.35f, x - size * 0.5f, y)
        path.close()
        c.drawPath(path, p)
        p.color = 0xFFFFE9A8.toInt()
        path.reset()
        path.moveTo(x - size * 0.26f, y)
        path.quadTo(x, y + size * 1.3f * flicker * power, x + size * 0.26f, y)
        path.quadTo(x, y + size * 0.2f, x - size * 0.26f, y)
        path.close()
        c.drawPath(path, p)
    }

    private fun drawShield(c: Canvas, cx: Float, pawY: Float, scale: Float, pose: Pose, rim: Int) {
        val r = 86f * scale
        val cy = pawY + BuddyGeom.BODY_CY * scale
        val expiring = pose.shield in 0.001f..3f
        val pulse = if (expiring) (0.4f + 0.6f * clamp01(sin(pose.time * 18f) * 0.5f + 0.5f)) else 1f
        val base = if (pose.shield > 0f) 0.55f else clamp01(pose.invuln) * 0.8f

        art.drawGlow(c, cx, cy, r * 1.5f, 0xFF8FD3F4.toInt(), 0.22f * base * pulse)
        p.reset(); p.isAntiAlias = true
        p.style = Paint.Style.STROKE
        p.strokeWidth = 5f * scale
        p.color = ColorX.withAlpha(0xFFBDEBFF.toInt(), 0.75f * base * pulse)
        c.drawCircle(cx, cy, r, p)
        p.strokeWidth = 2f * scale
        p.color = ColorX.withAlpha(0xFFFFFFFF.toInt(), 0.4f * base * pulse)
        c.drawCircle(cx, cy, r * 0.88f, p)
        p.style = Paint.Style.FILL
        p.color = ColorX.withAlpha(0xFF9FDCF7.toInt(), 0.10f * base)
        c.drawCircle(cx, cy, r, p)
    }
}
