import com.blacklab.buddybounce.game.EnemyKind
import com.blacklab.buddybounce.game.Tuning
import com.blacklab.buddybounce.game.World
import com.blacklab.buddybounce.game.DeathCause

/**
 * Drop Buddy onto every enemy, from every speed and every offset, and check what happens.
 *
 * "Sometimes stomping kills them and sometimes it kills me" is the hardest kind of bug to pin
 * down by playing, because the case that goes wrong depends on the fall speed, on which way he
 * was drifting, and - once the enemy is a bee that bobs or a crow that flies - on which way IT
 * was moving at the moment they met. This runs the real World through all of those.
 *
 * Two rules under test, and they are opposite halves of the same one:
 *
 *   - if his feet come down THROUGH the enemy, he stomps it and lives, whatever the fall speed,
 *     whichever way he was drifting, and whichever way IT was moving when they met;
 *   - if his feet never reach it, nothing happens to it. Coming from above used to be the whole
 *     test, so falling anywhere above an enemy killed it - a screen up, in clear air.
 *
 * Every world, because each one dresses the four roles as its own creature and each creature is
 * its own size (see game/EnemyBox.kt).
 */
private class Probe : World.Events {
    var stomped = 0
    var died = false
    var diedRising = false
    override fun onStomp(enemy: com.blacklab.buddybounce.game.Enemy) { stomped++ }
    override fun onDeath(cause: Int) { if (cause == DeathCause.ENEMY) died = true }
}

private val KIND_NAME = mapOf(
    EnemyKind.BEE to "bee", EnemyKind.CROW to "crow",
    EnemyKind.STORM to "storm", EnemyKind.RIFT to "rift"
)

/** A world dressed as one of the ten, with one enemy of [kind] in it, ready to be dropped on. */
private fun rig(probe: Probe, fauna: Int, kind: Int, offX: Int, phase: Int, dropAbove: Float): World {
    val w = World(900f, probe)
    w.bandFauna = IntArray(1) { fauna }
    w.reset()
    val e = w.enemies.obtain()
    e.kind = kind
    e.fauna = fauna
    e.x = 450f + offX
    e.y = w.buddy.y - 150f
    e.baseX = e.x
    e.baseY = e.y
    e.phase = phase * 0.785f
    e.t = phase * 0.31f
    e.amp = if (kind == EnemyKind.BEE) 240f else 0f
    e.vx = if (kind == EnemyKind.CROW) 260f else 0f
    e.alive = true
    e.dying = false
    w.buddy.x = 450f
    w.buddy.y = e.y + e.halfH + dropAbove
    return w
}

fun main() {
    var cases = 0
    var hits = 0
    var rising = 0
    var bad = 0
    val report = StringBuilder()

    for (fauna in 0 until com.blacklab.buddybounce.game.EnemyBox.ROWS)
    for ((kind, name) in KIND_NAME) {
        // every fall speed from a gentle drop to terminal, every sideways drift, and the enemy
        // caught at every point of its own little cycle
        for (fall in intArrayOf(-200, -600, -1200, -1900, -2600, -3200)) {
            for (drift in intArrayOf(-600, 0, 600)) {
                for (phase in 0 until 4) {
                    for (offX in intArrayOf(-40, 0, 40)) {
                        cases++
                        val probe = Probe()
                        val w = rig(probe, fauna, kind, offX, phase, 26f)
                        w.buddy.vy = fall.toFloat()
                        w.buddy.vx = drift.toFloat()

                        // long enough to pass all the way through it
                        var t = 0
                        while (t < 24 && !probe.died && probe.stomped == 0) {
                            w.update(1f / 60f, 0f, false, 0f, false)
                            t++
                        }
                        if (probe.stomped > 0) hits++
                        // Dying on the way UP is not this bug. He bounces off a platform under
                        // the enemy and rises into it, which is exactly what should kill him.
                        if (probe.died && w.buddy.vy > 0f) { rising++; continue }
                        // Missing is allowed - he drifts sideways, and a crow flies out from
                        // under him. Dying is not: he came down from above every single time.
                        if (probe.died) {
                            bad++
                            if (bad <= 12) {
                                report.append(
                                    "  fauna %d %-6s fall %5d drift %5d phase %d offX %3d -> DIED\n"
                                        .format(fauna, name, fall, drift, phase, offX)
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    println("Dropped Buddy onto an enemy $cases times across ${com.blacklab.buddybounce.game.EnemyBox.ROWS} " +
        "worlds; $hits landed on it, $rising bounced back up into it first.")

    // --- and the other half: falling in clear air above one must do nothing to it ------------
    var airCases = 0
    var airKills = 0
    val airReport = StringBuilder()
    for (fauna in 0 until com.blacklab.buddybounce.game.EnemyBox.ROWS) {
        for ((kind, name) in KIND_NAME) {
            for (gap in intArrayOf(160, 260, 420, 700, 1100)) {
                for (fall in intArrayOf(-200, -900, -1800)) {
                    for (phase in 0 until 4) {
                        airCases++
                        val probe = Probe()
                        val w = rig(probe, fauna, kind, 0, phase, gap.toFloat())
                        w.buddy.vy = fall.toFloat()
                        // Only as long as it takes to fall a third of the gap: his feet come
                        // nowhere near it, so nothing may happen to it.
                        val frames = ((gap / 3f) / (kotlin.math.abs(fall) / 60f)).toInt().coerceIn(1, 20)
                        repeat(frames) { w.update(1f / 60f, 0f, false, 0f, false) }
                        if (probe.stomped > 0) {
                            airKills++
                            if (airKills <= 10) {
                                airReport.append(
                                    "  fauna %d %-6s gap %4d fall %5d -> KILLED IT FROM THE AIR\n"
                                        .format(fauna, name, gap, fall)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
    println("Fell past $airCases times without reaching one; $airKills died anyway.\n")

    if (bad == 0 && airKills == 0 && hits > cases / 3) {
        println("ALL CHECKS PASSED - down through it always kills it and never him, " +
            "and clear air kills nothing")
    } else {
        if (bad > 0) { println("$bad of $cases killed HIM:"); print(report) }
        if (airKills > 0) { println("$airKills of $airCases died to a stomp on thin air:"); print(airReport) }
        if (hits <= cases / 3) println("only $hits of $cases made contact - the probe is not testing anything")
        System.exit(1)
    }
}
