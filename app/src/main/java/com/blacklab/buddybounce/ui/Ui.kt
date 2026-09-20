package com.blacklab.buddybounce.ui

import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import com.blacklab.buddybounce.game.MathX.approach
import com.blacklab.buddybounce.game.MathX.clamp01
import com.blacklab.buddybounce.render.Art
import com.blacklab.buddybounce.render.ColorX
import kotlin.math.sin

/** The game's visual language, in one place. Everything is in world units. */
object Theme {
    const val BG_DEEP = 0xFF0C101B.toInt()
    const val PANEL_SOLID = 0xFF1A2236.toInt()
    const val PANEL_RAISED = 0xFF232D46.toInt()
    const val PANEL_SUNK = 0xFF121828.toInt()
    const val ACCENT = 0xFFFFC24B.toInt()
    const val ACCENT_DEEP = 0xFFE09A22.toInt()
    const val GOOD = 0xFF7BE3A0.toInt()
    const val BAD = 0xFFE8595B.toInt()
    const val TEXT = 0xFFF3F6FB.toInt()
    const val TEXT_DIM = 0xFF9AA6BE.toInt()
    const val OUTLINE = 0x33FFFFFF

    const val RADIUS = 26f
    const val PAD = 28f
}

/**
 * A tiny immediate-mode UI toolkit drawn straight onto the game canvas, so the menus share the
 * game's look and resolution independence and the app needs no view hierarchy at all.
 */
class Ui(private val art: Art) {

    // ---- input state, fed by the host view ----
    var pointerX = 0f
    var pointerY = 0f
    var pointerDown = false
        private set
    private var justPressed = false
    private var justReleased = false
    private var pressedId = 0
    private var pressAnchorX = 0f
    private var pressAnchorY = 0f
    var scrollDrag = 0f
        private set

    /** Insets in world units so nothing lands under a notch or the gesture bar. */
    var safeTop = 0f
    var safeBottom = 0f
    var safeLeft = 0f
    var safeRight = 0f

    var time = 0f
        private set

    private val press = HashMap<Int, Float>()
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rect = RectF()
    private val path = Path()

