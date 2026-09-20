package com.blacklab.buddybounce.ui

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import com.blacklab.buddybounce.data.Powerups
import com.blacklab.buddybounce.render.Art
import com.blacklab.buddybounce.render.ColorX

/**
 * A distinct little glyph per consumable power-up, drawn rather than shipped as an asset.
 * Shared by the pre-run picker and the prize reveal so a power-up always looks the same.
 */
class PowerupIcon {

    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val path = Path()
    private val r = RectF()

    fun draw(c: Canvas, art: Art, id: String, cx: Float, cy: Float, rad: Float, tint: Int) {
        p.reset(); p.isAntiAlias = true
        art.drawGlow(c, cx, cy, rad * 2.6f, tint, 0.35f)
        when (id) {
            Powerups.MOON_JUMP -> {
                p.color = 0xFFE8EDF5.toInt()
                c.drawCircle(cx, cy, rad, p)
                p.color = 0xFFB9C2D0.toInt()
                c.drawCircle(cx - rad * 0.3f, cy - rad * 0.2f, rad * 0.22f, p)
                c.drawCircle(cx + rad * 0.35f, cy + rad * 0.3f, rad * 0.16f, p)
                p.color = tint
                arrowUp(c, cx, cy - rad * 1.45f, rad * 0.55f)
            }
            Powerups.ROCKET_START -> rocket(c, cx, cy, rad)
            Powerups.JETPACK_START -> {
                p.color = 0xFFCED6E2.toInt()
                r.set(cx - rad * 0.8f, cy - rad, cx - rad * 0.1f, cy + rad * 0.7f)
                c.drawRoundRect(r, rad * 0.3f, rad * 0.3f, p)
                r.set(cx + rad * 0.1f, cy - rad, cx + rad * 0.8f, cy + rad * 0.7f)
                c.drawRoundRect(r, rad * 0.3f, rad * 0.3f, p)
                p.color = 0xFFFFB347.toInt()
                flame(c, cx - rad * 0.45f, cy + rad * 0.7f, rad * 0.5f)
                flame(c, cx + rad * 0.45f, cy + rad * 0.7f, rad * 0.5f)
            }
            Powerups.SHIELD_START -> {
                p.color = ColorX.withAlpha(0xFFBDEBFF.toInt(), 0.45f)
                c.drawCircle(cx, cy, rad, p)
                p.style = Paint.Style.STROKE
                p.strokeWidth = rad * 0.18f
                p.color = 0xFFEAF7FF.toInt()
                c.drawCircle(cx, cy, rad, p)
                p.style = Paint.Style.FILL
                p.color = ColorX.withAlpha(0xFFFFFFFF.toInt(), 0.8f)
                c.drawCircle(cx - rad * 0.35f, cy - rad * 0.35f, rad * 0.2f, p)
            }
            Powerups.MAGNET_RUN -> {
                p.style = Paint.Style.STROKE
                p.strokeWidth = rad * 0.5f
                p.color = 0xFFE8595B.toInt()
                r.set(cx - rad * 0.8f, cy - rad * 0.9f, cx + rad * 0.8f, cy + rad * 0.7f)
                c.drawArc(r, 180f, 180f, false, p)
                p.style = Paint.Style.FILL
                p.color = 0xFFE8EDF5.toInt()
                r.set(cx - rad, cy + rad * 0.1f, cx - rad * 0.55f, cy + rad * 0.85f)
                c.drawRect(r, p)
                r.set(cx + rad * 0.55f, cy + rad * 0.1f, cx + rad, cy + rad * 0.85f)
                c.drawRect(r, p)
            }
            Powerups.COIN_DOUBLER -> {
                coin(c, cx - rad * 0.32f, cy - rad * 0.1f, rad * 0.72f)
                coin(c, cx + rad * 0.36f, cy + rad * 0.2f, rad * 0.72f)
            }
            Powerups.LUCKY_PAWS -> {
                p.color = tint
                c.drawCircle(cx, cy + rad * 0.28f, rad * 0.55f, p)
                c.drawCircle(cx - rad * 0.6f, cy - rad * 0.32f, rad * 0.26f, p)
                c.drawCircle(cx - rad * 0.2f, cy - rad * 0.68f, rad * 0.26f, p)
                c.drawCircle(cx + rad * 0.3f, cy - rad * 0.62f, rad * 0.26f, p)
                c.drawCircle(cx + rad * 0.68f, cy - rad * 0.22f, rad * 0.24f, p)
            }
            Powerups.FEATHER_FALL -> {
                p.color = 0xFFE8EDF5.toInt()
                path.reset()
                path.moveTo(cx, cy - rad)
                path.cubicTo(cx + rad * 0.9f, cy - rad * 0.3f, cx + rad * 0.5f, cy + rad * 0.8f, cx, cy + rad)
                path.cubicTo(cx - rad * 0.5f, cy + rad * 0.8f, cx - rad * 0.9f, cy - rad * 0.3f, cx, cy - rad)
                path.close()
                c.drawPath(path, p)
                p.color = ColorX.withAlpha(tint, 0.9f)
                p.style = Paint.Style.STROKE
                p.strokeWidth = rad * 0.1f
                c.drawLine(cx, cy - rad, cx, cy + rad, p)
                p.style = Paint.Style.FILL
            }
            Powerups.SAFETY_NET -> {
                p.style = Paint.Style.STROKE
                p.strokeWidth = rad * 0.12f
                p.color = 0xFF7BE3A0.toInt()
                for (i in -2..2) {
                    c.drawLine(cx + i * rad * 0.4f, cy - rad * 0.7f, cx + i * rad * 0.4f, cy + rad * 0.7f, p)
                    c.drawLine(cx - rad * 0.9f, cy + i * rad * 0.32f, cx + rad * 0.9f, cy + i * rad * 0.32f, p)
                }
                p.style = Paint.Style.FILL
            }
            else -> { // head start
                p.color = tint
                arrowUp(c, cx, cy - rad * 0.2f, rad)
                p.color = ColorX.withAlpha(tint, 0.4f)
                arrowUp(c, cx, cy + rad * 0.85f, rad * 0.7f)
            }
        }
    }

