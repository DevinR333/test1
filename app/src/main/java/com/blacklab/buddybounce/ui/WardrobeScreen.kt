package com.blacklab.buddybounce.ui

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import com.blacklab.buddybounce.Game
import com.blacklab.buddybounce.data.Outfits
import com.blacklab.buddybounce.data.Trails
import com.blacklab.buddybounce.game.Tuning
import com.blacklab.buddybounce.render.ColorX
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
        const val TABS = 3003
        const val RAND_OUTFIT = 3004
        const val RAND_TRAIL = 3005
        const val RAND_ALL = 3006
        const val GHOST = 3007
    }

    /** Two collections share this screen; the tab strip at the top switches between them. */
    private object Tab {
        const val OUTFITS = 0
        const val TRAILS = 1
    }

    private val tabLabels = arrayOf("OUTFITS", "TRAILS")
    private var tab = Tab.OUTFITS
    private var selected = 0
    private var trailSelected = 0
    private val scroll = Scroller()
    private var maxScroll = 0f

    /**
     * The outfits this player is allowed to see. Everything on this screen indexes into THIS,
     * never [Outfits.ALL] - the developer skin is absent from the list until it is earned, so the
     * two run out of step the moment it is unlocked.
     */
    private fun outfits(): List<Outfits.Outfit> = Outfits.visible(g.outfitOwned(Outfits.DEV_ID))

    fun draw(c: Canvas) {
        val ui = g.ui
        val wide = g.worldW > Theme.SCREEN_H * 1.12f
        val rise = (1f - g.screenAnim) * 50f

        // keep the selection on the equipped outfit when arriving
        if (selected == 0 && g.equippedOutfit != Outfits.DEFAULT_ID) {
            val idx = outfits().indexOfFirst { it.id == g.equippedOutfit }
            if (idx > 0) selected = idx
        }
        if (trailSelected == 0 && g.save.equippedTrail != Trails.NONE_ID) {
            // +1 because card 0 of the trail grid is the "no trail" entry, so the catalogue
            // index and the card index are off by one.
            val idx = Trails.ALL.indexOfFirst { it.id == g.save.equippedTrail }
            if (idx >= 0) trailSelected = idx + 1
        }

        handleDrag(g.ui.frameDt)

        if (ui.backButton(c, Id.BACK, ui.safeLeft + 78f, ui.safeTop + 78f, 52f)) {
            g.tap(); g.goto(Game.Screen.MENU)
        }
        ui.text(c, "WARDROBE", g.worldW * 0.5f, ui.safeTop + 96f, 62f, Theme.TEXT, ui.title)
        val counts = if (tab == Tab.OUTFITS) {
            "${g.ownedCount()} of ${Outfits.collectableCount(g.outfitOwned(Outfits.DEV_ID))} outfits"
        } else {
            "${g.save.trailCount()} of ${Trails.count} trails"
        }
        ui.text(c, counts, g.worldW * 0.5f, ui.safeTop + 140f, 30f, Theme.TEXT_DIM, ui.body, false)

        // tab strip
        val tw = min(g.worldW - ui.safeLeft - ui.safeRight - 120f, 520f)
        val picked = ui.segmented(c, Id.TABS, (g.worldW - tw) * 0.5f, ui.safeTop + 156f, tw, 64f, tabLabels, tab)
        if (picked != tab) {
            g.tap()
            tab = picked
            scroll.reset()
        }

        val randY = ui.safeTop + 230f
        drawRandomizers(c, randY)
        val top = drawGhostToggle(c, randY + 92f)
        // Whatever is left after the header, tabs and dice - which is what the preview and the
        // grid have to share. Nothing here is a fixed number, so a squarer screen or a tall
        // gesture bar shrinks the preview rather than pushing the grid off the bottom.
        val avail = (Theme.SCREEN_H - top - ui.safeBottom - 24f).coerceAtLeast(240f)
        val bodyW = g.worldW - ui.safeLeft - ui.safeRight - 72f
        if (wide) {
            val previewW = min(g.worldW * 0.38f, 560f)
            drawPreview(c, ui.safeLeft + 40f, top + rise, previewW, avail)
            val gridX = ui.safeLeft + previewW + 80f
            drawGrid(c, gridX, top, g.worldW - gridX - ui.safeRight - 40f, avail, 4)
        } else {
            // The preview takes a share, never so much that the grid stops being usable.
            val previewH = (avail * 0.44f).coerceIn(260f, 430f).coerceAtMost(avail - 190f)
            drawPreview(c, ui.safeLeft + 36f, top + rise, bodyW, previewH)
            val gridTop = top + previewH + 22f
            drawGrid(c, ui.safeLeft + 36f, gridTop, bodyW,
                (Theme.SCREEN_H - gridTop - ui.safeBottom - 24f).coerceAtLeast(150f), 3)
        }
    }

    private fun handleDrag(dt: Float) {
        val ui = g.ui
        scroll.update(dt, ui.pointerDown, ui.scrollDrag, maxScroll)
    }

    private fun drawPreview(c: Canvas, x: Float, y: Float, w: Float, h: Float) {
        if (tab == Tab.TRAILS) { drawTrailPreview(c, x, y, w, h); return }
        val ui = g.ui
        val list = outfits()
        val outfit = list[selected.coerceIn(0, list.size - 1)]
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
            // The equipped trail belongs in the OUTFIT preview too. Without it "randomize all"
            // looked broken: it rolled a new trail, but the dog you were looking at never showed
            // one, because trails were only drawn on the trails tab.
            if (g.save.equippedTrail != Trails.NONE_ID) {
                g.drawTrailSample(
                    c, x + w * 0.5f - w * 0.1f, buddyY - h * 0.34f, w * 0.62f, h * 0.2f,
                    g.save.equippedTrail, ui.time
                )
            }
            g.drawPosedBuddy(c, x + w * 0.5f, buddyY, min(w / 300f, h / 430f) * 1.5f, outfit.id, ui.time)
        } else {
            p.color = 0xFF2A3350.toInt()
            c.drawCircle(x + w * 0.5f, buddyY - 110f, 96f, p)
            ui.text(c, "?", x + w * 0.5f, buddyY - 72f, 130f, 0xFF4A5675.toInt(), ui.title, false)
        }

        ui.text(c, g.displayName(outfit.id, outfit.name).uppercase(),
            x + w * 0.5f, y + h - 108f, 44f, Theme.TEXT, ui.title, false, w - 50f)
        val lockLine = g.displayBlurb(outfit.id, outfit.blurb, owned)
        var blurbSize = 27f
        while (ui.measure(lockLine, blurbSize, ui.body) > w - 50f && blurbSize > 17f) blurbSize -= 1f
        ui.text(c, lockLine, x + w * 0.5f, y + h - 68f, blurbSize, Theme.TEXT_DIM, ui.body, false)

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
        val gap = 16f
        val cardW = (w - gap * (cols - 1)) / cols
        val cardH = cardW * 1.16f
        val count = if (tab == Tab.OUTFITS) outfits().size else Trails.ALL.size + 1
        val rows = ceil(count / cols.toFloat()).toInt()
        maxScroll = (rows * (cardH + gap) - gap - h).coerceAtLeast(0f)
        val scrollY = scroll.y

        c.save()
        c.clipRect(x - 4f, y, x + w + 4f, y + h)
        // the hit rects have to be clipped too, or a card scrolled up out of the viewport keeps
        // taking taps aimed at the back button and the tabs above it
        g.ui.setInputClip(x - 4f, y, x + w + 4f, y + h)
        for (i in 0 until count) {
            val row = i / cols
            val col = i % cols
            val cx = x + col * (cardW + gap)
            val cy = y + row * (cardH + gap) - scrollY
            if (cy + cardH < y - 20f || cy > y + h + 20f) continue
            if (tab == Tab.OUTFITS) drawCard(c, i, cx, cy, cardW, cardH)
            else drawTrailCard(c, i, cx, cy, cardW, cardH)
        }
        g.ui.clearInputClip()
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
        val outfit = outfits()[index]
        val owned = g.outfitOwned(outfit.id)
        val equipped = g.equippedOutfit == outfit.id
        val isSelected = index == selected

        val clicked = ui.button(c, Id.CARD + index, x, y, w, h, "", Ui.ButtonStyle.GHOST)
        if (clicked && !scroll.suppressTap) {
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
        val label = if (owned) g.displayName(outfit.id, outfit.name) else "???"
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

    // -------------------------------------------------------------------------------------
    // trails
    // -------------------------------------------------------------------------------------

    /** Index 0 of the trail grid is "no trail"; the rest map onto [Trails.ALL]. */
    private fun trailAt(index: Int): Trails.Trail? =
        if (index <= 0) null else Trails.ALL.getOrNull(index - 1)

    private fun rarityTint(rarity: Int): Int = when (rarity) {
        Trails.Rarity.COMMON -> 0xFF9FB2CC.toInt()
        Trails.Rarity.RARE -> 0xFF5AC8FA.toInt()
        Trails.Rarity.EPIC -> 0xFFB98CFF.toInt()
        else -> 0xFFFFC14A.toInt()
    }

    private fun drawTrailPreview(c: Canvas, x: Float, y: Float, w: Float, h: Float) {
        val ui = g.ui
        val trail = trailAt(trailSelected)
        val id = trail?.id ?: Trails.NONE_ID
        val owned = trail == null || g.save.ownsTrail(id)
        val equipped = g.save.equippedTrail == id
        val tint = if (trail == null) Theme.TEXT_DIM else rarityTint(trail.rarity)

        ui.panel(c, x, y, w, h)
        if (trail != null && trail.rarity == Trails.Rarity.LEGENDARY && owned) {
            ui.shimmer(c, x, y, w, h, Theme.RADIUS, 0.8f)
        }

        p.reset(); p.isAntiAlias = true
        p.color = ColorX.withAlpha(tint, 0.20f)
        rect.set(x, y, x + w, y + 86f)
        c.drawRoundRect(rect, Theme.RADIUS, Theme.RADIUS, p)
        rect.set(x, y + 50f, x + w, y + 86f)
        c.drawRect(rect, p)
        val rarityLabel = if (trail == null) "NO TRAIL" else Trails.rarityName(trail.rarity)
        ui.text(c, rarityLabel, x + w * 0.5f, y + 56f, 30f, tint, ui.title, false)

        // Buddy wearing it, with the trail streaming off behind him.
        val buddyY = y + h * 0.62f
        if (owned) {
            if (trail != null) {
                g.drawTrailSample(c, x + w * 0.5f, buddyY - h * 0.3f, w * 0.8f, h * 0.3f, id, ui.time)
            }
            g.drawPosedBuddy(c, x + w * 0.5f, buddyY, min(w / 300f, h / 430f) * 1.25f, g.equippedOutfit, ui.time)
        } else {
            p.color = 0xFF2A3350.toInt()
            c.drawCircle(x + w * 0.5f, buddyY - 100f, 88f, p)
            ui.text(c, "?", x + w * 0.5f, buddyY - 64f, 120f, 0xFF4A5675.toInt(), ui.title, false)
        }

        val name = if (trail == null) "No Trail" else g.displayName(trail.id, trail.name)
        ui.text(c, name.uppercase(), x + w * 0.5f, y + h - 108f, 42f, Theme.TEXT, ui.title, false, w - 50f)
        val blurb = when {
            trail == null -> "Clean paws. Nothing behind him."
            else -> g.displayBlurb(trail.id, trail.blurb, owned)
        }
        var size = 27f
        while (ui.measure(blurb, size, ui.body) > w - 50f && size > 17f) size -= 1f
        ui.text(c, blurb, x + w * 0.5f, y + h - 68f, size, Theme.TEXT_DIM, ui.body, false)

        val bw = min(w - 80f, 420f)
        val bx = x + (w - bw) * 0.5f
        val by = y + h - 56f
        if (owned) {
            if (equipped) {
                ui.pill(c, bx, by - 6f, bw, 62f, 0x332AE08A)
                ui.text(c, "WEARING IT", x + w * 0.5f, by + 36f, 34f, Theme.GOOD, ui.title, false)
            } else if (ui.button(c, Id.EQUIP, bx, by - 14f, bw, 78f, "WEAR IT", Ui.ButtonStyle.PRIMARY)) {
                g.tap()
                g.save.equippedTrail = id
            }
        } else if (ui.button(c, Id.EQUIP, bx, by - 14f, bw, 78f, "TO THE MACHINE", Ui.ButtonStyle.SECONDARY)) {
            g.tap(); g.goto(Game.Screen.GACHA)
        }
    }

    private fun drawTrailCard(c: Canvas, index: Int, x: Float, y: Float, w: Float, h: Float) {
        val ui = g.ui
        val trail = trailAt(index)
        val id = trail?.id ?: Trails.NONE_ID
        val owned = trail == null || g.save.ownsTrail(id)
        val equipped = g.save.equippedTrail == id
        val isSelected = index == trailSelected
        val tint = if (trail == null) Theme.TEXT_DIM else rarityTint(trail.rarity)

        val clicked = ui.button(c, Id.CARD + index, x, y, w, h, "", Ui.ButtonStyle.GHOST)
        if (clicked && !scroll.suppressTap) {
            g.tap()
            trailSelected = index
            if (owned) g.save.equippedTrail = id
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
            owned -> ColorX.withAlpha(tint, 0.55f)
            else -> 0x22FFFFFF
        }
        c.drawRoundRect(rect, 22f, 22f, p)
        p.style = Paint.Style.FILL

        if (owned && trail != null) {
            c.save()
            c.clipRect(x + 3f, y + 3f, x + w - 3f, y + h - 34f)
            g.drawTrailSample(
                c, x + w * 0.5f, y + h * 0.42f, w * 0.9f, h * 0.46f, id,
                ui.time * 0.7f + index * 0.5f
            )
            c.restore()
        } else if (trail == null) {
            p.style = Paint.Style.STROKE
            p.strokeWidth = 4f
            p.color = ColorX.withAlpha(Theme.TEXT_DIM, 0.7f)
            c.drawCircle(x + w * 0.5f, y + h * 0.42f, w * 0.18f, p)
            c.drawLine(
                x + w * 0.5f - w * 0.13f, y + h * 0.42f + w * 0.13f,
                x + w * 0.5f + w * 0.13f, y + h * 0.42f - w * 0.13f, p
            )
            p.style = Paint.Style.FILL
        } else {
            p.color = 0xFF2A3350.toInt()
            c.drawCircle(x + w * 0.5f, y + h * 0.42f, w * 0.22f, p)
            ui.text(c, "?", x + w * 0.5f, y + h * 0.42f + w * 0.11f, w * 0.30f, 0xFF4A5675.toInt(), ui.title, false)
        }

        p.color = tint
        c.drawCircle(x + 18f, y + 18f, 7f, p)
        val label = when {
            trail == null -> "None"
            owned -> g.displayName(trail.id, trail.name).removeSuffix(" Trail")
            else -> "???"
        }
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

    /**
     * Three dice. They only ever roll what the player owns (see Game.randomize*), and land on
     * something different from what is on now when there is anything else to land on.
     */
    private fun drawRandomizers(c: Canvas, y: Float) {
        val ui = g.ui
        val left = ui.safeLeft + 36f
        val w = g.worldW - ui.safeLeft - ui.safeRight - 72f
        val gap = 12f
        val bw = (w - gap * 2f) / 3f
        val h = 76f

        if (ui.button(c, Id.RAND_OUTFIT, left, y, bw, h, "OUTFIT", Ui.ButtonStyle.SECONDARY,
                sublabel = "randomize")) {
            g.tap()
            if (g.randomizeOutfit()) {
                tab = Tab.OUTFITS
                syncSelectionToEquipped()
            }
        }
        if (ui.button(c, Id.RAND_TRAIL, left + bw + gap, y, bw, h, "TRAIL", Ui.ButtonStyle.SECONDARY,
                sublabel = "randomize")) {
            g.tap()
            if (g.randomizeTrail()) {
                tab = Tab.TRAILS
                syncSelectionToEquipped()
            }
        }
        if (ui.button(c, Id.RAND_ALL, left + (bw + gap) * 2f, y, bw, h, "ALL", Ui.ButtonStyle.PRIMARY,
                sublabel = "randomize")) {
            g.tap()
            if (g.randomizeAll()) syncSelectionToEquipped()
        }
    }

    /**
     * The blessed look, once a thousand halos have been collected in Heaven. It is a toggle
     * rather than an outfit because it goes OVER whatever he is wearing - the only cosmetic in
     * the game that changes the dog rather than dressing him.
     */
    private fun drawGhostToggle(c: Canvas, y: Float): Float {
        if (!g.save.ghostUnlocked) return y
        val ui = g.ui
        val left = ui.safeLeft + 36f
        val w = g.worldW - ui.safeLeft - ui.safeRight - 72f
        val on = g.save.ghostEnabled
        if (ui.button(
                c, Id.GHOST, left, y, w, 72f,
                if (on) "BLESSED: ON" else "BLESSED: OFF",
                if (on) Ui.ButtonStyle.PRIMARY else Ui.ButtonStyle.SECONDARY,
                sublabel = "wings, halo and all"
            )
        ) {
            g.tap()
            g.save.ghostEnabled = !on
        }
        return y + 84f
    }

    /** Moves the preview and the grid highlight onto whatever is now equipped. */
    private fun syncSelectionToEquipped() {
        val oi = outfits().indexOfFirst { it.id == g.equippedOutfit }
        if (oi >= 0) selected = oi
        val trailId = g.save.equippedTrail
        trailSelected = if (trailId == Trails.NONE_ID) 0
        else Trails.ALL.indexOfFirst { it.id == trailId }.let { if (it >= 0) it + 1 else 0 }
        scroll.reset()
    }
}
