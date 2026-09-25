package com.blacklab.buddybounce.ui

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import com.blacklab.buddybounce.Game
import com.blacklab.buddybounce.data.Save
import kotlin.math.min

/** Orientation, controls, sound - everything the player can bend to their setup. */
class SettingsScreen(private val g: Game) {

    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rect = RectF()
    private val path = android.graphics.Path()

    private object Id {
        const val BACK = 6001
        const val ORIENTATION = 6002
        const val CONTROLS = 6003
        const val SENS = 6004
        const val CALIBRATE = 6005
        const val INVERT = 6006
        const val SOUND = 6007
        const val HAPTICS = 6008
        const val NAME = 6010
        const val MUSIC = 6011
        const val MUSIC_VOL = 6012
        const val SFX_VOL = 6013
    }

    private val orientationLabels = arrayOf("PORTRAIT", "LANDSCAPE")
    private val controlLabels = arrayOf("TILT", "SWIPE", "BOTH")

    fun draw(c: Canvas) {
        val ui = g.ui
        val rise = (1f - g.screenAnim) * 50f

        if (ui.backButton(c, Id.BACK, ui.safeLeft + 78f, ui.safeTop + 78f, 52f)) {
            g.tap(); g.goto(if (g.previousScreen == Game.Screen.PAUSE) Game.Screen.PAUSE else Game.Screen.MENU)
        }
        ui.text(c, "SETTINGS", g.worldW * 0.5f, ui.safeTop + 100f, 66f, Theme.TEXT, ui.title, true, ui.headerWidth(g.worldW))

        val wide = g.worldW > Theme.SCREEN_H * 1.12f
        val colW = min(g.worldW - ui.safeLeft - ui.safeRight - 80f, if (wide) 700f else 820f)
        val gap = 20f

        if (wide) {
            val totalW = colW * 2f + gap
            val x0 = (g.worldW - totalW) * 0.5f
            drawColumnA(c, x0, ui.safeTop + 170f + rise, colW)
            drawColumnB(c, x0 + colW + gap, ui.safeTop + 170f + rise, colW)
        } else {
            val x = (g.worldW - colW) * 0.5f
            val y = drawColumnA(c, x, ui.safeTop + 170f + rise, colW)
            drawColumnB(c, x, y + 26f, colW)
        }
    }

    /**
     * A section heading, returning the y the next widget should start at.
     *
     * The headings used to be drawn with their BASELINE on the running y and the widget placed
     * 18 below - but a 30pt cap reaches about 22 units ABOVE its baseline, so every heading
     * climbed back into the control above it. "FEEL" sat on the effects slider, "PLAYER" on the
     * vibration toggle, and so on down the column. Taking the ascender into account here fixes
     * all of them at once, and keeps the spacing identical between sections.
     */
    private fun heading(c: Canvas, s: String, x: Float, y: Float): Float {
        g.ui.text(c, s, x + 8f, y + 26f, 30f, Theme.TEXT_DIM, g.ui.bodyLeft, false)
        return y + 46f
    }

