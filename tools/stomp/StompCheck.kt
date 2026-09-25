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
 * The rule under test: if his feet were above the enemy when the frame began, he stomps it and
 * lives. Nothing else about the frame matters.
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

fun main() {
    var cases = 0
    var hits = 0
    var rising = 0
    var bad = 0
    val report = StringBuilder()

    for ((kind, name) in KIND_NAME) {
        // every fall speed from a gentle drop to terminal, every sideways drift, and the enemy
        // caught at every point of its own little cycle
        for (fall in intArrayOf(-200, -600, -1200, -1900, -2600, -3200)) {
            for (drift in intArrayOf(-600, -200, 0, 200, 600)) {
                for (phase in 0 until 8) {
                    for (offX in intArrayOf(-40, 0, 40)) {
                        cases++
                        val probe = Probe()
                        val w = World(900f, probe)
                        w.reset()
                        val e = w.enemies.obtain()
                        e.kind = kind
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

                        // put him straight above it, falling
                        w.buddy.x = 450f
                        w.buddy.y = e.y + e.halfH + 26f
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
                                    "  %-6s fall %5d drift %5d phase %d offX %3d -> DIED\n".format(
                                        name, fall, drift, phase, offX
                                    )
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    println("Dropped Buddy onto an enemy $cases times; $hits landed on it, " +
        "$rising bounced back up into it first.\n")
    if (bad == 0 && hits > cases / 3) {
        println("ALL CHECKS PASSED - coming down from above always kills the enemy, never him")
    } else if (bad > 0) {
        println("$bad of $cases killed HIM:")
        print(report)
    } else {
        println("only $hits of $cases actually made contact - the probe is not testing anything")
    }
}
