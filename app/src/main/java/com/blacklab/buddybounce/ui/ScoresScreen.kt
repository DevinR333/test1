package com.blacklab.buddybounce.ui

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import com.blacklab.buddybounce.Game
import com.blacklab.buddybounce.render.ColorX
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.min

/** The local top ten. */
class ScoresScreen(private val g: Game) {

    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rect = RectF()
    private val dateFmt = SimpleDateFormat("d MMM", Locale.getDefault())

    private object Id {
        const val BACK = 5001
    }

    fun draw(c: Canvas) {
        val ui = g.ui
        val h = Theme.SCREEN_H
        val rise = (1f - g.screenAnim) * 50f

        if (ui.backButton(c, Id.BACK, ui.safeLeft + 78f, ui.safeTop + 78f, 52f)) {
            g.tap(); g.goto(Game.Screen.MENU)
        }
        ui.text(c, "BEST RUNS", g.worldW * 0.5f, ui.safeTop + 100f, 66f, Theme.TEXT, ui.title)

        val entries = g.save.scores()
        val w = min(g.worldW - ui.safeLeft - ui.safeRight - 80f, 860f)
        val x = (g.worldW - w) * 0.5f
        val top = ui.safeTop + 168f + rise
        val rowH = ((h - top - ui.safeBottom - 60f) / 10f).coerceIn(74f, 112f)

        if (entries.isEmpty()) {
            ui.panel(c, x, top + 40f, w, 260f)
            ui.text(c, "No runs yet", g.worldW * 0.5f, top + 160f, 52f, Theme.TEXT, ui.title)
            ui.text(
                c, "Bounce once and you're on the board.",
                g.worldW * 0.5f, top + 216f, 32f, Theme.TEXT_DIM, ui.body, false
            )
            return
        }

        for (i in entries.indices) {
            val e = entries[i]
            val y = top + i * rowH
            val best = i == 0
            p.reset(); p.isAntiAlias = true
            p.color = if (best) 0xFF2B3550.toInt() else if (i % 2 == 0) 0xCC1A2236.toInt() else 0xCC161D2E.toInt()
            rect.set(x, y, x + w, y + rowH - 10f)
            c.drawRoundRect(rect, 20f, 20f, p)
            if (best) {
                ui.shimmer(c, x, y, w, rowH - 10f, 20f, 0.7f)
                p.style = Paint.Style.STROKE
                p.strokeWidth = 3f
                p.color = ColorX.withAlpha(Theme.ACCENT, 0.8f)
                c.drawRoundRect(rect, 20f, 20f, p)
                p.style = Paint.Style.FILL
            }

            val baseline = y + rowH * 0.5f + 12f
            val rankColor = when (i) {
                0 -> Theme.ACCENT
                1 -> 0xFFCFD8E8.toInt()
                2 -> 0xFFD9A06B.toInt()
                else -> Theme.TEXT_DIM
            }
            ui.text(c, "${i + 1}", x + 46f, baseline, 44f, rankColor, ui.title, false)
            ui.text(c, e.name.uppercase(), x + 96f, baseline, 40f, Theme.TEXT, ui.bodyLeft, false)
            ui.text(c, e.score.toString(), x + w - 34f, baseline, 46f, if (best) Theme.ACCENT else Theme.TEXT, ui.bodyRight, false)
            if (e.whenMs > 0L) {
                ui.text(
                    c, dateFmt.format(Date(e.whenMs)), x + w - 34f, baseline + 30f, 24f,
                    ColorX.withAlpha(Theme.TEXT_DIM, 0.8f), ui.bodyRight, false
                )
            }
        }
    }
}
