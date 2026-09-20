package com.blacklab.buddybounce.render

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import com.blacklab.buddybounce.data.Outfits
import kotlin.math.cos
import kotlin.math.sin

/**
 * Every outfit composes onto the same rig as Buddy himself (see [BuddyGeom]): [drawBack] runs
 * before his body, [drawFront] after his head. Drawing them as vectors means they squash and
 * lean with him for free.
 */
object OutfitArt {

    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val path = Path()
    private val r = RectF()

    private const val HEAD_TOP = BuddyGeom.HEAD_CY - BuddyGeom.HEAD_R
    private const val EYE_Y = BuddyGeom.HEAD_CY - BuddyGeom.HEAD_R * 0.22f

    fun drawBack(c: Canvas, id: String, pose: Pose, headX: Float, rim: Int) {
        p.reset(); p.isAntiAlias = true
        when (id) {
            "cape" -> cape(c, pose, 0xFFD23A4A.toInt(), 0xFF8E1F2E.toInt())
            "aviator" -> scarf(c, pose)
            "backpack" -> backpackBody(c)
            "bee" -> wings(c, pose)
            "astro" -> oxygenTank(c)
            "dino" -> dinoSpikes(c, pose)
            "cosmic" -> cape(c, pose, 0xFF3B2A7A.toInt(), 0xFF1A1140.toInt())
        }
    }

    fun drawFront(c: Canvas, id: String, pose: Pose, headX: Float, rim: Int) {
        p.reset(); p.isAntiAlias = true
        when (id) {
            Outfits.DEFAULT_ID -> collar(c, 0xFFC7413C.toInt())
            "bandana" -> bandana(c)
            "ball" -> tennisBall(c, pose, headX)
            "party" -> partyHat(c, pose, headX)
            "shades" -> shades(c, headX)
            "flowers" -> flowerCrown(c, pose, headX)
            "sweater" -> sweater(c)
            "aviator" -> goggles(c, headX)
            "chef" -> chefHat(c, headX)
            "cowboy" -> cowboyHat(c, headX)
            "snorkel" -> snorkel(c, headX)
            "backpack" -> { collar(c, 0xFF3F7A53.toInt()); straps(c) }
            "raincoat" -> raincoat(c, headX)
            "cape" -> { collar(c, 0xFFD23A4A.toInt()); mask(c, headX) }
            "knight" -> knightHelm(c, headX)
            "dino" -> dinoHood(c, headX)
            "bee" -> beeSuit(c, headX, pose)
            "astro" -> astroSuit(c, headX, pose)
            "cosmic" -> cosmicCoat(c, pose, headX)
            "crown" -> { crown(c, headX, pose); medal(c) }
        }
    }

    // ---- shared pieces ---------------------------------------------------------------------

    private fun collar(c: Canvas, color: Int) {
        p.color = color
        r.set(-42f, BuddyGeom.NECK_CY - 10f, 42f, BuddyGeom.NECK_CY + 8f)
        c.drawRoundRect(r, 9f, 9f, p)
        p.color = ColorX.shade(color, 0.75f)
        r.set(-42f, BuddyGeom.NECK_CY + 2f, 42f, BuddyGeom.NECK_CY + 8f)
        c.drawRoundRect(r, 4f, 4f, p)
        // name tag
        p.color = 0xFFF2C14E.toInt()
        c.drawCircle(0f, BuddyGeom.NECK_CY + 16f, 11f, p)
        p.color = 0xFFB98F25.toInt()
        c.drawCircle(0f, BuddyGeom.NECK_CY + 16f, 5.5f, p)
    }

