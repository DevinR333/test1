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
 * What the ledge GUARANTEES is one landing: whatever he was falling into when he died, a
 * Second Life hands him back standing on something. It cannot guarantee the jump after that,
 * and it never could - that one is the player's.
 *
 * This used to measure how many revived runs survived four seconds with no steering, which was
 * a fair comparison while a fall out of the bottom of the frame could still be caught by a
 * platform down there. Now that the bottom of the screen is the floor, that number is dominated
 * by the missing thumb on the glass rather than by the placement, and it stopped separating the
 * two. So it checks the thing the ledge is actually for: after a revive, the next thing that
 * happens to him is a LANDING, never a death.
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

/** True if the first thing that happens after the revive is a landing rather than a death. */
private fun landsBeforeDying(w: World): Boolean {
    var t = 0f
    var wasFalling = w.buddy.vy <= 0f
    while (t < 6f) {
        w.update(DT, 0f, false, 0f, false)
        t += DT
        if (w.finished || !w.buddy.alive || w.buddy.dying) return false
        // a bounce: he was going down and is now going up
        if (wasFalling && w.buddy.vy > 0f) return true
        wasFalling = w.buddy.vy <= 0f
    }
    // never fell at all in six seconds - he is airborne and safe, which is not a failure
    return true
}

fun main() {
    Palettes.current = Scenes.ALL[0]
    var lost = 0

    for (i in 0 until TRIALS) {
        val w = fallenWorld(i, 18f + (i % 7))
        w.revive()
        if (!landsBeforeDying(w)) lost++
    }

    fun pct(n: Int) = "%.1f%%".format(100f * n / TRIALS)
    println("Revived $TRIALS falls and watched what happened first.")
    println("  died before touching anything  ${pct(lost)}")
    println()
    // Not zero, and it is worth being exact about why. Putting him back on a platform that is
    // already there fixed the common case - it went from 17% to 1% - but he can still be handed
    // back into a stretch so sparse that the bounce off it reaches nothing, and building him a
    // staircase every time would be a different power-up. Two per hundred is the line: below it
    // the placement is doing its job, above it something has regressed.
    val bar = TRIALS / 50
    if (lost <= bar) {
        println("PASS  a Second Life puts him back on something ($lost of $TRIALS did not, bar is $bar)")
    } else {
        println("FAIL  $lost revive(s) died before he had touched a thing, over the bar of $bar")
        System.exit(1)
    }
}