    /** @return the y coordinate just past the last row drawn. */
    private fun drawColumnA(c: Canvas, x: Float, top: Float, w: Float): Float {
        val ui = g.ui
        val save = g.save
        var y = top

        y = heading(c, "SCREEN", x, y)
        val orientation = if (save.landscape) 1 else 0
        val picked = ui.segmented(c, Id.ORIENTATION, x, y, w, 92f, orientationLabels, orientation)
        if (picked != orientation) {
            g.tap()
            save.landscape = picked == 1
            g.host.setLandscape(save.landscape)
        }
        y += 116f

        y = heading(c, "CONTROLS", x, y)
        val mode = save.controlMode
        val pickedMode = ui.segmented(c, Id.CONTROLS, x, y, w, 92f, controlLabels, mode)
        if (pickedMode != mode) {
            g.tap()
            save.controlMode = pickedMode
            g.controls.clearTouch()
        }
        y += 104f

        val tilt = save.controlMode != Save.CONTROL_TOUCH
        if (tilt) {
            val sens = (save.tiltSensitivity - 0.5f) / 1.3f
            val v = ui.slider(c, Id.SENS, x, y, w, 120f, "TILT SENSITIVITY  " + tiltLabel(save.tiltSensitivity), sens)
            if (v != sens) save.tiltSensitivity = 0.5f + v * 1.3f
            y += 134f

            if (ui.button(c, Id.CALIBRATE, x, y, w, 88f, "SET NEUTRAL TILT",
                    Ui.ButtonStyle.SECONDARY, sublabel = "hold the phone how you like to play")) {
                g.tap()
                g.controls.calibrate()
                g.flashScreen(0.25f)
            }
            y += 100f

            if (ui.toggle(c, Id.INVERT, x, y, w, 88f, "Invert tilt", save.invertTilt)) {
                g.tap(); save.invertTilt = !save.invertTilt
            }
            y += 100f
        }

        return y
    }

    private fun drawColumnB(c: Canvas, x: Float, top: Float, w: Float): Float {
        val ui = g.ui
        val save = g.save
        var y = top

        y = heading(c, "SOUND", x, y)

        // The two mute buttons sit side by side as icons: a music note and a megaphone, each
        // with a slash struck through it when that mix is off. Faster to read at a glance than
        // two rows of words, and it leaves the width for the sliders underneath.
        val half = (w - 16f) * 0.5f
        val iconH = 92f
        if (ui.button(c, Id.MUSIC, x, y, half, iconH, "", if (save.musicOn) Ui.ButtonStyle.SECONDARY else Ui.ButtonStyle.GHOST)) {
            g.tap()
            save.musicOn = !save.musicOn
            g.music.refresh()
        }
        noteIcon(c, x + half * 0.5f, y + iconH * 0.5f, 26f, save.musicOn)
        if (ui.button(c, Id.SOUND, x + half + 16f, y, half, iconH, "", if (save.soundOn) Ui.ButtonStyle.SECONDARY else Ui.ButtonStyle.GHOST)) {
            save.soundOn = !save.soundOn
            g.tap()
        }
        megaphoneIcon(c, x + half + 16f + half * 0.5f, y + iconH * 0.5f, 26f, save.soundOn)
        y += iconH + 12f

        val mv = ui.slider(c, Id.MUSIC_VOL, x, y, w, 110f, "MUSIC  " + pct(save.musicVolume), save.musicVolume)
        if (mv != save.musicVolume) {
            save.musicVolume = mv
            g.music.refresh()
        }
        y += 118f
        val sv = ui.slider(c, Id.SFX_VOL, x, y, w, 110f, "EFFECTS  " + pct(save.sfxVolume), save.sfxVolume)
        if (sv != save.sfxVolume) {
            save.sfxVolume = sv
            // a tick at the new level, so you can hear what you are setting
            g.tap()
        }
        y += 126f

        y = heading(c, "FEEL", x, y)
        if (ui.toggle(c, Id.HAPTICS, x, y, w, 88f, "Vibration", save.hapticsOn)) {
            save.hapticsOn = !save.hapticsOn
            g.tap()
        }
        y += 116f

        y = heading(c, "PLAYER", x, y)
        if (ui.button(c, Id.NAME, x, y, w, 88f, "CHANGE NAME",
                Ui.ButtonStyle.SECONDARY, sublabel = g.save.playerName.ifEmpty { "not set" })) {
            g.tap(); g.host.promptName(save.playerName, "Change your name")
        }
        y += 112f

        ui.text(
            c, "Buddy Bounce  •  " + g.ownedCount() + "/" + com.blacklab.buddybounce.data.Outfits.collectableCount(g::outfitOwned) +
                " outfits  •  " + g.save.trailCount() + "/" + com.blacklab.buddybounce.data.Trails.count +
                " trails  •  " + save.totalRuns + " runs",
            x + w * 0.5f, y + 20f, 26f, Theme.TEXT_DIM, ui.body, false, w - 40f
        )
        return y + 40f
    }