    private fun bandana(c: Canvas) {
        p.color = 0xFFCC3B3B.toInt()
        path.reset()
        path.moveTo(-46f, BuddyGeom.NECK_CY - 12f)
        path.lineTo(46f, BuddyGeom.NECK_CY - 12f)
        path.lineTo(0f, BuddyGeom.NECK_CY + 44f)
        path.close()
        c.drawPath(path, p)
        p.color = 0xFFE8595B.toInt()
        r.set(-48f, BuddyGeom.NECK_CY - 16f, 48f, BuddyGeom.NECK_CY - 2f)
        c.drawRoundRect(r, 7f, 7f, p)
        p.color = ColorX.withAlpha(0xFFFFFFFF.toInt(), 0.55f)
        for (i in 0 until 3) c.drawCircle(-20f + i * 20f, BuddyGeom.NECK_CY + 10f + i * 4f, 3.4f, p)
    }

    private fun tennisBall(c: Canvas, pose: Pose, headX: Float) {
        val x = headX + pose.facing * 6f
        val y = BuddyGeom.MUZZLE_CY + 20f
        p.color = 0xFFD9E84C.toInt()
        c.drawCircle(x, y, 21f, p)
        p.color = ColorX.withAlpha(0xFFFFFFFF.toInt(), 0.85f)
        p.style = Paint.Style.STROKE
        p.strokeWidth = 3.2f
        r.set(x - 21f, y - 21f, x + 21f, y + 21f)
        c.drawArc(r, 200f, 140f, false, p)
        c.drawArc(r, 20f, 140f, false, p)
        p.style = Paint.Style.FILL
        p.color = ColorX.withAlpha(0xFF000000.toInt(), 0.12f)
        c.drawCircle(x + 6f, y + 6f, 16f, p)
    }

    private fun partyHat(c: Canvas, pose: Pose, headX: Float) {
        val x = headX + 4f
        val tipY = HEAD_TOP - 62f
        p.color = 0xFF57C4E5.toInt()
        path.reset()
        path.moveTo(x - 30f, HEAD_TOP + 10f)
        path.lineTo(x, tipY)
        path.lineTo(x + 30f, HEAD_TOP + 10f)
        path.close()
        c.drawPath(path, p)
        p.color = 0xFFF2645E.toInt()
        for (i in 0 until 3) {
            val t = 0.24f + i * 0.24f
            val w = 30f * (1f - t)
            val yy = HEAD_TOP + 10f - (HEAD_TOP + 10f - tipY) * t
            r.set(x - w, yy - 5f, x + w, yy + 5f)
            c.drawRoundRect(r, 3f, 3f, p)
        }
        p.color = 0xFFFFE07A.toInt()
        c.drawCircle(x, tipY - 6f, 10f, p)
    }

    private fun shades(c: Canvas, headX: Float) {
        p.color = 0xFF15161C.toInt()
        r.set(headX - 34f, EYE_Y - 15f, headX - 2f, EYE_Y + 11f)
        c.drawRoundRect(r, 9f, 9f, p)
        r.set(headX + 2f, EYE_Y - 15f, headX + 34f, EYE_Y + 11f)
        c.drawRoundRect(r, 9f, 9f, p)
        p.strokeWidth = 4f
        p.style = Paint.Style.STROKE
        c.drawLine(headX - 4f, EYE_Y - 6f, headX + 4f, EYE_Y - 6f, p)
        c.drawLine(headX - 34f, EYE_Y - 9f, headX - 46f, EYE_Y - 13f, p)
        c.drawLine(headX + 34f, EYE_Y - 9f, headX + 46f, EYE_Y - 13f, p)
        p.style = Paint.Style.FILL
        p.color = ColorX.withAlpha(0xFFFFFFFF.toInt(), 0.35f)
        c.drawLine(headX - 30f, EYE_Y - 10f, headX - 14f, EYE_Y + 4f, p)
    }

