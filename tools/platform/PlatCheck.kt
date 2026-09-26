import android.graphics.Rec
import com.blacklab.buddybounce.game.Boost
import com.blacklab.buddybounce.game.PlatKind
import com.blacklab.buddybounce.game.Platform
import com.blacklab.buddybounce.render.Art
import com.blacklab.buddybounce.render.GameRenderer
import com.blacklab.buddybounce.render.Palettes
import com.blacklab.buddybounce.render.Scenes

/**
 * A platform must draw the SAME NUMBER OF MARKS however squashed it is.
 *
 * Landing on one squashes it - 28% shorter and 5% wider - and every world's design divides
 * itself into slats, segments, spikes or bumps by asking how many fit along it. Asked with the
 * squashed size, the answer jumps by nearly half, so extra lines appear on the plank the instant
 * you touch it and vanish again a beat later. The chocolate bar went from eight segments to
 * thirteen on every bounce.
 *
 * The count is a property of the PLATFORM, not of the frame it is being drawn in, so the number
 * of ops it records must not move when only the squash does.
 */
private var failures = 0

fun main() {
    val renderer = GameRenderer(Art(1f))
    val squashes = floatArrayOf(0f, 0.25f, 0.5f, 0.75f, 1f)
    val kinds = intArrayOf(PlatKind.SOLID, PlatKind.SLIDER, PlatKind.HOVER, PlatKind.CRUMBLE, PlatKind.FRAGILE)
    val boosts = intArrayOf(Boost.NONE, Boost.SPRING, Boost.TRAMPOLINE)
    // every width a platform is ever generated at, so a count that is stable at one width but
    // tips over at another is caught too
    val widths = floatArrayOf(150f, 190f, 240f, 300f, 360f, 430f)

    var cases = 0
    for (scene in Scenes.ALL) {
        Palettes.current = scene
        for (kind in kinds) for (boost in boosts) for (width in widths) {
            cases++
            var first = -1
            var firstSq = 0f
            for (sq in squashes) {
                val plat = Platform()
                plat.reset()
                plat.alive = true
                plat.kind = kind
                plat.boost = boost
                plat.w = width
                plat.x = 450f
                plat.y = 0f
                plat.hitAnim = sq
                plat.boostAnim = sq
                plat.alpha = 1f
                plat.seed = 7
                plat.phase = 1.1f
                Rec.clear()
                renderer.drawPlatform(android.graphics.Canvas(), plat, Palettes.get(0), 800f)
                val n = Rec.ops.size
                if (first < 0) { first = n; firstSq = sq; continue }
                if (n != first) {
                    failures++
                    if (failures <= 12) {
                        println("FAIL  %-14s kind %d boost %d w %.0f : %d marks at squash %.2f, %d at %.2f"
                            .format(scene.name, kind, boost, width, first, firstSq, n, sq))
                    }
                }
            }
        }
    }

    println()
    println("Drew $cases platforms at ${squashes.size} squash steps each.")
    if (failures == 0) {
        println("ALL GOOD - a platform draws the same marks squashed as it does at rest")
    } else {
        println("$failures squash step(s) changed the number of marks")
        System.exit(1)
    }
}
