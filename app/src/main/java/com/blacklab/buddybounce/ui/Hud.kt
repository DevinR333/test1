package com.blacklab.buddybounce.ui

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import com.blacklab.buddybounce.Game
import com.blacklab.buddybounce.data.Powerups
import com.blacklab.buddybounce.game.Flight
import com.blacklab.buddybounce.game.MathX.clamp01
import com.blacklab.buddybounce.game.Tuning
import com.blacklab.buddybounce.render.ColorX
import kotlin.math.abs
import kotlin.math.sin

/** Score, coins, power-up timers, the pause button and the touch steering indicator. */
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

    /** The only screen region that is NOT a steering surface. */
    fun isOverPauseButton(x: Float, y: Float): Boolean {
        val cx = g.worldW - g.ui.safeRight - 74f
        val cy = g.ui.safeTop + 74f
        val dx = x - cx
        val dy = y - cy
        return dx * dx + dy * dy < 104f * 104f
    }

    // -----------------------------------------------------------------------------------

    fun draw(c: Canvas, hintTimer: Float, biomeToast: Float, biomeName: String) {
        val ui = g.ui
        val left = ui.safeLeft + 34f
        val top = ui.safeTop + 30f

        if (g.controls.touchEnabled) drawTouchIndicator(c)

        ui.text(c, g.world.score.toString(), left, top + 74f, 84f, Theme.TEXT, ui.numbers)
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

        // coins picked up this run (they bank when the run ends)
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

        if (hintTimer > 0f) {
            val a = clamp01(hintTimer / 0.8f)
            val msg = when {
                g.controls.touchEnabled && g.controls.tiltEnabled -> "Tilt, or slide a finger anywhere"
                g.controls.touchEnabled -> "Slide a finger anywhere to steer"
                else -> "Tilt your phone to steer"
            }
            ui.text(
                c, msg, g.worldW * 0.5f, Theme.SCREEN_H * 0.72f, 40f,
                ColorX.withAlpha(Theme.TEXT, a * 0.85f), ui.body
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
        if (b.magnetTime > 0f || g.world.magnetForever) {
            val fill = if (g.world.magnetForever) 1f else b.magnetTime / Tuning.MAGNET_TIME
            timerPill(c, x, y + slot * 62f, "MAGNET", fill, 0xFFE8595B.toInt())
            slot++
        }
        if (g.world.safetyNets > 0) {
            timerPill(c, x, y + slot * 62f, "SAFETY NET", 1f, 0xFF7BE3A0.toInt())
            slot++
        }
        if (g.world.coinMultiplier > 1) {
            timerPill(c, x, y + slot * 62f, "COINS x${g.world.coinMultiplier}", 1f, Theme.ACCENT)
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
     * Relative steering feedback: the ring is where your finger went down (the centre), the
     * knob is where it is now. Slide either side of the ring to steer that way, anywhere on
     * the screen. Nothing is drawn until you touch.
     */
    private fun drawTouchIndicator(c: Canvas) {
        val ctl = g.controls
        if (!ctl.touchActive) {
            if (g.save.showGaugeAlways) {
                val pulse = 0.18f + 0.1f * sin(g.ui.time * 2.2f)
                g.ui.text(
                    c, "◀  slide anywhere  ▶", g.worldW * 0.5f,
                    Theme.SCREEN_H - g.ui.safeBottom - 54f, 28f,
                    ColorX.withAlpha(Theme.TEXT_DIM, pulse * 2.4f), g.ui.body, false
                )
            }
            return
        }

        val ax = ctl.touchAnchorX
        val ay = ctl.touchAnchorY
        val fx = ctl.touchX
        val value = ctl.displayValue()
        val range = ctl.touchRange

        p.reset(); p.isAntiAlias = true

        // the track you are sliding along, centred on the anchor
        p.color = ColorX.withAlpha(0xFF0B1220.toInt(), 0.35f)
        rect.set(ax - range, ay - 13f, ax + range, ay + 13f)
        c.drawRoundRect(rect, 13f, 13f, p)

        // deflection fill
        p.color = ColorX.withAlpha(Theme.ACCENT, 0.55f)
        rect.set(minOf(ax, fx), ay - 9f, maxOf(ax, fx), ay + 9f)
        c.drawRoundRect(rect, 9f, 9f, p)

        // anchor ring
        p.style = Paint.Style.STROKE
        p.strokeWidth = 5f
        p.color = ColorX.withAlpha(0xFFFFFFFF.toInt(), 0.45f)
        c.drawCircle(ax, ay, 26f, p)
        p.style = Paint.Style.FILL
        p.color = ColorX.withAlpha(0xFFFFFFFF.toInt(), 0.18f)
        c.drawCircle(ax, ay, 26f, p)

        // knob under the finger
        val knobR = 34f
        g.art.drawGlow(c, fx, ay, knobR * 2.6f, Theme.ACCENT, 0.45f)
        p.color = 0xFFF3F6FB.toInt()
        c.drawCircle(fx, ay, knobR, p)
        p.color = Theme.ACCENT
        c.drawCircle(fx, ay, knobR * 0.55f, p)

        // direction chevron once you are actually steering
        if (abs(value) > 0.08f) {
            p.style = Paint.Style.STROKE
            p.strokeWidth = 6f
            p.strokeCap = Paint.Cap.ROUND
            p.color = ColorX.withAlpha(0xFF2A1D04.toInt(), 0.9f)
            val dir = if (value > 0f) 1f else -1f
            c.drawLine(fx - 6f * dir, ay - 11f, fx + 7f * dir, ay, p)
            c.drawLine(fx + 7f * dir, ay, fx - 6f * dir, ay + 11f, p)
            p.style = Paint.Style.FILL
        }
    }

    // ---- pause -------------------------------------------------------------------------

    fun drawPause(c: Canvas) {
        val ui = g.ui
        ui.scrim(c, g.worldW, Theme.SCREEN_H, 0.66f)
        val w = (g.worldW * 0.8f).coerceAtMost(700f)
        val h = 720f
        val x = (g.worldW - w) * 0.5f
        val y = (Theme.SCREEN_H - h) * 0.5f
        ui.panel(c, x, y, w, h)
        ui.text(c, "PAUSED", x + w * 0.5f, y + 96f, 68f, Theme.TEXT, ui.title)

        // Banked total first - that is the number that actually belongs to the player.
        val purse = g.save.coins
        val pillW = w - 96f
        ui.pill(c, x + 48f, y + 126f, pillW, 84f, 0xFF121828.toInt())
        coinIcon(c, x + 92f, y + 168f, 24f)
        ui.text(c, purse.toString(), x + 126f, y + 182f, 48f, Theme.ACCENT, ui.bodyLeft, false)
        ui.text(c, "COINS IN THE BANK", x + w - 72f, y + 178f, 26f, Theme.TEXT_DIM, ui.bodyRight, false)

        ui.text(
            c, "This run: ${g.world.score} pts, ${g.world.runCoins} coins picked up",
            x + w * 0.5f, y + 244f, 28f, Theme.TEXT_DIM, ui.body, false
        )
        ui.text(
            c, "run coins are banked when the run ends",
            x + w * 0.5f, y + 280f, 24f, ColorX.withAlpha(Theme.TEXT_DIM, 0.75f), ui.body, false
        )

        val bw = w - 96f
        val bx = x + 48f
        var by = y + 316f
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
