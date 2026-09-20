package com.blacklab.buddybounce.ui

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import com.blacklab.buddybounce.Game
import com.blacklab.buddybounce.game.Flight
import com.blacklab.buddybounce.game.MathX.clamp01
import com.blacklab.buddybounce.game.Tuning
import com.blacklab.buddybounce.render.ColorX
import kotlin.math.abs
import kotlin.math.sin

/** Score, coins, power-up timers, the pause button and the touch steering gauge. */
class Hud(private val g: Game) {

    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rect = RectF()

    private object Id {
        const val PAUSE = 1001
        const val RESUME = 1002
        const val RESTART = 1003
        const val SETTINGS = 1004
        const val QUIT = 1005
    }

    // ---- gauge geometry, shared with Game's touch routing ------------------------------

    fun gaugeTrackY(): Float = Tuning.VIEW_H - g.ui.safeBottom - 96f
    fun gaugeHalfWidth(): Float =
        ((g.worldW - g.ui.safeLeft - g.ui.safeRight) * 0.5f - 86f).coerceAtLeast(120f)
    fun gaugeCentreX(): Float = g.ui.safeLeft + (g.worldW - g.ui.safeLeft - g.ui.safeRight) * 0.5f
    /** Touches below this line steer instead of doing anything else. */
    fun gaugeBandTop(): Float = gaugeTrackY() - 190f

    fun isOverPauseButton(x: Float, y: Float): Boolean {
        val cx = g.worldW - g.ui.safeRight - 74f
        val cy = g.ui.safeTop + 74f
        val dx = x - cx
        val dy = y - cy
        return dx * dx + dy * dy < 96f * 96f
    }

    // -----------------------------------------------------------------------------------

    fun draw(c: Canvas, hintTimer: Float, biomeToast: Float, biomeName: String) {
        val ui = g.ui
        val left = ui.safeLeft + 34f
        val top = ui.safeTop + 30f

        // score
        val score = g.world.score
        ui.text(c, score.toString(), left, top + 74f, 84f, Theme.TEXT, ui.numbers)
        val best = g.save.bestScore
        if (best > 0) {
            ui.text(c, "BEST $best", left + 4f, top + 116f, 32f, ColorX.withAlpha(Theme.TEXT_DIM, 0.95f), ui.bodyLeft, false)
        }

        // pause
        val pcx = g.worldW - ui.safeRight - 74f
        val pcy = ui.safeTop + 74f
        if (ui.circleButton(c, Id.PAUSE, pcx, pcy, 52f)) {
            g.tap(); g.goto(Game.Screen.PAUSE)
        }
        p.reset(); p.isAntiAlias = true
        p.color = Theme.TEXT
        rect.set(pcx - 17f, pcy - 19f, pcx - 5f, pcy + 19f)
        c.drawRoundRect(rect, 5f, 5f, p)
        rect.set(pcx + 5f, pcy - 19f, pcx + 17f, pcy + 19f)
        c.drawRoundRect(rect, 5f, 5f, p)

        // coins earned this run
        val coinText = g.world.runCoins.toString()
        val cw = ui.measure(coinText, 40f, ui.bodyLeft) + 104f
        val cx = g.worldW - ui.safeRight - cw - 18f
        val cy = pcy + 74f
        ui.pill(c, cx, cy, cw, 58f, 0xCC101728.toInt())
        coinIcon(c, cx + 34f, cy + 29f, 20f)
        ui.text(c, coinText, cx + 62f, cy + 42f, 40f, Theme.ACCENT, ui.bodyLeft, false)

        drawPowerBar(c, left, top + 148f)

        if (biomeToast > 0f) {
            val a = clamp01(biomeToast / 0.6f) * clamp01((2.6f - biomeToast) / 0.35f)
            val y = ui.safeTop + 210f
            ui.text(c, biomeName.uppercase(), g.worldW * 0.5f, y, 62f, ColorX.withAlpha(Theme.TEXT, a), ui.title)
            ui.text(c, "NEW HEIGHTS", g.worldW * 0.5f, y + 42f, 28f, ColorX.withAlpha(Theme.ACCENT, a * 0.9f), ui.body, false)
        }

        if (g.controls.touchEnabled && (g.save.showGaugeAlways || g.controls.gaugeActive)) {
            drawGauge(c)
        }

        if (hintTimer > 0f) {
            val a = clamp01(hintTimer / 0.8f)
            val msg = when {
                g.controls.touchEnabled && g.controls.tiltEnabled -> "Tilt your phone, or slide the bar"
                g.controls.touchEnabled -> "Slide along the bar to steer"
                else -> "Tilt your phone to steer"
            }
            ui.text(
                c, msg, g.worldW * 0.5f, Tuning.VIEW_H * 0.60f, 40f,
                ColorX.withAlpha(Theme.TEXT, a * 0.85f), g.ui.body
            )
        }
    }

