package com.blacklab.buddybounce.ui

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import com.blacklab.buddybounce.Game
import com.blacklab.buddybounce.data.Outfits
import com.blacklab.buddybounce.data.Trails
import com.blacklab.buddybounce.game.Tuning
import com.blacklab.buddybounce.render.ColorX
import com.blacklab.buddybounce.render.Scenes
import kotlin.math.min
import kotlin.math.sin

/** Title screen: Buddy, the play button, and the way into everything else. */
class MenuScreen(private val g: Game) {

    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rect = RectF()

    private companion object {
        /** Natural height of PLAY + the two pairs + SETTINGS, including the gaps between them. */
        const val BUTTON_STACK_H = 478f
    }

    private object Id {
        const val PLAY = 2001
        const val WARDROBE = 2002
        const val GACHA = 2003
        const val SCORES = 2004
        const val SETTINGS = 2005
        const val NAME = 2006
        const val SCENES = 2007
    }

    fun draw(c: Canvas) {
        val ui = g.ui
        val h = Theme.SCREEN_H
        val wide = g.worldW > h * 1.12f
        val rise = (1f - g.screenAnim) * 60f

        // --- header chips -------------------------------------------------------------
        drawPlayerChip(c)
        drawCoinChip(c)

        // The button stack is anchored to the BOTTOM of the safe area and everything else is
        // placed above it, rather than all of it hanging off fractions of a fixed 1600-unit
        // height. That is what makes the screen work on any shape of display: a tall gesture
        // bar, a notch, or a squarer 4:3 panel changes where the bottom is, and the layout
        // follows it instead of running past it. [BUTTON_STACK_H] is the stack's natural
        // height; when there genuinely is not room for it, everything scales by one factor so
        // it stays in proportion instead of being cropped.
        val bottom = h - ui.safeBottom - 24f
        if (wide) {
            val leftCx = g.worldW * 0.30f
            val rightW = min(g.worldW * 0.40f, 620f)
            val rightX = g.worldW * 0.58f
            val room = bottom - ui.safeTop - 120f
            val k = (room / BUTTON_STACK_H).coerceIn(0.7f, 1f)
            val titleEnd = drawTitle(c, leftCx, h * 0.24f - titleSize() * 0.82f + rise)
            drawStage(c, leftCx, bottom - 40f, titleEnd)
            drawButtons(c, rightX, ui.safeTop + 120f + rise, rightW, k)
        } else {
            val bw = min(g.worldW - ui.safeLeft - ui.safeRight - 120f, 700f)
            // Leave room above the buttons for the title and for Buddy on his plank.
            val room = bottom - ui.safeTop - 520f
            val k = (room / BUTTON_STACK_H).coerceIn(0.7f, 1f)
            val stackH = BUTTON_STACK_H * k
            val btnTop = bottom - stackH
            val titleEnd = drawTitle(c, g.worldW * 0.5f, ui.safeTop + 190f - titleSize() * 0.82f + rise)
            drawStage(c, g.worldW * 0.5f, btnTop - 34f, titleEnd)
            drawButtons(c, (g.worldW - bw) * 0.5f, btnTop + rise, bw, k)
        }
    }

    /** Behind the native name-entry overlay: just Buddy, looking hopeful. */
    fun drawNamePrompt(c: Canvas) {
        val h = Theme.SCREEN_H
        val titleEnd = drawTitle(c, g.worldW * 0.5f, h * 0.20f - titleSize() * 0.82f)
        c.save()
        c.clipRect(0f, titleEnd, g.worldW, h)
        g.drawMenuBuddy(c, g.worldW * 0.5f, h * 0.56f, 1.3f, Outfits.DEFAULT_ID)
        c.restore()
    }

    private fun titleSize(): Float = min(g.worldW * 0.22f, 168f)

    /**
     * The bottom of the header chips, which the title has to start below.
     *
     * The coin chip is always there; the halo chip only appears once Heaven is owned, and it is
     * the one that got buried - the title was placed at a fixed offset from the safe area that
     * had no idea a second chip had turned up underneath the first.
     */
    private fun headerBottom(): Float {
        val ui = g.ui
        val coin = ui.safeTop + 24f + 64f
        return if (g.save.ownsScene(Scenes.HEAVEN_ID)) coin + 12f + 58f else coin
    }

