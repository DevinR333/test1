import com.blacklab.buddybounce.game.Flight
import com.blacklab.buddybounce.game.Tuning
import com.blacklab.buddybounce.game.World
import com.blacklab.buddybounce.render.Palettes
import com.blacklab.buddybounce.render.Scenes

/**
 * Nothing below the bottom of the screen may save you.
 *
 * Platforms live on for a while after they scroll off the bottom, and they used to stay solid,
 * so a fall out of the frame could land on something the player could not see and come back up.
 * The line the platforms stop at and the line he dies at are now the same line.
 *
 * This climbs real worlds and watches every frame for two things: a bounce taken off a platform
 * whose surface was below the bottom edge, and Buddy still alive with his feet under it.
 */
private class Silent : World.Events

fun main() {
    var frames = 0
    var caughtBelow = 0
    var aliveBelow = 0
    var deaths = 0

    for (scene in Scenes.ALL) {
        Palettes.current = scene
        for (seed in 0 until 5) {
            val w = World(900f, Silent())
            w.bandFauna = IntArray(scene.bands.size) { scene.faunaFor(it) }
            w.reset()
            var f = 0
            var wasAlive = true
            while (f < 60 * 240) {
                // climb for a while, then cut the engine and let him fall out of the world
                if (w.screens < 14f) {
                    w.buddy.flight = Flight.ROCKET
                    w.buddy.flightTime = 5f
                } else if (f % 900 < 450) {
                    w.buddy.flight = Flight.NONE
                    w.buddy.flightTime = 0f
                }
                w.update(1f / 60f, 0f, false, 0f, false)
                f++
                frames++

                if (w.finished) { if (wasAlive) deaths++; break }
                wasAlive = w.buddy.alive && !w.buddy.dying
                if (!wasAlive) continue

                if (w.buddy.y < w.camY) aliveBelow++
                // anything solid under the edge that he is standing on
                for (p in w.platforms.items) {
                    if (!p.alive || p.state != 0) continue
                    if (p.y >= w.camY) continue
                    if (w.buddy.vy > 0f && kotlin.math.abs(w.buddy.y - p.y) < 6f) caughtBelow++
                }
            }
        }
    }

    println("Watched $frames frames across ${Scenes.ALL.size} worlds; $deaths falls ended a run.")
    println("  bounces taken off a platform below the edge : $caughtBelow")
    println("  frames alive with his feet below the edge   : $aliveBelow")
    println()
    if (caughtBelow == 0 && aliveBelow == 0) {
        println("ALL GOOD - below the bottom of the screen there is nothing, and no way back")
    } else {
        println("FAIL - the bottom of the screen is still not the bottom")
        System.exit(1)
    }
}
