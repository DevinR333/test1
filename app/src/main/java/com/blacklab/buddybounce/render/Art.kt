package com.blacklab.buddybounce.render

import android.graphics.Bitmap
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader

/**
 * Bitmaps that are expensive to draw but cheap to reuse: soft shadows, glows, cloud puffs.
 * They are baked once at device resolution with a software canvas (blur mask filters are not
 * supported on a hardware canvas) and then blitted every frame.
 */
class Art(private val scale: Float) {

    val shadow: Bitmap = radial(px(150f), 0x8C000000.toInt(), 0.55f)
    val glow: Bitmap = radial(px(180f), 0xFFFFFFFF.toInt(), 0.0f)
    val softGlow: Bitmap = radial(px(220f), 0xFFFFFFFF.toInt(), 0.35f)
    val cloud: Bitmap = cloudPuff(px(420f), px(230f))
    val spark: Bitmap = radial(px(60f), 0xFFFFFFFF.toInt(), 0.1f)

    private val filters = HashMap<Int, ColorFilter>()
    private val dst = RectF()
    private val blit = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)

    private fun px(wu: Float): Int = (wu * scale).toInt().coerceIn(24, 900)

    /** Cached tint filters - colour filters allocate, and the game loop must not. */
    fun tint(color: Int): ColorFilter {
        var f = filters[color]
        if (f == null) {
            f = PorterDuffColorFilter(color, PorterDuff.Mode.SRC_IN)
            if (filters.size < 96) filters[color] = f
        }
        return f
    }

    /** Draws a baked bitmap centred on (cx, cy) at a size given in world units. */
    fun draw(c: Canvas, bmp: Bitmap, cx: Float, cy: Float, w: Float, h: Float, alpha: Float, tintColor: Int = 0) {
        if (alpha <= 0.004f) return
        blit.alpha = (alpha.coerceIn(0f, 1f) * 255f).toInt()
        blit.colorFilter = if (tintColor == 0) null else tint(tintColor)
        dst.set(cx - w * 0.5f, cy - h * 0.5f, cx + w * 0.5f, cy + h * 0.5f)
        c.drawBitmap(bmp, null, dst, blit)
        blit.colorFilter = null
    }

    fun drawShadow(c: Canvas, cx: Float, cy: Float, w: Float, h: Float, alpha: Float) =
        draw(c, shadow, cx, cy, w, h, alpha)

    fun drawGlow(c: Canvas, cx: Float, cy: Float, radius: Float, color: Int, alpha: Float) =
        draw(c, softGlow, cx, cy, radius * 2f, radius * 2f, alpha, color)

    private fun radial(size: Int, color: Int, solidCore: Float): Bitmap {
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val r = size * 0.5f
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        val core = solidCore.coerceIn(0f, 0.9f)
        p.shader = RadialGradient(
            r, r, r,
            intArrayOf(color, ColorX.scaleAlpha(color, 0.55f), ColorX.withAlpha(color, 0f)),
            floatArrayOf(core, core + (1f - core) * 0.45f, 1f),
            Shader.TileMode.CLAMP
        )
        c.drawCircle(r, r, r, p)
        return bmp
    }

    private fun cloudPuff(w: Int, h: Int): Bitmap {
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        p.color = 0xFFFFFFFF.toInt()
        p.maskFilter = BlurMaskFilter(h * 0.10f, BlurMaskFilter.Blur.NORMAL)
        val baseY = h * 0.66f
        // A few overlapping lobes read as a cloud once blurred.
        c.drawCircle(w * 0.30f, baseY, h * 0.28f, p)
        c.drawCircle(w * 0.48f, baseY - h * 0.16f, h * 0.33f, p)
        c.drawCircle(w * 0.66f, baseY - h * 0.05f, h * 0.27f, p)
        c.drawCircle(w * 0.80f, baseY, h * 0.20f, p)
        c.drawRect(w * 0.22f, baseY - h * 0.02f, w * 0.84f, baseY + h * 0.22f, p)
        p.maskFilter = null
        return bmp
    }

    fun dispose() {
        shadow.recycle(); glow.recycle(); softGlow.recycle(); cloud.recycle(); spark.recycle()
        filters.clear()
    }
}