    private fun drawPowerBar(c: Canvas, x: Float, y: Float) {
        val b = g.world.buddy
        var slot = 0
        if (b.flying) {
            val total = when (b.flight) {
                Flight.PROPELLER -> Tuning.PROPELLER_TIME
                Flight.JETPACK -> Tuning.JETPACK_TIME
                else -> Tuning.ROCKET_TIME
            }
            val label = when (b.flight) {
                Flight.PROPELLER -> "PROPELLER"
                Flight.JETPACK -> "JETPACK"
                else -> "ROCKET"
            }
            timerPill(c, x, y + slot * 62f, label, b.flightTime / total, 0xFFFFB347.toInt())
            slot++
        }
        if (b.shieldTime > 0f) {
            timerPill(c, x, y + slot * 62f, "SHIELD", b.shieldTime / Tuning.SHIELD_TIME, 0xFF8FD3F4.toInt())
            slot++
        }
        if (b.magnetTime > 0f) {
            timerPill(c, x, y + slot * 62f, "MAGNET", b.magnetTime / Tuning.MAGNET_TIME, 0xFFE8595B.toInt())
        }
    }

    private fun timerPill(c: Canvas, x: Float, y: Float, label: String, fill: Float, color: Int) {
        val w = 268f
        val h = 48f
        g.ui.pill(c, x, y, w, h, 0xCC101728.toInt())
        p.reset(); p.isAntiAlias = true
        p.color = ColorX.withAlpha(color, 0.9f)
        rect.set(x + 8f, y + 8f, x + 8f + (w - 16f) * clamp01(fill), y + h - 8f)
        c.drawRoundRect(rect, (h - 16f) * 0.5f, (h - 16f) * 0.5f, p)
        g.ui.text(c, label, x + w * 0.5f, y + h * 0.5f + 10f, 26f, 0xFF10151F.toInt(), g.ui.title, false)
    }

    private fun coinIcon(c: Canvas, cx: Float, cy: Float, r: Float) {
        p.reset(); p.isAntiAlias = true
        p.color = Theme.ACCENT_DEEP
        c.drawCircle(cx, cy, r, p)
        p.color = Theme.ACCENT
        c.drawCircle(cx, cy, r * 0.82f, p)
        p.color = Theme.ACCENT_DEEP
        c.drawCircle(cx, cy + r * 0.16f, r * 0.28f, p)
        c.drawCircle(cx - r * 0.3f, cy - r * 0.22f, r * 0.14f, p)
        c.drawCircle(cx, cy - r * 0.38f, r * 0.14f, p)
        c.drawCircle(cx + r * 0.3f, cy - r * 0.22f, r * 0.14f, p)
    }

