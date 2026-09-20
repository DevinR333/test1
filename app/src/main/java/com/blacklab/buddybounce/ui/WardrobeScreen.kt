package com.blacklab.buddybounce.ui

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import com.blacklab.buddybounce.Game
import com.blacklab.buddybounce.data.Outfits
import com.blacklab.buddybounce.game.MathX.clamp
import com.blacklab.buddybounce.render.ColorX
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.min

/** Everything Buddy owns, swappable at any time and for free. */
class WardrobeScreen(private val g: Game) {

    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rect = RectF()

    private object Id {
        const val BACK = 3001
        const val EQUIP = 3002
        const val CARD = 3100   // + index
    }

    private var selected = 0
    private var scrollY = 0f
    private var maxScroll = 0f
    private var wasDown = false
    private var dragAccum = 0f
    private var dragged = false

    fun draw(c: Canvas) {
        val ui = g.ui
        val wide = g.worldW > Theme.SCREEN_H * 1.12f
        val rise = (1f - g.screenAnim) * 50f

        // keep the selection on the equipped outfit when arriving
        if (selected == 0 && g.equippedOutfit != Outfits.DEFAULT_ID) {
            val idx = Outfits.ALL.indexOfFirst { it.id == g.equippedOutfit }
            if (idx > 0) selected = idx
        }

        handleDrag()

        if (ui.backButton(c, Id.BACK, ui.safeLeft + 78f, ui.safeTop + 78f, 52f)) {
            g.tap(); g.goto(Game.Screen.MENU)
        }
        ui.text(c, "WARDROBE", g.worldW * 0.5f, ui.safeTop + 96f, 62f, Theme.TEXT, ui.title)
        ui.text(
            c, "${g.ownedCount()} of ${Outfits.collectableCount} collected",
            g.worldW * 0.5f, ui.safeTop + 140f, 30f, Theme.TEXT_DIM, ui.body, false
        )

        if (wide) {
            val previewW = min(g.worldW * 0.38f, 560f)
            drawPreview(c, ui.safeLeft + 40f, ui.safeTop + 180f + rise, previewW, Theme.SCREEN_H - ui.safeTop - ui.safeBottom - 240f)
            val gridX = ui.safeLeft + previewW + 80f
            drawGrid(c, gridX, ui.safeTop + 180f, g.worldW - gridX - ui.safeRight - 40f,
                Theme.SCREEN_H - ui.safeTop - ui.safeBottom - 220f, 4)
        } else {
            val previewH = 430f
            drawPreview(c, ui.safeLeft + 36f, ui.safeTop + 172f + rise, g.worldW - ui.safeLeft - ui.safeRight - 72f, previewH)
            val gridTop = ui.safeTop + 172f + previewH + 26f
            drawGrid(c, ui.safeLeft + 36f, gridTop, g.worldW - ui.safeLeft - ui.safeRight - 72f,
                Theme.SCREEN_H - gridTop - ui.safeBottom - 24f, 3)
        }
    }

    private fun handleDrag() {
        val ui = g.ui
        val down = ui.pointerDown
        if (down && !wasDown) { dragAccum = 0f; dragged = false }
        if (down) {
            dragAccum += abs(ui.scrollDrag)
            if (dragAccum > 26f) dragged = true
            scrollY = clamp(scrollY - ui.scrollDrag, 0f, maxScroll)
        }
        wasDown = down
    }

    private fun drawPreview(c: Canvas, x: Float, y: Float, w: Float, h: Float) {
        val ui = g.ui
        val outfit = Outfits.ALL[selected.coerceIn(0, Outfits.ALL.size - 1)]
        val owned = g.outfitOwned(outfit.id)
        val equipped = g.equippedOutfit == outfit.id

        ui.panel(c, x, y, w, h)
        if (outfit.rarity == Outfits.Rarity.LEGENDARY && owned) ui.shimmer(c, x, y, w, h, Theme.RADIUS, 0.8f)

        // rarity ribbon
        p.reset(); p.isAntiAlias = true
        p.color = ColorX.withAlpha(outfit.rarity.tint, 0.20f)
        rect.set(x, y, x + w, y + 86f)
        c.drawRoundRect(rect, Theme.RADIUS, Theme.RADIUS, p)
        rect.set(x, y + 50f, x + w, y + 86f)
        c.drawRect(rect, p)
        ui.text(c, outfit.rarity.label.uppercase(), x + w * 0.5f, y + 56f, 30f, outfit.rarity.tint, ui.title, false)

        val buddyY = y + h * 0.70f
        if (owned) {
            g.art.drawGlow(c, x + w * 0.5f, buddyY - 90f, 260f, outfit.rarity.glow or 0xFF000000.toInt(), 0.22f)
            g.drawPosedBuddy(c, x + w * 0.5f, buddyY, min(w / 300f, h / 430f) * 1.5f, outfit.id, ui.time)
        } else {
            p.color = 0xFF2A3350.toInt()
            c.drawCircle(x + w * 0.5f, buddyY - 110f, 96f, p)
            ui.text(c, "?", x + w * 0.5f, buddyY - 72f, 130f, 0xFF4A5675.toInt(), ui.title, false)
        }

        ui.text(c, outfit.name.uppercase(), x + w * 0.5f, y + h - 108f, 44f, Theme.TEXT, ui.title)
        ui.text(
            c, if (owned) outfit.blurb else "Locked - win it from the coin machine",
            x + w * 0.5f, y + h - 68f, 27f, Theme.TEXT_DIM, ui.body, false
        )

        val bw = min(w - 80f, 420f)
        val bx = x + (w - bw) * 0.5f
        val by = y + h - 56f
        if (owned) {
            if (equipped) {
                ui.pill(c, bx, by - 6f, bw, 62f, 0x332AE08A)
                ui.text(c, "WEARING IT", x + w * 0.5f, by + 36f, 34f, Theme.GOOD, ui.title, false)
            } else if (ui.button(c, Id.EQUIP, bx, by - 14f, bw, 78f, "WEAR IT", Ui.ButtonStyle.PRIMARY)) {
                g.tap()
                g.save.equippedOutfit = outfit.id
            }
        } else if (ui.button(c, Id.EQUIP, bx, by - 14f, bw, 78f, "TO THE MACHINE", Ui.ButtonStyle.SECONDARY)) {
            g.tap(); g.goto(Game.Screen.GACHA)
        }
    }