    private fun flowerCrown(c: Canvas, pose: Pose, headX: Float) {
        val colors = intArrayOf(0xFFF7A8C4.toInt(), 0xFFFFE07A.toInt(), 0xFFB9E3F2.toInt(), 0xFFF2645E.toInt())
        for (i in 0 until 6) {
            val a = (-0.95f + i * 0.38f)
            val x = headX + sin(a) * BuddyGeom.HEAD_R * 1.02f
            val y = BuddyGeom.HEAD_CY - cos(a) * BuddyGeom.HEAD_R * 1.02f
            val col = colors[i % colors.size]
            p.color = col
            for (k in 0 until 5) {
                val ang = k * 1.2566f + i.toFloat()
                c.drawCircle(x + cos(ang) * 7f, y + sin(ang) * 7f, 6f, p)
            }
            p.color = 0xFFFFF3C4.toInt()
            c.drawCircle(x, y, 4.2f, p)
        }
    }

    private fun sweater(c: Canvas) {
        val w = BuddyGeom.BODY_W * 0.5f
        val cy = BuddyGeom.BODY_CY
        p.color = 0xFF9C5BC4.toInt()
        r.set(-w * 0.99f, cy - BuddyGeom.BODY_H * 0.42f, w * 0.99f, cy + BuddyGeom.BODY_H * 0.56f)
        c.drawRoundRect(r, 28f, 30f, p)
        p.color = 0xFFF2E3B3.toInt()
        for (i in 0 until 3) {
            val yy = cy - BuddyGeom.BODY_H * 0.22f + i * 15f
            r.set(-w * 0.97f, yy, w * 0.97f, yy + 7f)
            c.drawRect(r, p)
        }
        p.color = 0xFF7B4499.toInt()
        r.set(-w * 0.99f, cy - BuddyGeom.BODY_H * 0.46f, w * 0.99f, cy - BuddyGeom.BODY_H * 0.3f)
        c.drawRoundRect(r, 12f, 12f, p)
    }

    private fun scarf(c: Canvas, pose: Pose) {
        val sway = sin(pose.time * 6f) * 14f - pose.lean * 26f
        p.color = 0xFFE0654F.toInt()
        path.reset()
        path.moveTo(-10f, BuddyGeom.NECK_CY)
        path.quadTo(-50f, BuddyGeom.NECK_CY + 30f + sway, -96f - sway, BuddyGeom.NECK_CY + 6f + sway * 1.4f)
        path.quadTo(-58f, BuddyGeom.NECK_CY + 54f, -8f, BuddyGeom.NECK_CY + 26f)
        path.close()
        c.drawPath(path, p)
    }

    private fun goggles(c: Canvas, headX: Float) {
        p.color = 0xFF6B4A2F.toInt()
        r.set(headX - 48f, HEAD_TOP + 14f, headX + 48f, HEAD_TOP + 32f)
        c.drawRoundRect(r, 8f, 8f, p)
        p.color = 0xFF9FD8F2.toInt()
        c.drawCircle(headX - 22f, HEAD_TOP + 22f, 16f, p)
        c.drawCircle(headX + 22f, HEAD_TOP + 22f, 16f, p)
        p.style = Paint.Style.STROKE
        p.strokeWidth = 5f
        p.color = 0xFF4A3220.toInt()
        c.drawCircle(headX - 22f, HEAD_TOP + 22f, 16f, p)
        c.drawCircle(headX + 22f, HEAD_TOP + 22f, 16f, p)
        p.style = Paint.Style.FILL
        p.color = ColorX.withAlpha(0xFFFFFFFF.toInt(), 0.6f)
        c.drawCircle(headX - 27f, HEAD_TOP + 17f, 4.5f, p)
        c.drawCircle(headX + 17f, HEAD_TOP + 17f, 4.5f, p)
    }

    private fun chefHat(c: Canvas, headX: Float) {
        p.color = 0xFFF7F8FB.toInt()
        c.drawCircle(headX - 24f, HEAD_TOP - 18f, 22f, p)
        c.drawCircle(headX + 24f, HEAD_TOP - 18f, 22f, p)
        c.drawCircle(headX, HEAD_TOP - 34f, 26f, p)
        r.set(headX - 34f, HEAD_TOP - 14f, headX + 34f, HEAD_TOP + 14f)
        c.drawRoundRect(r, 10f, 10f, p)
        p.color = 0xFFDFE3EC.toInt()
        r.set(headX - 36f, HEAD_TOP + 4f, headX + 36f, HEAD_TOP + 18f)
        c.drawRoundRect(r, 7f, 7f, p)
    }

