package com.blacklab.buddybounce.ui

import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import com.blacklab.buddybounce.Game
import com.blacklab.buddybounce.data.Outfits
import com.blacklab.buddybounce.data.Trails
import com.blacklab.buddybounce.game.MathX.smoothstep
import com.blacklab.buddybounce.render.ColorX
import com.blacklab.buddybounce.render.Scenes
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/**
 * The reveal card for something earned OUTSIDE the prize machine - Heaven opening, and the two
 * things Heaven itself pays out.
 *
 * These used to be a green pill sliding in at the top of the menu, which is the same furniture
 * the game uses for "free spins on" and other housekeeping. Unlocking a secret world after
 * collecting everything in the game deserves the treatment the machine gives a legendary: the
 * scrim, the rays, the shimmering panel and the thing itself sitting in the middle of it. This
 * is that card, driven from a queue so several unlocks landing at once are shown one at a time
 * rather than on top of each other.
 *
 * It is drawn OVER whatever screen is up, and while it is up it takes the input clip, so the
 * screen underneath cannot be tapped through it.
 */
class UnlockPopup(private val g: Game) {

    object Kind {
        const val SCENE = 0
        const val OUTFIT = 1
        const val TRAIL = 2
    }

    private class Entry(val kind: Int, val id: String, val banner: String)

    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rect = RectF()
    private val path = Path()

    private object Id {
        const val DONE = 7701
        const val EQUIP = 7702
    }

    private val queue = ArrayList<Entry>()
    private var showing: Entry? = null
    private var anim = 0f

    val isShowing: Boolean get() = showing != null

    /** Queues a reveal. Spent the next time a screen that shows popups is drawn. */
    fun queue(kind: Int, id: String, banner: String) {
        queue.add(Entry(kind, id, banner))
    }

    fun update(dt: Float) {
        if (showing == null && queue.isNotEmpty()) {
            showing = queue.removeAt(0)
            anim = 0f
            g.onUnlockRevealed()
        }
        if (showing != null && anim < 1f) anim = (anim + dt * 2.6f).coerceAtMost(1f)
    }

    private fun dismiss() {
        showing = null
        anim = 0f
    }

    private fun tintOf(e: Entry): Int = when (e.kind) {
        Kind.SCENE -> Scenes.of(e.id).cardTint
        Kind.TRAIL -> Trails.of(e.id)?.hot ?: Theme.ACCENT
        else -> Outfits.of(e.id).rarity.tint
    }

    private fun nameOf(e: Entry): String = when (e.kind) {
        Kind.SCENE -> Scenes.of(e.id).name
        Kind.TRAIL -> Trails.of(e.id)?.name ?: ""
        else -> Outfits.of(e.id).name
    }

    private fun blurbOf(e: Entry): String = when (e.kind) {
        Kind.SCENE -> Scenes.of(e.id).blurb
        Kind.TRAIL -> Trails.of(e.id)?.blurb ?: ""
        else -> Outfits.of(e.id).blurb
    }

