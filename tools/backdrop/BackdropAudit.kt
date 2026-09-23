import android.graphics.Rec
import com.blacklab.buddybounce.game.Tuning
import com.blacklab.buddybounce.render.Art
import com.blacklab.buddybounce.render.Backdrop
import com.blacklab.buddybounce.render.Scenes
import com.blacklab.buddybounce.render.Palettes

/**
 * Looks at what every world's every altitude band actually PAINTS on screen, and WHERE.
 *
 * Reasoning about these by reading the code has not worked: the bands repeat, they parallax at
 * different rates, they cross-fade into each other and several of them fill downward, so
 * whether a given band puts anything behind the player is not something you can see by
 * staring at one function. This drives the real Backdrop against a Canvas that records instead
 * of painting, and counts the marks that land inside the visible screen.
 *
 * Counting the whole screen is not enough. A band can be busy up top and completely bare down
 * where Buddy actually is, which is exactly what "missing background elements" looks like. So
 * the screen is split into three zones and each is counted on its own:
 *
 *   SKY   top    0 .. 38%   far silhouettes, clouds, auroras
 *   MID   middle 38 .. 68%  the horizon the player reads as depth
 *   NEAR  bottom 68 .. 100% the ground / foreground behind the platforms
 *
 * Every band should put something in every zone. A zone that stays near zero across the
 * band's whole altitude range is a hole in the art.
 */
private const val W = 900f
private const val VIEW = Tuning.VIEW_H

private const val SKY_END = VIEW * 0.38f
private const val MID_END = VIEW * 0.68f

/** the floor each zone has to clear, averaged over the band's altitude range */
private const val NEED_SKY = 3
private const val NEED_MID = 3
private const val NEED_NEAR = 3

private class Sample(
    val sky: Int, val mid: Int, val near: Int,
    val skyCov: Float, val midCov: Float, val nearCov: Float
)

// Coverage is reported alongside the counts but is NOT a pass condition. It is measured from
// bounding boxes on a coarse grid, and the bounding box of a full-width wavy path is the whole
// band, so coverage reads near 1.0 for almost everything. It is useful only for spotting a zone
// that nothing reaches at all. The shape COUNT is what says whether a zone has anything in it.
private const val GX = 90
private const val GY = 160
private val grid = BooleanArray(GX * GY)

private fun measure(backdrop: Backdrop, biome: Int, blend: Float, camY: Float): Sample {
    Rec.clear()
    backdrop.draw(android.graphics.Canvas(), W, camY, 12.5f, biome, blend)
    var sky = 0; var mid = 0; var near = 0
    java.util.Arrays.fill(grid, false)
    for (op in Rec.ops) {
        // the full-bleed sky wash is not a "background element"; it is the paper
        if (op.kind == "color") continue
        val x0 = maxOf(op.x0, 0f); val x1 = minOf(op.x1, W)
        val y0 = maxOf(op.y0, 0f); val y1 = minOf(op.y1, VIEW)
        if (x1 <= x0 || y1 <= y0) continue
        // a shape as large as the whole screen is a wash, not an element
        if (x1 - x0 >= W * 0.98f && y1 - y0 >= VIEW * 0.98f) continue
        // count it in the zone its own middle sits in, so one tall shape is not counted thrice
        val cy = (y0 + y1) * 0.5f
        when {
            cy < SKY_END -> sky++
            cy < MID_END -> mid++
            else -> near++
        }
        // and mark the grid, so a band made of a few very large shapes (dunes, peaks) is not
        // called empty just for having a low shape count
        val cx0 = (x0 / W * GX).toInt().coerceIn(0, GX - 1)
        val cx1 = (x1 / W * GX).toInt().coerceIn(0, GX - 1)
        val cy0 = (y0 / VIEW * GY).toInt().coerceIn(0, GY - 1)
        val cy1 = (y1 / VIEW * GY).toInt().coerceIn(0, GY - 1)
        for (gy in cy0..cy1) for (gx in cx0..cx1) grid[gy * GX + gx] = true
    }
    val rowSky = (SKY_END / VIEW * GY).toInt()
    val rowMid = (MID_END / VIEW * GY).toInt()
    var a = 0; var b = 0; var d = 0
    for (gy in 0 until GY) for (gx in 0 until GX) {
        if (!grid[gy * GX + gx]) continue
        when {
            gy < rowSky -> a++
            gy < rowMid -> b++
            else -> d++
        }
    }
    return Sample(
        sky, mid, near,
        a / (rowSky * GX).toFloat(),
        b / ((rowMid - rowSky) * GX).toFloat(),
        d / ((GY - rowMid) * GX).toFloat()
    )
}

fun main() {
    val art = Art(1f)
    val backdrop = Backdrop(art)
    val holes = ArrayList<String>()

    println("Background marks by screen zone, per world per band.")
    println("SKY = top 38%   MID = middle 30%   NEAR = bottom 32% (behind the platforms)")
    println("Each band sampled across the altitude where it is the active biome.\n")

    for (scene in Scenes.ALL) {
        Palettes.current = scene
        println(scene.name)
        for (band in scene.bands.indices) {
            var sky = 0; var mid = 0; var near = 0
            var skyC = 0f; var midC = 0f; var nearC = 0f
            var worstNear = Int.MAX_VALUE; var worstNearAt = 0f
            val steps = 16
            for (step in 0 until steps) {
                val camY = (band * 4f + step * 4f / steps) * VIEW
                val s = measure(backdrop, band, 0f, camY)
                sky += s.sky; mid += s.mid; near += s.near
                skyC += s.skyCov; midC += s.midCov; nearC += s.nearCov
                if (s.near < worstNear) { worstNear = s.near; worstNearAt = camY }
            }
            sky /= steps; mid /= steps; near /= steps
            skyC /= steps; midC /= steps; nearC /= steps
            val bad = ArrayList<String>()
            if (sky < NEED_SKY) bad.add("SKY")
            if (mid < NEED_MID) bad.add("MID")
            if (near < NEED_NEAR) bad.add("NEAR")
            val flag = if (bad.isEmpty()) "ok" else "BARE " + bad.joinToString("+")
            if (bad.isNotEmpty()) holes.add("${scene.name} / ${scene.bands[band].name}: ${bad.joinToString("+")}")
            println(
                "   %-17s sky %3d/%.2f  mid %3d/%.2f  near %3d/%.2f   %-16s"
                    .format(scene.bands[band].name, sky, skyC, mid, midC, near, nearC, flag)
            )
        }
    }
    println()
    if (holes.isEmpty()) println("every band fills every zone")
    else {
        println("${holes.size} bare zone(s):")
        for (h in holes) println("   $h")
    }
}