    private fun cowboyHat(c: Canvas, headX: Float) {
        p.color = 0xFF8A5A33.toInt()
        r.set(headX - 68f, HEAD_TOP + 2f, headX + 68f, HEAD_TOP + 26f)
        c.drawRoundRect(r, 14f, 12f, p)
        p.color = 0xFFA06B3E.toInt()
        r.set(headX - 32f, HEAD_TOP - 34f, headX + 32f, HEAD_TOP + 12f)
        c.drawRoundRect(r, 18f, 18f, p)
        p.color = 0xFF5E3A20.toInt()
        r.set(headX - 33f, HEAD_TOP - 6f, headX + 33f, HEAD_TOP + 6f)
        c.drawRect(r, p)
        p.color = 0xFFE7C36B.toInt()
        c.drawCircle(headX + 20f, HEAD_TOP, 5f, p)
    }

    private fun snorkel(c: Canvas, headX: Float) {
        p.color = ColorX.withAlpha(0xFFBDEBFF.toInt(), 0.75f)
        r.set(headX - 40f, EYE_Y - 22f, headX + 40f, EYE_Y + 16f)
        c.drawRoundRect(r, 16f, 16f, p)
        p.style = Paint.Style.STROKE
        p.strokeWidth = 6f
        p.color = 0xFF2F80B8.toInt()
        c.drawRoundRect(r, 16f, 16f, p)
        p.style = Paint.Style.FILL
        p.color = ColorX.withAlpha(0xFFFFFFFF.toInt(), 0.5f)
        c.drawLine(headX - 30f, EYE_Y + 6f, headX - 8f, EYE_Y - 14f, p)
        // tube
        p.style = Paint.Style.STROKE
        p.strokeWidth = 9f
        p.strokeCap = Paint.Cap.ROUND
        p.color = 0xFFFFA33C.toInt()
        path.reset()
        path.moveTo(headX + 38f, EYE_Y + 10f)
        path.quadTo(headX + 62f, EYE_Y - 10f, headX + 58f, HEAD_TOP - 26f)
        c.drawPath(path, p)
        p.style = Paint.Style.FILL
    }

    private fun backpackBody(c: Canvas) {
        p.color = 0xFF3F7A53.toInt()
        r.set(-58f, BuddyGeom.BODY_CY - 26f, 58f, BuddyGeom.BODY_CY + 46f)
        c.drawRoundRect(r, 18f, 18f, p)
        p.color = 0xFF2E5C3E.toInt()
        r.set(-40f, BuddyGeom.BODY_CY + 4f, 40f, BuddyGeom.BODY_CY + 34f)
        c.drawRoundRect(r, 10f, 10f, p)
    }

    private fun straps(c: Canvas) {
        p.color = 0xFF2E5C3E.toInt()
        r.set(-34f, BuddyGeom.NECK_CY + 2f, -18f, BuddyGeom.BODY_CY + 30f)
        c.drawRoundRect(r, 7f, 7f, p)
        r.set(18f, BuddyGeom.NECK_CY + 2f, 34f, BuddyGeom.BODY_CY + 30f)
        c.drawRoundRect(r, 7f, 7f, p)
    }

