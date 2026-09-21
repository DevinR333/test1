package com.blacklab.buddybounce.ui

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import com.blacklab.buddybounce.Game
import com.blacklab.buddybounce.data.Powerups
import com.blacklab.buddybounce.game.MathX.clamp01
import com.blacklab.buddybounce.render.ColorX
import kotlin.math.ceil
import kotlin.math.min
import kotlin.math.sin

/**
 * Between pressing PLAY and the run starting: spend a power-up if you have one, then a short
 * countdown over the already-laid-out world so you can see where you are before it moves.
 */
class PreRunScreen(private val g: Game) {

    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val r = RectF()
    private val icons = PowerupIcon()

    private object Id {
        const val NONE = 8001
        const val CARD = 8100   // + index
    }

    fun draw(c: Canvas) {
        when (g.preRunPhase) {
            Game.PreRun.PICK -> drawPicker(c)
            Game.PreRun.COUNTDOWN -> drawCountdown(c)
        }
    }

    // -------------------------------------------------------------------------------------

    private fun drawPicker(c: Canvas) {
        val ui = g.ui
        val h = Theme.SCREEN_H
        ui.scrim(c, g.worldW, h, 0.55f)

        val owned = Powerups.preRunChoices.filter { g.save.powerupCount(it.id) > 0 }
        val wide = g.worldW > h * 1.12f
        val cols = if (wide) 4 else 2
        val rows = ceil(owned.size / cols.toFloat()).toInt().coerceAtLeast(1)

        val panelW = min(g.worldW - ui.safeLeft - ui.safeRight - 60f, if (wide) 1100f else 780f)
        val cardW = (panelW - 60f - 18f * (cols - 1)) / cols
        val cardH = cardW * 0.82f
        val panelH = (200f + rows * (cardH + 18f) + 130f).coerceAtMost(h - ui.safeTop - ui.safeBottom - 40f)
        val x = (g.worldW - panelW) * 0.5f
        val y = (h - panelH) * 0.5f + (1f - g.screenAnim) * 50f

        ui.panel(c, x, y, panelW, panelH)
        ui.text(c, "TAKE SOMETHING WITH YOU?", x + panelW * 0.5f, y + 82f, 48f, Theme.TEXT, ui.title)
        ui.text(
            c, "One per run. It's spent whether you finish or not.",
            x + panelW * 0.5f, y + 124f, 28f, Theme.TEXT_DIM, ui.body, false
        )

        val gridTop = y + 160f
        for (i in owned.indices) {
            val col = i % cols
            val row = i / cols
            val cx = x + 30f + col * (cardW + 18f)
            val cy = gridTop + row * (cardH + 18f)
            if (cy + cardH > y + panelH - 120f) break
            drawCard(c, i, owned[i], cx, cy, cardW, cardH)
        }

        val bw = min(panelW - 60f, 460f)
        if (ui.button(
                c, Id.NONE, x + (panelW - bw) * 0.5f, y + panelH - 104f, bw, 84f,
                "GO WITHOUT ONE", Ui.ButtonStyle.SECONDARY
            )
        ) {
            g.tap()
            g.beginCountdown(null)
        }
    }

    private fun drawCard(c: Canvas, index: Int, pu: Powerups.Powerup, x: Float, y: Float, w: Float, h: Float) {
        val ui = g.ui
        val count = g.save.powerupCount(pu.id)
        if (ui.button(c, Id.CARD + index, x, y, w, h, "", Ui.ButtonStyle.GHOST)) {
            g.tap()
            g.beginCountdown(pu.id)
        }

        p.reset(); p.isAntiAlias = true
        p.color = 0xFF1E2740.toInt()
        r.set(x, y, x + w, y + h)
        c.drawRoundRect(r, 20f, 20f, p)
        p.style = Paint.Style.STROKE
        p.strokeWidth = 3f
        p.color = ColorX.withAlpha(pu.tint, 0.65f)
        c.drawRoundRect(r, 20f, 20f, p)
        p.style = Paint.Style.FILL

        icons.draw(c, g.art, pu.id, x + w * 0.5f, y + h * 0.40f, h * 0.22f, pu.tint)

        var size = 24f
        while (ui.measure(pu.name.uppercase(), size, ui.title) > w - 20f && size > 14f) size -= 1f
        ui.text(c, pu.name.uppercase(), x + w * 0.5f, y + h - 34f, size, Theme.TEXT, ui.title, false)

        // stock badge
        val badge = "x$count"
        val bw = ui.measure(badge, 24f, ui.title) + 26f
        ui.pill(c, x + w - bw - 10f, y + 10f, bw, 36f, ColorX.withAlpha(pu.tint, 0.28f))
        ui.text(c, badge, x + w - bw * 0.5f - 10f, y + 34f, 24f, Theme.TEXT, ui.title, false)
    }

    // -------------------------------------------------------------------------------------

    private fun drawCountdown(c: Canvas) {
        val ui = g.ui
        val h = Theme.SCREEN_H
        // A touch darker, so the numbers read without hiding the world behind them.
        ui.scrim(c, g.worldW, h, 0.32f)

        val t = g.countdown
        val label = when {
            t > 2.9f -> "3"
            t > 1.9f -> "2"
            t > 0.9f -> "1"
            else -> "GO!"
        }
        val frac = t - t.toInt()
        val pop = if (label == "GO!") 1.1f + sin((0.9f - t).coerceAtLeast(0f) * 12f) * 0.08f else 0.82f + frac * 0.4f
        val alpha = if (label == "GO!") clamp01(t / 0.35f) else 1f
        val size = min(g.worldW * 0.42f, 320f) * pop

        val cx = g.worldW * 0.5f
        val cy = h * 0.42f
        g.art.drawGlow(c, cx, cy - size * 0.28f, size * 1.5f, Theme.ACCENT, 0.3f * alpha)

        ui.title.textSize = size
        ui.title.style = Paint.Style.STROKE
        ui.title.strokeWidth = size * 0.1f
        ui.title.strokeJoin = Paint.Join.ROUND
        ui.title.color = ColorX.withAlpha(0xFF10151F.toInt(), alpha)
        c.drawText(label, cx, cy, ui.title)
        ui.title.style = Paint.Style.FILL
        ui.title.color = ColorX.withAlpha(if (label == "GO!") Theme.GOOD else Theme.TEXT, alpha)
        c.drawText(label, cx, cy, ui.title)

        val chosen = if (g.resuming) null else g.chosenPowerup
        if (chosen != null) {
            val pu = Powerups.of(chosen)
            ui.text(
                c, pu.name.uppercase(), cx, cy + 90f, 44f,
                ColorX.withAlpha(pu.tint, alpha), ui.title
            )
        }
        // Holding a finger fast-forwards the count (see Game.updatePreRun), so say so - and say
        // it differently while they are actually holding, as the feedback that it is working.
        val hint = when {
            g.holdingToSkip -> "skipping ahead..."
            g.resuming -> "back in a moment - hold to skip"
            else -> "get ready - hold to skip"
        }
        ui.text(
            c, hint, cx, cy + 148f, 30f,
            ColorX.withAlpha(if (g.holdingToSkip) Theme.ACCENT else Theme.TEXT_DIM, alpha * 0.9f),
            ui.body, false
        )
    }
}
