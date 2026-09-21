package com.blacklab.buddybounce.render

import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import com.blacklab.buddybounce.data.Outfits
import com.blacklab.buddybounce.game.Flight
import com.blacklab.buddybounce.game.MathX.clamp01
import kotlin.math.cos
import kotlin.math.sin

/**
 * Buddy's proportions, in the local space every part of him is drawn in.
 *
 * He is drawn in PROFILE, nose pointing along +X, and the whole rig is mirrored when he turns,
 * so he always faces the way he is moving. The origin is the paw line and -Y is up.
 *
 * The shapes are the Labrador read: blocky skull, straight square muzzle, drop ear set high,
 * deep chest, level topline, and the thick tapering "otter" tail.
 */
object BuddyGeom {
    // body
    const val BODY_CX = -8f
    const val BODY_CY = -80f
    const val BODY_W = 124f
    const val BODY_H = 86f
    const val BACK_Y = -123f          // topline
    const val BELLY_Y = -40f

    // head
    const val HEAD_CX = 54f
    const val HEAD_CY = -124f
    const val HEAD_R = 30f
    const val MUZZLE_X = 90f
    const val MUZZLE_Y = -113f
    const val MUZZLE_W = 44f
    const val MUZZLE_H = 27f
    const val NOSE_X = 106f
    const val NOSE_Y = -117f
    const val EYE_X = 74f
    const val EYE_Y = -133f
    const val EAR_X = 46f
    const val EAR_Y = -146f
    const val NECK_X = 30f
    const val NECK_Y = -110f

    // limbs
    const val FRONT_LEG_X = 30f
    const val BACK_LEG_X = -44f
    const val LEG_TOP = -52f
    const val TAIL_X = -66f
    const val TAIL_Y = -96f

    // A black lab photographs as very dark blue-grey with a hard sheen, never as flat black.
    const val INK = 0xFF080A0F.toInt()
    const val FUR_DARK = 0xFF101219.toInt()
    const val FUR_BASE = 0xFF1E212A.toInt()
    const val FUR_LIGHT = 0xFF343947.toInt()
    const val FUR_SHEEN = 0xFF555E73.toInt()
    const val MUZZLE_COLOR = 0xFF2A2E39.toInt()
    const val NOSE_COLOR = 0xFF0A0B10.toInt()
    const val IRIS = 0xFF9A6428.toInt()
    const val IRIS_LIGHT = 0xFFD29A43.toInt()
    const val TONGUE = 0xFFE87284.toInt()
}

/**
 * The colours of the dog himself, as opposed to what he is wearing.
 *
 * Almost every costume sits on top of Buddy, so the fur underneath is a constant - but a couple
 * ("Anti-Buddy") recolour the animal instead. Pulling the seven coat colours out into a swappable
 * set means those work without a second copy of the rig: [BuddyArt] reads whichever coat is
 * current, so every shadow, tuft, sheen and rim light recolours with it for free.
 */
class Coat(
    val ink: Int,
    val dark: Int,
    val base: Int,
    val light: Int,
    val sheen: Int,
    val muzzle: Int,
    val nose: Int
)

object Coats {
    /** Buddy as he actually is. */
    val BLACK = Coat(
        BuddyGeom.INK, BuddyGeom.FUR_DARK, BuddyGeom.FUR_BASE, BuddyGeom.FUR_LIGHT,
        BuddyGeom.FUR_SHEEN, BuddyGeom.MUZZLE_COLOR, BuddyGeom.NOSE_COLOR
    )

    /**
     * Anti-Buddy: the same dog, inverted. Not flat white - a white coat in shade is a warm grey
     * with cool highlights, and the outline has to stay dark or he disappears against the sky.
     */
    val WHITE = Coat(
        ink = 0xFF6E6A63.toInt(),
        dark = 0xFFCFC8BC.toInt(),
        base = 0xFFEDE7DC.toInt(),
        light = 0xFFFAF7F1.toInt(),
        sheen = 0xFFFFFFFF.toInt(),
        muzzle = 0xFFE2DACD.toInt(),
        nose = 0xFFC08A8A.toInt()
    )

    /** Which coat an outfit implies. Everything not listed leaves him black. */
    fun forOutfit(id: String): Coat = if (id == "anti") WHITE else BLACK
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
    /**
     * 0 = the ordinary dog, 1 = the blessed one: translucent, with wings and a halo. Heaven
     * forces it on, a Second Life turns it on for the rest of a run, and a thousand halos buys
     * it as a wardrobe toggle that works anywhere.
     */
    var ghost = 0f

    fun reset(): Pose {
        squash = 0f; lean = 0f; earFlap = 0f; tail = 0f; blink = 0f; mouth = 0f
        facing = 1f; flight = Flight.NONE; flightT = 0f; shield = 0f; invuln = 0f
        hurt = 0f; spin = 0f; dead = false; time = 0f; ghost = 0f
        return this
    }
}

/**
 * Draws Buddy. Vector work rather than a sprite sheet, so he squashes, stretches, leans, flaps
 * and wags continuously, and every outfit composes onto the same rig.
 */
