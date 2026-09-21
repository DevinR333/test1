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
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/**
 * The game's visual language, in one place.
 *
 * The UI is laid out in its own 1600-unit-tall space ([SCREEN_H]) that always maps to the full
 * screen height. The world is drawn in a taller 2560-unit space (Tuning.VIEW_H) inside that,
 * which is what zooms the camera out without shrinking the menus.
 */
object Theme {
    /** Height of the UI coordinate space. Width is SCREEN_H * aspect. */
    const val SCREEN_H = 1600f

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

    // ---- gamepad focus ---------------------------------------------------------------------
    //
    // These menus were built for a finger, so there is no view hierarchy and no focus order to
    // inherit - every widget is just a rectangle drawn this frame. Focus is therefore rebuilt
    // from scratch each frame out of the rectangles the screen happens to draw, and a d-pad
    // press moves it to the nearest rectangle in that direction. That keeps every screen
    // controller-navigable without any of them knowing this exists.
    //
    // Navigation resolves against the PREVIOUS frame's rectangles, because the current frame's
    // are still being collected while the screen draws. Layout is stable frame to frame, so the
    // one-frame lag is invisible.

    private class Focusable(var id: Int, var x: Float, var y: Float, var w: Float, var h: Float)

    private val focusNow = ArrayList<Focusable>()
    private val focusPrev = ArrayList<Focusable>()
    private var focusPool = ArrayList<Focusable>()

    /** The widget a controller is pointing at, or 0 for none. */
    var focusId = 0
        private set

    /** True once a pad or key has been used. Until then no focus ring is drawn at all. */
    var padActive = false
        private set

    private var pendingNavX = 0
    private var pendingNavY = 0
    private var pendingActivate = false
    private var focusX = 0f
    private var focusY = 0f
    private var focusW = 0f
    private var focusH = 0f
    private var focusSeen = false

    /** Queued by the host on a d-pad press or a stick flick. */
    fun navigate(dx: Int, dy: Int) {
        padActive = true
        if (dx != 0) pendingNavX = dx
        if (dy != 0) pendingNavY = dy
    }

    /** Queued by the host on the confirm button. Spent by whichever widget holds focus. */
    fun activateFocused() {
        padActive = true
        pendingActivate = true
    }

    /** Where the focused widget was drawn, for [drawFocusRing]. */
    fun hasFocusRect(): Boolean = padActive && focusSeen && focusId != 0

    /**
     * Registers a widget for focus WITHOUT hit-testing it.
     *
     * Scrolling lists cull the rows they are not drawing, which would otherwise make them
     * unreachable by controller - focus can only land on rectangles it has been told about, so
     * the ring would stop dead at the bottom of the viewport. Culled rows register through here
     * instead, and the screen scrolls the focused one back into view.
     */
    fun focusOnly(id: Int, x: Float, y: Float, w: Float, h: Float) {
        registerFocusable(id, x, y, w, h)
    }

    private fun registerFocusable(id: Int, x: Float, y: Float, w: Float, h: Float) {
        val f = if (focusPool.isEmpty()) {
            Focusable(id, x, y, w, h)
        } else {
            focusPool.removeAt(focusPool.size - 1).also {
                it.id = id; it.x = x; it.y = y; it.w = w; it.h = h
            }
        }
        focusNow.add(f)
        if (id == focusId) {
            focusX = x; focusY = y; focusW = w; focusH = h
            focusSeen = true
        }
    }

    /**
     * Moves focus to the nearest widget in the given direction.
     *
     * "Nearest" weights travel ALONG the axis far more heavily than drift across it, so a d-pad
     * right lands on the button beside you rather than one that happens to be marginally closer
     * diagonally. With nothing focused, or nothing in that direction, it falls back to the first
     * widget on the screen - pressing a direction should always put the ring somewhere.
     */
    private fun moveFocus(dx: Int, dy: Int) {
        if (focusPrev.isEmpty()) return
        val from = focusPrev.firstOrNull { it.id == focusId }
        if (from == null) {
            focusId = focusPrev[0].id
            return
        }
        val fx = from.x + from.w * 0.5f
        val fy = from.y + from.h * 0.5f
        var best: Focusable? = null
        var bestScore = Float.MAX_VALUE
        for (f in focusPrev) {
            if (f.id == focusId) continue
            val cx = f.x + f.w * 0.5f
            val cy = f.y + f.h * 0.5f
            val along = (cx - fx) * dx + (cy - fy) * dy
            if (along <= 1f) continue                       // not in that direction
            val across = if (dx != 0) abs(cy - fy) else abs(cx - fx)
            // overlapping on the cross axis counts as perfectly in line
            val overlap = if (dx != 0) {
                max(0f, min(f.y + f.h, from.y + from.h) - max(f.y, from.y))
            } else {
                max(0f, min(f.x + f.w, from.x + from.w) - max(f.x, from.x))
            }
            val penalty = if (overlap > 4f) 0f else across * 2.2f
            val score = along + penalty
            if (score < bestScore) { bestScore = score; best = f }
        }
        if (best != null) focusId = best.id
    }

