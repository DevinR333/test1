import android.graphics.Rec
import com.blacklab.buddybounce.game.Tuning
import com.blacklab.buddybounce.render.Art
import com.blacklab.buddybounce.render.Backdrop
import com.blacklab.buddybounce.render.Scenes
import com.blacklab.buddybounce.render.Palettes
import kotlin.math.abs

/**
 * WHAT pops, not just that something does.
 *
 * Climb in very small steps and, at each one, total up how much of the frame each colour
 * covers. Between two steps a hand's breadth apart almost nothing should change; a colour that
 * gains or loses a large slice of the frame in one step is the pop you can see, and naming its
 * colour and the shape kind that carried it points straight at the line that drew it.
 *
 * Areas come from the recorded bounds, so they over-count shapes that are mostly hollow - which
 * does not matter, because what is being read is the CHANGE in one colour's own number.
 */
private const val W = 900f
private const val VIEW = Tuning.VIEW_H
private const val STEPS = 240
/** One step of the climb, in world units. About a fifth of a jump. */
private const val STEP = 26f

private fun area(op: Rec.Op): Float {
    val x0 = maxOf(op.x0, 0f); val x1 = minOf(op.x1, W)
    val y0 = maxOf(op.y0, 0f); val y1 = minOf(op.y1, VIEW)
    if (x1 <= x0 || y1 <= y0) return 0f
    val a = ((op.color ushr 24) and 0xFF) / 255f
    return (x1 - x0) * (y1 - y0) * a
}

fun main() {
    val art = Art(1f)
    val backdrop = Backdrop(art)
    val full = W * VIEW
    println("%-14s %-18s %8s  %s".format("scene", "band", "worst", "what jumped"))
    for (scene in Scenes.ALL) {
        Palettes.current = scene
        for (band in scene.bands.indices) {
            val bandBase = (band * Tuning.BIOME_SPAN - 0.5f) * VIEW
            var prev: HashMap<String, Float>? = null
            var worst = 0f
            var from = 0f
            var to = 0f
            var what = ""
            var atY = 0f
            for (step in 0 until STEPS) {
                val camY = bandBase + step * STEP
                Rec.clear()
                backdrop.draw(android.graphics.Canvas(), W, camY, 12.5f, band, 0f, bandBase)
                val now = HashMap<String, Float>()
                for (op in Rec.ops) {
                    if (op.stroke) continue
                    // a gradient's paint colour is whatever was left in the brush, so keying a
                    // gradient by colour reports jumps that are not on the screen at all
                    if (op.shader != null) continue
                    val key = op.kind + " #%06X".format(op.color and 0xFFFFFF)
                    now[key] = (now[key] ?: 0f) + area(op)
                }
                val p = prev
                if (p != null) {
                    for (k in p.keys + now.keys) {
                        val d = abs((now[k] ?: 0f) - (p[k] ?: 0f)) / full
                        if (d > worst) {
                            worst = d; what = k; atY = camY - bandBase
                            from = (p[k] ?: 0f) / full; to = (now[k] ?: 0f) / full
                        }
                    }
                }
                prev = now
            }
            val flag = if (worst > 0.06f) "  <-- POP" else ""
            println("%-14s %-18s %7.1f%%  %-22s %5.1f%% -> %5.1f%%  at %.2f screens in%s".format(
                scene.id, scene.bands[band].name, worst * 100f, what,
                from * 100f, to * 100f, atY / VIEW, flag))
        }
    }
}
