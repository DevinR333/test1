package com.blacklab.buddybounce.ui

import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import com.blacklab.buddybounce.Game
import com.blacklab.buddybounce.render.ColorX
import com.blacklab.buddybounce.render.Scenes
import kotlin.math.ceil
import kotlin.math.min

/**
 * The worlds Buddy can climb through. The yard is his; everything else is the rarest thing the
 * prize machine hands out, and swapping between the ones you own is free.
 */
class ScenesScreen(private val g: Game) {

    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val r = RectF()

    private object Id {
        const val BACK = 9001
        const val CARD = 9100   // + index
    }

    fun draw(c: Canvas) {
        val ui = g.ui
        val h = Theme.SCREEN_H
        val wide = g.worldW > h * 1.12f
        val rise = (1f - g.screenAnim) * 50f

        if (ui.backButton(c, Id.BACK, ui.safeLeft + 78f, ui.safeTop + 78f, 52f)) {
            g.tap(); g.goto(Game.Screen.MENU)
        }
        ui.text(c, "WORLDS", g.worldW * 0.5f, ui.safeTop + 96f, 62f, Theme.TEXT, ui.title)
        ui.text(
            c, "${g.ownedSceneCount()} of ${Scenes.ALL.size} unlocked  •  swap any time",
            g.worldW * 0.5f, ui.safeTop + 140f, 30f, Theme.TEXT_DIM, ui.body, false,
            g.worldW - ui.safeLeft - ui.safeRight - 80f
        )

        val cols = if (wide) 3 else 1
        val listW = min(g.worldW - ui.safeLeft - ui.safeRight - 70f, if (wide) 1200f else 820f)
        val gap = 20f
        val cardW = (listW - gap * (cols - 1)) / cols
        val cardH = if (wide) cardW * 1.05f else 230f
        val x0 = (g.worldW - listW) * 0.5f
        val top = ui.safeTop + 182f + rise

        for (i in Scenes.ALL.indices) {
            val col = i % cols
            val row = i / cols
            val x = x0 + col * (cardW + gap)
            val y = top + row * (cardH + gap)
            if (y + cardH > h - ui.safeBottom) break
            drawCard(c, i, x, y, cardW, cardH, wide)
        }

        val rows = ceil(Scenes.ALL.size / cols.toFloat()).toInt()
        val after = top + rows * (cardH + gap)
        if (after < h - ui.safeBottom - 60f) {
            ui.text(
                c, "More worlds turn up in the prize machine. Rarely.",
                g.worldW * 0.5f, after + 40f, 28f, Theme.TEXT_DIM, ui.body, false
            )
        }
    }