    private fun coin(c: Canvas, cx: Float, cy: Float, rad: Float) {
        p.color = Theme.ACCENT_DEEP
        c.drawCircle(cx, cy, rad, p)
        p.color = Theme.ACCENT
        c.drawCircle(cx, cy, rad * 0.8f, p)
        p.color = Theme.ACCENT_DEEP
        c.drawCircle(cx, cy + rad * 0.16f, rad * 0.26f, p)
        c.drawCircle(cx - rad * 0.3f, cy - rad * 0.22f, rad * 0.13f, p)
        c.drawCircle(cx, cy - rad * 0.38f, rad * 0.13f, p)
        c.drawCircle(cx + rad * 0.3f, cy - rad * 0.22f, rad * 0.13f, p)
    }

    private fun arrowUp(c: Canvas, cx: Float, cy: Float, rad: Float) {
        path.reset()
        path.moveTo(cx, cy - rad)
        path.lineTo(cx + rad * 0.8f, cy)
        path.lineTo(cx + rad * 0.34f, cy)
        path.lineTo(cx + rad * 0.34f, cy + rad * 0.8f)
        path.lineTo(cx - rad * 0.34f, cy + rad * 0.8f)
        path.lineTo(cx - rad * 0.34f, cy)
        path.lineTo(cx - rad * 0.8f, cy)
        path.close()
        c.drawPath(path, p)
    }

    private fun rocket(c: Canvas, cx: Float, cy: Float, rad: Float) {
        p.color = 0xFFEFEFF4.toInt()
        r.set(cx - rad * 0.45f, cy - rad, cx + rad * 0.45f, cy + rad * 0.55f)
        c.drawRoundRect(r, rad * 0.45f, rad * 0.35f, p)
        p.color = 0xFFD8453B.toInt()
        path.reset()
        path.moveTo(cx - rad * 0.45f, cy - rad * 0.55f)
        path.lineTo(cx, cy - rad * 1.45f)
        path.lineTo(cx + rad * 0.45f, cy - rad * 0.55f)
        path.close()
        c.drawPath(path, p)
        p.color = 0xFF57C4E5.toInt()
        c.drawCircle(cx, cy - rad * 0.3f, rad * 0.2f, p)
        p.color = 0xFFFFB347.toInt()
        flame(c, cx, cy + rad * 0.55f, rad * 0.7f)
    }

    private fun flame(c: Canvas, cx: Float, cy: Float, size: Float) {
        path.reset()
        path.moveTo(cx - size * 0.45f, cy)
        path.quadTo(cx, cy + size * 1.7f, cx + size * 0.45f, cy)
        path.quadTo(cx, cy + size * 0.3f, cx - size * 0.45f, cy)
        path.close()
        c.drawPath(path, p)
    }
}