    private fun drawGrid(c: Canvas, x: Float, y: Float, w: Float, h: Float, cols: Int) {
        val ui = g.ui
        val gap = 16f
        val cardW = (w - gap * (cols - 1)) / cols
        val cardH = cardW * 1.16f
        val rows = ceil(Outfits.ALL.size / cols.toFloat()).toInt()
        maxScroll = (rows * (cardH + gap) - gap - h).coerceAtLeast(0f)
        scrollY = clamp(scrollY, 0f, maxScroll)

        c.save()
        c.clipRect(x - 4f, y, x + w + 4f, y + h)
        for (i in Outfits.ALL.indices) {
            val row = i / cols
            val col = i % cols
            val cx = x + col * (cardW + gap)
            val cy = y + row * (cardH + gap) - scrollY
            if (cy + cardH < y - 20f || cy > y + h + 20f) continue
            drawCard(c, i, cx, cy, cardW, cardH)
        }
        c.restore()

        // scroll affordance
        if (maxScroll > 1f) {
            val trackH = h * 0.9f
            val knobH = (trackH * (h / (h + maxScroll))).coerceAtLeast(60f)
            val t = scrollY / maxScroll
            p.reset(); p.isAntiAlias = true
            p.color = 0x22FFFFFF
            rect.set(x + w + 6f, y + h * 0.05f, x + w + 12f, y + h * 0.05f + trackH)
            c.drawRoundRect(rect, 3f, 3f, p)
            p.color = 0x66FFFFFF
            val ky = y + h * 0.05f + (trackH - knobH) * t
            rect.set(x + w + 6f, ky, x + w + 12f, ky + knobH)
            c.drawRoundRect(rect, 3f, 3f, p)
        }
    }

    private fun drawCard(c: Canvas, index: Int, x: Float, y: Float, w: Float, h: Float) {
        val ui = g.ui
        val outfit = Outfits.ALL[index]
        val owned = g.outfitOwned(outfit.id)
        val equipped = g.equippedOutfit == outfit.id
        val isSelected = index == selected

        val clicked = ui.button(c, Id.CARD + index, x, y, w, h, "", Ui.ButtonStyle.GHOST)
        if (clicked && !dragged) {
            g.tap()
            selected = index
            if (owned) g.save.equippedOutfit = outfit.id
        }

        p.reset(); p.isAntiAlias = true
        p.color = if (owned) 0xFF1E2740.toInt() else 0xFF161C2C.toInt()
        rect.set(x, y, x + w, y + h)
        c.drawRoundRect(rect, 22f, 22f, p)

        p.style = Paint.Style.STROKE
        p.strokeWidth = if (equipped || isSelected) 4.5f else 2.5f
        p.color = when {
            equipped -> Theme.ACCENT
            isSelected -> ColorX.withAlpha(Theme.TEXT, 0.7f)
            owned -> ColorX.withAlpha(outfit.rarity.tint, 0.55f)
            else -> 0x22FFFFFF
        }
        c.drawRoundRect(rect, 22f, 22f, p)
        p.style = Paint.Style.FILL

        if (owned) {
            c.save()
            c.clipRect(x + 3f, y + 3f, x + w - 3f, y + h - 34f)
            g.drawPosedBuddy(
                c, x + w * 0.5f, y + h * 0.78f, (w / 300f) * 1.05f, outfit.id,
                ui.time * 0.6f + index * 0.7f
            )
            c.restore()
        } else {
            p.color = 0xFF2A3350.toInt()
            c.drawCircle(x + w * 0.5f, y + h * 0.42f, w * 0.22f, p)
            ui.text(c, "?", x + w * 0.5f, y + h * 0.42f + w * 0.11f, w * 0.30f, 0xFF4A5675.toInt(), ui.title, false)
        }

        // rarity dot + name
        p.color = outfit.rarity.tint
        c.drawCircle(x + 18f, y + 18f, 7f, p)
        val label = if (owned) outfit.name else "???"
        ui.title.textSize = 22f
        var size = 22f
        while (ui.measure(label.uppercase(), size, ui.title) > w - 18f && size > 13f) size -= 1f
        ui.text(
            c, label.uppercase(), x + w * 0.5f, y + h - 12f, size,
            if (owned) Theme.TEXT else Theme.TEXT_DIM, ui.title, false
        )

        if (equipped) {
            p.color = Theme.ACCENT
            c.drawCircle(x + w - 20f, y + 20f, 12f, p)
            p.color = 0xFF2A1D04.toInt()
            p.style = Paint.Style.STROKE
            p.strokeWidth = 3.5f
            p.strokeCap = Paint.Cap.ROUND
            c.drawLine(x + w - 26f, y + 20f, x + w - 21f, y + 25f, p)
            c.drawLine(x + w - 21f, y + 25f, x + w - 13f, y + 15f, p)
            p.style = Paint.Style.FILL
        }
    }
}