    /**
     * @param topY the TOP of the title block, not its baseline.
     * @return the Y below which the title has finished, so the stage can be kept clear of it.
     */
    private fun drawTitle(c: Canvas, cx: Float, topY: Float): Float {
        val ui = g.ui
        val bounce = sin(ui.time * 1.6f) * 8f
        val size = titleSize()
        // never under the chips, whether or not the halo one is showing
        val cy = maxOf(topY, headerBottom() + 22f) + size * 0.82f

        // "BUDDY" sits above a slightly larger "BOUNCE!"
        titleWord(c, "BUDDY", cx, cy + bounce, size * 0.82f, Theme.TEXT)
        titleWord(c, "BOUNCE!", cx, cy + size * 0.82f + bounce * 0.6f, size, Theme.ACCENT)
        ui.text(
            c, "good boy • great height", cx, cy + size * 1.22f, 34f,
            ColorX.withAlpha(Theme.TEXT_DIM, 0.95f), ui.body, false
        )
        return cy + size * 1.22f + 12f
    }

    private fun titleWord(c: Canvas, s: String, cx: Float, baseline: Float, size: Float, color: Int) {
        val ui = g.ui
        ui.title.textSize = size
        ui.title.style = Paint.Style.STROKE
        ui.title.strokeWidth = size * 0.12f
        ui.title.strokeJoin = Paint.Join.ROUND
        ui.title.color = 0xFF10151F.toInt()
        c.drawText(s, cx, baseline, ui.title)
        ui.title.style = Paint.Style.FILL
        ui.title.color = color
        c.drawText(s, cx, baseline, ui.title)
    }

    /**
     * @param topLimit the Y the title ends at. The stage is clipped to below it.
     *
     * Buddy's trail streams up and back from him and is happily taller than he is, so on a
     * short screen it climbed straight over the logo. Clipping the whole stage means the trail,
     * his glow and anything else that grows out of him all stop at the same line.
     */
    private fun drawStage(c: Canvas, cx: Float, groundY: Float, topLimit: Float) {
        c.save()
        c.clipRect(0f, topLimit, g.worldW, Theme.SCREEN_H)
        // a plank for him to bounce on
        val w = 300f
        g.art.drawShadow(c, cx, groundY + 26f, w * 1.2f, 90f, 0.45f)
        p.reset(); p.isAntiAlias = true
        p.color = 0xFF6B462A.toInt()
        rect.set(cx - w * 0.5f, groundY, cx + w * 0.5f, groundY + 34f)
        c.drawRoundRect(rect, 14f, 14f, p)
        p.color = 0xFF8E5F35.toInt()
        rect.set(cx - w * 0.5f, groundY - 4f, cx + w * 0.5f, groundY + 20f)
        c.drawRoundRect(rect, 12f, 12f, p)
        p.color = 0xFF7FC25C.toInt()
        for (i in 0 until 5) {
            val x = cx - w * 0.42f + i * (w * 0.21f)
            rect.set(x - 8f, groundY - 18f, x + 8f, groundY + 2f)
            c.drawRoundRect(rect, 8f, 8f, p)
        }
        // The whole look, trail included - this is the shop window for everything you own.
        if (g.save.equippedTrail != Trails.NONE_ID) {
            g.drawTrailSample(c, cx - 150f, groundY - 150f, 300f, 110f, g.save.equippedTrail, g.ui.time)
        }
        g.drawMenuBuddy(c, cx, groundY - 2f, 1.15f, g.equippedOutfit)
        c.restore()
    }

    /** @param k fit factor - 1 when there is room for the stack at its natural size. */
    private fun drawButtons(c: Canvas, x: Float, y: Float, w: Float, k: Float) {
        val ui = g.ui
        var cy = y
        if (ui.button(c, Id.PLAY, x, cy, w, 132f * k, "PLAY", Ui.ButtonStyle.PRIMARY)) {
            g.tap(); g.startRun()
        }
        cy += 154f * k

        val halfW = (w - 24f) * 0.5f
        val rowH = 100f * k
        if (ui.button(c, Id.WARDROBE, x, cy, halfW, rowH, "WARDROBE", Ui.ButtonStyle.SECONDARY,
                sublabel = "${g.ownedCount()} fits \u00b7 ${g.save.trailCount()} trails")) {
            g.tap(); g.goto(Game.Screen.WARDROBE)
        }
        val machineReady = g.save.coins >= Tuning.GACHA_COST
        if (ui.button(c, Id.GACHA, x + halfW + 24f, cy, halfW, rowH, "COIN MACHINE", Ui.ButtonStyle.SECONDARY,
                sublabel = if (machineReady) "ready to pull!" else "${Tuning.GACHA_COST} coins a go")) {
            g.tap(); g.goto(Game.Screen.GACHA)
        }
        if (machineReady) {
            val pulse = 0.4f + 0.35f * sin(ui.time * 3.2f)
            ui.shimmer(c, x + halfW + 24f, cy, halfW, rowH, 34f, pulse)
        }
        cy += 118f * k

        if (ui.button(c, Id.SCENES, x, cy, halfW, rowH, "WORLDS", Ui.ButtonStyle.SECONDARY,
                sublabel = "${g.ownedSceneCount()}/${g.visibleSceneCount()} unlocked")) {
            g.tap(); g.goto(Game.Screen.SCENES)
        }
        if (ui.button(c, Id.SCORES, x + halfW + 24f, cy, halfW, rowH, "SCORES", Ui.ButtonStyle.SECONDARY,
                sublabel = if (g.save.bestScore > 0) "best ${g.save.bestScore}" else "no runs yet")) {
            g.tap(); g.goto(Game.Screen.SCORES)
        }
        cy += 118f * k

        if (ui.button(c, Id.SETTINGS, x, cy, w, 88f * k, "SETTINGS")) {
            g.tap(); g.goto(Game.Screen.SETTINGS)
        }
    }

