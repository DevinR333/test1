package com.blacklab.buddybounce.render

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import com.blacklab.buddybounce.data.Outfits
import kotlin.math.sin

/**
 * Every outfit composes onto the same profile rig as Buddy himself (see [BuddyGeom]).
 *
 *   drawBack  - behind him: capes, packs, wings, sword hilts
 *   drawBody  - on the torso, after the body and legs
 *   drawHead  - hats, masks, collars, after the head and ear
 *
 * Drawing them as vectors means they squash, lean and mirror with him for free.
 */
object OutfitArt {

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

    // handy landmarks
    private const val HX = BuddyGeom.HEAD_CX
    private const val HY = BuddyGeom.HEAD_CY
    private const val HR = BuddyGeom.HEAD_R
    private const val TOP = HY - HR * 1.3f          // top of the skull
    private const val EX = BuddyGeom.EYE_X
    private const val EY = BuddyGeom.EYE_Y
    private const val NOSE_X = BuddyGeom.NOSE_X
    private const val MUZ_Y = BuddyGeom.MUZZLE_Y
    private const val NECK_X = 32f
    private const val NECK_Y = -104f
    private const val BACK = BuddyGeom.BACK_Y
    private const val BELLY = BuddyGeom.BELLY_Y
    private const val BX0 = -70f                     // rump
    private const val BX1 = 52f                      // chest

    // -------------------------------------------------------------------------------------
    // dispatch
    // -------------------------------------------------------------------------------------

    fun drawBack(c: Canvas, id: String, pose: Pose, rim: Int) {
        prep()
        when (id) {
            "cape" -> cape(c, pose, 0xFFD23A4A.toInt(), 0xFF8E1F2E.toInt())
            "cosmic" -> cape(c, pose, 0xFF3B2A7A.toInt(), 0xFF1A1140.toInt())
            "santa" -> sack(c)
            "hero" -> swordAndShield(c)
            "aviator" -> scarf(c, pose, 0xFFE0654F.toInt())
            "backpack" -> backpack(c)
            "bee" -> wings(c, pose)
            "unicorn" -> unicornMane(c, pose)
            "astro" -> oxygenTank(c)
            "dino" -> dinoSpikes(c)
            "shark" -> sharkFin(c)
            "wizard" -> cape(c, pose, 0xFF3A2E86.toInt(), 0xFF221A52.toInt())
            "firefighter" -> airTank(c)
            "detective" -> coatTail(c, pose, 0xFFB08A5A.toInt())
            "mafia" -> coatTail(c, pose, 0xFF3A3F55.toInt())
            "ninja" -> ninjaScarf(c, pose)
        }
    }

    fun drawBody(c: Canvas, id: String, pose: Pose, rim: Int) {
        prep()
        when (id) {
            "sweater" -> { torso(c, 0xFF9C5BC4.toInt(), 0xFF7B4499.toInt()); stripes(c, 0xFFF2E3B3.toInt()) }
            "towel" -> towel(c)
            "raincoat" -> torso(c, 0xFFF2C14E.toInt(), 0xFFD9A431.toInt())
            "clown" -> { torso(c, 0xFFF2F4F8.toInt(), 0xFFD8DEE9.toInt()); polkaDots(c) }
            "plumber" -> overalls(c)
            "detective" -> torso(c, 0xFFC49A66.toInt(), 0xFF9A7040.toInt())
            "vet" -> { torso(c, 0xFFF7F9FC.toInt(), 0xFFD9DFE8.toInt()); pocket(c, 0xFFBFC8D4.toInt()) }
            "racer" -> { torso(c, 0xFFE8433C.toInt(), 0xFFB32C28.toInt()); racingStripe(c) }
            "pirate" -> torso(c, 0xFF2F4A6E.toInt(), 0xFF1E3350.toInt())
            "cape" -> torso(c, 0xFF2F6FD6.toInt(), 0xFF1F4C99.toInt())
            "knight" -> plate(c)
            "dino" -> torso(c, 0xFF57B96B.toInt(), 0xFF3F9152.toInt())
            "bee" -> { torso(c, 0xFFF2C14E.toInt(), 0xFFD9A431.toInt()); beeStripes(c) }
            "santa" -> { torso(c, 0xFFD8453B.toInt(), 0xFFA8302A.toInt()); furTrim(c) }
            "mafia" -> { torso(c, 0xFF3A3F55.toInt(), 0xFF262B3C.toInt()); pinstripes(c); tie(c) }
            "ninja" -> torso(c, 0xFF23262F.toInt(), 0xFF15171D.toInt())
            "wizard" -> { torso(c, 0xFF4A3AA8.toInt(), 0xFF2E2470.toInt()); starSprinkle(c, pose) }
            "lucha" -> { torso(c, 0xFFE8B23C.toInt(), 0xFFC08A22.toInt()); beltBuckle(c) }
            "shark" -> torso(c, 0xFF5A93C4.toInt(), 0xFF3E6E99.toInt())
            "firefighter" -> { torso(c, 0xFF2A2F3D.toInt(), 0xFF1A1E28.toInt()); hiVis(c) }
            "astro" -> { torso(c, 0xFFF1F3F8.toInt(), 0xFFD8DEE9.toInt()); suitPanel(c) }
            "cosmic" -> { torso(c, 0xFF221A52.toInt(), 0xFF150F38.toInt()); starSprinkle(c, pose) }
            "hero" -> { torso(c, 0xFF7B4FD0.toInt(), 0xFF53309A.toInt()); heroBelt(c) }
            "robot" -> { torso(c, 0xFFB9C2D0.toInt(), 0xFF8A94A6.toInt()); robotPanel(c) }
            "unicorn" -> torso(c, 0xFFF7D7EC.toInt(), 0xFFE0B4D6.toInt())
            "backpack" -> straps(c)
        }
    }

