package com.blacklab.buddybounce.ui

import android.graphics.Canvas
import com.blacklab.buddybounce.Game
import com.blacklab.buddybounce.game.MathX.smoothstep
import com.blacklab.buddybounce.data.Powerups
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
        const val GACHA = 7004
        const val CONTINUE = 7005
    }

    fun draw(c: Canvas) {
        val ui = g.ui
        val k = smoothstep(0f, 1f, g.screenAnim)
        val wide = g.worldW > Theme.SCREEN_H * 1.12f

        val w = min(g.worldW - ui.safeLeft - ui.safeRight - 80f, if (wide) 900f else 760f)
        val h = min(Theme.SCREEN_H - ui.safeTop - ui.safeBottom - 100f, 1020f)
        val x = (g.worldW - w) * 0.5f
        val y = (Theme.SCREEN_H - h) * 0.5f + (1f - k) * 80f

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
        stat(c, x + third * 0.5f, statsY, g.lastCoins.toString(), "COINS EARNED", Theme.ACCENT, third)
        stat(c, x + third * 1.5f, statsY, ((g.world.heightWu / Tuning.VIEW_H).toInt()).toString(), "SCREENS", Theme.TEXT, third)
        stat(c, x + third * 2.5f, statsY, Palettes.label(g.lastBiome).uppercase(), "REACHED", Palettes.get(g.lastBiome).platAccent, third)

        ui.text(
            c, "${g.lastRunCoins} picked up  +  ${g.lastBonusCoins} for the height  \u2192  banked",
            x + w * 0.5f, statsY + 72f, 25f, Theme.TEXT_DIM, ui.body, false, w - 72f
        )
        ui.text(
            c, "${g.save.coins} coins in the bank",
            x + w * 0.5f, statsY + 106f, 27f, ColorX.withAlpha(Theme.ACCENT, 0.9f), ui.body, false, w - 72f
        )

        // buttons
        val bw = w - 96f
        val bx = x + 48f
        var by = y + h - 268f

        // A Second Life is spent HERE, not chosen before a run - it is the answer to "no, not
        // yet". Offering it above BOUNCE AGAIN puts it where the thumb already is.
        val lives = g.save.powerupCount(Powerups.SECOND_LIFE)
        if (lives > 0) {
            by -= 124f
            if (ui.button(
                    c, Id.CONTINUE, bx, by, bw, 108f, "SECOND LIFE", Ui.ButtonStyle.PRIMARY,
                    sublabel = "carry on from here \u00b7 $lives left"
                )
            ) {
                g.tap(); g.reviveWithSecondLife()
            }
            ui.shimmer(c, bx, by, bw, 108f, 36f, 0.4f + 0.35f * sin(ui.time * 3.4f))
            by += 124f
        }

        if (ui.button(c, Id.RETRY, bx, by, bw, 116f,
                if (lives > 0) "START OVER" else "BOUNCE AGAIN", Ui.ButtonStyle.PRIMARY)) {
            g.tap(); g.startRun()
        }
        by += 132f
        // Three across: the end of a run is exactly when you have coins burning a hole, so the
        // machine gets a place here rather than making you go back to the menu for it.
        val thirdW = (bw - 40f) * (1f / 3f)
        if (ui.button(c, Id.MENU, bx, by, thirdW, 92f, "MENU")) {
            g.tap(); g.goto(Game.Screen.MENU)
        }
        val machineReady = g.save.coins >= Tuning.GACHA_COST
        if (ui.button(c, Id.GACHA, bx + thirdW + 20f, by, thirdW, 92f, "MACHINE",
                Ui.ButtonStyle.SECONDARY,
                sublabel = if (machineReady) "ready!" else "${g.save.coins}/${Tuning.GACHA_COST}")) {
            g.tap(); g.goto(Game.Screen.GACHA)
        }
        if (machineReady) {
            val pulse = 0.4f + 0.35f * sin(ui.time * 3.2f)
            ui.shimmer(c, bx + thirdW + 20f, by, thirdW, 92f, 32f, pulse)
        }
        if (ui.button(c, Id.WARDROBE, bx + (thirdW + 20f) * 2f, by, thirdW, 92f, "WARDROBE")) {
            g.tap(); g.goto(Game.Screen.WARDROBE)
        }

        // Buddy peeking over the top of the card
        g.drawPosedBuddy(c, x + w - 96f, y + 18f, 0.85f, g.equippedOutfit, ui.time * 0.7f)
    }

    /** One of the three end-of-run figures. [colW] is its share of the panel, so a long band
     *  name like "CLOUDLINE III" shrinks instead of running into its neighbours. */
    private fun stat(c: Canvas, cx: Float, cy: Float, value: String, label: String, color: Int, colW: Float) {
        val ui = g.ui
        val box = colW - 16f
        ui.text(c, value, cx, cy, 46f, color, ui.title, false, box)
        ui.text(c, label, cx, cy + 34f, 24f, Theme.TEXT_DIM, ui.body, false, box)
    }
}