class BuddyArt(private val art: Art) {

    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val ink = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
        color = BuddyGeom.INK      // replaced every frame by useCoat
    }
    private val path = Path()
    private val path2 = Path()
    private val rect = RectF()

    /**
     * The coat being drawn this frame. Set once at the top of [draw] from the outfit, then read
     * by every shape below - the render thread is the only caller, so a field is safe and saves
     * threading a parameter through forty private functions.
     */
    private var coat: Coat = Coats.BLACK

    // Gradients are built once per coat and kept, since there are only ever a couple.
    private var shaderCoat: Coat? = null
    private var bodyShader: Shader = bodyGradient(Coats.BLACK)
    private var headShader: Shader = headGradient(Coats.BLACK)

    private fun bodyGradient(k: Coat): Shader = LinearGradient(
        0f, BuddyGeom.BACK_Y, 0f, BuddyGeom.BELLY_Y + 10f,
        intArrayOf(k.light, k.base, k.dark),
        floatArrayOf(0f, 0.42f, 1f),
        Shader.TileMode.CLAMP
    )

    private fun headGradient(k: Coat): Shader = LinearGradient(
        0f, BuddyGeom.HEAD_CY - BuddyGeom.HEAD_R * 1.2f,
        0f, BuddyGeom.HEAD_CY + BuddyGeom.HEAD_R,
        intArrayOf(k.light, k.base, k.dark),
        floatArrayOf(0f, 0.5f, 1f),
        Shader.TileMode.CLAMP
    )

    private fun useCoat(k: Coat) {
        coat = k
        if (shaderCoat !== k) {
            bodyShader = bodyGradient(k)
            headShader = headGradient(k)
            shaderCoat = k
        }
        ink.color = k.ink
    }

    /**
     * @param cx      screen x of Buddy's centre line
     * @param pawY    screen y of his paws
     * @param scale   1 = gameplay size; menus draw him larger
     * @param rim     rim-light colour, taken from the current biome
     */
    fun draw(c: Canvas, cx: Float, pawY: Float, scale: Float, pose: Pose, outfit: String, rim: Int) {
        useCoat(Coats.forOutfit(outfit))
        c.save()
        c.translate(cx, pawY)
        if (scale != 1f) c.scale(scale, scale)
        if (pose.spin != 0f) {
            c.rotate(Math.toDegrees(pose.spin.toDouble()).toFloat(), 0f, BuddyGeom.BODY_CY)
        }
        // Mirror the whole rig so he faces the way he is going.
        if (pose.facing < 0f) c.scale(-1f, 1f)
        // A little body English into the turn, and squash/stretch off the paws.
        c.rotate(-pose.lean * 5f)
        c.scale(1f - 0.13f * pose.squash, 1f + 0.18f * pose.squash)

        val stretch = clamp01(pose.squash)
        val tuck = clamp01(-pose.squash)
        ink.strokeWidth = 6f

        // The wings go on behind everything, at full strength - they are made of light, so they
        // should not fade out along with the dog.
        if (pose.ghost > 0.01f) drawWings(c, pose)

        drawFlightBack(c, pose)
        OutfitArt.drawBack(c, outfit, pose, rim)

        // The dog himself, and whatever he is wearing, go into one layer so the whole thing can
        // be made translucent together. Fading each shape individually would let the ones behind
        // show through the ones in front, which reads as a mess rather than as a ghost.
        val ghostLayer = if (pose.ghost > 0.01f) {
            c.saveLayerAlpha(null, (255 * (1f - 0.42f * pose.ghost)).toInt())
        } else {
            -1
        }
        if (outfit == Outfits.DEV_ID) {
            // The one skin that is not a dog at all, so it replaces the rig instead of dressing
            // it. Everything around this block - wings, halo, the ghost layer, flight, the hurt
            // flash - still applies, because none of it cares what shape he is.
            DevArt.draw(c, pose, rim)
        } else {
            drawTail(c, pose, rim)
            drawLegs(c, pose, stretch, tuck, back = true)
            drawBody(c, pose, rim)
            drawLegs(c, pose, stretch, tuck, back = false)
            OutfitArt.drawBody(c, outfit, pose, rim)
            drawHead(c, pose, rim)
            drawEar(c, pose)
            OutfitArt.drawHead(c, outfit, pose, rim)
        }
        if (ghostLayer >= 0) c.restoreToCount(ghostLayer)

        if (pose.ghost > 0.01f) drawHalo(c, pose)
        drawFlightFront(c, pose)

        if (pose.hurt > 0.01f) {
            p.reset(); p.isAntiAlias = true
            p.color = ColorX.withAlpha(0xFFFF6B6B.toInt(), pose.hurt * 0.45f)
            rect.set(
                BuddyGeom.BODY_CX - BuddyGeom.BODY_W * 0.6f, BuddyGeom.BACK_Y - 20f,
                BuddyGeom.BODY_CX + BuddyGeom.BODY_W * 0.6f, BuddyGeom.BELLY_Y + 20f
            )
            c.drawOval(rect, p)
        }

        c.restore()

        if (pose.shield > 0f || pose.invuln > 0f) drawShield(c, cx, pawY, scale, pose)
    }

    // -------------------------------------------------------------------------------------
    // body
    // -------------------------------------------------------------------------------------

    private fun bodyPath(): Path {
        val cx = BuddyGeom.BODY_CX
        val w = BuddyGeom.BODY_W * 0.5f
        val back = BuddyGeom.BACK_Y
        val belly = BuddyGeom.BELLY_Y

        path.reset()
        // Level topline from the withers back to the croup.
        path.moveTo(cx + w * 0.86f, back + 6f)
        path.cubicTo(cx + w * 0.3f, back - 4f, cx - w * 0.45f, back - 2f, cx - w * 0.92f, back + 16f)
        // Rump, well rounded.
        path.cubicTo(cx - w * 1.12f, back + 34f, cx - w * 1.08f, belly - 6f, cx - w * 0.78f, belly + 4f)
        // Belly with a slight tuck behind the ribs.
        path.cubicTo(cx - w * 0.3f, belly + 14f, cx + w * 0.18f, belly + 12f, cx + w * 0.62f, belly + 2f)
        // Deep chest, the Labrador's best feature.
        path.cubicTo(cx + w * 1.06f, belly - 8f, cx + w * 1.12f, back + 42f, cx + w * 0.86f, back + 6f)
        path.close()
        return path
    }

    private fun drawBody(c: Canvas, pose: Pose, rim: Int) {
        val cx = BuddyGeom.BODY_CX
        val w = BuddyGeom.BODY_W * 0.5f
        val back = BuddyGeom.BACK_Y
        val belly = BuddyGeom.BELLY_Y

        art.drawShadow(c, cx, belly + 22f, BuddyGeom.BODY_W * 1.1f, 46f, 0.28f)

        val body = bodyPath()
        c.drawPath(body, ink)
        p.reset(); p.isAntiAlias = true
        p.shader = bodyShader
        c.drawPath(body, p)
        p.shader = null

        // Ambient occlusion along the belly, sheen across the ribs.
        p.color = ColorX.withAlpha(0xFF000000.toInt(), 0.35f)
        rect.set(cx - w * 0.9f, belly - 22f, cx + w * 0.8f, belly + 16f)
        c.drawOval(rect, p)
        p.color = ColorX.withAlpha(coat.sheen, 0.30f)
        rect.set(cx - w * 0.55f, back + 10f, cx + w * 0.62f, back + 44f)
        c.drawOval(rect, p)

        // Shoulder and haunch masses - two soft ovals read as muscle under a short coat.
        p.color = ColorX.withAlpha(coat.light, 0.32f)
        rect.set(cx + w * 0.18f, back + 18f, cx + w * 0.92f, belly - 2f)
        c.drawOval(rect, p)
        p.color = ColorX.withAlpha(coat.light, 0.22f)
        rect.set(cx - w * 0.98f, back + 20f, cx - w * 0.3f, belly - 6f)
        c.drawOval(rect, p)

        // The small white chest patch a lot of labs carry.
        p.color = ColorX.withAlpha(0xFFFFFFFF.toInt(), 0.13f)
        rect.set(cx + w * 0.72f, belly - 30f, cx + w * 1.0f, belly + 4f)
        c.drawOval(rect, p)

        // Rim light down the topline.
        //
        // This used to take the scene's rim colour neat, at half alpha and 4.5 wide, which on a
        // warm-lit world painted a solid tan stripe along his spine - it read as a marking, not
        // as light. Mixing most of the way to white and thinning it right down gives the sheen
        // the highlight was meant to be, and it stays inside the silhouette rather than riding
        // on top of it.
        c.save()
        c.clipPath(body)
        ink.color = ColorX.withAlpha(ColorX.lerp(rim, 0xFFFFFFFF.toInt(), 0.65f), 0.22f)
        ink.strokeWidth = 2.6f
        path2.reset()
        path2.moveTo(cx + w * 0.78f, back + 9f)
        path2.cubicTo(cx + w * 0.3f, back - 1f, cx - w * 0.45f, back + 1f, cx - w * 0.86f, back + 19f)
        c.drawPath(path2, ink)
        c.restore()
        ink.color = coat.ink
        ink.strokeWidth = 6f

        // Coat tufts at the rump and behind the front leg break the silhouette.
        p.color = coat.dark
        path2.reset()
        path2.moveTo(cx - w * 1.02f, belly - 18f)
        path2.quadTo(cx - w * 1.2f, belly + 2f, cx - w * 0.86f, belly + 4f)
        path2.quadTo(cx - w * 0.96f, belly - 6f, cx - w * 1.02f, belly - 18f)
        path2.close()
        c.drawPath(path2, p)

        // Short dense coat: a scatter of fine tufts catching the light along the back, the
        // chest and the haunch. Cheap, and it stops the body reading as one flat shape.
        p.color = ColorX.withAlpha(coat.sheen, 0.30f)
        for (i in 0 until 7) {
            val t = i / 6f
            val fx = cx - w * 0.82f + w * 1.62f * t
            val fy = back + 8f + sin(t * 3.1f) * 5f
            tuft(c, fx, fy, 13f, -18f + t * 26f)
        }
        p.color = ColorX.withAlpha(coat.light, 0.34f)
        for (i in 0 until 4) {
            val t = i / 3f
            tuft(c, cx + w * (0.62f + t * 0.3f), belly - 24f + t * 20f, 11f, 120f + t * 20f)
        }
        p.color = ColorX.withAlpha(coat.dark, 0.55f)
        for (i in 0 until 4) {
            val t = i / 3f
            tuft(c, cx - w * (0.5f + t * 0.42f), belly - 4f + t * 6f, 12f, 150f - t * 18f)
        }
    }

    /** One tapered fur tuft, pointing along [angle] degrees. */
    private fun tuft(c: Canvas, x: Float, y: Float, len: Float, angle: Float) {
        c.save()
        c.translate(x, y)
        c.rotate(angle)
        path2.reset()
        path2.moveTo(0f, -4f)
        path2.quadTo(len * 0.6f, -3f, len, 0f)
        path2.quadTo(len * 0.6f, 2f, 0f, 4f)
        path2.close()
        c.drawPath(path2, p)
        c.restore()
    }

    private fun drawLegs(c: Canvas, pose: Pose, stretch: Float, tuck: Float, back: Boolean) {
        // Rising: legs trail and tuck. Falling: legs reach down. Landing: they splay.
        val reach = clamp01(-pose.squash * 0.6f + 0.4f)
        val legLen = 52f * (0.62f + 0.45f * reach) + stretch * -6f
        val swing = (stretch * -16f + tuck * 10f)
        val color = if (back) coat.dark else coat.base
        val depth = if (back) 0.55f else 1f

        p.reset(); p.isAntiAlias = true
        ink.strokeWidth = if (back) 4.5f else 6f
        ink.color = if (back) ColorX.withAlpha(coat.ink, 0.65f) else coat.ink

        drawLeg(c, BuddyGeom.FRONT_LEG_X + (if (back) -13f else 0f), legLen, swing * 0.7f, color, depth, front = true)
        drawLeg(c, BuddyGeom.BACK_LEG_X + (if (back) -13f else 0f), legLen * 0.96f, -swing, color, depth, front = false)

        ink.color = coat.ink
        ink.strokeWidth = 6f
    }

    private fun drawLeg(c: Canvas, x: Float, len: Float, swing: Float, color: Int, depth: Float, front: Boolean) {
        val top = BuddyGeom.LEG_TOP
        val bottom = top + len
        val footX = x + swing
        path2.reset()
        path2.moveTo(x - 11f, top)
        path2.cubicTo(x - 12f, top + len * 0.45f, footX - 11f, bottom - 14f, footX - 12f, bottom - 4f)
        path2.quadTo(footX, bottom + 6f, footX + 12f, bottom - 4f)
        path2.cubicTo(footX + 11f, bottom - 14f, x + 12f, top + len * 0.45f, x + 11f, top)
        path2.close()
        c.drawPath(path2, ink)
        p.color = ColorX.withAlpha(color, depth)
        c.drawPath(path2, p)

        // paw
        p.color = ColorX.withAlpha(if (front) coat.light else coat.base, depth)
        rect.set(footX - 15f, bottom - 13f, footX + 16f, bottom + 4f)
        c.drawRoundRect(rect, 9f, 9f, p)
        if (front) {
            p.color = ColorX.withAlpha(coat.sheen, 0.35f * depth)
            rect.set(footX - 11f, bottom - 10f, footX + 6f, bottom - 3f)
            c.drawRoundRect(rect, 4f, 4f, p)
            // toe splits
            ink.strokeWidth = 2.6f
            ink.color = ColorX.withAlpha(coat.ink, 0.55f)
            c.drawLine(footX - 4f, bottom - 6f, footX - 4f, bottom + 2f, ink)
            c.drawLine(footX + 5f, bottom - 6f, footX + 5f, bottom + 2f, ink)
            ink.color = coat.ink
            ink.strokeWidth = 6f
        }
    }

    private fun drawTail(c: Canvas, pose: Pose, rim: Int) {
        // The otter tail: thick at the base, tapering, carried level and wagging hard.
        val wag = sin(pose.tail) * 22f - 16f
        c.save()
        c.translate(BuddyGeom.TAIL_X, BuddyGeom.TAIL_Y)
        c.rotate(wag)

        path2.reset()
        path2.moveTo(6f, -14f)
        path2.cubicTo(-26f, -26f, -52f, -34f, -74f, -46f)
        path2.quadTo(-58f, -18f, -62f, -2f)
        path2.cubicTo(-40f, 0f, -16f, 6f, 6f, 12f)
        path2.close()
        ink.strokeWidth = 5.5f
        c.drawPath(path2, ink)
        p.reset(); p.isAntiAlias = true
        p.color = coat.dark
        c.drawPath(path2, p)
        p.color = ColorX.withAlpha(coat.light, 0.5f)
        path2.reset()
        path2.moveTo(2f, -10f)
        path2.cubicTo(-24f, -22f, -46f, -28f, -66f, -40f)
        path2.quadTo(-40f, -20f, 0f, -2f)
        path2.close()
        c.drawPath(path2, p)
        ink.strokeWidth = 6f
        c.restore()
    }

    // -------------------------------------------------------------------------------------
    // head
    // -------------------------------------------------------------------------------------

    private fun drawHead(c: Canvas, pose: Pose, rim: Int) {
        val hx = BuddyGeom.HEAD_CX
        val hy = BuddyGeom.HEAD_CY
        val r = BuddyGeom.HEAD_R

        // Neck wedge into the shoulders.
        path2.reset()
        path2.moveTo(hx - 26f, hy + 4f)
        path2.cubicTo(hx - 34f, hy + 30f, hx - 46f, hy + 40f, hx - 52f, hy + 52f)
        path2.lineTo(hx - 4f, hy + 54f)
        path2.cubicTo(hx + 6f, hy + 30f, hx + 10f, hy + 16f, hx + 8f, hy + 2f)
        path2.close()
        c.drawPath(path2, ink)
        p.reset(); p.isAntiAlias = true
        p.color = coat.base
        c.drawPath(path2, p)

        // Skull + square muzzle as one silhouette: blocky head, clear stop, straight bridge.
        path.reset()
        path.moveTo(hx - 28f, hy - 6f)                       // back of skull
        path.cubicTo(hx - 30f, hy - r * 1.25f, hx + 4f, hy - r * 1.45f, hx + 22f, hy - r * 1.2f)
        path.cubicTo(hx + 32f, hy - r * 1.05f, hx + 34f, hy - 22f, hx + 36f, hy - 14f)   // stop
        path.lineTo(BuddyGeom.NOSE_X - 2f, BuddyGeom.MUZZLE_Y - 13f)                      // bridge
        path.quadTo(BuddyGeom.NOSE_X + 10f, BuddyGeom.MUZZLE_Y - 6f, BuddyGeom.NOSE_X + 8f, BuddyGeom.MUZZLE_Y + 6f)
        path.quadTo(BuddyGeom.NOSE_X + 4f, BuddyGeom.MUZZLE_Y + 15f, BuddyGeom.NOSE_X - 12f, BuddyGeom.MUZZLE_Y + 16f)
        path.cubicTo(hx + 30f, BuddyGeom.MUZZLE_Y + 20f, hx + 20f, hy + 26f, hx + 2f, hy + 26f)  // jaw + cheek
        path.cubicTo(hx - 18f, hy + 26f, hx - 28f, hy + 12f, hx - 28f, hy - 6f)
        path.close()
        c.drawPath(path, ink)
        p.shader = headShader
        c.drawPath(path, p)
        p.shader = null

        // Cheek mass and the lighter muzzle.
        p.color = ColorX.withAlpha(coat.muzzle, 0.9f)
        rect.set(hx + 26f, BuddyGeom.MUZZLE_Y - 12f, BuddyGeom.NOSE_X + 4f, BuddyGeom.MUZZLE_Y + 16f)
        c.drawRoundRect(rect, 13f, 13f, p)
        p.color = ColorX.withAlpha(coat.sheen, 0.26f)
        rect.set(hx - 16f, hy - r * 1.15f, hx + 24f, hy - 8f)
        c.drawOval(rect, p)

        // Lip line and the corner of the mouth.
        ink.strokeWidth = 3.4f
        ink.color = ColorX.withAlpha(coat.ink, 0.85f)
        path2.reset()
        path2.moveTo(BuddyGeom.NOSE_X - 6f, BuddyGeom.MUZZLE_Y + 13f)
        path2.quadTo(hx + 34f, BuddyGeom.MUZZLE_Y + 19f, hx + 20f, BuddyGeom.MUZZLE_Y + 14f)
        c.drawPath(path2, ink)

        // Open mouth + tongue when he is hooning.
        if (pose.mouth > 0.04f) {
            p.color = 0xFF14151A.toInt()
            path2.reset()
            path2.moveTo(BuddyGeom.NOSE_X - 8f, BuddyGeom.MUZZLE_Y + 12f)
            path2.quadTo(hx + 40f, BuddyGeom.MUZZLE_Y + 16f + 16f * pose.mouth, hx + 18f, BuddyGeom.MUZZLE_Y + 12f)
            path2.close()
            c.drawPath(path2, p)
            p.color = BuddyGeom.TONGUE
            rect.set(
                hx + 30f, BuddyGeom.MUZZLE_Y + 12f,
                hx + 56f, BuddyGeom.MUZZLE_Y + 14f + 22f * pose.mouth
            )
            c.drawRoundRect(rect, 10f, 10f, p)
        }

        // Nose: big, square, glossy.
        p.color = coat.nose
        rect.set(BuddyGeom.NOSE_X - 10f, BuddyGeom.NOSE_Y - 9f, BuddyGeom.NOSE_X + 9f, BuddyGeom.NOSE_Y + 8f)
        c.drawRoundRect(rect, 8f, 7f, p)
        p.color = ColorX.withAlpha(0xFFFFFFFF.toInt(), 0.30f)
        rect.set(BuddyGeom.NOSE_X - 7f, BuddyGeom.NOSE_Y - 7f, BuddyGeom.NOSE_X - 1f, BuddyGeom.NOSE_Y - 2f)
        c.drawOval(rect, p)
        p.color = ColorX.withAlpha(0xFF000000.toInt(), 0.6f)
        rect.set(BuddyGeom.NOSE_X - 3f, BuddyGeom.NOSE_Y + 1f, BuddyGeom.NOSE_X + 5f, BuddyGeom.NOSE_Y + 5f)
        c.drawOval(rect, p)

        // Whiskers.
        //
        // They used to be three long strokes that all set off from the same spot and swept
        // forward past the end of his nose, which converged into what looked like a spike
        // growing off his face. Real ones sprout from the whisker pad - the fleshy patch just
        // behind the nose - and are SHORT, so these start from three separate roots there and
        // fan apart instead of together. Faint on purpose: at gameplay size they are a texture,
        // not a feature.
        ink.strokeWidth = 1.8f
        ink.color = ColorX.withAlpha(coat.sheen, 0.38f)
        val padX = BuddyGeom.NOSE_X - 24f
        val padY = BuddyGeom.MUZZLE_Y + 2f
        for (i in 0 until 3) {
            val rootX = padX - i * 5f
            val rootY = padY + i * 5f
            val len = 23f - i * 3f
            val droop = 4f + i * 5f          // lower whiskers hang further
            path2.reset()
            path2.moveTo(rootX, rootY)
            path2.quadTo(rootX + len * 0.6f, rootY + droop * 0.4f, rootX + len, rootY + droop)
            c.drawPath(path2, ink)
        }
        ink.strokeWidth = 3.2f
        ink.color = ColorX.withAlpha(coat.sheen, 0.42f)
        path2.reset()
        path2.moveTo(hx + 30f, BuddyGeom.MUZZLE_Y - 13f)
        path2.quadTo(hx + 52f, BuddyGeom.MUZZLE_Y - 18f, BuddyGeom.NOSE_X - 8f, BuddyGeom.MUZZLE_Y - 14f)
        c.drawPath(path2, ink)
        ink.color = coat.ink
        ink.strokeWidth = 6f

        // cheek fluff where the jaw meets the neck
        p.color = ColorX.withAlpha(coat.dark, 0.75f)
        for (i in 0 until 3) {
            tuft(c, hx - 18f + i * 8f, hy + 20f + i * 4f, 14f, 160f + i * 10f)
        }

        drawEye(c, pose)

        // Brow ridge - labs have a soft, kind expression, and one stroke carries it.
        ink.color = ColorX.withAlpha(coat.sheen, 0.55f)
        ink.strokeWidth = 4f
        path2.reset()
        path2.moveTo(BuddyGeom.EYE_X - 13f, BuddyGeom.EYE_Y - 12f)
        path2.quadTo(BuddyGeom.EYE_X - 2f, BuddyGeom.EYE_Y - 17f, BuddyGeom.EYE_X + 11f, BuddyGeom.EYE_Y - 12f)
        c.drawPath(path2, ink)
        ink.color = coat.ink
        ink.strokeWidth = 6f
    }

    private fun drawEye(c: Canvas, pose: Pose) {
        val ex = BuddyGeom.EYE_X
        val ey = BuddyGeom.EYE_Y
        p.reset(); p.isAntiAlias = true

        if (pose.dead) {
            ink.color = 0xFFE7E9F2.toInt()
            ink.strokeWidth = 4f
            c.drawLine(ex - 7f, ey - 7f, ex + 7f, ey + 7f, ink)
            c.drawLine(ex + 7f, ey - 7f, ex - 7f, ey + 7f, ink)
            ink.color = coat.ink
            ink.strokeWidth = 6f
            return
        }

        p.color = 0xFF07080C.toInt()
        c.drawCircle(ex, ey, 9.5f, p)
        p.color = BuddyGeom.IRIS
        c.drawCircle(ex + 0.8f, ey + 0.6f, 7f, p)
        p.color = BuddyGeom.IRIS_LIGHT
        rect.set(ex - 3f, ey - 5f, ex + 6f, ey + 2f)
        c.drawOval(rect, p)
        p.color = 0xFF06070A.toInt()
        c.drawCircle(ex + 1.5f, ey + 1f, 3.8f, p)
        p.color = ColorX.withAlpha(0xFFFFFFFF.toInt(), 0.95f)
        c.drawCircle(ex - 2.4f, ey - 3.4f, 2.6f, p)
        p.color = ColorX.withAlpha(0xFFFFFFFF.toInt(), 0.45f)
        c.drawCircle(ex + 4f, ey + 3.4f, 1.5f, p)

        if (pose.blink > 0.02f) {
            p.color = coat.base
            rect.set(ex - 12f, ey - 13f, ex + 12f, ey - 13f + 26f * pose.blink)
            c.drawRect(rect, p)
            ink.strokeWidth = 3f
            c.drawLine(ex - 11f, ey - 13f + 26f * pose.blink, ex + 11f, ey - 13f + 26f * pose.blink, ink)
            ink.strokeWidth = 6f
        }
    }

    private fun drawEar(c: Canvas, pose: Pose) {
        // Drop ear set high on the skull, lifting with speed.
        val lift = pose.earFlap * 26f + clamp01(pose.squash) * 16f
        c.save()
        c.translate(BuddyGeom.EAR_X, BuddyGeom.EAR_Y)
        c.rotate(-8f - lift + sin(pose.time * 2.2f) * 2.5f)

        path2.reset()
        path2.moveTo(-4f, 6f)
        path2.cubicTo(16f, 0f, 30f, 12f, 28f, 34f)
        path2.cubicTo(26f, 54f, 12f, 64f, -2f, 60f)
        path2.cubicTo(-14f, 56f, -16f, 28f, -4f, 6f)
        path2.close()
        ink.strokeWidth = 5.5f
        c.drawPath(path2, ink)
        p.reset(); p.isAntiAlias = true
        p.color = coat.dark
        c.drawPath(path2, p)

        p.color = ColorX.withAlpha(coat.sheen, 0.22f)
        path2.reset()
        path2.moveTo(2f, 12f)
        path2.cubicTo(16f, 10f, 24f, 20f, 22f, 36f)
        path2.cubicTo(16f, 26f, 8f, 18f, 2f, 12f)
        path2.close()
        c.drawPath(path2, p)

        // a crease down the fold of the ear, and a wisp at the tip
        ink.strokeWidth = 2.6f
        ink.color = ColorX.withAlpha(coat.ink, 0.5f)
        path2.reset()
        path2.moveTo(6f, 14f)
        path2.quadTo(16f, 34f, 10f, 54f)
        c.drawPath(path2, ink)
        ink.color = coat.ink
        p.color = ColorX.withAlpha(coat.dark, 0.9f)
        tuft(c, 0f, 58f, 12f, 100f)
        ink.strokeWidth = 6f
        c.restore()
    }

    // -------------------------------------------------------------------------------------
    // flight rigs
    // -------------------------------------------------------------------------------------

    private fun drawFlightBack(c: Canvas, pose: Pose) {
        when (pose.flight) {
            Flight.JETPACK -> {
                p.reset(); p.isAntiAlias = true
                rect.set(BuddyGeom.BODY_CX - 60f, BuddyGeom.BACK_Y - 6f, BuddyGeom.BODY_CX - 6f, BuddyGeom.BODY_CY + 26f)
                ink.strokeWidth = 5f
                c.drawRoundRect(rect, 16f, 16f, ink)
                p.color = 0xFFC7CEDA.toInt()
                c.drawRoundRect(rect, 16f, 16f, p)
                p.color = 0xFFE05E4A.toInt()
                rect.set(BuddyGeom.BODY_CX - 56f, BuddyGeom.BACK_Y - 2f, BuddyGeom.BODY_CX - 10f, BuddyGeom.BACK_Y + 12f)
                c.drawRoundRect(rect, 7f, 7f, p)
                drawFlame(c, BuddyGeom.BODY_CX - 33f, BuddyGeom.BODY_CY + 30f, 26f, 1f, pose.time)
                ink.strokeWidth = 6f
            }
            Flight.ROCKET -> {
                p.reset(); p.isAntiAlias = true
                rect.set(BuddyGeom.BODY_CX - 78f, BuddyGeom.BODY_CY - 26f, BuddyGeom.BODY_CX + 30f, BuddyGeom.BODY_CY + 26f)
                ink.strokeWidth = 5f
                c.drawRoundRect(rect, 26f, 26f, ink)
                p.color = 0xFFEFEFF4.toInt()
                c.drawRoundRect(rect, 26f, 26f, p)
                p.color = 0xFFD8453B.toInt()
                path2.reset()
                path2.moveTo(BuddyGeom.BODY_CX + 24f, BuddyGeom.BODY_CY - 26f)
                path2.lineTo(BuddyGeom.BODY_CX + 74f, BuddyGeom.BODY_CY)
                path2.lineTo(BuddyGeom.BODY_CX + 24f, BuddyGeom.BODY_CY + 26f)
                path2.close()
                c.drawPath(path2, p)
                drawFlame(c, BuddyGeom.BODY_CX - 88f, BuddyGeom.BODY_CY, 40f, 1.5f, pose.time, sideways = true)
                ink.strokeWidth = 6f
            }
        }
    }

    private fun drawFlightFront(c: Canvas, pose: Pose) {
        if (pose.flight != Flight.PROPELLER) return
        val hx = BuddyGeom.HEAD_CX + 4f
        val cy = BuddyGeom.HEAD_CY - BuddyGeom.HEAD_R * 1.45f
        p.reset(); p.isAntiAlias = true

        rect.set(hx - 30f, cy - 4f, hx + 30f, cy + 30f)
        ink.strokeWidth = 5f
        c.drawArc(rect, 180f, 180f, true, ink)
        p.color = 0xFF3FA9E0.toInt()
        c.drawArc(rect, 180f, 180f, true, p)
        p.color = 0xFF2E86B5.toInt()
        rect.set(hx - 34f, cy + 16f, hx + 34f, cy + 27f)
        c.drawRoundRect(rect, 6f, 6f, p)

        val spin = cos(pose.time * 34f)
        p.color = 0xFFE8EDF5.toInt()
        val bw = 46f * (if (spin < 0f) -spin else spin).coerceAtLeast(0.12f)
        rect.set(hx - bw, cy - 22f, hx + bw, cy - 11f)
        c.drawRoundRect(rect, 5f, 5f, p)
        p.color = 0xFF9AA6B8.toInt()
        c.drawCircle(hx, cy - 16f, 6f, p)
        ink.strokeWidth = 6f
    }

    private fun drawFlame(c: Canvas, x: Float, y: Float, size: Float, power: Float, t: Float, sideways: Boolean = false) {
        val flicker = 0.75f + 0.25f * sin(t * 33f + x)
        art.draw(c, art.softGlow, x, y, size * 3.4f, size * 3.4f, 0.45f * power, 0xFFFF9A3C.toInt())
        p.reset(); p.isAntiAlias = true
        c.save()
        c.translate(x, y)
        if (sideways) c.rotate(90f)
        p.color = 0xFFFFB347.toInt()
        path2.reset()
        path2.moveTo(-size * 0.5f, 0f)
        path2.quadTo(0f, size * 2.3f * flicker * power, size * 0.5f, 0f)
        path2.quadTo(0f, size * 0.35f, -size * 0.5f, 0f)
        path2.close()
        c.drawPath(path2, p)
        p.color = 0xFFFFE9A8.toInt()
        path2.reset()
        path2.moveTo(-size * 0.26f, 0f)
        path2.quadTo(0f, size * 1.3f * flicker * power, size * 0.26f, 0f)
        path2.quadTo(0f, size * 0.2f, -size * 0.26f, 0f)
        path2.close()
        c.drawPath(path2, p)
        c.restore()
    }

    private fun drawShield(c: Canvas, cx: Float, pawY: Float, scale: Float, pose: Pose) {
        val r = 108f * scale
        val cy = pawY + BuddyGeom.BODY_CY * scale
        val expiring = pose.shield > 0.001f && pose.shield < 3f
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
        p.color = ColorX.withAlpha(0xFF9FDCF7.toInt(), 0.09f * base)
        c.drawCircle(cx, cy, r, p)
    }

    // -------------------------------------------------------------------------------------
    // the blessed look: wings and a halo
    // -------------------------------------------------------------------------------------

    /** Two feathered wings off the shoulders, beating slowly. */
    private fun drawWings(c: Canvas, pose: Pose) {
        val a = clamp01(pose.ghost)
        val beat = sin(pose.time * 4.2f)
        val ox = BuddyGeom.BODY_CX + 6f
        val oy = BuddyGeom.BACK_Y + 18f

        art.drawGlow(c, ox, oy, 320f, 0xFFFFF6DC.toInt(), 0.3f * a)

        for (side in 0 until 2) {
            // the far wing is smaller and dimmer, so the pair reads as depth rather than a
            // symmetrical cut-out stuck on his back
            val far = side == 0
            val k = if (far) 0.82f else 1f
            val lift = beat * (if (far) 12f else 18f)
            p.reset(); p.isAntiAlias = true
            p.color = ColorX.withAlpha(0xFFFFFDF4.toInt(), a * (if (far) 0.5f else 0.9f))

            c.save()
            c.translate(ox, oy)
            c.scale(k, k)
            c.rotate(if (far) 8f else -4f)

            // three overlapping feather banks per wing
            for (bank in 0 until 3) {
                val bk = 1f - bank * 0.22f
                path.reset()
                path.moveTo(-6f, 0f)
                path.cubicTo(
                    -58f * bk, -50f * bk - lift,
                    -128f * bk, -18f * bk - lift * 0.6f,
                    -104f * bk, 26f * bk
                )
                path.cubicTo(-70f * bk, 18f * bk, -30f * bk, 10f * bk, -6f, 0f)
                path.close()
                c.drawPath(path, p)
            }
            // primary feather separations
            ink.strokeWidth = 2.4f
            ink.color = ColorX.withAlpha(0xFFD8E4F2.toInt(), a * 0.55f)
            for (i in 0 until 4) {
                val t = i / 3f
                c.drawLine(-30f - t * 30f, 2f + t * 6f, -70f - t * 40f, 14f + t * 8f, ink)
            }
            ink.color = coat.ink
            ink.strokeWidth = 6f
            c.restore()
        }
    }

    /** The ring, floating above the skull and tilting gently. */
    private fun drawHalo(c: Canvas, pose: Pose) {
        val a = clamp01(pose.ghost)
        val hx = BuddyGeom.HEAD_CX - 4f
        val hy = BuddyGeom.HEAD_CY - BuddyGeom.HEAD_R * 1.85f + sin(pose.time * 1.9f) * 4f
        val rad = 30f
        val tilt = 0.3f + 0.07f * sin(pose.time * 1.5f)

        art.drawGlow(c, hx, hy, rad * 5f, 0xFFFFE9A8.toInt(), 0.45f * a)
        p.reset(); p.isAntiAlias = true
        p.style = Paint.Style.STROKE
        p.strokeWidth = rad * 0.24f
        p.color = ColorX.withAlpha(0xFFFFE9A8.toInt(), a)
        rect.set(hx - rad, hy - rad * tilt, hx + rad, hy + rad * tilt)
        c.drawOval(rect, p)
        p.strokeWidth = rad * 0.09f
        p.color = ColorX.withAlpha(0xFFFFFDF0.toInt(), a * 0.85f)
        c.drawOval(rect, p)
        p.style = Paint.Style.FILL
    }
}