    private fun raincoat(c: Canvas, headX: Float) {
        val w = BuddyGeom.BODY_W * 0.5f
        p.color = 0xFFF2C14E.toInt()
        r.set(-w * 1.06f, BuddyGeom.BODY_CY - BuddyGeom.BODY_H * 0.48f, w * 1.06f, BuddyGeom.BODY_CY + BuddyGeom.BODY_H * 0.6f)
        c.drawRoundRect(r, 26f, 26f, p)
        p.color = 0xFFD9A431.toInt()
        r.set(-5f, BuddyGeom.BODY_CY - BuddyGeom.BODY_H * 0.4f, 5f, BuddyGeom.BODY_CY + BuddyGeom.BODY_H * 0.55f)
        c.drawRect(r, p)
        // hood brim over the head
        p.color = 0xFFF2C14E.toInt()
        r.set(headX - 56f, HEAD_TOP - 16f, headX + 56f, HEAD_TOP + 34f)
        c.drawArc(r, 180f, 180f, true, p)
        p.color = 0xFFD9A431.toInt()
        r.set(headX - 58f, HEAD_TOP + 22f, headX + 58f, HEAD_TOP + 38f)
        c.drawRoundRect(r, 8f, 8f, p)
    }

    private fun cape(c: Canvas, pose: Pose, top: Int, bottom: Int) {
        val sway = sin(pose.time * 5.5f) * 10f - pose.lean * 30f
        p.color = top
        path.reset()
        path.moveTo(-40f, BuddyGeom.NECK_CY - 2f)
        path.lineTo(40f, BuddyGeom.NECK_CY - 2f)
        path.quadTo(70f + sway, BuddyGeom.BODY_CY + 40f, 34f + sway * 1.6f, BuddyGeom.BODY_CY + 74f)
        path.quadTo(0f, BuddyGeom.BODY_CY + 58f, -34f + sway * 1.6f, BuddyGeom.BODY_CY + 74f)
        path.quadTo(-70f + sway, BuddyGeom.BODY_CY + 40f, -40f, BuddyGeom.NECK_CY - 2f)
        path.close()
        c.drawPath(path, p)
        p.color = ColorX.withAlpha(bottom, 0.55f)
        path.reset()
        path.moveTo(-40f, BuddyGeom.NECK_CY - 2f)
        path.quadTo(-10f, BuddyGeom.BODY_CY + 30f, -34f + sway * 1.6f, BuddyGeom.BODY_CY + 74f)
        path.quadTo(-70f + sway, BuddyGeom.BODY_CY + 40f, -40f, BuddyGeom.NECK_CY - 2f)
        path.close()
        c.drawPath(path, p)
    }

    private fun mask(c: Canvas, headX: Float) {
        p.color = 0xFF2B3A6B.toInt()
        path.reset()
        path.moveTo(headX - 40f, EYE_Y - 16f)
        path.lineTo(headX + 40f, EYE_Y - 16f)
        path.lineTo(headX + 34f, EYE_Y + 10f)
        path.lineTo(headX, EYE_Y + 2f)
        path.lineTo(headX - 34f, EYE_Y + 10f)
        path.close()
        c.drawPath(path, p)
        p.color = 0xFFF7F8FB.toInt()
        c.drawCircle(headX - 18f, EYE_Y - 3f, 7f, p)
        c.drawCircle(headX + 18f, EYE_Y - 3f, 7f, p)
    }

    private fun knightHelm(c: Canvas, headX: Float) {
        p.color = 0xFFB9C2D0.toInt()
        r.set(headX - 48f, HEAD_TOP - 14f, headX + 48f, BuddyGeom.HEAD_CY + 34f)
        c.drawRoundRect(r, 30f, 26f, p)
        p.color = 0xFF8D97A8.toInt()
        r.set(headX - 48f, EYE_Y - 4f, headX + 48f, BuddyGeom.HEAD_CY + 34f)
        c.drawRoundRect(r, 18f, 18f, p)
        p.color = 0xFF31363F.toInt()
        r.set(headX - 40f, EYE_Y - 14f, headX + 40f, EYE_Y + 2f)
        c.drawRoundRect(r, 6f, 6f, p)
        p.color = 0xFFD7DEE9.toInt()
        for (i in 0 until 4) {
            r.set(headX - 30f + i * 18f, EYE_Y + 12f, headX - 22f + i * 18f, EYE_Y + 34f)
            c.drawRoundRect(r, 4f, 4f, p)
        }
        p.color = 0xFFD23A4A.toInt()
        path.reset()
        path.moveTo(headX - 8f, HEAD_TOP - 16f)
        path.quadTo(headX, HEAD_TOP - 66f, headX + 22f, HEAD_TOP - 40f)
        path.quadTo(headX + 10f, HEAD_TOP - 26f, headX + 8f, HEAD_TOP - 14f)
        path.close()
        c.drawPath(path, p)
    }

