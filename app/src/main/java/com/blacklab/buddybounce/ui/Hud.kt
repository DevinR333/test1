package com.blacklab.buddybounce.ui

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import com.blacklab.buddybounce.Game
import com.blacklab.buddybounce.game.Flight
import com.blacklab.buddybounce.game.MathX.clamp01
import com.blacklab.buddybounce.game.Tuning
import com.blacklab.buddybounce.render.ColorX
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

    fun draw(c: Canvas, biomeToast: Float, biomeName: String) {
        val ui = g.ui
        val left = ui.safeLeft + 34f
        val top = ui.safeTop + 30f

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

        // Everything this run is worth so far: the coins picked up PLUS the height bonus earned
        // so far. The bonus used to be invisible until the run ended, which made the "+N" flash
        // below meaningless - there was no number for it to be an increment of.
        val runTotal = g.world.runCoins + g.world.heightBonusCoins
        val coinText = runTotal.toString()
        val cw = ui.measure(coinText, 40f, ui.bodyLeft) + 104f
        val cx = g.worldW - ui.safeRight - cw - 18f
        val cy = pcy + 74f
        ui.pill(c, cx, cy, cw, 58f, 0xCC101728.toInt())
        coinIcon(c, cx + 34f, cy + 29f, 20f)
        ui.text(c, coinText, cx + 62f, cy + 42f, 40f, Theme.ACCENT, ui.bodyLeft, false)

        drawCoinFlash(c, cx + cw * 0.5f, cy)

        drawPowerBar(c, left, top + 148f)

        if (biomeToast > 0f) {
            val a = clamp01(biomeToast / 0.6f) * clamp01((2.6f - biomeToast) / 0.35f)
            val y = ui.safeTop + 210f
            ui.text(c, biomeName.uppercase(), g.worldW * 0.5f, y, 62f, ColorX.withAlpha(Theme.TEXT, a), ui.title)
            ui.text(c, "NEW HEIGHTS", g.worldW * 0.5f, y + 42f, 28f, ColorX.withAlpha(Theme.ACCENT, a * 0.9f), ui.body, false)
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
     * The "+N" that pops over the coin counter when a height threshold pays out. Rises a little
     * and fades quickly - it is a receipt, not a notification.
     */
    private fun drawCoinFlash(c: Canvas, cx: Float, pillTop: Float) {
        val t = g.coinFlashT
        if (t <= 0f) return
        val k = clamp01(t / Game.COIN_FLASH_TIME)
        val rise = (1f - k) * 46f
        // a quick overshoot on arrival, so it reads as a hit rather than a fade-in
        val pop = if (k > 0.86f) 1f + (k - 0.86f) * 2.6f else 1f
        g.ui.text(
            c, "+" + g.coinFlash, cx, pillTop - 14f - rise, 42f * pop,
            ColorX.withAlpha(Theme.ACCENT, clamp01(k * 1.6f)), g.ui.title, true, 220f
        )
    }

    // ---- pause -------------------------------------------------------------------------

    fun drawPause(c: Canvas) {
        val ui = g.ui
        ui.scrim(c, g.worldW, Theme.SCREEN_H, 0.66f)

        // The panel is sized from what is IN it, then shrunk to fit the room it has. The old
        // version hard-coded 720 units of height for 840 units of content, so the bottom two
        // buttons hung outside the panel; and it never looked at the safe insets at all, so a
        // squarer screen or a tall gesture bar pushed the last one off. Deriving the height and
        // scaling everything by one factor means it fits any display without cropping and
        // without going squat.
        val headerH = 300f
        val btnH = floatArrayOf(100f, 92f, 92f, 92f)
        val gap = 16f
        val padBottom = 40f
        var natural = headerH + padBottom
        for (b in btnH) natural += b + gap

        val availH = Theme.SCREEN_H - ui.safeTop - ui.safeBottom - 60f
        val k = (availH / natural).coerceAtMost(1f)
        val h = natural * k
        val w = (g.worldW - ui.safeLeft - ui.safeRight - 80f).coerceIn(360f, 700f)
        val x = (g.worldW - w) * 0.5f
        val y = ui.safeTop + (availH - h) * 0.5f + 30f

        ui.panel(c, x, y, w, h)
        ui.text(c, "PAUSED", x + w * 0.5f, y + 96f * k, 68f * k, Theme.TEXT, ui.title, true, w - 60f)

        // Banked total first - that is the number that actually belongs to the player.
        val purse = g.save.coins
        val pillW = w - 96f
        ui.pill(c, x + 48f, y + 126f * k, pillW, 84f * k, 0xFF121828.toInt())
        coinIcon(c, x + 92f, y + 168f * k, 24f * k)
        ui.text(c, purse.toString(), x + 126f, y + 182f * k, 48f * k, Theme.ACCENT, ui.bodyLeft, false, pillW * 0.4f)
        ui.text(
            c, "COINS IN THE BANK", x + w - 72f, y + 178f * k, 26f * k, Theme.TEXT_DIM, ui.bodyRight, false,
            pillW * 0.55f
        )

        ui.text(
            c, "This run: ${g.world.score} pts, ${g.world.runCoins} coins picked up",
            x + w * 0.5f, y + 244f * k, 28f * k, Theme.TEXT_DIM, ui.body, false, w - 80f
        )
        ui.text(
            c, "run coins are banked when the run ends",
            x + w * 0.5f, y + 280f * k, 24f * k, ColorX.withAlpha(Theme.TEXT_DIM, 0.75f), ui.body, false, w - 80f
        )

        val bw = w - 96f
        val bx = x + 48f
        var by = y + headerH * k
        if (ui.button(c, Id.RESUME, bx, by, bw, btnH[0] * k, "RESUME", Ui.ButtonStyle.PRIMARY)) {
            g.tap(); g.goto(Game.Screen.PLAY)
        }
        by += (btnH[0] + gap) * k
        if (ui.button(c, Id.RESTART, bx, by, bw, btnH[1] * k, "RESTART")) {
            g.tap(); g.startRun()
        }
        by += (btnH[1] + gap) * k
        if (ui.button(c, Id.SETTINGS, bx, by, bw, btnH[2] * k, "SETTINGS")) {
            g.tap(); g.goto(Game.Screen.SETTINGS)
        }
        by += (btnH[2] + gap) * k
        if (ui.button(c, Id.QUIT, bx, by, bw, btnH[3] * k, "QUIT TO MENU", Ui.ButtonStyle.GHOST)) {
            g.tap(); g.goto(Game.Screen.MENU)
        }
    }
}