    /**
     * The steering gauge: an absolute track - wherever your finger sits along it is where
     * Buddy steers, like a slider rather than a d-pad.
     */
    private fun drawGauge(c: Canvas) {
        val ui = g.ui
        val cx = gaugeCentreX()
        val y = gaugeTrackY()
        val half = gaugeHalfWidth()
        val value = g.controls.displayValue()
        val active = g.controls.gaugeActive

        val trackH = 22f
        p.reset(); p.isAntiAlias = true

        // track
        p.color = ColorX.withAlpha(0xFF0B1220.toInt(), if (active) 0.75f else 0.5f)
        rect.set(cx - half - 26f, y - trackH, cx + half + 26f, y + trackH)
        c.drawRoundRect(rect, trackH, trackH, p)

        // ticks
        p.color = ColorX.withAlpha(0xFFFFFFFF.toInt(), 0.18f)
        for (i in -4..4) {
            if (i == 0) continue
            val tx = cx + half * (i / 4f)
            val hh = if (i % 2 == 0) 9f else 5f
            rect.set(tx - 2f, y - hh, tx + 2f, y + hh)
            c.drawRoundRect(rect, 2f, 2f, p)
        }
        // centre notch
        p.color = ColorX.withAlpha(0xFFFFFFFF.toInt(), 0.35f)
        rect.set(cx - 2.5f, y - 15f, cx + 2.5f, y + 15f)
        c.drawRoundRect(rect, 2.5f, 2.5f, p)

        // fill from centre toward the knob
        val knobX = cx + half * value
        p.color = ColorX.withAlpha(Theme.ACCENT, if (active) 0.9f else 0.55f)
        rect.set(minOf(cx, knobX), y - 9f, maxOf(cx, knobX), y + 9f)
        c.drawRoundRect(rect, 9f, 9f, p)

        // knob
        val knobR = if (active) 32f else 26f
        g.art.drawGlow(c, knobX, y, knobR * 2.6f, Theme.ACCENT, if (active) 0.5f else 0.25f)
        p.color = 0xFFF3F6FB.toInt()
        c.drawCircle(knobX, y, knobR, p)
        p.color = Theme.ACCENT
        c.drawCircle(knobX, y, knobR * 0.55f, p)
        if (abs(value) > 0.04f) {
            p.color = 0xFF2A1D04.toInt()
            val dir = if (value > 0f) 1f else -1f
            val tri = knobR * 0.3f
            c.drawCircle(knobX + dir * knobR * 0.02f, y, tri * 0.6f, p)
        }

        // end chevrons
        p.style = Paint.Style.STROKE
        p.strokeWidth = 6f
        p.strokeCap = Paint.Cap.ROUND
        p.color = ColorX.withAlpha(0xFFFFFFFF.toInt(), 0.35f)
        chevron(c, cx - half - 52f, y, -1f)
        chevron(c, cx + half + 52f, y, 1f)
        p.style = Paint.Style.FILL

        if (!active) {
            val pulse = 0.35f + 0.25f * sin(ui.time * 2.4f)
            ui.text(
                c, "SLIDE TO STEER", cx, y + 62f, 24f,
                ColorX.withAlpha(Theme.TEXT_DIM, pulse), ui.body, false
            )
        }
    }

    private fun chevron(c: Canvas, x: Float, y: Float, dir: Float) {
        c.drawLine(x - 9f * dir, y - 13f, x + 5f * dir, y, p)
        c.drawLine(x + 5f * dir, y, x - 9f * dir, y + 13f, p)
    }

    // ---- pause -------------------------------------------------------------------------

    fun drawPause(c: Canvas) {
        val ui = g.ui
        ui.scrim(c, g.worldW, Tuning.VIEW_H, 0.66f)
        val w = (g.worldW * 0.8f).coerceAtMost(700f)
        val h = 640f
        val x = (g.worldW - w) * 0.5f
        val y = (Tuning.VIEW_H - h) * 0.5f
        ui.panel(c, x, y, w, h)
        ui.text(c, "PAUSED", x + w * 0.5f, y + 108f, 72f, Theme.TEXT, ui.title)
        ui.text(
            c, "Score ${g.world.score}   •   ${g.world.runCoins} coins",
            x + w * 0.5f, y + 158f, 32f, Theme.TEXT_DIM, ui.body, false
        )

        val bw = w - 96f
        val bx = x + 48f
        var by = y + 210f
        if (ui.button(c, Id.RESUME, bx, by, bw, 100f, "RESUME", Ui.ButtonStyle.PRIMARY)) {
            g.tap(); g.goto(Game.Screen.PLAY)
        }
        by += 116f
        if (ui.button(c, Id.RESTART, bx, by, bw, 92f, "RESTART")) {
            g.tap(); g.startRun()
        }
        by += 108f
        if (ui.button(c, Id.SETTINGS, bx, by, bw, 92f, "SETTINGS")) {
            g.tap(); g.goto(Game.Screen.SETTINGS)
        }
        by += 108f
        if (ui.button(c, Id.QUIT, bx, by, bw, 92f, "QUIT TO MENU", Ui.ButtonStyle.GHOST)) {
            g.tap(); g.goto(Game.Screen.MENU)
        }
    }
}