    fun drawHead(c: Canvas, id: String, pose: Pose, rim: Int) {
        prep()
        when (id) {
            Outfits.DEFAULT_ID -> collar(c, 0xFFC7413C.toInt())
            "bandana" -> bandana(c)
            "ball" -> tennisBall(c)
            "party" -> cone(c, 0xFF57C4E5.toInt(), 0xFFF2645E.toInt(), 56f, pompom = true)
            "shades" -> shades(c)
            "flowers" -> flowerCrown(c)
            "sweater" -> collar(c, 0xFF7B4499.toInt())
            "beanie" -> beanie(c)
            "towel" -> towelHood(c)
            "cone" -> vetCone(c)
            "aviator" -> goggles(c)
            "chef" -> chefHat(c)
            "cowboy" -> { cowboyHat(c); bandana(c) }
            "snorkel" -> snorkel(c)
            "backpack" -> collar(c, 0xFF3F7A53.toInt())
            "raincoat" -> rainHood(c)
            "clown" -> { clownRuff(c); clownHair(c); redNose(c) }
            "plumber" -> { plumberCap(c); moustache(c) }
            "detective" -> fedora(c, 0xFF8A6A42.toInt(), 0xFF6B5130.toInt())
            "vet" -> { collar(c, 0xFF4F9FD6.toInt()); glasses(c) }
            "racer" -> racingHelmet(c)
            "pirate" -> { tricorn(c); eyePatch(c) }
            "cape" -> { collar(c, 0xFFD23A4A.toInt()); mask(c) }
            "knight" -> knightHelm(c)
            "dino" -> dinoHood(c)
            "bee" -> antennae(c, pose)
            "santa" -> { santaHat(c); beard(c) }
            "mafia" -> fedora(c, 0xFF23262F.toInt(), 0xFF15171D.toInt())
            "ninja" -> ninjaHood(c)
            "wizard" -> { cone(c, 0xFF3A2E86.toInt(), 0xFF2E2470.toInt(), 76f, pompom = false); wizardBeard(c) }
            "viking" -> vikingHelm(c)
            "lucha" -> luchaMask(c)
            "shark" -> sharkHood(c)
            "firefighter" -> fireHelmet(c)
            "astro" -> astroHelmet(c)
            "cosmic" -> collar(c, 0xFFCBB7FF.toInt())
            "crown" -> { crown(c, pose); medal(c) }
            "hero" -> { heroCap(c); collar(c, 0xFF3FA95E.toInt()) }
            "robot" -> robotHead(c)
            "unicorn" -> unicornHorn(c)
        }
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

    private fun rrInk(c: Canvas, x0: Float, y0: Float, x1: Float, y1: Float, rad: Float, color: Int) {
        r.set(x0, y0, x1, y1)
        c.drawRoundRect(r, rad, rad, ink)
        p.color = color
        c.drawRoundRect(r, rad, rad, p)
    }

    // -------------------------------------------------------------------------------------
    // shared pieces
    // -------------------------------------------------------------------------------------

    /** A coat/suit over the torso, following the body silhouette. */
    private fun torso(c: Canvas, color: Int, shade: Int) {
        path.reset()
        path.moveTo(BX1 - 2f, BACK + 10f)
        path.cubicTo(20f, BACK - 2f, -40f, BACK + 1f, BX0 - 2f, BACK + 20f)
        path.cubicTo(BX0 - 16f, BACK + 40f, BX0 - 12f, BELLY - 4f, BX0 + 12f, BELLY + 4f)
        path.cubicTo(-20f, BELLY + 14f, 16f, BELLY + 12f, BX1 - 8f, BELLY + 2f)
        path.cubicTo(BX1 + 14f, BELLY - 10f, BX1 + 16f, BACK + 40f, BX1 - 2f, BACK + 10f)
        path.close()
        fillInk(c, color)
        p.color = ColorX.withAlpha(shade, 0.85f)
        r.set(BX0 - 8f, BELLY - 26f, BX1 + 6f, BELLY + 12f)
        c.drawOval(r, p)
        p.color = ColorX.withAlpha(0xFFFFFFFF.toInt(), 0.12f)
        r.set(BX0 + 4f, BACK + 8f, BX1 - 8f, BACK + 34f)
        c.drawOval(r, p)
    }

    private fun stripes(c: Canvas, color: Int) {
        p.color = color
        for (i in 0 until 3) {
            val y = BACK + 26f + i * 18f
            r.set(BX0 - 4f, y, BX1 + 2f, y + 8f)
            c.drawRect(r, p)
        }
    }

    private fun beeStripes(c: Canvas) {
        p.color = 0xFF23262F.toInt()
        for (i in 0 until 3) {
            r.set(BX0 + 4f + i * 34f, BACK + 8f, BX0 + 22f + i * 34f, BELLY + 10f)
            c.drawRect(r, p)
        }
    }

    private fun pinstripes(c: Canvas) {
        p.color = ColorX.withAlpha(0xFFE8ECF5.toInt(), 0.55f)
        for (i in 0 until 6) {
            val x = BX0 + 6f + i * 20f
            r.set(x, BACK + 12f, x + 2.6f, BELLY + 6f)
            c.drawRect(r, p)
        }
    }

    private fun polkaDots(c: Canvas) {
        val cols = intArrayOf(0xFFE8595B.toInt(), 0xFF4FC3F7.toInt(), 0xFFF2C14E.toInt(), 0xFF7BE3A0.toInt())
        for (i in 0 until 6) {
            p.color = cols[i % cols.size]
            c.drawCircle(BX0 + 10f + i * 20f, BACK + 26f + (i % 3) * 20f, 6.5f, p)
        }
    }

    private fun starSprinkle(c: Canvas, pose: Pose) {
        for (i in 0 until 11) {
            val sx = BX0 + 8f + (i * 37 % 110)
            val sy = BACK + 14f + (i * 53 % 62)
            val tw = 0.35f + 0.65f * (0.5f + 0.5f * sin(pose.time * 3f + i))
            p.color = ColorX.withAlpha(0xFFFFFFFF.toInt(), tw)
            c.drawCircle(sx, sy, 2.2f + (i % 3), p)
        }
    }

    /**
     * The neck runs diagonally from the chest up to the jaw, so the collar is a band drawn
     * across that axis rather than a flat bar - otherwise it reads as a floating rectangle.
     * The tag hangs from the throat under gravity, not off the side.
     */
    private fun collar(c: Canvas, color: Int) {
        c.save()
        c.translate(36f, -96f)
        c.rotate(21f)

        r.set(-40f, -11f, 40f, 11f)
        c.drawRoundRect(r, 8f, 8f, ink)
        p.color = color
        c.drawRoundRect(r, 8f, 8f, p)

        // lower half in shadow, a stitched highlight along the top edge
        p.color = ColorX.shade(color, 0.7f)
        r.set(-40f, 2f, 40f, 11f)
        c.drawRoundRect(r, 7f, 7f, p)
        p.color = ColorX.withAlpha(0xFFFFFFFF.toInt(), 0.22f)
        r.set(-33f, -8f, 33f, -4f)
        c.drawRoundRect(r, 2f, 2f, p)

        // buckle
        p.color = 0xFFD8DEE9.toInt()
        r.set(-7f, -13f, 9f, 13f)
        c.drawRoundRect(r, 3f, 3f, p)
        p.color = 0xFF9AA4B4.toInt()
        r.set(-3f, -9f, 2f, 9f)
        c.drawRect(r, p)
        c.restore()

        // a little bone tag on a ring, hanging under the throat
        val tx = 52f
        val ty = -72f
        ink.strokeWidth = 3.2f
        c.drawLine(tx - 4f, ty - 12f, tx, ty - 5f, ink)
        p.color = 0xFFF2C14E.toInt()
        r.set(tx - 9f, ty - 4f, tx + 9f, ty + 3f)
        c.drawRoundRect(r, 3.5f, 3.5f, p)
        c.drawCircle(tx - 9f, ty - 4f, 4.5f, p)
        c.drawCircle(tx - 9f, ty + 3f, 4.5f, p)
        c.drawCircle(tx + 9f, ty - 4f, 4.5f, p)
        c.drawCircle(tx + 9f, ty + 3f, 4.5f, p)
        p.color = ColorX.withAlpha(0xFFB98F25.toInt(), 0.7f)
        r.set(tx - 6f, ty - 1f, tx + 6f, ty + 2f)
        c.drawRoundRect(r, 1.5f, 1.5f, p)
        ink.strokeWidth = 5f
    }

    private fun bandana(c: Canvas) {
        path.reset()
        path.moveTo(NECK_X - 22f, NECK_Y - 2f)
        path.lineTo(NECK_X + 26f, NECK_Y - 18f)
        path.lineTo(NECK_X + 22f, NECK_Y + 28f)
        path.lineTo(NECK_X - 6f, NECK_Y + 34f)
        path.close()
        fillInk(c, 0xFFCC3B3B.toInt())
        p.color = ColorX.withAlpha(0xFFFFFFFF.toInt(), 0.5f)
        c.drawCircle(NECK_X + 8f, NECK_Y + 8f, 3.4f, p)
        c.drawCircle(NECK_X + 16f, NECK_Y + 18f, 3.4f, p)
    }

    /** A cap with a forward brim, used by several outfits. */
    private fun cap(c: Canvas, crown: Int, brim: Int, brimLen: Float = 34f) {
        path.reset()
        path.moveTo(HX - 28f, TOP + 18f)
        path.cubicTo(HX - 26f, TOP - 12f, HX + 24f, TOP - 14f, HX + 30f, TOP + 14f)
        path.close()
        fillInk(c, crown)
        rrInk(c, HX + 12f, TOP + 8f, HX + 12f + brimLen, TOP + 20f, 6f, brim)
    }

    private fun cone(c: Canvas, a: Int, b: Int, height: Float, pompom: Boolean) {
        path.reset()
        path.moveTo(HX - 30f, TOP + 14f)
        path.lineTo(HX + 6f, TOP - height)
        path.lineTo(HX + 30f, TOP + 8f)
        path.close()
        fillInk(c, a)
        p.color = b
        for (i in 0 until 3) {
            val t = 0.24f + i * 0.24f
            val w = 30f * (1f - t)
            val yy = TOP + 12f - (TOP + 12f - (TOP - height)) * t
            r.set(HX + 6f - w, yy - 5f, HX + 6f + w, yy + 5f)
            c.drawRoundRect(r, 3f, 3f, p)
        }
        if (pompom) {
            p.color = 0xFFFFE07A.toInt()
            c.drawCircle(HX + 6f, TOP - height - 4f, 10f, p)
            c.drawCircle(HX + 6f, TOP - height - 4f, 10f, ink)
        }
    }

    private fun brimHat(c: Canvas, crown: Int, band: Int, crownH: Float, brimW: Float) {
        rrInk(c, HX - brimW, TOP + 10f, HX + brimW, TOP + 24f, 8f, crown)
        path.reset()
        path.moveTo(HX - 26f, TOP + 14f)
        path.cubicTo(HX - 24f, TOP - crownH, HX + 26f, TOP - crownH, HX + 28f, TOP + 14f)
        path.close()
        fillInk(c, crown)
        p.color = band
        r.set(HX - 27f, TOP - 2f, HX + 29f, TOP + 12f)
        c.drawRect(r, p)
    }

    // -------------------------------------------------------------------------------------
    // head pieces
    // -------------------------------------------------------------------------------------

    private fun tennisBall(c: Canvas) {
        val x = NOSE_X + 4f
        val y = MUZ_Y + 16f
        p.color = 0xFFD9E84C.toInt()
        c.drawCircle(x, y, 20f, p)
        c.drawCircle(x, y, 20f, ink)
        ink.strokeWidth = 3f
        ink.color = ColorX.withAlpha(0xFFFFFFFF.toInt(), 0.85f)
        r.set(x - 20f, y - 20f, x + 20f, y + 20f)
        c.drawArc(r, 200f, 140f, false, ink)
        c.drawArc(r, 20f, 140f, false, ink)
        prep()
    }

    private fun shades(c: Canvas) {
        rrInk(c, EX - 16f, EY - 12f, EX + 20f, EY + 8f, 7f, 0xFF15161C.toInt())
        p.color = ColorX.withAlpha(0xFF4FC3F7.toInt(), 0.35f)
        r.set(EX - 13f, EY - 9f, EX + 17f, EY + 5f)
        c.drawRoundRect(r, 5f, 5f, p)
        ink.strokeWidth = 4f
        c.drawLine(EX - 16f, EY - 8f, HX - 6f, EY - 6f, ink)
        prep()
        p.color = ColorX.withAlpha(0xFFFFFFFF.toInt(), 0.5f)
        c.drawLine(EX - 10f, EY + 3f, EX + 2f, EY - 8f, p)
    }

    private fun glasses(c: Canvas) {
        ink.strokeWidth = 4f
        ink.color = 0xFF2A2E39.toInt()
        c.drawCircle(EX + 2f, EY - 2f, 13f, ink)
        c.drawLine(EX - 11f, EY - 4f, HX - 4f, EY - 4f, ink)
        prep()
        p.color = ColorX.withAlpha(0xFFBDEBFF.toInt(), 0.28f)
        c.drawCircle(EX + 2f, EY - 2f, 12f, p)
    }

    private fun flowerCrown(c: Canvas) {
        val cols = intArrayOf(0xFFF7A8C4.toInt(), 0xFFFFE07A.toInt(), 0xFFB9E3F2.toInt(), 0xFFF2645E.toInt())
        for (i in 0 until 5) {
            val x = HX - 26f + i * 15f
            val y = TOP + 12f - sin(i * 0.8f) * 6f
            p.color = cols[i % cols.size]
            for (k in 0 until 5) {
                val ang = k * 1.2566f + i
                c.drawCircle(x + kotlin.math.cos(ang) * 6.5f, y + sin(ang) * 6.5f, 5.5f, p)
            }
            p.color = 0xFFFFF3C4.toInt()
            c.drawCircle(x, y, 3.6f, p)
        }
    }

    private fun beanie(c: Canvas) {
        path.reset()
        path.moveTo(HX - 30f, TOP + 16f)
        path.cubicTo(HX - 28f, TOP - 20f, HX + 26f, TOP - 22f, HX + 32f, TOP + 12f)
        path.close()
        fillInk(c, 0xFF3F7A9E.toInt())
        rrInk(c, HX - 32f, TOP + 8f, HX + 34f, TOP + 24f, 8f, 0xFFE8EDF5.toInt())
        p.color = 0xFFE8EDF5.toInt()
        c.drawCircle(HX + 2f, TOP - 24f, 11f, p)
        c.drawCircle(HX + 2f, TOP - 24f, 11f, ink)
    }

    private fun towel(c: Canvas) {
        torso(c, 0xFFBFE4F2.toInt(), 0xFF8FC4D8.toInt())
        p.color = ColorX.withAlpha(0xFFFFFFFF.toInt(), 0.6f)
        for (i in 0 until 3) {
            r.set(BX0 + 2f, BACK + 30f + i * 16f, BX1 - 4f, BACK + 34f + i * 16f)
            c.drawRect(r, p)
        }
    }

    private fun towelHood(c: Canvas) {
        path.reset()
        path.moveTo(HX - 32f, TOP + 26f)
        path.cubicTo(HX - 30f, TOP - 26f, HX + 30f, TOP - 26f, HX + 34f, TOP + 20f)
        path.lineTo(HX + 10f, TOP + 28f)
        path.close()
        fillInk(c, 0xFFBFE4F2.toInt())
        p.color = 0xFFFFFFFF.toInt()
        c.drawCircle(HX + 2f, TOP - 22f, 9f, p)
    }

    private fun vetCone(c: Canvas) {
        path.reset()
        path.moveTo(HX + 4f, HY - 6f)
        path.lineTo(HX + 74f, TOP - 26f)
        path.lineTo(HX + 88f, HY + 44f)
        path.lineTo(HX + 12f, HY + 30f)
        path.close()
        c.drawPath(path, ink)
        p.color = ColorX.withAlpha(0xFFE8F4FF.toInt(), 0.55f)
        c.drawPath(path, p)
        ink.strokeWidth = 3f
        ink.color = ColorX.withAlpha(0xFF9FB4C8.toInt(), 0.9f)
        c.drawLine(HX + 40f, TOP - 8f, HX + 48f, HY + 38f, ink)
        prep()
    }

    private fun goggles(c: Canvas) {
        rrInk(c, HX - 28f, TOP + 6f, HX + 34f, TOP + 24f, 8f, 0xFF6B4A2F.toInt())
        p.color = 0xFF9FD8F2.toInt()
        c.drawCircle(EX + 2f, TOP + 15f, 13f, p)
        c.drawCircle(EX + 2f, TOP + 15f, 13f, ink)
        p.color = ColorX.withAlpha(0xFFFFFFFF.toInt(), 0.6f)
        c.drawCircle(EX - 3f, TOP + 10f, 4f, p)
    }

    private fun chefHat(c: Canvas) {
        p.color = 0xFFF7F8FB.toInt()
        c.drawCircle(HX - 16f, TOP - 18f, 19f, p)
        c.drawCircle(HX + 16f, TOP - 20f, 20f, p)
        c.drawCircle(HX + 2f, TOP - 32f, 20f, p)
        rrInk(c, HX - 28f, TOP - 6f, HX + 32f, TOP + 20f, 8f, 0xFFF7F8FB.toInt())
        p.color = 0xFFDFE3EC.toInt()
        r.set(HX - 27f, TOP + 8f, HX + 31f, TOP + 19f)
        c.drawRect(r, p)
    }

    private fun cowboyHat(c: Canvas) = brimHat(c, 0xFF8A5A33.toInt(), 0xFF5E3A20.toInt(), 34f, 54f)

    private fun fedora(c: Canvas, crown: Int, band: Int) {
        brimHat(c, crown, band, 28f, 46f)
        p.color = ColorX.withAlpha(0xFF000000.toInt(), 0.3f)
        r.set(HX - 8f, TOP - 26f, HX + 10f, TOP - 12f)
        c.drawOval(r, p)
    }

    private fun tricorn(c: Canvas) {
        path.reset()
        path.moveTo(HX - 44f, TOP + 22f)
        path.quadTo(HX - 10f, TOP - 30f, HX + 46f, TOP + 18f)
        path.quadTo(HX + 2f, TOP + 34f, HX - 44f, TOP + 22f)
        path.close()
        fillInk(c, 0xFF23262F.toInt())
        p.color = 0xFFF2E3B3.toInt()
        c.drawCircle(HX + 20f, TOP + 6f, 7f, p)
        p.color = 0xFF15171D.toInt()
        c.drawCircle(HX + 20f, TOP + 6f, 3f, p)
    }

    private fun eyePatch(c: Canvas) {
        ink.strokeWidth = 4f
        c.drawLine(EX - 14f, EY - 12f, HX - 10f, EY - 16f, ink)
        prep()
        rrInk(c, EX - 11f, EY - 11f, EX + 11f, EY + 8f, 6f, 0xFF15171D.toInt())
    }

    private fun snorkel(c: Canvas) {
        rrInk(c, EX - 16f, EY - 13f, EX + 22f, EY + 10f, 10f, ColorX.withAlpha(0xFFBDEBFF.toInt(), 0.8f))
        ink.strokeWidth = 7f
        ink.color = 0xFFFFA33C.toInt()
        path.reset()
        path.moveTo(EX + 18f, EY + 6f)
        path.quadTo(HX + 2f, EY - 24f, HX - 14f, TOP - 12f)
        c.drawPath(path, ink)
        prep()
    }

    private fun rainHood(c: Canvas) {
        path.reset()
        path.moveTo(HX - 34f, TOP + 30f)
        path.cubicTo(HX - 32f, TOP - 22f, HX + 32f, TOP - 22f, HX + 38f, TOP + 22f)
        path.lineTo(HX + 12f, TOP + 32f)
        path.close()
        fillInk(c, 0xFFF2C14E.toInt())
        p.color = 0xFFD9A431.toInt()
        r.set(HX + 6f, TOP + 14f, HX + 40f, TOP + 26f)
        c.drawRoundRect(r, 6f, 6f, p)
    }

    private fun clownRuff(c: Canvas) {
        p.color = 0xFFF2645E.toInt()
        for (i in 0 until 6) {
            val a = -0.9f + i * 0.36f
            c.drawCircle(NECK_X + kotlin.math.cos(a) * 26f, NECK_Y + 6f + sin(a) * 22f, 12f, p)
        }
        p.color = ColorX.withAlpha(0xFFFFFFFF.toInt(), 0.25f)
        c.drawCircle(NECK_X + 4f, NECK_Y + 4f, 14f, p)
    }

    private fun clownHair(c: Canvas) {
        p.color = 0xFFE8595B.toInt()
        c.drawCircle(HX - 24f, TOP + 6f, 15f, p)
        c.drawCircle(HX - 10f, TOP - 8f, 13f, p)
        c.drawCircle(HX + 12f, TOP - 10f, 13f, p)
        c.drawCircle(HX + 30f, TOP + 4f, 12f, p)
    }

    private fun redNose(c: Canvas) {
        p.color = 0xFFE8433C.toInt()
        c.drawCircle(NOSE_X + 2f, BuddyGeom.NOSE_Y, 13f, p)
        c.drawCircle(NOSE_X + 2f, BuddyGeom.NOSE_Y, 13f, ink)
        p.color = ColorX.withAlpha(0xFFFFFFFF.toInt(), 0.5f)
        c.drawCircle(NOSE_X - 2f, BuddyGeom.NOSE_Y - 5f, 4f, p)
    }

    private fun plumberCap(c: Canvas) {
        cap(c, 0xFFD8453B.toInt(), 0xFFB32C28.toInt(), 36f)
        p.color = 0xFFF7F8FB.toInt()
        c.drawCircle(HX + 2f, TOP + 2f, 10f, p)
        p.color = 0xFFD8453B.toInt()
        c.drawCircle(HX + 2f, TOP + 2f, 5f, p)
    }

    private fun moustache(c: Canvas) {
        p.color = 0xFF3A2A1E.toInt()
        path.reset()
        path.moveTo(NOSE_X - 20f, MUZ_Y + 4f)
        path.quadTo(NOSE_X - 6f, MUZ_Y - 4f, NOSE_X + 6f, MUZ_Y + 6f)
        path.quadTo(NOSE_X - 4f, MUZ_Y + 14f, NOSE_X - 20f, MUZ_Y + 4f)
        path.close()
        c.drawPath(path, p)
    }

    private fun racingHelmet(c: Canvas) {
        path.reset()
        path.moveTo(HX - 32f, HY + 6f)
        path.cubicTo(HX - 34f, TOP - 18f, HX + 30f, TOP - 20f, HX + 36f, HY - 2f)
        path.lineTo(HX + 34f, HY + 12f)
        path.lineTo(HX - 30f, HY + 16f)
        path.close()
        fillInk(c, 0xFFE8433C.toInt())
        p.color = 0xFFF7F8FB.toInt()
        r.set(HX - 30f, TOP + 2f, HX + 34f, TOP + 12f)
        c.drawRect(r, p)
        rrInk(c, HX + 6f, EY - 10f, HX + 40f, EY + 8f, 7f, 0xFF2A2E39.toInt())
        p.color = ColorX.withAlpha(0xFF8FD3F4.toInt(), 0.45f)
        r.set(HX + 9f, EY - 7f, HX + 37f, EY + 5f)
        c.drawRoundRect(r, 5f, 5f, p)
    }

    private fun mask(c: Canvas) {
        path.reset()
        path.moveTo(HX - 6f, EY - 14f)
        path.lineTo(HX + 40f, EY - 12f)
        path.lineTo(HX + 36f, EY + 8f)
        path.lineTo(HX - 2f, EY + 6f)
        path.close()
        fillInk(c, 0xFF2B3A6B.toInt())
        p.color = 0xFFF7F8FB.toInt()
        c.drawCircle(EX + 2f, EY - 3f, 6f, p)
    }

    private fun knightHelm(c: Canvas) {
        path.reset()
        path.moveTo(HX - 32f, HY + 14f)
        path.cubicTo(HX - 34f, TOP - 14f, HX + 34f, TOP - 16f, HX + 42f, HY - 4f)
        path.lineTo(HX + 44f, HY + 22f)
        path.lineTo(HX - 28f, HY + 22f)
        path.close()
        fillInk(c, 0xFFB9C2D0.toInt())
        p.color = 0xFF31363F.toInt()
        r.set(HX + 2f, EY - 8f, HX + 44f, EY + 4f)
        c.drawRoundRect(r, 4f, 4f, p)
        p.color = 0xFFD7DEE9.toInt()
        for (i in 0 until 3) {
            r.set(HX + 8f + i * 12f, EY + 12f, HX + 15f + i * 12f, EY + 28f)
            c.drawRoundRect(r, 3f, 3f, p)
        }
        p.color = 0xFFD23A4A.toInt()
        path.reset()
        path.moveTo(HX - 6f, TOP - 12f)
        path.quadTo(HX + 4f, TOP - 54f, HX + 26f, TOP - 34f)
        path.quadTo(HX + 10f, TOP - 20f, HX + 8f, TOP - 8f)
        path.close()
        c.drawPath(path, p)
    }

    private fun vikingHelm(c: Canvas) {
        path.reset()
        path.moveTo(HX - 30f, TOP + 18f)
        path.cubicTo(HX - 28f, TOP - 16f, HX + 28f, TOP - 18f, HX + 34f, TOP + 14f)
        path.close()
        fillInk(c, 0xFFB0B8C6.toInt())
        rrInk(c, HX - 32f, TOP + 10f, HX + 36f, TOP + 24f, 6f, 0xFF8A93A3.toInt())
        p.color = 0xFFE8DCC0.toInt()
        for (s in 0 until 2) {
            val dir = if (s == 0) -1f else 1f
            path.reset()
            path.moveTo(HX + dir * 26f, TOP + 6f)
            path.quadTo(HX + dir * 54f, TOP - 14f, HX + dir * 44f, TOP - 36f)
            path.quadTo(HX + dir * 40f, TOP - 12f, HX + dir * 20f, TOP + 10f)
            path.close()
            c.drawPath(path, ink)
            c.drawPath(path, p)
        }
        p.color = 0xFF8A93A3.toInt()
        r.set(HX + 2f, TOP + 14f, HX + 10f, EY + 4f)
        c.drawRect(r, p)
    }

    private fun luchaMask(c: Canvas) {
        path.reset()
        path.moveTo(HX - 30f, HY + 10f)
        path.cubicTo(HX - 32f, TOP - 10f, HX + 32f, TOP - 12f, HX + 40f, HY - 8f)
        path.cubicTo(HX + 44f, HY + 8f, HX + 30f, HY + 24f, HX + 4f, HY + 24f)
        path.cubicTo(HX - 14f, HY + 24f, HX - 28f, HY + 20f, HX - 30f, HY + 10f)
        path.close()
        fillInk(c, 0xFFE8B23C.toInt())
        p.color = 0xFFC0392B.toInt()
        path.reset()
        path.moveTo(HX + 2f, TOP - 6f)
        path.lineTo(HX + 32f, HY - 6f)
        path.lineTo(HX + 2f, HY + 18f)
        path.lineTo(HX - 22f, HY - 4f)
        path.close()
        c.drawPath(path, p)
        p.color = 0xFF15171D.toInt()
        r.set(EX - 10f, EY - 9f, EX + 12f, EY + 6f)
        c.drawOval(r, p)
        p.color = 0xFFF7F8FB.toInt()
        c.drawCircle(EX + 2f, EY - 2f, 4.5f, p)
    }

    private fun santaHat(c: Canvas) {
        path.reset()
        path.moveTo(HX - 28f, TOP + 16f)
        path.cubicTo(HX - 20f, TOP - 34f, HX + 24f, TOP - 40f, HX + 44f, TOP - 30f)
        path.quadTo(HX + 10f, TOP - 12f, HX + 32f, TOP + 12f)
        path.close()
        fillInk(c, 0xFFD8453B.toInt())
        rrInk(c, HX - 32f, TOP + 8f, HX + 36f, TOP + 26f, 9f, 0xFFF7F8FB.toInt())
        p.color = 0xFFF7F8FB.toInt()
        c.drawCircle(HX + 48f, TOP - 30f, 11f, p)
        c.drawCircle(HX + 48f, TOP - 30f, 11f, ink)
    }

    private fun beard(c: Canvas) {
        p.color = 0xFFF2F4F8.toInt()
        path.reset()
        path.moveTo(HX + 6f, HY + 6f)
        path.cubicTo(HX + 40f, HY + 10f, HX + 46f, HY + 34f, HX + 18f, HY + 40f)
        path.cubicTo(HX - 6f, HY + 44f, HX - 10f, HY + 16f, HX + 6f, HY + 6f)
        path.close()
        c.drawPath(path, ink)
        c.drawPath(path, p)
    }

    private fun wizardBeard(c: Canvas) {
        p.color = 0xFFE8EDF5.toInt()
        path.reset()
        path.moveTo(HX + 10f, HY + 4f)
        path.cubicTo(HX + 44f, HY + 12f, HX + 38f, HY + 52f, HX + 12f, HY + 48f)
        path.cubicTo(HX - 8f, HY + 44f, HX - 4f, HY + 12f, HX + 10f, HY + 4f)
        path.close()
        c.drawPath(path, ink)
        c.drawPath(path, p)
    }

    private fun ninjaHood(c: Canvas) {
        path.reset()
        path.moveTo(HX - 32f, HY + 16f)
        path.cubicTo(HX - 34f, TOP - 10f, HX + 32f, TOP - 12f, HX + 44f, HY - 6f)
        path.cubicTo(HX + 46f, HY + 10f, HX + 30f, HY + 26f, HX + 2f, HY + 26f)
        path.close()
        fillInk(c, 0xFF23262F.toInt())
        p.color = 0xFF3A3F4E.toInt()
        r.set(EX - 18f, EY - 9f, EX + 26f, EY + 7f)
        c.drawRoundRect(r, 6f, 6f, p)
        p.color = 0xFFF2F4F8.toInt()
        c.drawCircle(EX + 2f, EY - 1f, 5f, p)
    }

    private fun sharkHood(c: Canvas) {
        path.reset()
        path.moveTo(HX - 32f, HY + 14f)
        path.cubicTo(HX - 34f, TOP - 14f, HX + 30f, TOP - 16f, HX + 50f, HY - 10f)
        path.cubicTo(HX + 58f, HY + 4f, HX + 40f, HY + 26f, HX + 4f, HY + 26f)
        path.close()
        fillInk(c, 0xFF5A93C4.toInt())
        p.color = 0xFFF2F4F8.toInt()
        path.reset()
        for (i in 0 until 5) {
            val x = HX + 8f + i * 10f
            path.moveTo(x, HY + 16f)
            path.lineTo(x + 5f, HY + 26f)
            path.lineTo(x + 10f, HY + 16f)
            path.close()
        }
        c.drawPath(path, p)
        p.color = 0xFF2A2E39.toInt()
        c.drawCircle(EX + 6f, EY - 4f, 4.5f, p)
    }

    private fun fireHelmet(c: Canvas) {
        path.reset()
        path.moveTo(HX - 38f, TOP + 22f)
        path.cubicTo(HX - 30f, TOP - 18f, HX + 28f, TOP - 20f, HX + 40f, TOP + 14f)
        path.lineTo(HX + 52f, TOP + 26f)
        path.lineTo(HX - 40f, TOP + 28f)
        path.close()
        fillInk(c, 0xFFD8453B.toInt())
        p.color = 0xFFF2E3B3.toInt()
        r.set(HX - 12f, TOP - 12f, HX + 16f, TOP + 4f)
        c.drawRoundRect(r, 4f, 4f, p)
    }

    private fun astroHelmet(c: Canvas) {
        p.color = ColorX.withAlpha(0xFFBDEBFF.toInt(), 0.26f)
        c.drawCircle(HX + 16f, HY - 6f, 56f, p)
        ink.strokeWidth = 7f
        ink.color = 0xFFF1F3F8.toInt()
        c.drawCircle(HX + 16f, HY - 6f, 56f, ink)
        prep()
        p.color = ColorX.withAlpha(0xFFFFFFFF.toInt(), 0.4f)
        path.reset()
        path.moveTo(HX - 24f, HY - 26f)
        path.quadTo(HX - 6f, HY - 58f, HX + 24f, HY - 56f)
        path.quadTo(HX - 8f, HY - 40f, HX - 16f, HY - 12f)
        path.close()
        c.drawPath(path, p)
    }

    private fun robotHead(c: Canvas) {
        rrInk(c, HX - 30f, TOP - 4f, HX + 44f, HY + 14f, 10f, 0xFFB9C2D0.toInt())
        p.color = 0xFF2A2E39.toInt()
        r.set(HX - 12f, EY - 10f, HX + 40f, EY + 6f)
        c.drawRoundRect(r, 5f, 5f, p)
        p.color = 0xFF4FE8FF.toInt()
        c.drawCircle(EX + 8f, EY - 2f, 5.5f, p)
        p.color = 0xFF8A94A6.toInt()
        r.set(HX - 6f, TOP - 22f, HX + 2f, TOP - 4f)
        c.drawRect(r, p)
        p.color = 0xFFE8433C.toInt()
        c.drawCircle(HX - 2f, TOP - 24f, 5f, p)
    }

    private fun unicornHorn(c: Canvas) {
        path.reset()
        path.moveTo(HX + 8f, TOP + 10f)
        path.lineTo(HX + 22f, TOP - 40f)
        path.lineTo(HX + 30f, TOP + 6f)
        path.close()
        fillInk(c, 0xFFFFE07A.toInt())
        p.color = ColorX.withAlpha(0xFFFFFFFF.toInt(), 0.5f)
        c.drawLine(HX + 14f, TOP + 2f, HX + 22f, TOP - 30f, p)
    }

    private fun heroCap(c: Canvas) {
        path.reset()
        path.moveTo(HX - 30f, TOP + 16f)
        path.cubicTo(HX - 34f, TOP - 26f, HX - 16f, TOP - 52f, HX - 44f, TOP - 62f)
        path.quadTo(HX - 6f, TOP - 54f, HX + 32f, TOP + 12f)
        path.close()
        fillInk(c, 0xFF7B4FD0.toInt())
        rrInk(c, HX - 32f, TOP + 8f, HX + 34f, TOP + 22f, 7f, 0xFF53309A.toInt())
    }

    private fun crown(c: Canvas, pose: Pose) {
        path.reset()
        path.moveTo(HX - 26f, TOP + 14f)
        path.lineTo(HX - 26f, TOP - 20f)
        path.lineTo(HX - 10f, TOP - 2f)
        path.lineTo(HX + 4f, TOP - 26f)
        path.lineTo(HX + 18f, TOP - 2f)
        path.lineTo(HX + 32f, TOP - 20f)
        path.lineTo(HX + 32f, TOP + 14f)
        path.close()
        fillInk(c, 0xFFF2C14E.toInt())
        val cols = intArrayOf(0xFFE8595B.toInt(), 0xFF7BE3FF.toInt(), 0xFF7BE3A0.toInt())
        for (i in 0 until 3) {
            p.color = cols[i]
            c.drawCircle(HX - 14f + i * 16f, TOP + 6f, 5f, p)
        }
        p.color = ColorX.withAlpha(0xFFFFFFFF.toInt(), 0.5f + 0.5f * sin(pose.time * 4f))
        c.drawCircle(HX + 20f, TOP - 18f, 4f, p)
    }

    private fun medal(c: Canvas) {
        p.color = 0xFF3B6FD6.toInt()
        r.set(NECK_X - 2f, NECK_Y + 2f, NECK_X + 10f, NECK_Y + 20f)
        c.drawRect(r, p)
        p.color = 0xFFF2C14E.toInt()
        c.drawCircle(NECK_X + 4f, NECK_Y + 30f, 13f, p)
        c.drawCircle(NECK_X + 4f, NECK_Y + 30f, 13f, ink)
        p.color = 0xFFD9A431.toInt()
        c.drawCircle(NECK_X + 4f, NECK_Y + 30f, 7f, p)
    }

    private fun antennae(c: Canvas, pose: Pose) {
        ink.strokeWidth = 4.5f
        val wobble = sin(pose.time * 7f) * 5f
        path.reset()
        path.moveTo(HX - 4f, TOP + 14f)
        path.quadTo(HX - 18f, TOP - 18f, HX - 24f + wobble, TOP - 34f)
        c.drawPath(path, ink)
        path.reset()
        path.moveTo(HX + 16f, TOP + 10f)
        path.quadTo(HX + 12f, TOP - 20f, HX + 6f + wobble, TOP - 38f)
        c.drawPath(path, ink)
        prep()
        p.color = 0xFFF2C14E.toInt()
        c.drawCircle(HX - 24f + wobble, TOP - 36f, 7.5f, p)
        c.drawCircle(HX + 6f + wobble, TOP - 40f, 7.5f, p)
    }

    private fun dinoHood(c: Canvas) {
        path.reset()
        path.moveTo(HX - 32f, HY + 14f)
        path.cubicTo(HX - 34f, TOP - 12f, HX + 30f, TOP - 14f, HX + 46f, HY - 8f)
        path.cubicTo(HX + 50f, HY + 8f, HX + 32f, HY + 24f, HX + 2f, HY + 24f)
        path.close()
        fillInk(c, 0xFF57B96B.toInt())
        p.color = 0xFFF2E3B3.toInt()
        path.reset()
        for (i in 0 until 4) {
            val x = HX - 22f + i * 16f
            path.moveTo(x - 7f, TOP + 6f)
            path.lineTo(x, TOP - 20f)
            path.lineTo(x + 7f, TOP + 6f)
            path.close()
        }
        c.drawPath(path, ink)
        c.drawPath(path, p)
        p.color = 0xFF2F7A42.toInt()
        c.drawCircle(EX + 10f, EY + 2f, 4f, p)
    }

    // -------------------------------------------------------------------------------------
    // body / back pieces
    // -------------------------------------------------------------------------------------

    private fun cape(c: Canvas, pose: Pose, top: Int, bottom: Int) {
        val sway = sin(pose.time * 5.5f) * 12f + pose.lean * 26f
        path.reset()
        path.moveTo(NECK_X - 6f, NECK_Y + 2f)
        path.cubicTo(-10f, BACK - 4f, BX0 - 26f, BACK + 20f, BX0 - 48f - sway, BELLY + 6f)
        path.quadTo(BX0 - 24f, BELLY + 24f, BX0 + 6f, BELLY + 10f)
        path.cubicTo(BX0 + 20f, BACK + 40f, NECK_X - 20f, NECK_Y + 22f, NECK_X - 6f, NECK_Y + 2f)
        path.close()
        fillInk(c, top)
        p.color = ColorX.withAlpha(bottom, 0.6f)
        path.reset()
        path.moveTo(BX0 - 10f, BACK + 26f)
        path.cubicTo(BX0 - 30f, BACK + 44f, BX0 - 40f - sway, BELLY - 8f, BX0 - 44f - sway, BELLY + 6f)
        path.quadTo(BX0 - 18f, BELLY + 16f, BX0 - 10f, BACK + 26f)
        path.close()
        c.drawPath(path, p)
    }

    private fun coatTail(c: Canvas, pose: Pose, color: Int) {
        val sway = sin(pose.time * 4f) * 6f
        path.reset()
        path.moveTo(BX0 + 10f, BACK + 26f)
        path.cubicTo(BX0 - 16f, BACK + 50f, BX0 - 22f - sway, BELLY + 4f, BX0 - 12f - sway, BELLY + 22f)
        path.quadTo(BX0 + 12f, BELLY + 16f, BX0 + 18f, BELLY - 4f)
        path.close()
        fillInk(c, color)
    }

    private fun scarf(c: Canvas, pose: Pose, color: Int) {
        val sway = sin(pose.time * 6f) * 12f + pose.lean * 18f
        path.reset()
        path.moveTo(NECK_X - 4f, NECK_Y + 2f)
        path.quadTo(-20f, NECK_Y + 24f + sway, -62f - sway, NECK_Y + 10f + sway)
        path.quadTo(-26f, NECK_Y + 44f, NECK_X - 8f, NECK_Y + 24f)
        path.close()
        fillInk(c, color)
    }

    private fun ninjaScarf(c: Canvas, pose: Pose) = scarf(c, pose, 0xFFD8453B.toInt())

    private fun backpack(c: Canvas) {
        rrInk(c, BX0 - 18f, BACK + 12f, BX0 + 28f, BELLY - 2f, 14f, 0xFF3F7A53.toInt())
        p.color = 0xFF2E5C3E.toInt()
        r.set(BX0 - 10f, BACK + 34f, BX0 + 20f, BELLY - 10f)
        c.drawRoundRect(r, 8f, 8f, p)
    }

    private fun straps(c: Canvas) {
        p.color = 0xFF2E5C3E.toInt()
        r.set(BX1 - 34f, BACK + 8f, BX1 - 22f, BELLY + 2f)
        c.drawRoundRect(r, 6f, 6f, p)
    }

    private fun sack(c: Canvas) {
        rrInk(c, BX0 - 34f, BACK + 2f, BX0 + 22f, BELLY - 4f, 22f, 0xFFD8453B.toInt())
        p.color = 0xFFF7F8FB.toInt()
        r.set(BX0 - 30f, BACK + 2f, BX0 + 16f, BACK + 16f)
        c.drawRoundRect(r, 7f, 7f, p)
        p.color = 0xFFF2C14E.toInt()
        c.drawCircle(BX0 - 8f, BACK + 34f, 7f, p)
    }

    private fun swordAndShield(c: Canvas) {
        // hilt over the shoulder
        ink.strokeWidth = 5f
        p.color = 0xFF8A6A42.toInt()
        r.set(BX0 + 4f, BACK - 34f, BX0 + 14f, BACK + 16f)
        c.drawRoundRect(r, 4f, 4f, ink)
        c.drawRoundRect(r, 4f, 4f, p)
        p.color = 0xFFD8DEE9.toInt()
        r.set(BX0 - 6f, BACK - 40f, BX0 + 24f, BACK - 30f)
        c.drawRoundRect(r, 3f, 3f, p)
        // shield
        path.reset()
        path.moveTo(BX0 - 36f, BACK + 16f)
        path.lineTo(BX0 - 4f, BACK + 12f)
        path.lineTo(BX0 - 8f, BELLY - 2f)
        path.lineTo(BX0 - 30f, BELLY + 2f)
        path.close()
        fillInk(c, 0xFF3F6FD6.toInt())
        p.color = 0xFFF2C14E.toInt()
        c.drawCircle(BX0 - 19f, BACK + 34f, 6f, p)
    }

    private fun wings(c: Canvas, pose: Pose) {
        val flap = 0.55f + 0.45f * sin(pose.time * 26f)
        p.color = ColorX.withAlpha(0xFFE8F4FF.toInt(), 0.75f)
        c.save()
        c.translate(BuddyGeom.BODY_CX, BACK + 6f)
        c.rotate(-24f - flap * 24f)
        r.set(-72f, -22f, 10f, 22f)
        c.drawOval(r, p)
        c.drawOval(r, ink)
        c.restore()
    }

    private fun unicornMane(c: Canvas, pose: Pose) {
        val cols = intArrayOf(0xFFF7A8C4.toInt(), 0xFFFFE07A.toInt(), 0xFF9FE8FF.toInt(), 0xFFB9F5C0.toInt())
        for (i in 0 until 4) {
            p.color = cols[i]
            c.drawCircle(NECK_X - 12f - i * 15f, NECK_Y + 2f + i * 5f, 14f - i * 1.5f, p)
        }
    }

    private fun oxygenTank(c: Canvas) {
        rrInk(c, BX0 - 22f, BACK + 10f, BX0 + 14f, BELLY - 6f, 16f, 0xFFCED6E2.toInt())
        p.color = 0xFF7D8798.toInt()
        r.set(BX0 - 18f, BACK + 16f, BX0 + 10f, BACK + 26f)
        c.drawRect(r, p)
    }

    private fun airTank(c: Canvas) {
        rrInk(c, BX0 - 20f, BACK + 12f, BX0 + 12f, BELLY - 8f, 14f, 0xFFF2C14E.toInt())
        p.color = 0xFF2A2F3D.toInt()
        r.set(BX0 - 16f, BACK + 20f, BX0 + 8f, BACK + 28f)
        c.drawRect(r, p)
    }

    private fun dinoSpikes(c: Canvas) {
        p.color = 0xFFF2E3B3.toInt()
        path.reset()
        for (i in 0 until 4) {
            val x = BX0 + 6f + i * 22f
            path.moveTo(x - 9f, BACK + 12f)
            path.lineTo(x, BACK - 14f)
            path.lineTo(x + 9f, BACK + 12f)
            path.close()
        }
        c.drawPath(path, ink)
        c.drawPath(path, p)
    }

    private fun sharkFin(c: Canvas) {
        path.reset()
        path.moveTo(BuddyGeom.BODY_CX - 18f, BACK + 8f)
        path.lineTo(BuddyGeom.BODY_CX + 4f, BACK - 44f)
        path.lineTo(BuddyGeom.BODY_CX + 26f, BACK + 6f)
        path.close()
        fillInk(c, 0xFF3E6E99.toInt())
    }

    private fun overalls(c: Canvas) {
        torso(c, 0xFF3F6FD6.toInt(), 0xFF2A4E99.toInt())
        p.color = 0xFF2A4E99.toInt()
        r.set(BX1 - 40f, BACK + 6f, BX1 - 28f, BELLY + 2f)
        c.drawRoundRect(r, 5f, 5f, p)
        p.color = 0xFFF2C14E.toInt()
        c.drawCircle(BX1 - 34f, BACK + 22f, 5f, p)
        c.drawCircle(BX0 + 18f, BACK + 26f, 5f, p)
    }

    private fun pocket(c: Canvas, color: Int) {
        p.color = color
        r.set(BX0 + 14f, BACK + 32f, BX0 + 42f, BACK + 56f)
        c.drawRoundRect(r, 4f, 4f, p)
    }

    private fun racingStripe(c: Canvas) {
        p.color = 0xFFF7F8FB.toInt()
        r.set(BX0, BACK + 22f, BX1 - 6f, BACK + 34f)
        c.drawRect(r, p)
        p.color = 0xFF2A2E39.toInt()
        r.set(BX0, BACK + 34f, BX1 - 6f, BACK + 40f)
        c.drawRect(r, p)
    }

    private fun plate(c: Canvas) {
        torso(c, 0xFFB9C2D0.toInt(), 0xFF8A94A6.toInt())
        p.color = ColorX.withAlpha(0xFF6E7787.toInt(), 0.8f)
        for (i in 0 until 3) {
            r.set(BX0 + 4f, BACK + 20f + i * 18f, BX1 - 8f, BACK + 24f + i * 18f)
            c.drawRect(r, p)
        }
    }

    private fun furTrim(c: Canvas) {
        p.color = 0xFFF7F8FB.toInt()
        r.set(BX0 - 6f, BELLY - 16f, BX1 + 2f, BELLY + 6f)
        c.drawRoundRect(r, 10f, 10f, p)
        p.color = 0xFF2A2E39.toInt()
        r.set(BuddyGeom.BODY_CX - 16f, BACK + 26f, BuddyGeom.BODY_CX + 10f, BELLY - 10f)
        c.drawRoundRect(r, 4f, 4f, p)
        p.color = 0xFFF2C14E.toInt()
        r.set(BuddyGeom.BODY_CX - 8f, BACK + 40f, BuddyGeom.BODY_CX + 4f, BACK + 52f)
        c.drawRect(r, p)
    }

    private fun tie(c: Canvas) {
        p.color = 0xFFD8453B.toInt()
        path.reset()
        path.moveTo(BX1 - 16f, BACK + 12f)
        path.lineTo(BX1 - 4f, BACK + 18f)
        path.lineTo(BX1 - 10f, BELLY - 8f)
        path.lineTo(BX1 - 22f, BELLY - 12f)
        path.close()
        c.drawPath(path, p)
        p.color = 0xFFF2F4F8.toInt()
        r.set(BX1 - 26f, BACK + 6f, BX1 - 2f, BACK + 16f)
        c.drawRoundRect(r, 3f, 3f, p)
    }

    private fun hiVis(c: Canvas) {
        p.color = 0xFFF2C14E.toInt()
        r.set(BX0, BACK + 24f, BX1 - 6f, BACK + 34f)
        c.drawRect(r, p)
        p.color = 0xFFE8EDF5.toInt()
        r.set(BX0, BACK + 36f, BX1 - 6f, BACK + 42f)
        c.drawRect(r, p)
    }

    private fun suitPanel(c: Canvas) {
        p.color = 0xFF3FA9E0.toInt()
        r.set(BuddyGeom.BODY_CX - 4f, BACK + 26f, BuddyGeom.BODY_CX + 30f, BACK + 50f)
        c.drawRoundRect(r, 7f, 7f, p)
        p.color = 0xFFF2645E.toInt()
        c.drawCircle(BuddyGeom.BODY_CX + 6f, BACK + 38f, 4.5f, p)
        p.color = 0xFF7BE3A0.toInt()
        c.drawCircle(BuddyGeom.BODY_CX + 20f, BACK + 38f, 4.5f, p)
    }

    private fun robotPanel(c: Canvas) {
        p.color = 0xFF6E7787.toInt()
        r.set(BuddyGeom.BODY_CX - 10f, BACK + 24f, BuddyGeom.BODY_CX + 26f, BACK + 52f)
        c.drawRoundRect(r, 6f, 6f, p)
        p.color = 0xFF4FE8FF.toInt()
        r.set(BuddyGeom.BODY_CX - 4f, BACK + 30f, BuddyGeom.BODY_CX + 20f, BACK + 36f)
        c.drawRect(r, p)
        p.color = 0xFFF2C14E.toInt()
        c.drawCircle(BuddyGeom.BODY_CX + 4f, BACK + 44f, 4f, p)
    }

    private fun heroBelt(c: Canvas) {
        p.color = 0xFF8A6A42.toInt()
        r.set(BX0 + 2f, BELLY - 24f, BX1 - 10f, BELLY - 10f)
        c.drawRect(r, p)
        p.color = 0xFFF2C14E.toInt()
        r.set(BuddyGeom.BODY_CX - 8f, BELLY - 27f, BuddyGeom.BODY_CX + 8f, BELLY - 7f)
        c.drawRoundRect(r, 4f, 4f, p)
    }

    private fun beltBuckle(c: Canvas) {
        p.color = 0xFF23262F.toInt()
        r.set(BX0 + 2f, BELLY - 22f, BX1 - 10f, BELLY - 8f)
        c.drawRect(r, p)
        p.color = 0xFFF2C14E.toInt()
        r.set(BuddyGeom.BODY_CX - 12f, BELLY - 26f, BuddyGeom.BODY_CX + 12f, BELLY - 4f)
        c.drawRoundRect(r, 5f, 5f, p)
    }
}
