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
        // The card is laid out from BOTH ends. The button stack owns the foot of it, the score
        // owns the top, and the stats strip goes in whatever is left between them.
        //
        // The stack used to be anchored to a single fixed offset and then, if a Second Life was
        // on offer, shoved 124 units UPWARD to make room for the extra button - straight over
        // the stats strip and the coin lines under it. Measuring the stack first and placing
        // everything else above it means an extra button pushes the layout instead of covering
        // it, and the score shrinks to fit rather than the two colliding.
        val lives = g.save.powerupCount(Powerups.SECOND_LIFE)
        val ranked = g.lastRank in 0..9
        val rankH = if (ranked) 68f else 0f
        val stackH = BUTTON_STACK_H + (if (lives > 0) SECOND_LIFE_ROW_H else 0f)
        val stackTop = y + h - 44f - stackH

        // The stats block gets first refusal on the middle: the coin lines are information and
        // the size of the score is decoration, so the number shrinks for them rather than the
        // other way round. What is left pays for the headline, POINTS and any rank pill; the
        // number consumes its own size plus a quarter of it again in descender and gap.
        val scoreRoom = (stackTop - STATS_FULL_H - 38f - STATS_TOP_GAP) - (y + 138f) - (44f + rankH)
        val scoreSize = min(w * 0.30f, 200f).coerceAtMost((scoreRoom / 1.24f).coerceAtLeast(56f))

        var ty = y + 104f
        ui.text(c, headline, x + w * 0.5f, ty, 66f,
            if (g.lastNewBest) Theme.ACCENT else Theme.TEXT, ui.title, true, w - 60f)
        ty += 34f
        val bounce = if (k < 1f) (1f - k) * 30f else sin(ui.time * 2.2f) * 3f
        ty += scoreSize
        ui.text(c, g.lastScore.toString(), x + w * 0.5f, ty + bounce, scoreSize, Theme.TEXT, ui.title, true, w - 60f)
        ty += scoreSize * 0.24f + 10f          // past the digits' descenders

        ty += 24f
        ui.text(c, "POINTS", x + w * 0.5f, ty, 28f, Theme.TEXT_DIM, ui.body, false, w - 60f)
        ty += 20f

        if (ranked) {
            val label = "#${g.lastRank + 1} ON THE BOARD"
            val pw = ui.measure(label, 30f, ui.body) + 60f
            ty += 16f
            ui.pill(c, x + (w - pw) * 0.5f, ty, pw, 52f, ColorX.withAlpha(Theme.ACCENT, 0.2f))
            ui.text(c, label, x + w * 0.5f, ty + 36f, 30f, Theme.ACCENT, ui.title, false, w - 60f)
            ty += 52f
        }

        // Whatever is genuinely left between the score and the stack. On a card too short to
        // hold all of it the block SHEDS lines from the bottom rather than overlapping the
        // buttons - a missing line is a smaller card, a line under a button is a broken one.
        val gap = stackTop - 8f - (ty + STATS_TOP_GAP)
        val statsBlockH = when {
            gap >= STATS_FULL_H -> STATS_FULL_H
            gap >= STATS_ONE_LINE_H -> STATS_ONE_LINE_H
            gap >= STATS_BARE_H -> STATS_BARE_H
            else -> 0f
        }
        val showStats = statsBlockH > 0f
        val showPickupLine = statsBlockH >= STATS_ONE_LINE_H
        val roomForBankLine = statsBlockH >= STATS_FULL_H
        val statsY = ty + STATS_TOP_GAP + (gap - statsBlockH).coerceAtLeast(0f) * 0.4f
        val third = w / 3f
        if (showStats) {
            stat(c, x + third * 0.5f, statsY, g.lastCoins.toString(), "COINS EARNED", Theme.ACCENT, third)
            stat(c, x + third * 1.5f, statsY, ((g.world.heightWu / Tuning.VIEW_H).toInt()).toString(), "SCREENS", Theme.TEXT, third)
            stat(c, x + third * 2.5f, statsY, Palettes.label(g.lastBiome).uppercase(), "REACHED", Palettes.get(g.lastBiome).platAccent, third)
        }

        if (showPickupLine) {
            ui.text(
                c, "${g.lastRunCoins} picked up  +  ${g.lastBonusCoins} for the height  \u2192  banked",
                x + w * 0.5f, statsY + PICKUP_BASE, 25f, Theme.TEXT_DIM, ui.body, false, w - 72f
            )
        }
        if (roomForBankLine) {
            ui.text(
                c, "${g.save.coins} coins in the bank",
                x + w * 0.5f, statsY + BANK_BASE, 27f, ColorX.withAlpha(Theme.ACCENT, 0.9f), ui.body, false, w - 72f
            )
        }

        // buttons
        val bw = w - 96f
        val bx = x + 48f
        var by = stackTop

        // A Second Life is spent HERE, not chosen before a run - it is the answer to "no, not
        // yet". Offering it above BOUNCE AGAIN puts it where the thumb already is.
        if (lives > 0) {
            if (ui.button(
                    c, Id.CONTINUE, bx, by, bw, 108f, "SECOND LIFE", Ui.ButtonStyle.PRIMARY,
                    sublabel = "carry on from here \u00b7 $lives left"
                )
            ) {
                g.tap(); g.reviveWithSecondLife()
            }
            ui.shimmer(c, bx, by, bw, 108f, 36f, 0.4f + 0.35f * sin(ui.time * 3.4f))
            by += SECOND_LIFE_ROW_H
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

    /**
     * One of the three end-of-run figures. [top] is the TOP of the block, not a baseline.
     *
     * It used to be a baseline, while everything around it was laid out from the top - so the
     * 46-unit digits reached about 37 units ABOVE the y they were handed and sat on the bottom
     * of the rank pill above them. The layout checker agreed with the layout and disagreed with
     * the drawing, which is why it passed. [colW] is the column's share of the panel, so a long
     * band name like "CLOUDLINE III" shrinks instead of running into its neighbours.
     */
    private fun stat(c: Canvas, cx: Float, top: Float, value: String, label: String, color: Int, colW: Float) {
        val ui = g.ui
        val box = colW - 16f
        ui.text(c, value, cx, top + VALUE_BASE, 44f, color, ui.title, false, box)
        ui.text(c, label, cx, top + LABEL_BASE, 24f, Theme.TEXT_DIM, ui.body, false, box)
    }

    private companion object {
        /** BOUNCE AGAIN (116) + gap (16) + the three-across row (92). */
        const val BUTTON_STACK_H = 224f
        /** What a SECOND LIFE button adds to the stack: its height plus the gap under it. */
        const val SECOND_LIFE_ROW_H = 124f

        // Every line in the stats block is a BASELINE measured down from the top of the block,
        // so the block's height and what it actually paints cannot drift apart again.
        /** Baseline of the 44-unit figure, clear of its own cap height. */
        /** Clear air between whatever the score column ends with and the top of the block. */
        const val STATS_TOP_GAP = 30f
        const val VALUE_BASE = 40f
        /** Baseline of the 24-unit caption under it. */
        const val LABEL_BASE = 72f
        /** Baseline of "N picked up + N for the height -> banked". */
        const val PICKUP_BASE = 110f
        /** Baseline of "N coins in the bank". */
        const val BANK_BASE = 148f

        /** The stats strip with both coin lines under it: BANK_BASE plus its descender. */
        const val STATS_FULL_H = 158f
        /** The strip plus the "picked up ... banked" line, the bank total dropped. */
        const val STATS_ONE_LINE_H = 120f
        /** The three values and their labels, nothing else. */
        const val STATS_BARE_H = 80f
    }
}