    private fun dinoSpikes(c: Canvas, pose: Pose) {
        p.color = 0xFFF2C14E.toInt()
        for (i in 0 until 4) {
            val yy = BuddyGeom.BODY_CY - 26f + i * 20f
            val x = -BuddyGeom.BODY_W * 0.5f - 2f
            path.reset()
            path.moveTo(x + 6f, yy)
            path.lineTo(x - 18f, yy + 8f)
            path.lineTo(x + 6f, yy + 18f)
            path.close()
            c.drawPath(path, p)
        }
    }

    private fun dinoHood(c: Canvas, headX: Float) {
        p.color = 0xFF57B96B.toInt()
        r.set(headX - 52f, HEAD_TOP - 18f, headX + 52f, BuddyGeom.HEAD_CY + 16f)
        c.drawRoundRect(r, 34f, 30f, p)
        p.color = 0xFF3F9152.toInt()
        r.set(headX - 52f, BuddyGeom.HEAD_CY - 4f, headX + 52f, BuddyGeom.HEAD_CY + 16f)
        c.drawRoundRect(r, 16f, 16f, p)
        p.color = 0xFFF2E3B3.toInt()
        for (i in 0 until 5) {
            val x = headX - 34f + i * 17f
            path.reset()
            path.moveTo(x - 7f, HEAD_TOP - 14f)
            path.lineTo(x, HEAD_TOP - 38f)
            path.lineTo(x + 7f, HEAD_TOP - 14f)
            path.close()
            c.drawPath(path, p)
        }
        // nostrils on the snout of the hood
        p.color = 0xFF2F7A42.toInt()
        c.drawCircle(headX - 12f, BuddyGeom.HEAD_CY + 4f, 4f, p)
        c.drawCircle(headX + 12f, BuddyGeom.HEAD_CY + 4f, 4f, p)
    }

    private fun wings(c: Canvas, pose: Pose) {
        val flap = 0.55f + 0.45f * sin(pose.time * 26f)
        p.color = ColorX.withAlpha(0xFFE8F4FF.toInt(), 0.75f)
        for (side in 0 until 2) {
            val dir = if (side == 0) -1f else 1f
            c.save()
            c.translate(dir * 30f, BuddyGeom.BODY_CY - 20f)
            c.rotate(dir * (18f + flap * 26f))
            r.set(if (dir > 0f) 0f else -76f, -26f, if (dir > 0f) 76f else 0f, 26f)
            c.drawOval(r, p)
            c.restore()
        }
    }

    private fun beeSuit(c: Canvas, headX: Float, pose: Pose) {
        val w = BuddyGeom.BODY_W * 0.5f
        p.color = 0xFFF2C14E.toInt()
        r.set(-w * 1.02f, BuddyGeom.BODY_CY - BuddyGeom.BODY_H * 0.44f, w * 1.02f, BuddyGeom.BODY_CY + BuddyGeom.BODY_H * 0.58f)
        c.drawRoundRect(r, 28f, 28f, p)
        p.color = 0xFF2A2B32.toInt()
        for (i in 0 until 3) {
            val yy = BuddyGeom.BODY_CY - BuddyGeom.BODY_H * 0.26f + i * 22f
            r.set(-w * 1.0f, yy, w * 1.0f, yy + 11f)
            c.drawRect(r, p)
        }
        // antennae
        p.style = Paint.Style.STROKE
        p.strokeWidth = 4.5f
        p.strokeCap = Paint.Cap.ROUND
        p.color = 0xFF2A2B32.toInt()
        val wobble = sin(pose.time * 7f) * 5f
        path.reset()
        path.moveTo(headX - 14f, HEAD_TOP + 6f)
        path.quadTo(headX - 30f, HEAD_TOP - 24f, headX - 26f + wobble, HEAD_TOP - 44f)
        c.drawPath(path, p)
        path.reset()
        path.moveTo(headX + 14f, HEAD_TOP + 6f)
        path.quadTo(headX + 30f, HEAD_TOP - 24f, headX + 26f + wobble, HEAD_TOP - 44f)
        c.drawPath(path, p)
        p.style = Paint.Style.FILL
        p.color = 0xFFF2C14E.toInt()
        c.drawCircle(headX - 26f + wobble, HEAD_TOP - 46f, 8f, p)
        c.drawCircle(headX + 26f + wobble, HEAD_TOP - 46f, 8f, p)
    }