    private fun drawCard(c: Canvas, index: Int, x: Float, y: Float, w: Float, h: Float, wide: Boolean) {
        val ui = g.ui
        val scene = Scenes.ALL[index]
        val owned = g.save.ownsScene(scene.id)
        val selected = g.save.selectedScene == scene.id

        if (ui.button(c, Id.CARD + index, x, y, w, h, "", Ui.ButtonStyle.GHOST) && owned && !selected) {
            g.tap()
            g.save.selectedScene = scene.id
            g.applyScene()
            g.flashScreen(0.2f)
        }

        p.reset(); p.isAntiAlias = true
        p.color = Theme.PANEL_SOLID
        r.set(x, y, x + w, y + h)
        c.drawRoundRect(r, 24f, 24f, p)

        // A stack of the scene's skies, bottom band first: an at-a-glance preview of the climb.
        val previewW = if (wide) w - 36f else 210f
        val previewH = if (wide) h * 0.44f else h - 36f
        val px = x + 18f
        val py = y + 18f
        val bands = scene.bands
        val colors = IntArray(bands.size + 1)
        for (i in bands.indices) colors[bands.size - 1 - i] = bands[i].skyMid
        colors[bands.size] = bands[0].skyLow
        p.shader = LinearGradient(px, py, px, py + previewH, colors, null, Shader.TileMode.CLAMP)
        r.set(px, py, px + previewW, py + previewH)
        c.drawRoundRect(r, 16f, 16f, p)
        p.shader = null

        if (!owned) {
            p.color = 0xCC0B1020.toInt()
            c.drawRoundRect(r, 16f, 16f, p)
            ui.text(c, "?", px + previewW * 0.5f, py + previewH * 0.5f + 26f, 76f, 0xFF4A5675.toInt(), ui.title, false)
        } else {
            // a hint of the ground and a star field so each preview reads differently
            p.color = ColorX.withAlpha(bands[0].platTop, 0.9f)
            r.set(px, py + previewH - 26f, px + previewW, py + previewH)
            c.drawRoundRect(r, 12f, 12f, p)
            p.color = ColorX.withAlpha(0xFFFFFFFF.toInt(), bands[bands.size - 1].starAlpha * 0.8f)
            for (i in 0 until 12) {
                c.drawCircle(
                    px + ((i * 37) % 100) / 100f * previewW,
                    py + 8f + ((i * 53) % 60) / 100f * previewH * 0.5f,
                    2.4f, p
                )
            }
        }

        val textX = if (wide) x + w * 0.5f else px + previewW + 22f
        val textAlign = if (wide) ui.title else ui.bodyLeft
        val nameY = if (wide) py + previewH + 52f else y + 72f

        // In the narrow layout the text column starts beside the preview, so its room is
        // whatever is left of the card - the band list in particular is long enough to run off
        // the edge on a phone if nothing holds it back.
        val textBox = if (wide) w - 32f else x + w - textX - 20f

        if (wide) {
            ui.text(c, scene.name.uppercase(), textX, nameY, 34f,
                if (owned) Theme.TEXT else Theme.TEXT_DIM, ui.title, false, textBox)
        } else {
            ui.text(c, scene.name.uppercase(), textX, nameY, 40f,
                if (owned) Theme.TEXT else Theme.TEXT_DIM, ui.bodyLeft, false, textBox)
        }

        if (!wide) {
            ui.text(
                c, if (owned) scene.blurb else "Locked - a rare find in the prize machine",
                textX, nameY + 40f, 26f, Theme.TEXT_DIM, ui.bodyLeft, false, textBox
            )
            ui.text(
                c, bandList(index), textX, nameY + 74f, 24f,
                ColorX.withAlpha(scene.cardTint, 0.9f), ui.bodyLeft, false, textBox
            )
        }

        val tagW = 168f
        val tagX = if (wide) x + (w - tagW) * 0.5f else x + w - tagW - 20f
        val tagY = if (wide) y + h - 56f else y + h - 62f
        when {
            selected -> {
                ui.pill(c, tagX, tagY, tagW, 44f, ColorX.withAlpha(Theme.GOOD, 0.25f))
                ui.text(c, "PLAYING", tagX + tagW * 0.5f, tagY + 31f, 26f, Theme.GOOD, ui.title, false)
            }
            owned -> {
                ui.pill(c, tagX, tagY, tagW, 44f, ColorX.withAlpha(Theme.ACCENT, 0.22f))
                ui.text(c, "TAP TO PLAY", tagX + tagW * 0.5f, tagY + 31f, 24f, Theme.ACCENT, ui.title, false)
            }
            else -> {
                ui.pill(c, tagX, tagY, tagW, 44f, 0x33FFFFFF)
                ui.text(c, "LOCKED", tagX + tagW * 0.5f, tagY + 31f, 24f, Theme.TEXT_DIM, ui.title, false)
            }
        }

        p.style = Paint.Style.STROKE
        p.strokeWidth = if (selected) 4.5f else 2.5f
        p.color = if (selected) Theme.ACCENT else ColorX.withAlpha(scene.cardTint, if (owned) 0.5f else 0.18f)
        r.set(x, y, x + w, y + h)
        c.drawRoundRect(r, 24f, 24f, p)
        p.style = Paint.Style.FILL
    }

    private fun bandList(index: Int): String {
        val bands = Scenes.ALL[index].bands
        val sb = StringBuilder()
        for (i in bands.indices) {
            if (i > 0) sb.append("  ›  ")
            sb.append(bands[i].name)
        }
        return sb.toString()
    }
}
