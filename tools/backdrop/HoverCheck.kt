import android.graphics.Rec
import com.blacklab.buddybounce.game.Tuning
import com.blacklab.buddybounce.render.Art
import com.blacklab.buddybounce.render.Backdrop
import com.blacklab.buddybounce.render.Scenes
import com.blacklab.buddybounce.render.Palettes

/**
 * Things standing in mid-air.
 *
 * SeamCheck only looks at shapes that span nearly the whole frame, because it was written for
 * the horizontal rules that run right across it. A butte, a tower, a tree or a headstone is a
 * NARROW silhouette, so it slips straight past that filter - and when its own flat bottom edge
 * lands inside the frame with nothing painted under it, it hovers. That is the "pillar things
 * are hovering" fault, and it is invisible to every check there was.
 *
 * A silhouette is rooted when SOMETHING covers the strip just below its foot, all the way
 * across its own width - the ground painted after it, or the mountain painted before it that a
 * snow cap sits on. What hovers is a foot with nothing but sky underneath. This samples that
 * strip and reports the ones that come up empty.
 */
private const val W = 900f
private const val VIEW = Tuning.VIEW_H

/** Below this a shape is a detail - a rock, a bin, a window - not a standing silhouette. */
private const val MIN_TALL = 150f
private const val MIN_WIDE = 46f
/** Fainter than this and the foot does not read as an edge. */
private const val MIN_ALPHA = 0.4f
/** How far under the foot has to be covered for it to count as rooted. */
private const val UNDER = 34f
/** Fraction of the width that has to be covered. */
private const val ROOTED = 0.82f

private class Hover(val scene: String, val band: String, val camY: Float, val op: Rec.Op)

/** Is the bottom edge a straight cut, rather than a point where a curve happens to bottom out? */
private fun flatFoot(op: Rec.Op): Boolean {
    if (op.kind == "rect" || op.kind == "rrect") return true
    val d = op.svg ?: return false
    var lo = Float.MAX_VALUE
    var hi = -Float.MAX_VALUE
    var n = 0
    for (m in Regex("(-?[0-9.]+) (-?[0-9.]+)").findAll(d)) {
        val x = m.groupValues[1].toFloatOrNull() ?: continue
        val y = m.groupValues[2].toFloatOrNull() ?: continue
        if (y >= op.y1 - 2f) { if (x < lo) lo = x; if (x > hi) hi = x; n++ }
    }
    return n >= 2 && (hi - lo) >= (op.x1 - op.x0) * 0.5f
}

fun main() {
    val art = Art(1f)
    val backdrop = Backdrop(art)
    val hits = ArrayList<Hover>()
    var samples = 0
    for (scene in Scenes.ALL) {
        Palettes.current = scene
        for (band in scene.bands.indices) {
            for (step in 0 until 24) {
                val screens = band * Tuning.BIOME_SPAN + step * Tuning.BIOME_SPAN / 24f
                val camY = (screens - 0.5f) * VIEW
                val bandBase = (band * Tuning.BIOME_SPAN - 0.5f) * VIEW
                Rec.clear()
                backdrop.draw(android.graphics.Canvas(), W, camY, 12.5f, band, 0f, bandBase)
                samples++
                for ((i, op) in Rec.ops.withIndex()) {
                    if (op.kind == "color" || op.stroke || op.kind == "bitmap") continue
                    if (op.shader != null) continue
                    if (op.y1 - op.y0 < MIN_TALL) continue
                    if (op.x1 - op.x0 < MIN_WIDE) continue
                    if (((op.color ushr 24) and 0xFF) / 255f < MIN_ALPHA) continue
                    // a foot below the frame is rooted by definition
                    if (op.y1 >= VIEW - 8f) continue
                    if (op.y1 <= 0f) continue
                    if (!flatFoot(op)) continue
                    // sample across its own width: is the strip under the foot painted over?
                    var covered = 0
                    val n = 12
                    for (k in 0 until n) {
                        val x = op.x0 + (op.x1 - op.x0) * (k + 0.5f) / n
                        if (x < 0f || x > W) { covered++; continue }
                        var ok = false
                        for ((j, o) in Rec.ops.withIndex()) {
                            // ANY order. A snow cap sits on the peak painted before it and a
                            // butte sits in the canyon floor painted after it; both are rooted.
                            // What hovers is a foot with nothing but sky under it.
                            if (j == i || o.stroke || o.kind == "bitmap") continue
                            if (o.kind == "color" || o.shader != null) continue
                            // faint counts: a pale house wall behind a pale roof is still not
                            // sky, and demanding near-opacity here reported roofs, spires and
                            // belfries as hovering when they were sitting on their own bodies
                            if (((o.color ushr 24) and 0xFF) / 255f < 0.28f) continue
                            if (o.x0 > x || o.x1 < x) continue
                            if (o.y0 <= op.y1 + 2f && o.y1 >= op.y1 + UNDER) { ok = true; break }
                        }
                        if (ok) covered++
                    }
                    if (covered >= n * ROOTED) continue
                    hits.add(Hover(scene.name, scene.bands[band].name, camY, op))
                }
            }
        }
    }
    println("Checked $samples camera positions across ${Scenes.ALL.size} scenes.\n")
    if (hits.isEmpty()) { println("nothing standing in mid-air"); return }
    val byBand = hits.groupBy { it.scene + " / " + it.band }
    println("${hits.size} hovering silhouette(s) in ${byBand.size} band(s):")
    for ((k, v) in byBand.entries.sortedByDescending { it.value.size }) {
        val w = v.maxByOrNull { it.op.y1 - it.op.y0 }!!
        println("   %-36s %4d hits  tallest %.0f x %.0f  foot y=%.0f  %s #%06X".format(
            k, v.size, w.op.x1 - w.op.x0, w.op.y1 - w.op.y0, w.op.y1, w.op.kind,
            w.op.color and 0xFFFFFF))
    }
}