    private fun oxygenTank(c: Canvas) {
        p.color = 0xFFCED6E2.toInt()
        r.set(-62f, BuddyGeom.BODY_CY - 24f, -28f, BuddyGeom.BODY_CY + 40f)
        c.drawRoundRect(r, 16f, 16f, p)
        r.set(28f, BuddyGeom.BODY_CY - 24f, 62f, BuddyGeom.BODY_CY + 40f)
        c.drawRoundRect(r, 16f, 16f, p)
        p.color = 0xFF7D8798.toInt()
        r.set(-58f, BuddyGeom.BODY_CY - 18f, -32f, BuddyGeom.BODY_CY - 8f)
        c.drawRect(r, p)
        r.set(32f, BuddyGeom.BODY_CY - 18f, 58f, BuddyGeom.BODY_CY - 8f)
        c.drawRect(r, p)
    }

    private fun astroSuit(c: Canvas, headX: Float, pose: Pose) {
        val w = BuddyGeom.BODY_W * 0.5f
        p.color = 0xFFF1F3F8.toInt()
        r.set(-w * 1.05f, BuddyGeom.BODY_CY - BuddyGeom.BODY_H * 0.46f, w * 1.05f, BuddyGeom.BODY_CY + BuddyGeom.BODY_H * 0.6f)
        c.drawRoundRect(r, 28f, 28f, p)
        p.color = 0xFFD8DEE9.toInt()
        for (i in 0 until 3) {
            r.set(-w * 1.03f, BuddyGeom.BODY_CY + i * 16f - 8f, w * 1.03f, BuddyGeom.BODY_CY + i * 16f - 2f)
            c.drawRect(r, p)
        }
        p.color = 0xFF3FA9E0.toInt()
        r.set(-20f, BuddyGeom.BODY_CY - 4f, 20f, BuddyGeom.BODY_CY + 20f)
        c.drawRoundRect(r, 8f, 8f, p)
        p.color = 0xFFF2645E.toInt()
        c.drawCircle(-10f, BuddyGeom.BODY_CY + 8f, 4.5f, p)
        p.color = 0xFF7BE3A0.toInt()
        c.drawCircle(4f, BuddyGeom.BODY_CY + 8f, 4.5f, p)

        // helmet glass over the head
        p.color = ColorX.withAlpha(0xFFBDEBFF.toInt(), 0.30f)
        c.drawCircle(headX, BuddyGeom.HEAD_CY - 2f, 62f, p)
        p.style = Paint.Style.STROKE
        p.strokeWidth = 7f
        p.color = 0xFFF1F3F8.toInt()
        c.drawCircle(headX, BuddyGeom.HEAD_CY - 2f, 62f, p)
        p.style = Paint.Style.FILL
        p.color = ColorX.withAlpha(0xFFFFFFFF.toInt(), 0.45f)
        path.reset()
        path.moveTo(headX - 46f, BuddyGeom.HEAD_CY - 22f)
        path.quadTo(headX - 30f, BuddyGeom.HEAD_CY - 56f, headX + 2f, BuddyGeom.HEAD_CY - 54f)
        path.quadTo(headX - 26f, BuddyGeom.HEAD_CY - 40f, headX - 36f, BuddyGeom.HEAD_CY - 12f)
        path.close()
        c.drawPath(path, p)
    }