    fun draw(c: Canvas) {
        val e = showing ?: return
        val ui = g.ui
        val k = smoothstep(0f, 1f, anim)
        val pop = 1f + (1f - k) * 0.25f
        val tint = tintOf(e)

        ui.scrim(c, g.worldW, Theme.SCREEN_H, 0.72f * k)

        val w = min(g.worldW - ui.safeLeft - ui.safeRight - 80f, 720f)
        val h = min(Theme.SCREEN_H - ui.safeTop - ui.safeBottom - 120f, 980f)
        val x = (g.worldW - w) * 0.5f
        val y = (Theme.SCREEN_H - h) * 0.5f

        // Nothing underneath may be tapped while this is up. The card is drawn last, so without
        // this the buttons behind it would still take the press.
        ui.setInputClip(x, y, x + w, y + h)

        c.save()
        c.scale(pop, pop, g.worldW * 0.5f, Theme.SCREEN_H * 0.5f)

        g.art.drawGlow(c, g.worldW * 0.5f, y + h * 0.42f, w * 1.1f, tint, 0.35f * k)
        // Everything that comes through here is rare by definition, so every one gets the rays.
        p.reset(); p.isAntiAlias = true
        for (i in 0 until 12) {
            val a = ui.time * 0.5f + i * 0.5236f
            p.color = ColorX.withAlpha(tint, 0.16f * k)
            path.reset()
            path.moveTo(g.worldW * 0.5f, y + h * 0.42f)
            path.lineTo(g.worldW * 0.5f + cos(a) * w * 1.2f - 40f, y + h * 0.42f + sin(a) * w * 1.2f)
            path.lineTo(g.worldW * 0.5f + cos(a) * w * 1.2f + 40f, y + h * 0.42f + sin(a) * w * 1.2f)
            path.close()
            c.drawPath(path, p)
        }

        ui.panel(c, x, y, w, h)
        ui.shimmer(c, x, y, w, h, Theme.RADIUS, 1f)

        ui.text(c, e.banner, g.worldW * 0.5f, y + 96f, 52f, Theme.ACCENT, ui.title, true, w - 48f)
        val label = when (e.kind) {
            Kind.SCENE -> "NEW WORLD"
            Kind.TRAIL -> "NEW TRAIL"
            else -> "NEW OUTFIT"
        }
        ui.pill(c, g.worldW * 0.5f - 110f, y + 122f, 220f, 52f, ColorX.withAlpha(tint, 0.25f))
        ui.text(c, label, g.worldW * 0.5f, y + 158f, 28f, tint, ui.title, false, 210f)

        when (e.kind) {
            Kind.OUTFIT -> g.drawPosedBuddy(
                c, g.worldW * 0.5f, y + h * 0.72f, min(w / 300f, h / 620f) * 1.05f, e.id, ui.time
            )
            Kind.TRAIL -> g.drawTrailPreview(c, g.worldW * 0.5f, y + h * 0.46f, w * 0.52f, h * 0.22f, e.id, ui.time)
            else -> scenePreview(c, e.id, g.worldW * 0.5f, y + h * 0.46f, w * 0.52f, h * 0.30f)
        }

        ui.text(c, nameOf(e).uppercase(), g.worldW * 0.5f, y + h - 168f, 48f, Theme.TEXT, ui.title, true, w - 60f)
        ui.text(
            c, blurbOf(e), g.worldW * 0.5f, y + h - 124f, 30f,
            Theme.TEXT_DIM, ui.body, false, w - 60f
        )

        val by = y + h - 96f
        val cw = (w - 80f - 20f) / 2f
        val equipLabel = if (e.kind == Kind.SCENE) "PLAY IT" else "EQUIP"
        if (ui.button(c, Id.EQUIP, x + 40f, by, cw, 78f, equipLabel, Ui.ButtonStyle.SECONDARY)) {
            g.tap()
            equip(e)
            dismiss()
        }
        if (ui.button(c, Id.DONE, x + 40f + cw + 20f, by, cw, 78f, "NICE")) {
            g.tap()
            dismiss()
        }
        c.restore()
        ui.clearInputClip()
    }

    private fun equip(e: Entry) {
        when (e.kind) {
            Kind.SCENE -> {
                g.save.selectedScene = e.id
                g.applyScene()
            }
            Kind.TRAIL -> g.save.equippedTrail = e.id
            else -> g.save.equippedOutfit = e.id
        }
        g.flashScreen(0.25f)
    }

    /** The scene's sky stack, bottom band first - the same at-a-glance preview the machine uses. */
    private fun scenePreview(c: Canvas, id: String, cx: Float, cy: Float, w: Float, h: Float) {
        val scene = Scenes.of(id)
        val bands = scene.bands
        val colors = IntArray(bands.size + 1)
        for (i in bands.indices) colors[bands.size - 1 - i] = bands[i].skyMid
        colors[bands.size] = bands[0].skyLow
        p.reset(); p.isAntiAlias = true
        p.shader = LinearGradient(cx, cy - h * 0.5f, cx, cy + h * 0.5f, colors, null, Shader.TileMode.CLAMP)
        rect.set(cx - w * 0.5f, cy - h * 0.5f, cx + w * 0.5f, cy + h * 0.5f)
        c.drawRoundRect(rect, 22f, 22f, p)
        p.shader = null
        p.color = ColorX.withAlpha(bands[0].platTop, 0.95f)
        rect.set(cx - w * 0.5f, cy + h * 0.5f - 26f, cx + w * 0.5f, cy + h * 0.5f)
        c.drawRoundRect(rect, 12f, 12f, p)
        p.style = Paint.Style.STROKE
        p.strokeWidth = 4f
        p.color = ColorX.withAlpha(scene.cardTint, 0.8f)
        rect.set(cx - w * 0.5f, cy - h * 0.5f, cx + w * 0.5f, cy + h * 0.5f)
        c.drawRoundRect(rect, 22f, 22f, p)
        p.style = Paint.Style.FILL
    }
}
