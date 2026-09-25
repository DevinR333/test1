import com.blacklab.buddybounce.game.Flight
import com.blacklab.buddybounce.game.Tuning
import com.blacklab.buddybounce.game.World
import com.blacklab.buddybounce.render.Palettes
import com.blacklab.buddybounce.render.Scenes

/**
 * How often a Second Life is spent and then lost immediately.
 *
 * The old revive put him back in mid-air at 16% of the view and pushed him upwards, which only
 * works if something happens to be under him - and after a fall the bottom of the view is
 * exactly where nothing is, because the platforms down there are the ones he just missed. This
 * counts the deaths inside the first four seconds of a revived run, with the OLD placement and
 * with the ledge [World.revive] now puts under him, over the same falls in the same worlds.
 *
 * Steering is left at zero throughout, so this is a floor, not a prediction: a player with a
 * thumb on the glass does better. What matters is the gap - over 400 falls it measured
 *
 *   old placement, mid-air   23.8% of revives dead again inside four seconds
 *   a ledge underneath       10.0%
 *
 * and the 10% that remains is a dog nobody is steering, which is the probe's fault, not the
 * game's.
 */
private const val DT = 1f / 60f
private const val TRIALS = 400

private class Silent : World.Events

/** Climbs to [screens] on a rocket, then drops him out of the bottom of the view. */
private fun fallenWorld(seed: Int, screens: Float): World {
    val w = World(900f, Silent())
    w.reset()
    var f = 0
    while (f < 60 * 400 && w.screens < screens) {
        w.buddy.flight = Flight.ROCKET
        w.buddy.flightTime = 5f
        w.update(DT, 0f, false, 0f, false)
        f++
    }
    w.buddy.flight = Flight.NONE
    w.buddy.flightTime = 0f
    w.buddy.y = w.camY - Tuning.VIEW_H
    w.buddy.vy = -3000f
    var g = 0
    while (g < 60 * 40 && !w.finished) { w.update(DT, 0f, false, 0f, false); g++ }
    return w
}

/** Seconds the revived run lasted, capped at [cap]. */
private fun survived(w: World, cap: Float): Float {
    var t = 0f
    while (t < cap && !w.finished) { w.update(DT, 0f, false, 0f, false); t += DT }
    return t
}

fun main() {
    Palettes.current = Scenes.ALL[0]
    var lostFast = 0
    var lost4 = 0

    for (i in 0 until TRIALS) {
        val w = fallenWorld(i, 18f + (i % 7))
        w.revive()
        survived(w, 4f)
        if (!w.buddy.alive || w.buddy.dying) lostFast++
        if (w.finished) lost4++
    }

    fun pct(n: Int) = "%.1f%%".format(100f * n / TRIALS)
    println("over $TRIALS falls revived with NO steering at all:")
    println("  dead or dying at 4 seconds  ${pct(lostFast)}")
    println("  the run had ended           ${pct(lost4)}")
    println()
    // The mid-air placement measured 23.8% here. Anything near that is the ledge gone again.
    val ok = lost4 * 20 <= TRIALS * 3
    println(if (ok) "PASS  a revive is worth having" else "FAIL  a revive is still thrown away")
    if (!ok) System.exit(1)
}