    /**
     * Width of the screen in UI units. [text] uses it as a backstop so a string can never run
     * off the edge of the display, whatever the device's aspect ratio - the caller passing an
     * explicit maxWidth is still the better fix inside a panel, but nothing should ever fall
     * off the screen just because nobody remembered to.
     */
    var screenW = 0f

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

    /** Seconds in the frame being drawn. Screens with their own animation need it. */
    var frameDt = 0f
        private set

    fun beginFrame(dt: Float) {
        frameDt = dt
        time += dt
        // resolve navigation against last frame's rectangles, then start collecting this one's
        if (pendingNavX != 0) { moveFocus(pendingNavX, 0); pendingNavX = 0 }
        if (pendingNavY != 0) { moveFocus(0, pendingNavY); pendingNavY = 0 }
        focusPool.addAll(focusNow)
        focusNow.clear()
        focusSeen = false
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
        inputClipOn = false      // never let a screen's clip leak into the next frame
        // An activate nobody claimed is dropped rather than carried into the next screen, where
        // it would fire whatever happened to inherit the id.
        pendingActivate = false
        focusPool.addAll(focusPrev)
        focusPrev.clear()
        focusPrev.addAll(focusNow)
        // A screen change can leave focus on an id nothing draws any more; snap it back.
        if (focusId != 0 && focusPrev.none { it.id == focusId }) {
            focusId = if (focusPrev.isEmpty()) 0 else focusPrev[0].id
        }
    }

    /**
     * The ring around whatever the controller is pointing at. Drawn after the screen, so it is
     * never buried under a panel, and only once a pad has actually been used - a touch player
     * should never see it.
     */
    fun drawFocusRing(c: Canvas) {
        if (!hasFocusRect()) return
        val pulse = 0.55f + sin(time * 4.2f) * 0.2f
        p.reset(); p.isAntiAlias = true
        p.style = Paint.Style.STROKE
        p.strokeWidth = 5f
        p.color = ColorX.withAlpha(Theme.ACCENT, pulse)
        rect.set(focusX - 6f, focusY - 6f, focusX + focusW + 6f, focusY + focusH + 6f)
        c.drawRoundRect(rect, Theme.RADIUS * 0.6f + 6f, Theme.RADIUS * 0.6f + 6f, p)
        p.strokeWidth = 2f
        p.color = ColorX.withAlpha(0xFFFFFFFF.toInt(), pulse * 0.5f)
        rect.set(focusX - 10f, focusY - 10f, focusX + focusW + 10f, focusY + focusH + 10f)
        c.drawRoundRect(rect, Theme.RADIUS * 0.6f + 10f, Theme.RADIUS * 0.6f + 10f, p)
        p.style = Paint.Style.FILL
    }

    // ---- input clipping -------------------------------------------------------------------
    //
    // Canvas.clipRect stops a widget being DRAWN outside a scrolling viewport. It does nothing
    // about where that widget reports taps. A card scrolled up behind the back button was
    // therefore invisible and still live, and because a list is processed after the button, the
    // later widget won the press - so the back button was unclickable whenever a card sat under
    // it. Any screen that clips a scrolling list must clip its input the same way.

    private var inputClipOn = false
    private var icX0 = 0f
    private var icY0 = 0f
    private var icX1 = 0f
    private var icY1 = 0f

    /** Restricts hit-testing to this rect until [clearInputClip]. Pair it with the canvas clip. */
    fun setInputClip(x0: Float, y0: Float, x1: Float, y1: Float) {
        inputClipOn = true
        icX0 = x0; icY0 = y0; icX1 = x1; icY1 = y1
    }

    fun clearInputClip() {
        inputClipOn = false
    }

    private fun inInputClip(x: Float, y: Float): Boolean =
        !inputClipOn || (x >= icX0 && x <= icX1 && y >= icY0 && y <= icY1)

