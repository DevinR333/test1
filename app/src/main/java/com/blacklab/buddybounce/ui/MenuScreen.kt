package com.blacklab.buddybounce.ui

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import com.blacklab.buddybounce.Game
import com.blacklab.buddybounce.data.Outfits
import com.blacklab.buddybounce.game.Tuning
import com.blacklab.buddybounce.render.ColorX
import com.blacklab.buddybounce.render.Scenes
import kotlin.math.min
import kotlin.math.sin

/** Title screen: Buddy, the play button, and the way into everything else. */
class MenuScreen(private val g: Game) {

    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rect = RectF()

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
        drawNotice(c)

        if (wide) {
            val leftCx = g.worldW * 0.30f
            val rightW = min(g.worldW * 0.40f, 620f)
            val rightX = g.worldW * 0.58f
            drawTitle(c, leftCx, h * 0.24f + rise)
            drawStage(c, leftCx, h * 0.84f)
            drawButtons(c, rightX, h * 0.28f + rise, rightW)
        } else {
            drawTitle(c, g.worldW * 0.5f, h * 0.19f + rise)
            drawStage(c, g.worldW * 0.5f, h * 0.60f)
            val bw = min(g.worldW - ui.safeLeft - ui.safeRight - 120f, 700f)
            drawButtons(c, (g.worldW - bw) * 0.5f, h * 0.64f + rise, bw)
        }
    }

    /** Behind the native name-entry overlay: just Buddy, looking hopeful. */
    fun drawNamePrompt(c: Canvas) {
        val h = Theme.SCREEN_H
        drawTitle(c, g.worldW * 0.5f, h * 0.20f)
        g.drawMenuBuddy(c, g.worldW * 0.5f, h * 0.56f, 1.3f, Outfits.DEFAULT_ID)
    }

    private fun drawTitle(c: Canvas, cx: Float, cy: Float) {
        val ui = g.ui
        val bounce = sin(ui.time * 1.6f) * 8f
        val size = min(g.worldW * 0.22f, 168f)

        // "BUDDY" sits above a slightly larger "BOUNCE!"
        titleWord(c, "BUDDY", cx, cy + bounce, size * 0.82f, Theme.TEXT)
        titleWord(c, "BOUNCE!", cx, cy + size * 0.82f + bounce * 0.6f, size, Theme.ACCENT)
        ui.text(
            c, "good boy • great height", cx, cy + size * 1.22f, 34f,
            ColorX.withAlpha(Theme.TEXT_DIM, 0.95f), ui.body, false
        )
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

    private fun drawStage(c: Canvas, cx: Float, groundY: Float) {
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
        g.drawMenuBuddy(c, cx, groundY - 2f, 1.15f, g.equippedOutfit)
    }

    private fun drawButtons(c: Canvas, x: Float, y: Float, w: Float) {
        val ui = g.ui
        var cy = y
        if (ui.button(c, Id.PLAY, x, cy, w, 132f, "PLAY", Ui.ButtonStyle.PRIMARY)) {
            g.tap(); g.startRun()
        }
        cy += 154f

        val halfW = (w - 24f) * 0.5f
        if (ui.button(c, Id.WARDROBE, x, cy, halfW, 100f, "WARDROBE", Ui.ButtonStyle.SECONDARY,
                sublabel = "${g.ownedCount()} fits \u00b7 ${g.save.trailCount()} trails")) {
            g.tap(); g.goto(Game.Screen.WARDROBE)
        }
        val machineReady = g.save.coins >= Tuning.GACHA_COST
        if (ui.button(c, Id.GACHA, x + halfW + 24f, cy, halfW, 100f, "MACHINE", Ui.ButtonStyle.SECONDARY,
                sublabel = if (machineReady) "ready to pull!" else "${Tuning.GACHA_COST} coins a go")) {
            g.tap(); g.goto(Game.Screen.GACHA)
        }
        if (machineReady) {
            val pulse = 0.4f + 0.35f * sin(ui.time * 3.2f)
            ui.shimmer(c, x + halfW + 24f, cy, halfW, 100f, 34f, pulse)
        }
        cy += 118f

        if (ui.button(c, Id.SCENES, x, cy, halfW, 100f, "WORLDS", Ui.ButtonStyle.SECONDARY,
                sublabel = "${g.ownedSceneCount()}/${Scenes.ALL.size} unlocked")) {
            g.tap(); g.goto(Game.Screen.SCENES)
        }
        if (ui.button(c, Id.SCORES, x + halfW + 24f, cy, halfW, 100f, "SCORES", Ui.ButtonStyle.SECONDARY,
                sublabel = if (g.save.bestScore > 0) "best ${g.save.bestScore}" else "no runs yet")) {
            g.tap(); g.goto(Game.Screen.SCORES)
        }
        cy += 118f

        if (ui.button(c, Id.SETTINGS, x, cy, w, 88f, "SETTINGS")) {
            g.tap(); g.goto(Game.Screen.SETTINGS)
        }
    }

    private fun drawNotice(c: Canvas) {
        if (g.noticeT <= 0f) return
        val ui = g.ui
        val a = (g.noticeT / 0.7f).coerceAtMost(1f)
        val w = ui.measure(g.notice, 40f, ui.title) + 80f
        val x = (g.worldW - w) * 0.5f
        val y = ui.safeTop + 110f
        ui.pill(c, x, y, w, 68f, ColorX.withAlpha(Theme.GOOD, 0.3f * a))
        ui.text(c, g.notice, g.worldW * 0.5f, y + 46f, 40f, ColorX.withAlpha(Theme.TEXT, a), ui.title, false)
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
        ui.text(c, name.uppercase(), x + 62f, y + 44f, 36f, Theme.TEXT, ui.bodyLeft, false)
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
        ui.text(c, text, x + 70f, y + 46f, 40f, Theme.ACCENT, ui.bodyLeft, false)
    }
}
