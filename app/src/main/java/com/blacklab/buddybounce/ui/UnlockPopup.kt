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
        /** No artwork - just something to say, and one button to say it back. */
        const val MESSAGE = 3
        /** The one-off "how do you want to steer him" card. */
        const val CONTROLS = 4
    }

    private class Entry(
        val kind: Int,
        val id: String,
        val banner: String,
        val title: String = "",
        val body: String = "",
        val button: String = "NICE"
    )

    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rect = RectF()
    private val path = Path()

    private object Id {
        const val DONE = 7701
        const val EQUIP = 7702
        const val OPTIONS = 7703
    }

    private val queue = ArrayList<Entry>()
    private var showing: Entry? = null
    private var anim = 0f

    val isShowing: Boolean get() = showing != null

    /** Queues a reveal. Spent the next time a screen that shows popups is drawn. */
    fun queue(kind: Int, id: String, banner: String) {
        queue.add(Entry(kind, id, banner))
    }

    /**
     * A card with no prize in it - what the cheat codes use.
     *
     * They used to slide a green pill across the top of the menu, which looks exactly like a
     * transient status message and disappears whether or not you read it. Anything worth
     * telling the player gets a card they have to dismiss.
     */
    fun queueMessage(banner: String, title: String, body: String, button: String = "NICE") {
        queue.add(Entry(Kind.MESSAGE, "", banner, title, body, button))
    }

    fun queueControls() {
        queue.add(Entry(Kind.CONTROLS, "", "HOW DO YOU WANT TO PLAY?", button = "GOT IT!"))
    }

    fun update(dt: Float) {
        if (showing == null && queue.isNotEmpty()) {
            val next = queue.removeAt(0)
            showing = next
            anim = 0f
            val prize = next.kind == Kind.SCENE || next.kind == Kind.OUTFIT || next.kind == Kind.TRAIL
            g.onUnlockRevealed(prize)
            if (prize) g.haptics.prize(hapticLevel(next))
        }
        if (showing != null && anim < 1f) anim = (anim + dt * 2.6f).coerceAtMost(1f)
    }

    /**
     * How hard the phone should buzz for this. The ladder runs with how rare the thing is -
     * a world is the rarest thing the machine gives out, and Heaven's own two, plus the
     * developer skin, sit at the top with it because they are not in the machine at all.
     */
    private fun hapticLevel(e: Entry): Int = when {
        e.kind == Kind.SCENE -> 3
        e.id == Outfits.HEAVEN_ONLY_ID || e.id == Trails.HEAVEN_ONLY_ID || e.id == Outfits.DEV_ID -> 3
        e.kind == Kind.OUTFIT -> 2
        else -> 1
    }

    private fun dismiss() {
        showing = null
        anim = 0f
    }

    private fun tintOf(e: Entry): Int = when (e.kind) {
        Kind.SCENE -> Scenes.of(e.id).cardTint
        Kind.TRAIL -> Trails.of(e.id)?.hot ?: Theme.ACCENT
        Kind.OUTFIT -> Outfits.of(e.id).rarity.tint
        else -> Theme.ACCENT
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
        val pop = 1f + (1f - k) * 0.06f   // a settle, not a pounce
        val tint = tintOf(e)

        val w = min(g.worldW - ui.safeLeft - ui.safeRight - 80f, 720f)
        val h = min(Theme.SCREEN_H - ui.safeTop - ui.safeBottom - 120f, 980f)
        val x = (g.worldW - w) * 0.5f
        val y = (Theme.SCREEN_H - h) * 0.5f

        // Nothing underneath may be tapped while this is up. The card is drawn last, so without
        // this the buttons behind it would still take the press.
        ui.setInputClip(x, y, x + w, y + h)

        // The whole thing - dimmed background included - fades up as ONE layer.
        //
        // It used to be assembled from parts that arrived at different rates: the scrim and the
        // rays faded in on k while the panel and its text were already at full opacity, and the
        // card started at 125% and shrank onto the screen. So the first frames showed a solid
        // card over an almost-clear background, snapping about as it settled - which reads as a
        // glitch rather than as an entrance. One layer, one alpha, and a much smaller settle.
        val layer = c.saveLayerAlpha(null, (255 * k).toInt())
        ui.scrim(c, g.worldW, Theme.SCREEN_H, 0.72f)

        c.save()
        c.scale(pop, pop, g.worldW * 0.5f, Theme.SCREEN_H * 0.5f)

        g.art.drawGlow(c, g.worldW * 0.5f, y + h * 0.42f, w * 1.1f, tint, 0.35f)
        // Everything that comes through here is rare by definition, so every one gets the rays.
        p.reset(); p.isAntiAlias = true
        for (i in 0 until 12) {
            val a = ui.time * 0.5f + i * 0.5236f
            p.color = ColorX.withAlpha(tint, 0.16f)
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
        // Only a prize gets the category pill - a message or the chooser has no category.
        val label = when (e.kind) {
            Kind.SCENE -> "NEW WORLD"
            Kind.TRAIL -> "NEW TRAIL"
            Kind.OUTFIT -> "NEW OUTFIT"
            else -> ""
        }
        if (label.isNotEmpty()) {
            ui.pill(c, g.worldW * 0.5f - 110f, y + 122f, 220f, 52f, ColorX.withAlpha(tint, 0.25f))
            ui.text(c, label, g.worldW * 0.5f, y + 158f, 28f, tint, ui.title, false, 210f)
        }

        val by = y + h - 96f
        // Same clip as the machine's reveal - nothing the prize draws may reach its name.
        c.save()
        c.clipRect(x + 8f, y + 186f, x + w - 8f, by - 86f)
        when (e.kind) {
            Kind.OUTFIT -> g.drawPosedBuddy(
                c, g.worldW * 0.5f, y + h * 0.72f, min(w / 300f, h / 620f) * 1.05f, e.id, ui.time
            )
            Kind.TRAIL -> g.drawTrailPreview(c, g.worldW * 0.5f, y + h * 0.46f, w * 0.52f, h * 0.22f, e.id, ui.time)
            Kind.SCENE -> scenePreview(c, e.id, g.worldW * 0.5f, y + h * 0.46f, w * 0.52f, h * 0.30f)
            Kind.CONTROLS -> {}
            else -> {}
        }
        c.restore()
        // The chooser is interactive, so it is NOT inside the clip - a clipped button still
        // takes taps where it cannot draw, which is the trap the worlds list fell into.
        if (e.kind == Kind.CONTROLS) controlChooser(c, x, y, w, h)

        if (e.kind == Kind.MESSAGE) {
            ui.text(c, e.title.uppercase(), g.worldW * 0.5f, y + h * 0.44f, 48f, Theme.TEXT, ui.title, true, w - 60f)
            paragraph(c, e.body, g.worldW * 0.5f, y + h * 0.52f, w - 96f, 30f, Theme.TEXT_DIM)
        } else if (e.kind != Kind.CONTROLS) {
            ui.text(c, nameOf(e).uppercase(), g.worldW * 0.5f, y + h - 168f, 48f, Theme.TEXT, ui.title, true, w - 60f)
            ui.text(
                c, blurbOf(e), g.worldW * 0.5f, y + h - 124f, 30f,
                Theme.TEXT_DIM, ui.body, false, w - 60f
            )
        }

        // A prize can be put on from here; a message or a chooser only needs acknowledging.
        val canEquip = e.kind == Kind.SCENE || e.kind == Kind.OUTFIT || e.kind == Kind.TRAIL
        if (canEquip) {
            val cw = (w - 80f - 20f) / 2f
            val equipLabel = if (e.kind == Kind.SCENE) "PLAY IT" else "EQUIP"
            if (ui.button(c, Id.EQUIP, x + 40f, by, cw, 78f, equipLabel, Ui.ButtonStyle.SECONDARY)) {
                g.tap()
                equip(e)
                dismiss()
            }
            if (ui.button(c, Id.DONE, x + 40f + cw + 20f, by, cw, 78f, e.button)) {
                g.tap()
                dismiss()
            }
        } else if (ui.button(c, Id.DONE, x + 40f, by, w - 80f, 78f, e.button, Ui.ButtonStyle.PRIMARY)) {
            g.tap()
            dismiss()
        }
        c.restore()
        c.restoreToCount(layer)
        ui.clearInputClip()
    }

    /** Wraps [text] to [maxW] and draws it as centred lines from [topY] down. */
    private fun paragraph(
        c: Canvas, text: String, cx: Float, topY: Float, maxW: Float, size: Float, color: Int
    ) {
        val ui = g.ui
        val words = text.split(' ')
        val line = StringBuilder()
        var y = topY
        for (word in words) {
            val candidate = if (line.isEmpty()) word else "$line $word"
            if (ui.measure(candidate, size, ui.body) > maxW && line.isNotEmpty()) {
                ui.text(c, line.toString(), cx, y, size, color, ui.body, false, maxW)
                y += size * 1.34f
                line.setLength(0)
                line.append(word)
            } else {
                line.setLength(0)
                line.append(candidate)
            }
        }
        if (line.isNotEmpty()) ui.text(c, line.toString(), cx, y, size, color, ui.body, false, maxW)
    }

    /** The three ways to steer him, with what each one actually feels like. */
    private fun controlChooser(c: Canvas, x: Float, y: Float, w: Float, h: Float) {
        val ui = g.ui
        val labels = arrayOf("TILT", "SWIPE", "BOTH")
        val blurbs = arrayOf(
            "Lean the phone the way you want him to go. Hands stay clear of the screen, so you " +
                "can see everything you are about to land on.",
            "Drag anywhere on the glass and he follows your thumb. Exact, and it works lying " +
                "down or on a table, where tilt does not.",
            "Both at once. Tilt for the long drifts, a thumb on the glass when you need to be " +
                "precise about a platform."
        )
        val picked = g.save.controlMode.coerceIn(0, 2)
        val sw = w - 96f
        val sx = x + 48f
        val sy = y + h * 0.28f
        val chosen = ui.segmented(c, Id.OPTIONS, sx, sy, sw, 76f, labels, picked)
        if (chosen != picked) {
            g.tap()
            g.save.controlMode = chosen
        }
        paragraph(c, blurbs[chosen], g.worldW * 0.5f, sy + 140f, sw - 24f, 30f, Theme.TEXT_DIM)
        ui.text(
            c, "You can change this any time in Settings.", g.worldW * 0.5f, y + h - 150f, 26f,
            ColorX.withAlpha(Theme.TEXT_DIM, 0.8f), ui.body, false, sw
        )
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
