import android.graphics.Rec
import com.blacklab.buddybounce.game.Tuning
import com.blacklab.buddybounce.render.Art
import com.blacklab.buddybounce.render.Backdrop
import com.blacklab.buddybounce.render.Scenes
import com.blacklab.buddybounce.render.Palettes

/**
 * Hunts for the hard horizontal rules that run right across the backdrop.
 *
 * Ground, water, rock and buildings are silhouettes filled DOWNWARD from their own line, and
 * each one used to stop at a fixed depth. Whenever that depth landed inside the frame you got a
 * straight edge across the whole screen with sky underneath it. They are easy to miss by eye,
 * because whether one shows depends on the camera height, so this looks for them directly:
 *
 *   a filled shape that spans nearly the full width, is not a full-screen wash, is solid
 *   enough to see, and whose BOTTOM edge lands inside the frame
 *
 * is a seam. Every scene, every band, 48 camera heights each.
 */
private const val W = 900f
private const val VIEW = Tuning.VIEW_H

/** Ignore the very top and bottom - an edge there is off-frame or under the haze. */
private const val TOP_MARGIN = 30f
private const val BOTTOM_MARGIN = 40f
/** How much of the width a shape has to cover before its bottom edge reads as a rule. */
private const val WIDE = 0.94f
/** Below this the edge is too faint to see. */
private const val VISIBLE_ALPHA = 0.13f
/**
 * How tall a shape has to be before its bottom edge counts as a seam rather than as art.
 *
 * Plenty of things are legitimately full width and hard edged - a fence rail, a roof parapet,
 * the shelf under the cakes, the plinth under the organ pipes, a line of icing. They are all
 * THIN. A seam comes from a silhouette hundreds of units tall that simply stops.
 */
private const val TALL = 320f

private class Seam(val scene: String, val band: String, val camY: Float, val y: Float,
                   val kind: String, val alpha: Float, val color: Int)

/**
 * True when the shape's bottom really is a straight rule right across, rather than a wavy edge
 * that happens to have a low point - the underside of the sea, for instance, is a rippled line
 * and is meant to be seen. Reads the recorded geometry rather than guessing from the bounds.
 */
private fun flatEdge(op: android.graphics.Rec.Op, bottom: Boolean): Boolean {
    if (op.kind == "rect" || op.kind == "rrect") return true
    val d = op.svg ?: return false
    val edge = if (bottom) op.y1 else op.y0
    var lo = Float.MAX_VALUE
    var hi = -Float.MAX_VALUE
    var i = 0
    val pts = Regex("(-?[0-9.]+) (-?[0-9.]+)").findAll(d)
    for (m in pts) {
        val x = m.groupValues[1].toFloatOrNull() ?: continue
        val y = m.groupValues[2].toFloatOrNull() ?: continue
        val on = if (bottom) y >= edge - 1.5f else y <= edge + 1.5f
        if (on) { if (x < lo) lo = x; if (x > hi) hi = x; i++ }
    }
    return i >= 2 && (minOf(hi, W) - maxOf(lo, 0f)) >= W * WIDE
}

fun main() {
    val art = Art(1f)
    val backdrop = Backdrop(art)
    val seams = ArrayList<Seam>()
    var samples = 0

    for (scene in Scenes.ALL) {
        Palettes.current = scene
        for (band in scene.bands.indices) {
            for (step in 0 until 48) {
                val camY = (band * 4f + step * 4f / 48f) * VIEW
                Rec.clear()
                backdrop.draw(android.graphics.Canvas(), W, camY, 12.5f, band, 0f)
                samples++
                for ((i, op) in Rec.ops.withIndex()) {
                    if (op.kind == "color" || op.stroke) continue
                    if (op.shader != null) continue          // a gradient has no hard edge
                    // a blitted cloud or glow is blurred: its bottom fades, it cannot rule a line
                    if (op.kind == "bitmap") continue
                    if (op.y1 - op.y0 < TALL) continue
                    val a = ((op.color ushr 24) and 0xFF) / 255f
                    if (a < VISIBLE_ALPHA) continue
                    val wide = (minOf(op.x1, W) - maxOf(op.x0, 0f)) >= W * WIDE
                    if (!wide) continue
                    // a shape as tall as the screen as well as as wide is a wash, not an edge
                    if (op.y0 <= 0f && op.y1 >= VIEW) continue
                    // BOTH edges. A fill's top rules a line across the screen exactly as its
                    // bottom does - a wall painted over the wall of the repeat behind it, a roof
                    // over a roof - and only looking at bottoms is why these survived a pass
                    // that reported itself clean.
                    val edges = ArrayList<Pair<Float, Boolean>>(2)
                    if (op.y1 > TOP_MARGIN && op.y1 < VIEW - BOTTOM_MARGIN) edges.add(op.y1 to true)
                    if (op.y0 > TOP_MARGIN && op.y0 < VIEW - BOTTOM_MARGIN) edges.add(op.y0 to false)
                    if (edges.isEmpty()) continue
                    for ((edgeY, isBottom) in edges) {
                    // and only if something drawn LATER does not simply cover the edge
                    var hidden = false
                    for (j in i + 1 until Rec.ops.size) {
                        val o = Rec.ops[j]
                        if (o.stroke || o.kind == "bitmap") continue
                        if (((o.color ushr 24) and 0xFF) / 255f < 0.88f) continue
                        if ((minOf(o.x1, W) - maxOf(o.x0, 0f)) < W * WIDE) continue
                        // anything opaque laid across the line hides it, however thin - a roof
                        // parapet, the lip of a plinth, the kerb of a road
                        if (o.y0 <= edgeY + 2f && o.y1 >= edgeY - 2f &&
                            o.y1 - o.y0 > 6f) { hidden = true; break }
                    }
                    if (hidden) continue
                    if (!flatEdge(op, isBottom)) continue
                    seams.add(Seam(scene.name, scene.bands[band].name, camY, edgeY,
                        op.kind + (if (isBottom) " bottom" else " TOP"), a, op.color))
                    }
                }
            }
        }
    }

    println("Checked $samples camera positions across ${Scenes.ALL.size} scenes.\n")
    if (seams.isEmpty()) {
        println("no full-width fill ends inside the frame")
        return
    }
    // group, so one bad call site does not print four hundred times
    val byPlace = LinkedHashMap<String, MutableList<Seam>>()
    for (s in seams) byPlace.getOrPut("${s.scene} / ${s.band}") { ArrayList() }.add(s)
    println("${seams.size} seam(s) in ${byPlace.size} band(s):")
    for ((place, list) in byPlace) {
        val worst = list.maxByOrNull { it.alpha }!!
        println("   %-34s %4d hits  worst alpha %.2f (%s) #%06X at y=%.0f camY=%.0f"
            .format(place, list.size, worst.alpha, worst.kind, worst.color and 0xFFFFFF,
                worst.y, worst.camY))
    }
    kotlin.system.exitProcess(1)
}
