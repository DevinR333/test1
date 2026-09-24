import android.graphics.Rec
import com.blacklab.buddybounce.game.Tuning
import com.blacklab.buddybounce.render.Art
import com.blacklab.buddybounce.render.Backdrop
import com.blacklab.buddybounce.render.Scenes
import com.blacklab.buddybounce.render.Palettes
import kotlin.math.abs

/**
 * Looks for the backdrop CHANGING ALL AT ONCE as the camera creeps upward.
 *
 * A seam you can see standing still is one problem; a step you see while moving is another. The
 * parallax bands repeat, and a repeat that scrolls out of range stops being drawn. While every
 * fill stopped at a fixed depth that only ever removed a patch. Now that fills run past the
 * bottom of the frame, a repeat popping in or out adds or removes colour over the WHOLE lower
 * screen - which would read as the background jumping to a different shade as you climb.
 *
 * So: walk the camera up in small steps and measure how much ink lands in the frame at each
 * one. Between two positions a hand's breadth apart that figure should barely move.
 */
private const val W = 900f
private const val VIEW = Tuning.VIEW_H

/** Ink inside the frame, as a fraction of the frame, weighted by how opaque it is. */
private fun inked(backdrop: Backdrop, biome: Int, blend: Float, camY: Float): Float {
    Rec.clear()
    backdrop.draw(android.graphics.Canvas(), W, camY, 12.5f, biome, blend)
    var sum = 0f
    for (op in Rec.ops) {
        if (op.kind == "color") continue
        val x0 = maxOf(op.x0, 0f); val x1 = minOf(op.x1, W)
        val y0 = maxOf(op.y0, 0f); val y1 = minOf(op.y1, VIEW)
        if (x1 <= x0 || y1 <= y0) continue
        if (x1 - x0 >= W * 0.98f && y1 - y0 >= VIEW * 0.98f) continue   // the sky wash
        val a = ((op.color ushr 24) and 0xFF) / 255f
        sum += (x1 - x0) * (y1 - y0) * a
    }
    return sum / (W * VIEW)
}

fun main() {
    val art = Art(1f)
    val backdrop = Backdrop(art)
    // one step is about a tenth of a screen of climbing - far less than a jump you would notice
    val step = VIEW * 0.1f
    var worst = 0f
    var worstWhere = ""
    val bad = ArrayList<String>()

    for (scene in Scenes.ALL) {
        Palettes.current = scene
        for (band in scene.bands.indices) {
            var prev = -1f
            var camY = band * 4f * VIEW
            val end = (band * 4f + 4f) * VIEW
            while (camY <= end) {
                val now = inked(backdrop, band, 0f, camY)
                if (prev >= 0f) {
                    val jump = abs(now - prev)
                    if (jump > worst) {
                        worst = jump
                        worstWhere = "${scene.name} / ${scene.bands[band].name} @ camY=%.0f".format(camY)
                    }
                    // a tenth of the frame appearing or vanishing in one small step is a pop
                    if (jump > 0.10f) {
                        bad.add("%-34s camY %7.0f  %.3f -> %.3f  (%+.3f)"
                            .format("${scene.name}/${scene.bands[band].name}", camY, prev, now, now - prev))
                    }
                }
                prev = now
                camY += step
            }
        }
    }

    println("Largest change over one tenth-screen step: %.3f of the frame".format(worst))
    println("  at $worstWhere")
    println()
    if (bad.isEmpty()) {
        println("nothing pops in or out")
    } else {
        println("${bad.size} pop(s):")
        for (b in bad.take(25)) println("   $b")
        kotlin.system.exitProcess(1)
    }
}