    /** True on the frame the pointer is released inside [rect] having been pressed inside it. */
    private fun hit(id: Int, x: Float, y: Float, w: Float, h: Float): Boolean {
        // Focus is collected from the same rectangles the pointer uses, so a widget is
        // controller-reachable for free the moment it is drawn.
        registerFocusable(id, x, y, w, h)
        if (pendingActivate && focusId == id) {
            pendingActivate = false
            return true
        }
        val inside = pointerX >= x && pointerX <= x + w && pointerY >= y && pointerY <= y + h &&
            inInputClip(pointerX, pointerY)
        val anchorInside = pressAnchorX >= x && pressAnchorX <= x + w &&
            pressAnchorY >= y && pressAnchorY <= y + h &&
            inInputClip(pressAnchorX, pressAnchorY)
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

    /**
     * Draws a string, shrinking it to fit if it would otherwise overrun.
     *
     * [maxWidth] is the box the string has to live in - a panel's inner width, usually. When it
     * is left at 0 the screen (minus the safe insets and a gutter) is used instead, which is a
     * backstop, not a substitute for passing the real width.
     */
    /**
     * The width a CENTRED header may occupy without reaching the controls that flank it.
     *
     * Every screen puts its title in the middle of the top bar, with a back button pinned left
     * and sometimes a chip pinned right. A long title on a narrow phone grew until it ran under
     * them. Measuring symmetrically from the centre means the box is safe whichever side has
     * something in it, and the title shrinks instead of colliding.
     */
    fun headerWidth(worldW: Float, sideInset: Float = 150f): Float =
        (worldW - 2f * (max(safeLeft, safeRight) + sideInset)).coerceAtLeast(180f)

    fun text(
        c: Canvas, s: String, x: Float, y: Float, size: Float, color: Int,
        paint: Paint = body, shadow: Boolean = true, maxWidth: Float = 0f
    ) {
        val size2 = fitSize(s, size, paint, maxWidth)
        // Shrinking alone is not a guarantee: past the floor a long string would still run out
        // of its box, so anything that cannot be made to fit is cut and elided instead.
        val box = boxFor(maxWidth)
        val str = if (box > 0f && measure(s, size2, paint) > box) {
            elide(s, size2, paint, box)
        } else {
            s
        }
        paint.textSize = size2
        if (shadow) {
            paint.color = ColorX.withAlpha(0xFF000000.toInt(), 0.35f)
            c.drawText(str, x + size2 * 0.045f, y + size2 * 0.055f, paint)
        }
        paint.color = color
        c.drawText(str, x, y, paint)
    }

    /**
     * The largest size at or below [size] that fits [s] into [limit]. Never goes below 55 % of
     * the requested size: past that it is better to let a pathological string clip than to draw
     * something nobody can read.
     */
    /** The box a string must fit in: an explicit limit, else the whole usable screen width. */
    private fun boxFor(limit: Float): Float = when {
        limit > 0f -> limit
        screenW > 0f -> screenW - safeLeft - safeRight - 32f
        else -> 0f
    }

    fun fitSize(s: String, size: Float, paint: Paint = body, limit: Float = 0f): Float {
        val box = boxFor(limit)
        if (box <= 0f || s.isEmpty()) return size
        // Shrink further than the old 55% before giving up - a heading that is small is a
        // cosmetic problem, a heading that runs off the display is a broken one.
        val floor = size * 0.42f
        var sz = size
        while (sz > floor && measure(s, sz, paint) > box) sz -= 1f
        return sz
    }

    /** Trims a string until it fits, ending it in an ellipsis. */
    private fun elide(s: String, size: Float, paint: Paint, box: Float): String {
        if (s.isEmpty()) return s
        var end = s.length
        while (end > 1) {
            val candidate = s.substring(0, end).trimEnd() + "\u2026"
            if (measure(candidate, size, paint) <= box) return candidate
            end--
        }
        return "\u2026"
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
        // Labels are fitted to the button, not the screen: a long sublabel like
        // "12 fits - 7 trails" has to stay inside its own pill, not just on the display.
        val inner = w - h * 0.42f
        text(c, label, cx, baseline, h * (if (sublabel != null) 0.34f else 0.40f),
            ColorX.withAlpha(labelColor, a), title, style != ButtonStyle.PRIMARY, inner)
        if (sublabel != null) {
            text(c, sublabel, cx, baseline + h * 0.28f, h * 0.22f,
                ColorX.withAlpha(if (style == ButtonStyle.PRIMARY) 0xFF5A4110.toInt() else Theme.TEXT_DIM, a),
                body, false, inner)
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