    private val boldFace: Typeface = Typeface.create("sans-serif-black", Typeface.NORMAL)
    private val mediumFace: Typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)

    val title = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = boldFace
        textAlign = Paint.Align.CENTER
    }
    val body = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = mediumFace
        textAlign = Paint.Align.CENTER
    }
    val bodyLeft = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = mediumFace
        textAlign = Paint.Align.LEFT
    }
    val bodyRight = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = mediumFace
        textAlign = Paint.Align.RIGHT
    }
    val numbers = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = boldFace
        textAlign = Paint.Align.LEFT
    }

    // -------------------------------------------------------------------------------------
    // frame + input plumbing
    // -------------------------------------------------------------------------------------

    fun onDown(x: Float, y: Float) {
        pointerX = x; pointerY = y
        pointerDown = true
        justPressed = true
        pressAnchorX = x; pressAnchorY = y
        scrollDrag = 0f
    }

    fun onMove(x: Float, y: Float) {
        scrollDrag += y - pointerY
        pointerX = x; pointerY = y
    }

    fun onUp(x: Float, y: Float) {
        pointerX = x; pointerY = y
        pointerDown = false
        justReleased = true
    }

    fun beginFrame(dt: Float) {
        time += dt
        for (entry in press.entries) {
            val target = if (pressedId == entry.key) 1f else 0f
            entry.setValue(approach(entry.value, target, 16f, dt))
        }
    }

    fun endFrame() {
        justPressed = false
        justReleased = false
        if (!pointerDown) pressedId = 0
        scrollDrag = 0f
    }

    /** True on the frame the pointer is released inside [rect] having been pressed inside it. */
    private fun hit(id: Int, x: Float, y: Float, w: Float, h: Float): Boolean {
        val inside = pointerX >= x && pointerX <= x + w && pointerY >= y && pointerY <= y + h
        val anchorInside = pressAnchorX >= x && pressAnchorX <= x + w &&
            pressAnchorY >= y && pressAnchorY <= y + h
        if (justPressed && inside) {
            pressedId = id
            if (!press.containsKey(id)) press[id] = 0f
        }
        val clicked = justReleased && inside && anchorInside && pressedId == id
        if (clicked) pressedId = 0
        return clicked
    }

    private fun pressAmount(id: Int): Float = press[id] ?: 0f

    fun pointerInside(x: Float, y: Float, w: Float, h: Float): Boolean =
        pointerX >= x && pointerX <= x + w && pointerY >= y && pointerY <= y + h

    // -------------------------------------------------------------------------------------
    // primitives
    // -------------------------------------------------------------------------------------

    fun fill(c: Canvas, w: Float, h: Float, color: Int) {
        p.reset(); p.isAntiAlias = true
        p.color = color
        c.drawRect(0f, 0f, w, h, p)
    }

    fun panel(c: Canvas, x: Float, y: Float, w: Float, h: Float, color: Int = Theme.PANEL_SOLID, radius: Float = Theme.RADIUS, shadow: Boolean = true) {
        if (shadow) art.drawShadow(c, x + w * 0.5f, y + h * 0.5f + 16f, w * 1.06f, h * 1.18f, 0.5f)
        p.reset(); p.isAntiAlias = true
        p.color = color
        rect.set(x, y, x + w, y + h)
        c.drawRoundRect(rect, radius, radius, p)
        p.style = Paint.Style.STROKE
        p.strokeWidth = 2.5f
        p.color = Theme.OUTLINE
        rect.set(x + 1.2f, y + 1.2f, x + w - 1.2f, y + h - 1.2f)
        c.drawRoundRect(rect, radius, radius, p)
        p.style = Paint.Style.FILL
    }

    fun scrim(c: Canvas, w: Float, h: Float, alpha: Float) {
        p.reset(); p.isAntiAlias = true
        p.color = ColorX.withAlpha(0xFF060912.toInt(), alpha)
        c.drawRect(0f, 0f, w, h, p)
    }

    fun text(c: Canvas, s: String, x: Float, y: Float, size: Float, color: Int, paint: Paint = body, shadow: Boolean = true) {
        paint.textSize = size
        if (shadow) {
            paint.color = ColorX.withAlpha(0xFF000000.toInt(), 0.35f)
            c.drawText(s, x + size * 0.045f, y + size * 0.055f, paint)
        }
        paint.color = color
        c.drawText(s, x, y, paint)
    }

    fun measure(s: String, size: Float, paint: Paint = body): Float {
        paint.textSize = size
        return paint.measureText(s)
    }

    // -------------------------------------------------------------------------------------
    // widgets
    // -------------------------------------------------------------------------------------

    enum class ButtonStyle { PRIMARY, SECONDARY, GHOST, DANGER }

    fun button(
        c: Canvas, id: Int, x: Float, y: Float, w: Float, h: Float, label: String,
        style: ButtonStyle = ButtonStyle.SECONDARY, enabled: Boolean = true, sublabel: String? = null
    ): Boolean {
        val clicked = if (enabled) hit(id, x, y, w, h) else false
        val pr = pressAmount(id)
        val squish = pr * 5f

        val top: Int
        val bottom: Int
        val labelColor: Int
        when (style) {
            ButtonStyle.PRIMARY -> { top = Theme.ACCENT; bottom = Theme.ACCENT_DEEP; labelColor = 0xFF2A1D04.toInt() }
            ButtonStyle.SECONDARY -> { top = Theme.PANEL_RAISED; bottom = 0xFF182034.toInt(); labelColor = Theme.TEXT }
            ButtonStyle.DANGER -> { top = 0xFFE8595B.toInt(); bottom = 0xFFB8393C.toInt(); labelColor = 0xFF2B0B0C.toInt() }
            ButtonStyle.GHOST -> { top = 0x00000000; bottom = 0x00000000; labelColor = Theme.TEXT_DIM }
        }

        val a = if (enabled) 1f else 0.38f
        if (style != ButtonStyle.GHOST) {
            art.drawShadow(c, x + w * 0.5f, y + h + 4f - squish, w * 0.95f, h * 0.8f, 0.45f * a)
            p.reset(); p.isAntiAlias = true
            // solid "shelf" under the face gives the button thickness
            p.color = ColorX.withAlpha(ColorX.shade(bottom, 0.6f), a)
            rect.set(x, y + 8f, x + w, y + h)
            c.drawRoundRect(rect, h * 0.34f, h * 0.34f, p)
            p.shader = LinearGradient(
                x, y + squish, x, y + h - 8f + squish,
                ColorX.withAlpha(top, a), ColorX.withAlpha(bottom, a), Shader.TileMode.CLAMP
            )
            rect.set(x, y + squish, x + w, y + h - 8f + squish)
            c.drawRoundRect(rect, h * 0.34f, h * 0.34f, p)
            p.shader = null
            // gloss
            p.color = ColorX.withAlpha(0xFFFFFFFF.toInt(), 0.14f * a)
            rect.set(x + w * 0.06f, y + 7f + squish, x + w * 0.94f, y + h * 0.42f + squish)
            c.drawRoundRect(rect, h * 0.22f, h * 0.22f, p)
        }

        val cx = x + w * 0.5f
        val baseline = y + h * 0.5f + h * 0.17f + squish - (if (sublabel != null) h * 0.1f else 0f)
        text(c, label, cx, baseline, h * (if (sublabel != null) 0.34f else 0.40f),
            ColorX.withAlpha(labelColor, a), title, style != ButtonStyle.PRIMARY)
        if (sublabel != null) {
            text(c, sublabel, cx, baseline + h * 0.28f, h * 0.22f,
                ColorX.withAlpha(if (style == ButtonStyle.PRIMARY) 0xFF5A4110.toInt() else Theme.TEXT_DIM, a), body, false)
        }
        return clicked
    }

    /** Round icon button; [glyph] is drawn by the caller through [iconDraw]. */
    fun circleButton(c: Canvas, id: Int, cx: Float, cy: Float, radius: Float, color: Int = Theme.PANEL_RAISED): Boolean {
        val clicked = hit(id, cx - radius, cy - radius, radius * 2f, radius * 2f)
        val pr = pressAmount(id)
        art.drawShadow(c, cx, cy + 6f, radius * 2.2f, radius * 1.8f, 0.4f)
        p.reset(); p.isAntiAlias = true
        p.color = ColorX.shade(color, 0.65f)
        c.drawCircle(cx, cy + 5f, radius, p)
        p.color = color
        c.drawCircle(cx, cy + pr * 4f, radius, p)
        p.color = ColorX.withAlpha(0xFFFFFFFF.toInt(), 0.12f)
        c.drawCircle(cx, cy - radius * 0.28f + pr * 4f, radius * 0.72f, p)
        return clicked
    }

    fun backButton(c: Canvas, id: Int, cx: Float, cy: Float, radius: Float): Boolean {
        val clicked = circleButton(c, id, cx, cy, radius)
        p.reset(); p.isAntiAlias = true
        p.style = Paint.Style.STROKE
        p.strokeWidth = radius * 0.17f
        p.strokeCap = Paint.Cap.ROUND
        p.strokeJoin = Paint.Join.ROUND
        p.color = Theme.TEXT
        val pr = pressAmount(id) * 4f
        path.reset()
        path.moveTo(cx + radius * 0.22f, cy - radius * 0.34f + pr)
        path.lineTo(cx - radius * 0.24f, cy + pr)
        path.lineTo(cx + radius * 0.22f, cy + radius * 0.34f + pr)
        c.drawPath(path, p)
        p.style = Paint.Style.FILL
        return clicked
    }

    fun toggle(c: Canvas, id: Int, x: Float, y: Float, w: Float, h: Float, label: String, value: Boolean): Boolean {
        val clicked = hit(id, x, y, w, h)
        p.reset(); p.isAntiAlias = true
        p.color = Theme.PANEL_SUNK
        rect.set(x, y, x + w, y + h)
        c.drawRoundRect(rect, h * 0.34f, h * 0.34f, p)
        text(c, label, x + Theme.PAD, y + h * 0.5f + h * 0.14f, h * 0.36f, Theme.TEXT, bodyLeft, false)

        val knobW = h * 1.75f
        val trackX = x + w - Theme.PAD - knobW
        val trackY = y + h * 0.22f
        val trackH = h * 0.56f
        p.color = if (value) Theme.GOOD else 0xFF3A4462.toInt()
        rect.set(trackX, trackY, trackX + knobW, trackY + trackH)
        c.drawRoundRect(rect, trackH * 0.5f, trackH * 0.5f, p)
        p.color = 0xFFF3F6FB.toInt()
        val kx = if (value) trackX + knobW - trackH * 0.5f else trackX + trackH * 0.5f
        c.drawCircle(kx, trackY + trackH * 0.5f, trackH * 0.42f, p)
        return clicked
    }

    /** Horizontal slider. Returns the (possibly updated) value. */
    fun slider(c: Canvas, id: Int, x: Float, y: Float, w: Float, h: Float, label: String, value: Float): Float {
        var v = value
        val trackY = y + h * 0.66f
        val trackX = x + Theme.PAD
        val trackW = w - Theme.PAD * 2f
        val grabbed = pointerDown && (pressedId == id ||
            (justPressed && pointerInside(x, y, w, h)))
        if (grabbed) {
            pressedId = id
            if (!press.containsKey(id)) press[id] = 0f
            v = clamp01((pointerX - trackX) / trackW)
        }

        p.reset(); p.isAntiAlias = true
        p.color = Theme.PANEL_SUNK
        rect.set(x, y, x + w, y + h)
        c.drawRoundRect(rect, Theme.RADIUS * 0.7f, Theme.RADIUS * 0.7f, p)
        text(c, label, x + Theme.PAD, y + h * 0.34f, h * 0.30f, Theme.TEXT, bodyLeft, false)

        p.color = 0xFF3A4462.toInt()
        rect.set(trackX, trackY - 7f, trackX + trackW, trackY + 7f)
        c.drawRoundRect(rect, 7f, 7f, p)
        p.color = Theme.ACCENT
        rect.set(trackX, trackY - 7f, trackX + trackW * v, trackY + 7f)
        c.drawRoundRect(rect, 7f, 7f, p)
        p.color = 0xFFFFFFFF.toInt()
        c.drawCircle(trackX + trackW * v, trackY, h * 0.19f, p)
        p.color = Theme.ACCENT_DEEP
        c.drawCircle(trackX + trackW * v, trackY, h * 0.09f, p)
        return v
    }

    /** Segmented control. Returns the selected index. */
    fun segmented(c: Canvas, id: Int, x: Float, y: Float, w: Float, h: Float, options: Array<String>, selected: Int): Int {
        var result = selected
        p.reset(); p.isAntiAlias = true
        p.color = Theme.PANEL_SUNK
        rect.set(x, y, x + w, y + h)
        c.drawRoundRect(rect, h * 0.32f, h * 0.32f, p)

        val segW = w / options.size
        for (i in options.indices) {
            val sx = x + segW * i
            if (hit(id * 100 + i, sx, y, segW, h)) result = i
            if (i == selected) {
                p.color = Theme.ACCENT
                rect.set(sx + 6f, y + 6f, sx + segW - 6f, y + h - 6f)
                c.drawRoundRect(rect, h * 0.26f, h * 0.26f, p)
            }
            text(
                c, options[i], sx + segW * 0.5f, y + h * 0.5f + h * 0.13f, h * 0.33f,
                if (i == selected) 0xFF2A1D04.toInt() else Theme.TEXT_DIM, title, false
            )
        }
        return result
    }

    fun pill(c: Canvas, x: Float, y: Float, w: Float, h: Float, color: Int, alpha: Float = 1f) {
        p.reset(); p.isAntiAlias = true
        p.color = ColorX.withAlpha(color, alpha)
        rect.set(x, y, x + w, y + h)
        c.drawRoundRect(rect, h * 0.5f, h * 0.5f, p)
    }

    /** A subtle animated shine used on legendary cards and the coin counter. */
    fun shimmer(c: Canvas, x: Float, y: Float, w: Float, h: Float, radius: Float, strength: Float) {
        val t = (time * 0.35f) % 1.6f - 0.3f
        val sx = x + w * t
        p.reset(); p.isAntiAlias = true
        p.shader = LinearGradient(
            sx - w * 0.18f, y, sx + w * 0.18f, y + h,
            intArrayOf(0x00FFFFFF, ColorX.withAlpha(0xFFFFFFFF.toInt(), 0.22f * strength), 0x00FFFFFF),
            floatArrayOf(0f, 0.5f, 1f), Shader.TileMode.CLAMP
        )
        rect.set(x, y, x + w, y + h)
        c.drawRoundRect(rect, radius, radius, p)
        p.shader = null
    }

    fun bobbing(amount: Float, speed: Float, phase: Float = 0f): Float = sin(time * speed + phase) * amount
}
