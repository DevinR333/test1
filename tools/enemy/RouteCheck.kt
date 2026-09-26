import com.blacklab.buddybounce.game.EnemyKind
import com.blacklab.buddybounce.game.Flight
import com.blacklab.buddybounce.game.PlatKind
import com.blacklab.buddybounce.game.Tuning
import com.blacklab.buddybounce.game.World
import com.blacklab.buddybounce.render.Palettes
import com.blacklab.buddybounce.render.Scenes
import kotlin.math.abs

/**
 * There must always be a way up that does not go through an enemy.
 *
 * An enemy was already kept off the platform its own row had just placed, but that platform can
 * be a crumble - and if the solid one beside it is the one the enemy is parked over, the only
 * way on is to take a hit. Getting hit should be a price the player chooses, never the price of
 * carrying on.
 *
 * This climbs a real World through every world and a spread of seeds, and checks every enemy
 * that exists against the platforms around it: at least one that is still there after you touch
 * it - not a crumble, not a fragile - has to be clear of the strip the enemy denies you. For a
 * bee that strip is its whole swing, not where it happens to be this frame.
 */
private class Silent : World.Events

private fun envelope(kind: Int, halfW: Float, amp: Float): Float = when (kind) {
    EnemyKind.BEE -> halfW + amp
    else -> halfW
}

fun main() {
    var blocked = 0
    var enemiesSeen = 0
    var runs = 0
    val report = StringBuilder()

    for (scene in Scenes.ALL) {
        Palettes.current = scene
        for (seed in 0 until 6) {
            runs++
            val w = World(900f, Silent())
            w.bandFauna = IntArray(scene.bands.size) { scene.faunaFor(it) }
            w.reset()
            // climb a long way so every band and every enemy kind is generated
            var f = 0
            while (f < 60 * 700 && w.screens < 46f) {
                w.buddy.flight = Flight.ROCKET
                w.buddy.flightTime = 5f
                w.update(1f / 60f, 0f, false, 0f, false)
                f++
                if (f % 40 != 0) continue

                for (e in w.enemies.items) {
                    if (!e.alive || e.dying) continue
                    // Only what is still ahead of the player. An enemy below the camera is not
                    // in the way of a climb, and the game deliberately never moves one that is
                    // already on screen - a creature that jumps sideways while you are looking
                    // at it is a worse bug than the one being fixed.
                    if (e.y < w.camY) continue
                    enemiesSeen++
                    val deny = envelope(e.kind, e.halfW, e.amp)
                    val window = 420f
                    var dependable = 0
                    var clear = 0
                    for (p in w.platforms.items) {
                        if (!p.alive || p.isGround || p.state != 0) continue
                        if (p.kind == PlatKind.CRUMBLE || p.kind == PlatKind.FRAGILE) continue
                        if (abs(p.y - e.y) > window) continue
                        dependable++
                        if (abs(p.x - e.baseX) > deny + p.w * 0.5f) clear++
                    }
                    if (dependable > 0 && clear == 0) {
                        blocked++
                        if (blocked <= 8) {
                            report.append("  %-14s seed %d  kind %d at (%.0f, %.0f) denies all %d\n"
                                .format(scene.name, seed, e.kind, e.baseX, e.y, dependable))
                        }
                    }
                }
            }
        }
    }

    println("Climbed $runs runs and looked at $enemiesSeen enemies in place.")
    println()
    if (blocked == 0) {
        println("ALL GOOD - no enemy was ever the only way up")
    } else {
        println("$blocked enemy sighting(s) left no way up but through:")
        print(report)
        System.exit(1)
    }
}
