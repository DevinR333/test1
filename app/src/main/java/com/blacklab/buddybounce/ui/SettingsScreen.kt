package com.blacklab.buddybounce.ui

import android.graphics.Canvas
import com.blacklab.buddybounce.Game
import com.blacklab.buddybounce.data.Save
import com.blacklab.buddybounce.game.Tuning
import kotlin.math.min

/** Orientation, controls, sound - everything the player can bend to their setup. */
class SettingsScreen(private val g: Game) {

    private object Id {
        const val BACK = 6001
        const val ORIENTATION = 6002
        const val CONTROLS = 6003
        const val SENS = 6004
        const val CALIBRATE = 6005
        const val INVERT = 6006
        const val SOUND = 6007
        const val HAPTICS = 6008
        const val GAUGE = 6009
        const val NAME = 6010
    }

    private val orientationLabels = arrayOf("PORTRAIT", "LANDSCAPE")
    private val controlLabels = arrayOf("TILT", "SLIDE", "BOTH")

    fun draw(c: Canvas) {
        val ui = g.ui
        val rise = (1f - g.screenAnim) * 50f

        if (ui.backButton(c, Id.BACK, ui.safeLeft + 78f, ui.safeTop + 78f, 52f)) {
            g.tap(); g.goto(if (g.previousScreen == Game.Screen.PAUSE) Game.Screen.PAUSE else Game.Screen.MENU)
        }
        ui.text(c, "SETTINGS", g.worldW * 0.5f, ui.safeTop + 100f, 66f, Theme.TEXT, ui.title)

        val wide = g.worldW > Tuning.VIEW_H * 1.12f
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

    /** @return the y coordinate just past the last row drawn. */
    private fun drawColumnA(c: Canvas, x: Float, top: Float, w: Float): Float {
        val ui = g.ui
        val save = g.save
        var y = top

        ui.text(c, "SCREEN", x + 8f, y, 30f, Theme.TEXT_DIM, ui.bodyLeft, false)
        y += 18f
        val orientation = if (save.landscape) 1 else 0
        val picked = ui.segmented(c, Id.ORIENTATION, x, y, w, 92f, orientationLabels, orientation)
        if (picked != orientation) {
            g.tap()
            save.landscape = picked == 1
            g.host.setLandscape(save.landscape)
        }
        y += 116f

        ui.text(c, "CONTROLS", x + 8f, y, 30f, Theme.TEXT_DIM, ui.bodyLeft, false)
        y += 18f
        val mode = save.controlMode
        val pickedMode = ui.segmented(c, Id.CONTROLS, x, y, w, 92f, controlLabels, mode)
        if (pickedMode != mode) {
            g.tap()
            save.controlMode = pickedMode
            g.controls.clearGauge()
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

        if (save.controlMode != Save.CONTROL_TILT) {
            if (ui.toggle(c, Id.GAUGE, x, y, w, 88f, "Always show the gauge", save.showGaugeAlways)) {
                g.tap(); save.showGaugeAlways = !save.showGaugeAlways
            }
            y += 100f
        }
        return y
    }

    private fun drawColumnB(c: Canvas, x: Float, top: Float, w: Float): Float {
        val ui = g.ui
        val save = g.save
        var y = top

        ui.text(c, "FEEL", x + 8f, y, 30f, Theme.TEXT_DIM, ui.bodyLeft, false)
        y += 18f
        if (ui.toggle(c, Id.SOUND, x, y, w, 88f, "Sound effects", save.soundOn)) {
            g.tap(); save.soundOn = !save.soundOn
        }
        y += 100f
        if (ui.toggle(c, Id.HAPTICS, x, y, w, 88f, "Vibration", save.hapticsOn)) {
            save.hapticsOn = !save.hapticsOn
            g.tap()
        }
        y += 116f

        ui.text(c, "PLAYER", x + 8f, y, 30f, Theme.TEXT_DIM, ui.bodyLeft, false)
        y += 18f
        if (ui.button(c, Id.NAME, x, y, w, 88f, "CHANGE NAME",
                Ui.ButtonStyle.SECONDARY, sublabel = g.save.playerName.ifEmpty { "not set" })) {
            g.tap(); g.host.promptName(save.playerName, "Change your name")
        }
        y += 112f

        ui.text(
            c, "Buddy Bounce  •  " + g.ownedCount() + "/" + com.blacklab.buddybounce.data.Outfits.collectableCount +
                " outfits  •  " + save.totalRuns + " runs",
            x + w * 0.5f, y + 20f, 26f, Theme.TEXT_DIM, ui.body, false
        )
        return y + 40f
    }

    private fun tiltLabel(v: Float): String = when {
        v < 0.75f -> "GENTLE"
        v < 1.15f -> "NORMAL"
        v < 1.5f -> "SHARP"
        else -> "TWITCHY"
    }
}
