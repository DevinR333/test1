package com.blacklab.buddybounce.render

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import kotlin.math.sin

/**
 * "Developer Approved": the one skin that is not a dog.
 *
 * Every other outfit composes onto Buddy's rig (see [BuddyGeom]), because every other outfit is
 * something a dog puts on. This one replaces him outright, so it is drawn here rather than in
 * [OutfitArt] - there is no torso to paint a shirt onto and no muzzle to hang a collar from.
 *
 * He is built to Buddy's footprint on purpose. The collision box does not change with the skin,
 * so a figure that stood taller or wider than the dog would take hits out of thin air. Being
 * hunched over a keyboard he has never quite left solves that for free: it costs him the height
 * a standing man would have and puts his shoulders out in front of his hips.
 *
 * Profile, facing +x, feet at y = 0, exactly like the dog.
 */
object DevArt {

    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val ink = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
        strokeWidth = 5f
        color = BuddyGeom.INK
    }
    private val path = Path()
    private val r = RectF()

    private const val SHIRT = 0xFFF7F9FC.toInt()
    private const val SHIRT_SHADE = 0xFFD5DCE6.toInt()
    private const val TROUSER = 0xFF23262F.toInt()
    private const val TROUSER_LIT = 0xFF3A3F4C.toInt()
    private const val SHOE = 0xFF15171D.toInt()
    private const val STRAP = 0xFFC8342F.toInt()
    private const val STRAP_SHADE = 0xFF8E1F1C.toInt()
    private const val SKIN = 0xFFF2C6A0.toInt()
    private const val SKIN_SHADE = 0xFFD9A47B.toInt()
    private const val HAIR = 0xFF4A3524.toInt()
    private const val LENS = 0xFF2E6BD6.toInt()

    // landmarks: hips, shoulders, head
    private const val HIP_X = -14f
    private const val HIP_Y = -74f
    private const val SHO_X = 18f
    private const val SHO_Y = -142f
    private const val HEAD_X = 42f
    private const val HEAD_Y = -168f
    private const val HEAD_R = 27f

    fun draw(c: Canvas, pose: Pose, rim: Int) {
        val t = pose.time
        // the only thing that moves: a nervous little bob, and the legs swinging with the jump
        val swing = pose.lean * 9f + sin(t * 2.1f) * 1.5f
        val stretch = if (pose.squash > 0f) pose.squash else 0f

        prep()

        backLeg(c, swing)
        backArm(c, swing)
        trousers(c)
        shirt(c, stretch)
        suspenders(c)
        frontLeg(c, swing)
        bowTie(c)
        head(c, pose, rim)
        frontArm(c, swing)
    }

    private fun prep() {
        p.reset(); p.isAntiAlias = true
        ink.strokeWidth = 5f
        ink.color = BuddyGeom.INK
    }

    private fun fillInk(c: Canvas, color: Int) {
        c.drawPath(path, ink)
        p.color = color
        c.drawPath(path, p)
    }

    /** One leg plus its shoe. [dx] shifts it fore/aft, [depth] darkens the far one. */
    private fun leg(c: Canvas, dx: Float, depth: Float, color: Int) {
        val topX = HIP_X + dx
        path.reset()
        path.moveTo(topX - 15f, HIP_Y + 6f)
        path.cubicTo(topX - 18f, -46f, topX - 14f, -22f, topX - 13f, -6f)
        path.lineTo(topX + 13f, -6f)
        path.cubicTo(topX + 14f, -24f, topX + 17f, -48f, topX + 15f, HIP_Y + 6f)
        path.close()
        fillInk(c, ColorX.shade(color, depth))

        // the shoe: a rounded wedge with the toe out front, because dress shoes have a toe
        path.reset()
        path.moveTo(topX - 16f, -14f)
        path.lineTo(topX + 16f, -14f)
        path.cubicTo(topX + 34f, -12f, topX + 40f, -4f, topX + 40f, 0f)
        path.lineTo(topX - 16f, 0f)
        path.close()
        fillInk(c, ColorX.shade(SHOE, depth))
        // a lace-line highlight so it is not a black blob
        p.color = ColorX.withAlpha(0xFFFFFFFF.toInt(), 0.14f * depth)
        r.set(topX + 2f, -13f, topX + 26f, -8f)
        c.drawRoundRect(r, 3f, 3f, p)
    }

    private fun backLeg(c: Canvas, swing: Float) = leg(c, -13f - swing, 0.62f, TROUSER)

    private fun frontLeg(c: Canvas, swing: Float) = leg(c, 11f + swing, 1f, TROUSER_LIT)

    /** The waistband, drawn over the leg tops so the trousers read as one garment. */
    private fun trousers(c: Canvas) {
        path.reset()
        path.moveTo(HIP_X - 26f, HIP_Y - 16f)
        path.cubicTo(HIP_X - 30f, HIP_Y + 8f, HIP_X + 24f, HIP_Y + 14f, HIP_X + 28f, HIP_Y - 4f)
        path.lineTo(HIP_X + 26f, HIP_Y - 20f)
        path.close()
        fillInk(c, TROUSER)
        // belt line
        p.color = ColorX.shade(TROUSER, 0.55f)
        r.set(HIP_X - 27f, HIP_Y - 18f, HIP_X + 27f, HIP_Y - 9f)
        c.drawRoundRect(r, 3f, 3f, p)
    }

    /**
     * The button-down, hunched: the back is a long curve from the hips up and OVER, so the
     * shoulders end up in front of the waist rather than above it.
     */
    private fun shirt(c: Canvas, stretch: Float) {
        val lift = stretch * 5f
        path.reset()
        path.moveTo(HIP_X - 26f, HIP_Y - 6f)
        // the curved back - the hunch
        path.cubicTo(
            HIP_X - 38f, SHO_Y + 46f - lift,
            SHO_X - 30f, SHO_Y - 4f - lift,
            SHO_X + 6f, SHO_Y - 10f - lift
        )
        // over the shoulder and down the chest
        path.cubicTo(
            SHO_X + 26f, SHO_Y - 4f - lift,
            SHO_X + 30f, SHO_Y + 40f - lift,
            SHO_X + 22f, HIP_Y - 16f
        )
        path.cubicTo(HIP_X + 16f, HIP_Y - 2f, HIP_X - 10f, HIP_Y - 2f, HIP_X - 26f, HIP_Y - 6f)
        path.close()
        fillInk(c, SHIRT)

        // the shaded underside of the hunch, so the back reads as curved rather than flat
        p.color = ColorX.withAlpha(SHIRT_SHADE, 0.85f)
        path.reset()
        path.moveTo(HIP_X - 26f, HIP_Y - 6f)
        path.cubicTo(
            HIP_X - 38f, SHO_Y + 46f - lift,
            SHO_X - 30f, SHO_Y - 4f - lift,
            SHO_X + 6f, SHO_Y - 10f - lift
        )
        path.lineTo(SHO_X - 4f, SHO_Y + 6f - lift)
        path.cubicTo(
            SHO_X - 26f, SHO_Y + 12f - lift,
            HIP_X - 22f, SHO_Y + 48f - lift,
            HIP_X - 14f, HIP_Y - 6f
        )
        path.close()
        c.drawPath(path, p)

        // placket and buttons down the front
        p.color = ColorX.withAlpha(SHIRT_SHADE, 0.9f)
        path.reset()
        path.moveTo(SHO_X + 14f, SHO_Y + 6f - lift)
        path.cubicTo(SHO_X + 22f, SHO_Y + 40f - lift, SHO_X + 18f, HIP_Y - 22f, SHO_X + 12f, HIP_Y - 12f)
        path.lineTo(SHO_X + 3f, HIP_Y - 14f)
        path.cubicTo(SHO_X + 10f, HIP_Y - 24f, SHO_X + 13f, SHO_Y + 40f - lift, SHO_X + 5f, SHO_Y + 6f - lift)
        path.close()
        c.drawPath(path, p)
        p.color = ColorX.shade(SHIRT_SHADE, 0.7f)
        for (i in 0 until 3) {
            val k = i / 2f
            c.drawCircle(
                SHO_X + 8f + k * 7f,
                SHO_Y + 24f - lift + k * (HIP_Y - 30f - (SHO_Y + 24f - lift)),
                2.6f, p
            )
        }
    }

    /** Red suspenders: over the shoulder, down the chest, clipped to the waistband. */
    private fun suspenders(c: Canvas) {
        ink.strokeWidth = 3f
        ink.color = ColorX.withAlpha(BuddyGeom.INK, 0.55f)
        // the far strap, darker - it goes over the shoulder we can barely see
        p.style = Paint.Style.STROKE
        p.strokeCap = Paint.Cap.BUTT
        p.strokeWidth = 9f
        p.color = STRAP_SHADE
        path.reset()
        path.moveTo(SHO_X - 6f, SHO_Y + 4f)
        path.cubicTo(SHO_X - 16f, SHO_Y + 40f, HIP_X - 4f, HIP_Y - 40f, HIP_X - 2f, HIP_Y - 14f)
        c.drawPath(path, p)

        // the near strap
        p.strokeWidth = 12f
        p.color = STRAP
        path.reset()
        path.moveTo(SHO_X + 10f, SHO_Y - 2f)
        path.cubicTo(SHO_X + 12f, SHO_Y + 40f, HIP_X + 18f, HIP_Y - 44f, HIP_X + 16f, HIP_Y - 12f)
        c.drawPath(path, p)
        // a lit edge along it
        p.strokeWidth = 3f
        p.color = ColorX.withAlpha(0xFFFFFFFF.toInt(), 0.22f)
        path.reset()
        path.moveTo(SHO_X + 14f, SHO_Y)
        path.cubicTo(SHO_X + 16f, SHO_Y + 40f, HIP_X + 22f, HIP_Y - 44f, HIP_X + 20f, HIP_Y - 14f)
        c.drawPath(path, p)
        p.style = Paint.Style.FILL

        // the clips
        p.color = 0xFFC9D0DA.toInt()
        r.set(HIP_X + 11f, HIP_Y - 20f, HIP_X + 21f, HIP_Y - 10f)
        c.drawRoundRect(r, 2f, 2f, p)
        prep()
    }

    /** A proper bow tie: two wedges meeting at a knot. */
    private fun bowTie(c: Canvas) {
        val bx = SHO_X + 16f
        val by = SHO_Y - 4f
        ink.strokeWidth = 3.4f
        path.reset()
        path.moveTo(bx, by)
        path.lineTo(bx - 19f, by - 11f)
        path.lineTo(bx - 19f, by + 11f)
        path.close()
        fillInk(c, 0xFF17181C.toInt())
        path.reset()
        path.moveTo(bx, by)
        path.lineTo(bx + 17f, by - 12f)
        path.lineTo(bx + 17f, by + 10f)
        path.close()
        fillInk(c, 0xFF23262F.toInt())
        p.color = 0xFF0D0E11.toInt()
        r.set(bx - 5f, by - 6f, bx + 5f, by + 6f)
        c.drawRoundRect(r, 3f, 3f, p)
        prep()
    }

    private fun head(c: Canvas, pose: Pose, rim: Int) {
        // neck, craned forward out of the hunched shoulders
        path.reset()
        path.moveTo(SHO_X + 2f, SHO_Y + 2f)
        path.lineTo(HEAD_X - 12f, HEAD_Y + HEAD_R - 2f)
        path.lineTo(HEAD_X + 4f, HEAD_Y + HEAD_R + 2f)
        path.lineTo(SHO_X + 18f, SHO_Y + 4f)
        path.close()
        fillInk(c, SKIN_SHADE)

        // the skull, a touch longer than it is tall
        path.reset()
        r.set(HEAD_X - HEAD_R - 3f, HEAD_Y - HEAD_R, HEAD_X + HEAD_R + 1f, HEAD_Y + HEAD_R)
        path.addOval(r, Path.Direction.CW)
        fillInk(c, SKIN)

        // ear, small and set back
        p.color = SKIN_SHADE
        r.set(HEAD_X - 24f, HEAD_Y - 6f, HEAD_X - 10f, HEAD_Y + 12f)
        c.drawOval(r, p)

        // nose and a weak chin
        path.reset()
        path.moveTo(HEAD_X + HEAD_R - 3f, HEAD_Y - 2f)
        path.lineTo(HEAD_X + HEAD_R + 12f, HEAD_Y + 5f)
        path.lineTo(HEAD_X + HEAD_R - 3f, HEAD_Y + 9f)
        path.close()
        p.color = SKIN
        c.drawPath(path, p)
        p.color = ColorX.withAlpha(SKIN_SHADE, 0.7f)
        r.set(HEAD_X + 6f, HEAD_Y + 15f, HEAD_X + 24f, HEAD_Y + 26f)
        c.drawOval(r, p)

        buckTeeth(c)
        hair(c)
        glasses(c, pose)

        // the same rim light the dog gets, so he sits in the world rather than on it
        ink.strokeWidth = 3f
        ink.color = ColorX.withAlpha(rim, 0.5f)
        r.set(HEAD_X - HEAD_R - 3f, HEAD_Y - HEAD_R, HEAD_X + HEAD_R + 1f, HEAD_Y + HEAD_R)
        c.drawArc(r, 200f, 100f, false, ink)
        prep()
    }

    /** Two of them, front and centre, resting on the lower lip. */
    private fun buckTeeth(c: Canvas) {
        val mx = HEAD_X + 15f
        val my = HEAD_Y + 13f
        // the mouth line they come out of
        ink.strokeWidth = 2.6f
        ink.color = ColorX.withAlpha(BuddyGeom.INK, 0.6f)
        c.drawLine(mx - 13f, my - 1f, mx + 9f, my + 1f, ink)

        ink.strokeWidth = 2.2f
        ink.color = ColorX.withAlpha(BuddyGeom.INK, 0.75f)
        r.set(mx - 3f, my - 1f, mx + 5f, my + 13f)
        c.drawRoundRect(r, 2f, 2f, ink)
        p.color = 0xFFFDFDF6.toInt()
        c.drawRoundRect(r, 2f, 2f, p)
        r.set(mx + 4f, my - 1f, mx + 12f, my + 12f)
        c.drawRoundRect(r, 2f, 2f, ink)
        p.color = 0xFFF2F2E8.toInt()
        c.drawRoundRect(r, 2f, 2f, p)
        prep()
    }

    /** Short, and slicked flat to the skull with a hard part. */
    private fun hair(c: Canvas) {
        p.color = HAIR
        path.reset()
        path.moveTo(HEAD_X + HEAD_R - 6f, HEAD_Y - 14f)
        path.cubicTo(
            HEAD_X + 12f, HEAD_Y - HEAD_R - 9f,
            HEAD_X - 20f, HEAD_Y - HEAD_R - 7f,
            HEAD_X - HEAD_R - 4f, HEAD_Y + 4f
        )
        path.cubicTo(
            HEAD_X - HEAD_R - 6f, HEAD_Y - 10f,
            HEAD_X - 14f, HEAD_Y - 20f,
            HEAD_X + 8f, HEAD_Y - 19f
        )
        path.cubicTo(HEAD_X + 18f, HEAD_Y - 19f, HEAD_X + 22f, HEAD_Y - 16f, HEAD_X + HEAD_R - 6f, HEAD_Y - 14f)
        path.close()
        c.drawPath(path, p)

        // the comb lines that make it read as slicked rather than shaved
        p.style = Paint.Style.STROKE
        p.strokeWidth = 1.6f
        p.color = ColorX.withAlpha(0xFF8A6B4A.toInt(), 0.55f)
        for (i in 0 until 4) {
            val y = HEAD_Y - 24f + i * 4.2f
            path.reset()
            path.moveTo(HEAD_X + 18f, y + 3f)
            path.quadTo(HEAD_X - 4f, y - 2f, HEAD_X - 24f, y + 9f)
            c.drawPath(path, p)
        }
        p.style = Paint.Style.FILL
    }

    /**
     * Black frames, lenses a flat opaque blue - you never see his eyes - and a strip of white
     * tape across the bridge, because of course there is.
     */
    private fun glasses(c: Canvas, pose: Pose) {
        val ey = HEAD_Y - 2f
        val nearX = HEAD_X + 14f
        val farX = HEAD_X - 9f
        val rad = 11f

        // lenses first, then the frames on top of them
        p.color = LENS
        c.drawCircle(nearX, ey, rad, p)
        p.color = ColorX.shade(LENS, 0.72f)
        c.drawCircle(farX, ey, rad * 0.86f, p)
        // a hard little glint, the one thing that moves on his face
        p.color = ColorX.withAlpha(0xFFFFFFFF.toInt(), 0.7f)
        r.set(nearX - 6f, ey - 7f, nearX - 1f, ey - 2f)
        c.drawOval(r, p)
        r.set(farX - 5f, ey - 6f, farX - 1f, ey - 2f)
        p.color = ColorX.withAlpha(0xFFFFFFFF.toInt(), 0.35f)
        c.drawOval(r, p)

        p.style = Paint.Style.STROKE
        p.strokeWidth = 4f
        p.color = 0xFF15171D.toInt()
        c.drawCircle(nearX, ey, rad, p)
        p.strokeWidth = 3.4f
        c.drawCircle(farX, ey, rad * 0.86f, p)
        // bridge, and the arm going back over the ear
        p.strokeWidth = 3.4f
        c.drawLine(farX + rad * 0.82f, ey - 1f, nearX - rad + 1f, ey - 1f, p)
        p.strokeWidth = 3f
        c.drawLine(farX - rad * 0.8f, ey - 2f, HEAD_X - 22f, ey + 2f, p)
        p.style = Paint.Style.FILL

        // the tape, wrapped round the bridge
        val bx = (farX + nearX) * 0.5f + 1f
        p.color = 0xFFF4F1E6.toInt()
        r.set(bx - 4.5f, ey - 9f, bx + 4.5f, ey + 7f)
        c.drawRoundRect(r, 1.5f, 1.5f, p)
        p.color = ColorX.withAlpha(0xFF9AA4B4.toInt(), 0.55f)
        r.set(bx - 4.5f, ey - 9f, bx - 2.5f, ey + 7f)
        c.drawRect(r, p)
        r.set(bx + 2.5f, ey - 9f, bx + 4.5f, ey + 7f)
        c.drawRect(r, p)
    }

    /** The far arm, tucked behind the body. */
    private fun backArm(c: Canvas, swing: Float) {
        p.style = Paint.Style.STROKE
        p.strokeCap = Paint.Cap.ROUND
        p.strokeWidth = 15f
        p.color = ColorX.shade(SHIRT, 0.72f)
        path.reset()
        path.moveTo(SHO_X - 4f, SHO_Y + 14f)
        path.quadTo(SHO_X - 16f, SHO_Y + 52f + swing, SHO_X - 6f, HIP_Y - 8f + swing)
        c.drawPath(path, p)
        p.strokeWidth = 11f
        p.color = ColorX.shade(SKIN, 0.78f)
        c.drawLine(SHO_X - 6f, HIP_Y - 8f + swing, SHO_X - 2f, HIP_Y + 12f + swing, p)
        p.style = Paint.Style.FILL
    }

    /** The near arm, bent at the elbow with the hand held up in a small nervous curl. */
    private fun frontArm(c: Canvas, swing: Float) {
        p.style = Paint.Style.STROKE
        p.strokeCap = Paint.Cap.ROUND
        p.strokeWidth = 8f
        p.color = BuddyGeom.INK
        path.reset()
        path.moveTo(SHO_X + 10f, SHO_Y + 18f)
        path.quadTo(SHO_X + 4f, SHO_Y + 54f - swing, SHO_X + 24f, SHO_Y + 62f - swing)
        c.drawPath(path, p)
        p.strokeWidth = 16f
        p.color = SHIRT
        c.drawPath(path, p)
        // the cuff, then the forearm and hand
        p.strokeWidth = 17f
        p.color = SHIRT_SHADE
        c.drawLine(SHO_X + 20f, SHO_Y + 61f - swing, SHO_X + 25f, SHO_Y + 62f - swing, p)
        p.strokeWidth = 11f
        p.color = BuddyGeom.INK
        c.drawLine(SHO_X + 25f, SHO_Y + 62f - swing, SHO_X + 40f, SHO_Y + 56f - swing, p)
        p.strokeWidth = 8f
        p.color = SKIN
        c.drawLine(SHO_X + 25f, SHO_Y + 62f - swing, SHO_X + 40f, SHO_Y + 56f - swing, p)
        p.style = Paint.Style.FILL
        p.color = SKIN
        c.drawCircle(SHO_X + 41f, SHO_Y + 55f - swing, 7f, p)
    }
}