    private fun cosmicCoat(c: Canvas, pose: Pose, headX: Float) {
        val w = BuddyGeom.BODY_W * 0.5f
        p.color = 0xFF221A52.toInt()
        r.set(-w * 1.06f, BuddyGeom.BODY_CY - BuddyGeom.BODY_H * 0.46f, w * 1.06f, BuddyGeom.BODY_CY + BuddyGeom.BODY_H * 0.62f)
        c.drawRoundRect(r, 30f, 30f, p)
        // starfield on the coat
        for (i in 0 until 14) {
            val sx = -w + (i * 37 % 100) / 100f * (w * 2f)
            val sy = BuddyGeom.BODY_CY - 32f + (i * 53 % 100) / 100f * 74f
            val tw = 0.35f + 0.65f * (0.5f + 0.5f * sin(pose.time * 3f + i))
            p.color = ColorX.withAlpha(0xFFFFFFFF.toInt(), tw)
            c.drawCircle(sx, sy, 2.4f + (i % 3), p)
        }
        p.color = ColorX.withAlpha(0xFF9F7BFF.toInt(), 0.5f)
        r.set(-w * 0.7f, BuddyGeom.BODY_CY + 6f, w * 0.7f, BuddyGeom.BODY_CY + 40f)
        c.drawOval(r, p)
        // constellation collar
        p.color = 0xFFCBB7FF.toInt()
        r.set(-44f, BuddyGeom.NECK_CY - 10f, 44f, BuddyGeom.NECK_CY + 8f)
        c.drawRoundRect(r, 9f, 9f, p)
    }

    private fun crown(c: Canvas, headX: Float, pose: Pose) {
        p.color = 0xFFF2C14E.toInt()
        path.reset()
        path.moveTo(headX - 40f, HEAD_TOP + 14f)
        path.lineTo(headX - 40f, HEAD_TOP - 34f)
        path.lineTo(headX - 20f, HEAD_TOP - 10f)
        path.lineTo(headX, HEAD_TOP - 42f)
        path.lineTo(headX + 20f, HEAD_TOP - 10f)
        path.lineTo(headX + 40f, HEAD_TOP - 34f)
        path.lineTo(headX + 40f, HEAD_TOP + 14f)
        path.close()
        c.drawPath(path, p)
        p.color = 0xFFD9A431.toInt()
        r.set(headX - 42f, HEAD_TOP + 6f, headX + 42f, HEAD_TOP + 20f)
        c.drawRoundRect(r, 6f, 6f, p)
        val cols = intArrayOf(0xFFE8595B.toInt(), 0xFF7BE3FF.toInt(), 0xFF7BE3A0.toInt())
        for (i in 0 until 3) {
            p.color = cols[i]
            c.drawCircle(headX - 22f + i * 22f, HEAD_TOP + 13f, 5.5f, p)
        }
        val sparkle = 0.5f + 0.5f * sin(pose.time * 4f)
        p.color = ColorX.withAlpha(0xFFFFFFFF.toInt(), sparkle)
        c.drawCircle(headX + 12f, HEAD_TOP - 30f, 4f, p)
    }

    private fun medal(c: Canvas) {
        p.color = 0xFF3B6FD6.toInt()
        r.set(-8f, BuddyGeom.NECK_CY + 4f, 8f, BuddyGeom.NECK_CY + 24f)
        c.drawRect(r, p)
        p.color = 0xFFF2C14E.toInt()
        c.drawCircle(0f, BuddyGeom.NECK_CY + 34f, 15f, p)
        p.color = 0xFFD9A431.toInt()
        c.drawCircle(0f, BuddyGeom.NECK_CY + 34f, 9f, p)
    }
}