    private fun drawPlayerChip(c: Canvas) {
        val ui = g.ui
        val name = g.save.playerName.ifEmpty { "BUDDY" }
        val x = ui.safeLeft + 28f
        val y = ui.safeTop + 24f
        val w = ui.measure(name.uppercase(), 36f, ui.bodyLeft) + 90f
        ui.pill(c, x, y, w.coerceAtLeast(230f), 64f, 0xAA101728.toInt())
        p.reset(); p.isAntiAlias = true
        p.color = Theme.ACCENT
        c.drawCircle(x + 34f, y + 32f, 18f, p)
        p.color = 0xFF10151F.toInt()
        c.drawCircle(x + 34f, y + 34f, 9f, p)
        ui.text(c, name.uppercase(), x + 62f, y + 44f, 36f, Theme.TEXT, ui.bodyLeft, false, 200f)
        if (g.save.bestScore > 0) {
            ui.text(
                c, "BEST ${g.save.bestScore}", x + 8f, y + 96f, 30f,
                Theme.TEXT_DIM, ui.bodyLeft, false
            )
        }
        if (ui.button(c, Id.NAME, x, y, w.coerceAtLeast(230f), 64f, "", Ui.ButtonStyle.GHOST)) {
            g.tap(); g.host.promptName(g.save.playerName, "Change your name")
        }
    }

    private fun drawCoinChip(c: Canvas) {
        val ui = g.ui
        val text = g.save.coins.toString()
        val w = ui.measure(text, 40f, ui.bodyLeft) + 110f
        val x = g.worldW - ui.safeRight - w - 28f
        val y = ui.safeTop + 24f
        ui.pill(c, x, y, w, 64f, 0xAA101728.toInt())
        p.reset(); p.isAntiAlias = true
        p.color = Theme.ACCENT_DEEP
        c.drawCircle(x + 38f, y + 32f, 22f, p)
        p.color = Theme.ACCENT
        c.drawCircle(x + 38f, y + 32f, 18f, p)
        p.color = Theme.ACCENT_DEEP
        c.drawCircle(x + 38f, y + 36f, 6f, p)
        c.drawCircle(x + 32f, y + 27f, 3.4f, p)
        c.drawCircle(x + 38f, y + 24f, 3.4f, p)
        c.drawCircle(x + 44f, y + 27f, 3.4f, p)
        ui.text(c, text, x + 70f, y + 46f, 40f, Theme.ACCENT, ui.bodyLeft, false, w - 82f)

        // Halos only exist once Heaven does. Showing a second currency before then would be the
        // loudest possible hint that there is a world left to find.
        if (g.save.ownsScene(Scenes.HEAVEN_ID)) drawHaloChip(c, x, y + 76f)
    }

    private fun drawHaloChip(c: Canvas, coinX: Float, y: Float) {
        val ui = g.ui
        val text = g.save.halos.toString()
        val w = ui.measure(text, 36f, ui.bodyLeft) + 100f
        // right-aligned with the coin chip above it, so the two read as one stack
        val x = g.worldW - ui.safeRight - w - 28f
        ui.pill(c, x, y, w, 58f, 0xAA101728.toInt())
        p.reset(); p.isAntiAlias = true
        p.style = Paint.Style.STROKE
        p.strokeWidth = 6f
        p.color = 0xFFE8A93C.toInt()
        rect.set(x + 20f, y + 20f, x + 56f, y + 38f)
        c.drawOval(rect, p)
        p.strokeWidth = 2.6f
        p.color = 0xFFFFF3C2.toInt()
        rect.set(x + 22f, y + 19f, x + 54f, y + 33f)
        c.drawArc(rect, 186f, 168f, false, p)
        p.style = Paint.Style.FILL
        ui.text(c, text, x + 68f, y + 41f, 36f, 0xFFE8A93C.toInt(), ui.bodyLeft, false, w - 80f)
    }
}
