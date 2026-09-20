package com.blacklab.buddybounce.ui

import android.graphics.Canvas
import com.blacklab.buddybounce.Game
import com.blacklab.buddybounce.game.MathX.smoothstep
import com.blacklab.buddybounce.game.Tuning
import com.blacklab.buddybounce.render.ColorX
import com.blacklab.buddybounce.render.Palettes
import kotlin.math.min
import kotlin.math.sin

/** Run summary: score, where it lands on the board, and the fastest possible way back in. */
class GameOverScreen(private val g: Game) {

    private object Id {
        const val RETRY = 7001
        const val MENU = 7002
        const val WARDROBE = 7003
    }

    fun draw(c: Canvas) {
        val ui = g.ui
        val k = smoothstep(0f, 1f, g.screenAnim)
        val wide = g.worldW > Tuning.VIEW_H * 1.12f

        val w = min(g.worldW - ui.safeLeft - ui.safeRight - 80f, if (wide) 900f else 760f)
        val h = min(Tuning.VIEW_H - ui.safeTop - ui.safeBottom - 100f, 1020f)
        val x = (g.worldW - w) * 0.5f
        val y = (Tuning.VIEW_H - h) * 0.5f + (1f - k) * 80f

        ui.panel(c, x, y, w, h)

        val headline = when {
            g.lastNewBest && g.lastScore > 0 -> "NEW BEST!"
            g.lastScore <= 0 -> "OOF."
            g.lastRank in 0..2 -> "GOOD BOY!"
            else -> "NICE RUN"
        }
        ui.text(c, headline, x + w * 0.5f, y + 104f, 66f, if (g.lastNewBest) Theme.ACCENT else Theme.TEXT, ui.title)

        // score
        val scoreSize = min(w * 0.30f, 200f)
        val bounce = if (k < 1f) (1f - k) * 30f else sin(ui.time * 2.2f) * 3f
        ui.text(c, g.lastScore.toString(), x + w * 0.5f, y + 104f + scoreSize + bounce, scoreSize, Theme.TEXT, ui.title)
        ui.text(c, "POINTS", x + w * 0.5f, y + 130f + scoreSize + 34f, 28f, Theme.TEXT_DIM, ui.body, false)

        if (g.lastRank in 0..9) {
            val label = "#${g.lastRank + 1} ON THE BOARD"
            val pw = ui.measure(label, 30f, ui.body) + 60f
            ui.pill(c, x + (w - pw) * 0.5f, y + 130f + scoreSize + 56f, pw, 52f, ColorX.withAlpha(Theme.ACCENT, 0.2f))
            ui.text(c, label, x + w * 0.5f, y + 130f + scoreSize + 92f, 30f, Theme.ACCENT, ui.title, false)
        }

        // stats strip
        val statsY = y + h * 0.60f
        val third = w / 3f
        stat(c, x + third * 0.5f, statsY, g.lastCoins.toString(), "COINS", Theme.ACCENT)
        stat(c, x + third * 1.5f, statsY, ((g.world.heightWu / Tuning.VIEW_H).toInt()).toString(), "SCREENS", Theme.TEXT)
        stat(c, x + third * 2.5f, statsY, Palettes.label(g.lastBiome).uppercase(), "REACHED", Palettes.get(g.lastBiome).platAccent)

        // buttons
        val bw = w - 96f
        val bx = x + 48f
        var by = y + h - 268f
        if (ui.button(c, Id.RETRY, bx, by, bw, 116f, "BOUNCE AGAIN", Ui.ButtonStyle.PRIMARY)) {
            g.tap(); g.startRun()
        }
        by += 132f
        val halfW = (bw - 20f) * 0.5f
        if (ui.button(c, Id.MENU, bx, by, halfW, 92f, "MENU")) {
            g.tap(); g.goto(Game.Screen.MENU)
        }
        if (ui.button(c, Id.WARDROBE, bx + halfW + 20f, by, halfW, 92f, "WARDROBE")) {
            g.tap(); g.goto(Game.Screen.WARDROBE)
        }

        // Buddy peeking over the top of the card
        g.drawPosedBuddy(c, x + w - 96f, y + 18f, 0.85f, g.equippedOutfit, ui.time * 0.7f)
    }

    private fun stat(c: Canvas, cx: Float, cy: Float, value: String, label: String, color: Int) {
        val ui = g.ui
        var size = 46f
        while (ui.measure(value, size, ui.title) > 240f && size > 22f) size -= 2f
        ui.text(c, value, cx, cy, size, color, ui.title, false)
        ui.text(c, label, cx, cy + 34f, 24f, Theme.TEXT_DIM, ui.body, false)
    }
}
