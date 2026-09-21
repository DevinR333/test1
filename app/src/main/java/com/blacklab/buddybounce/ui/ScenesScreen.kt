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

    private val scroll = Scroller()
    private var maxScroll = 0f

    /**
     * The worlds to show. Heaven is left out until it has been earned - it is meant to be a
     * surprise, and a locked "???" card (or a total that does not add up) gives it away.
     */
    private fun visibleScenes(): List<com.blacklab.buddybounce.render.Scene> =
        if (g.save.ownsScene(Scenes.HEAVEN_ID)) Scenes.ALL
        else Scenes.ALL.filter { it.id != Scenes.HEAVEN_ID }

    fun draw(c: Canvas) {
        val ui = g.ui
        val h = Theme.SCREEN_H
        val wide = g.worldW > h * 1.12f
        val rise = (1f - g.screenAnim) * 50f
        val list = visibleScenes()

        if (ui.backButton(c, Id.BACK, ui.safeLeft + 78f, ui.safeTop + 78f, 52f)) {
            g.tap(); g.goto(Game.Screen.MENU)
        }
        ui.text(c, "WORLDS", g.worldW * 0.5f, ui.safeTop + 96f, 62f, Theme.TEXT, ui.title, true, ui.headerWidth(g.worldW))
        val owned = list.count { g.save.ownsScene(it.id) }
        // list already leaves Heaven out until it is owned, so this counts nine worlds up to
        // the moment it opens and ten afterwards - saying "of 10" early is the whole giveaway
        ui.text(
            c, "$owned of ${list.size} unlocked  \u2022  swap any time",
            g.worldW * 0.5f, ui.safeTop + 140f, 30f, Theme.TEXT_DIM, ui.body, false,
            g.worldW - ui.safeLeft - ui.safeRight - 80f
        )

        val cols = if (wide) 3 else 1
        val listW = min(g.worldW - ui.safeLeft - ui.safeRight - 70f, if (wide) 1200f else 820f)
        val gap = 20f
        val cardW = (listW - gap * (cols - 1)) / cols
        val cardH = if (wide) cardW * 1.05f else 230f
        val x0 = (g.worldW - listW) * 0.5f
        val top = ui.safeTop + 182f
        val viewH = (h - ui.safeBottom - top - 16f).coerceAtLeast(200f)

        // There are ten worlds now. The list used to simply stop drawing when it ran out of
        // room, which left the later ones invisible AND unreachable - there was no way to
        // select a world you could not see.
        val rows = ceil(list.size / cols.toFloat()).toInt()
        val contentH = rows * (cardH + gap) + 72f
        maxScroll = (contentH - viewH).coerceAtLeast(0f)
        scroll.update(ui.frameDt, ui.pointerDown, ui.scrollDrag, maxScroll)

        c.save()
        c.clipRect(x0 - 12f, top, x0 + listW + 12f, top + viewH)
        // and the same clip on the input, or a card scrolled up behind the back button still
        // swallows the tap meant for it
        ui.setInputClip(x0 - 12f, top, x0 + listW + 12f, top + viewH)
        for (i in list.indices) {
            val col = i % cols
            val row = i / cols
            val x = x0 + col * (cardW + gap)
            val y = top + row * (cardH + gap) - scroll.y + rise
            // A card the controller is pointing at is brought into view rather than left
            // behind the fold - focus can outrun the scroll otherwise.
            if (ui.padActive && ui.focusId == Id.CARD + i) {
                if (y < top) scroll.nudge(y - top)
                else if (y + cardH > top + viewH) scroll.nudge(y + cardH - (top + viewH))
            }
            if (y + cardH < top - 40f || y > top + viewH + 40f) {
                ui.focusOnly(Id.CARD + i, x, y, cardW, cardH)
                continue
            }
            drawCard(c, list[i], i, x, y, cardW, cardH, wide)
        }
        val after = top + rows * (cardH + gap) - scroll.y
        if (after < top + viewH) {
            ui.text(
                c, "More worlds turn up in the prize machine.",
                g.worldW * 0.5f, after + 40f, 28f, Theme.TEXT_DIM, ui.body, false, listW - 40f
            )
        }
        ui.clearInputClip()
        c.restore()

        // scroll affordance
        if (maxScroll > 1f) {
            val trackH = viewH * 0.9f
            val knobH = (trackH * (viewH / (viewH + maxScroll))).coerceAtLeast(60f)
            val t = scroll.y / maxScroll
            p.reset(); p.isAntiAlias = true
            p.color = 0x22FFFFFF
            r.set(x0 + listW + 16f, top + viewH * 0.05f, x0 + listW + 22f, top + viewH * 0.05f + trackH)
            c.drawRoundRect(r, 3f, 3f, p)
            p.color = 0x66FFFFFF
            val ky = top + viewH * 0.05f + (trackH - knobH) * t
            r.set(x0 + listW + 16f, ky, x0 + listW + 22f, ky + knobH)
            c.drawRoundRect(r, 3f, 3f, p)
        }
    }

    private fun drawCard(
        c: Canvas, scene: com.blacklab.buddybounce.render.Scene, index: Int,
        x: Float, y: Float, w: Float, h: Float, wide: Boolean
    ) {
        val ui = g.ui
        val owned = g.save.ownsScene(scene.id)
        val selected = g.save.selectedScene == scene.id

        if (ui.button(c, Id.CARD + index, x, y, w, h, "", Ui.ButtonStyle.GHOST) &&
            owned && !selected && !scroll.suppressTap
        ) {
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
                c, bandList(scene), textX, nameY + 74f, 24f,
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

    private fun bandList(scene: com.blacklab.buddybounce.render.Scene): String {
        val bands = scene.bands
        val sb = StringBuilder()
        for (i in bands.indices) {
            if (i > 0) sb.append("  ›  ")
            sb.append(bands[i].name)
        }
        return sb.toString()
    }
}