    private fun pct(v: Float): String = (v * 100f).toInt().toString() + "%"

    /** A quaver. Struck through with a slash when the music is muted. */
    private fun noteIcon(c: Canvas, cx: Float, cy: Float, s: Float, on: Boolean) {
        val col = if (on) Theme.TEXT else Theme.TEXT_DIM
        p.reset(); p.isAntiAlias = true
        p.color = col
        // head
        c.save()
        c.translate(cx, cy)
        c.rotate(-18f)
        rect.set(-s * 0.62f, s * 0.06f, -s * 0.08f, s * 0.6f)
        c.drawOval(rect, p)
        c.restore()
        // stem
        rect.set(cx + s * 0.16f, cy - s * 0.84f, cx + s * 0.32f, cy + s * 0.3f)
        c.drawRect(rect, p)
        // flag
        path.reset()
        path.moveTo(cx + s * 0.32f, cy - s * 0.84f)
        path.quadTo(cx + s * 0.96f, cy - s * 0.54f, cx + s * 0.56f, cy - s * 0.1f)
        path.quadTo(cx + s * 0.74f, cy - s * 0.5f, cx + s * 0.32f, cy - s * 0.52f)
        path.close()
        c.drawPath(path, p)
        if (!on) slash(c, cx, cy, s)
    }

    /** A megaphone, with the same slash when the effects are muted. */
    private fun megaphoneIcon(c: Canvas, cx: Float, cy: Float, s: Float, on: Boolean) {
        val col = if (on) Theme.TEXT else Theme.TEXT_DIM
        p.reset(); p.isAntiAlias = true
        p.color = col
        // the cone, opening to the right
        path.reset()
        path.moveTo(cx - s * 0.7f, cy - s * 0.34f)
        path.lineTo(cx + s * 0.1f, cy - s * 0.8f)
        path.lineTo(cx + s * 0.1f, cy + s * 0.8f)
        path.lineTo(cx - s * 0.7f, cy + s * 0.34f)
        path.close()
        c.drawPath(path, p)
        // the handle
        rect.set(cx - s * 0.98f, cy - s * 0.26f, cx - s * 0.66f, cy + s * 0.26f)
        c.drawRoundRect(rect, s * 0.1f, s * 0.1f, p)
        if (on) {
            // sound coming out of it
            p.style = Paint.Style.STROKE
            p.strokeWidth = s * 0.14f
            for (i in 0 until 2) {
                rect.set(
                    cx + s * (0.1f + i * 0.3f), cy - s * (0.42f + i * 0.26f),
                    cx + s * (0.5f + i * 0.34f), cy + s * (0.42f + i * 0.26f)
                )
                c.drawArc(rect, -54f, 108f, false, p)
            }
            p.style = Paint.Style.FILL
        } else {
            slash(c, cx, cy, s)
        }
    }

    /** The diagonal "off" stroke, drawn with a dark backing so it reads over the icon. */
    private fun slash(c: Canvas, cx: Float, cy: Float, s: Float) {
        p.style = Paint.Style.STROKE
        p.strokeCap = Paint.Cap.ROUND
        p.strokeWidth = s * 0.34f
        p.color = Theme.PANEL_SOLID
        c.drawLine(cx - s * 0.95f, cy + s * 0.95f, cx + s * 0.95f, cy - s * 0.95f, p)
        p.strokeWidth = s * 0.17f
        p.color = Theme.BAD
        c.drawLine(cx - s * 0.9f, cy + s * 0.9f, cx + s * 0.9f, cy - s * 0.9f, p)
        p.style = Paint.Style.FILL
    }

    private fun tiltLabel(v: Float): String = when {
        v < 0.75f -> "GENTLE"
        v < 1.15f -> "NORMAL"
        v < 1.5f -> "SHARP"
        else -> "TWITCHY"
    }
}
