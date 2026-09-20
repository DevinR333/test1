import com.blacklab.buddybounce.game.*
import kotlin.math.abs

/**
 * Headless balance harness. The simulation in `game/` has no Android dependencies, so it can be
 * played without a device: a bot locks a reachable target platform at each bounce and steers
 * toward it until it lands, which is roughly what a competent human does. Run it after touching
 * anything in Tuning.kt to check that every aspect ratio is still climbable.
 *
 *   kotlinc app/src/main/java/com/blacklab/buddybounce/game package sources tools/sim/Sim.kt \
 *       -include-runtime -d sim.jar && java -jar sim.jar
 *
 * See docs/MECHANICS.md for what the numbers should look like.
 */
object Sim {
    class Stats : World.Events {
        var bounces = 0; var breaks = 0; var pickups = 0; var deaths = 0; var stomps = 0
        override fun onBounce(platform: Platform, strength: Float) { bounces++ }
        override fun onPlatformBreak(platform: Platform) { breaks++ }
        override fun onPickup(pickup: Pickup) { pickups++ }
        override fun onStomp(enemy: Enemy) { stomps++ }
        override fun onDeath(cause: Int) { deaths++ }
    }

    private var target: Platform? = null
    private var lastVy = 0f

    private fun pickTarget(w: World): Platform? {
        val b = w.buddy
        var best: Platform? = null
        var bestScore = -1e9f
        // Apex of this jump, so we only consider platforms we can actually reach.
        val apex = b.y + (b.vy * b.vy) / (2f * Tuning.GRAVITY)
        for (p in w.platforms.items) {
            if (!p.alive || p.state != 0) continue
            if (p.kind == PlatKind.FRAGILE || p.isGround) continue
            if (p.y <= b.y + 40f || p.y > apex - 20f) continue
            val dx = abs(MathX.wrapDelta(p.x, b.x, w.worldW))
            // Time available to travel there ~ time to reach that height going up.
            val score = (p.y - b.y) * 1.0f - dx * 0.55f + (if (p.boost != Boost.NONE) 260f else 0f)
            if (score > bestScore) { bestScore = score; best = p }
        }
        return best
    }

    fun botSteer(w: World): Float {
        val b = w.buddy
        // Re-target at the start of each rise.
        if (b.vy > 0f && lastVy <= 0f) target = pickTarget(w)
        lastVy = b.vy
        var t = target
        if (t == null || !t.alive || t.state != 0) {
            t = pickTarget(w)
            target = t
        }
        if (t == null) return 0f
        val dx = MathX.wrapDelta(t.x, b.x, w.worldW)
        return (dx / 45f).coerceIn(-1f, 1f)
    }

    @JvmStatic
    fun main(args: Array<String>) {
        val widths = floatArrayOf(1080f, 1440f, 1707f, 3413f, 4551f, 5973f)
        val names = arrayOf("portrait 3:4", "portrait 9:16", "portrait 20:9", "landscape 4:3", "landscape 16:9", "landscape 21:9")
        var failures = 0
        for (i in widths.indices) {
            var bestScreens = 0f
            var bestScore = 0
            var sumScreens = 0f
            var sumCoins = 0
            var totalFrames = 0
            var maxPlatforms = 0
            var maxParticles = 0
            val runs = 8
            val stats = Stats()
            val w = World(widths[i], stats)
            repeat(runs) {
                w.reset()
                target = null
                var frames = 0
                while (!w.deathSettled && frames < 60 * 240) {
                    w.update(1f / 60f, botSteer(w), true, 0f, false)
                    frames++
                    maxPlatforms = maxOf(maxPlatforms, w.platforms.items.size)
                    maxParticles = maxOf(maxParticles, w.pickups.items.size + w.enemies.items.size)
                }
                totalFrames += frames
                sumScreens += w.screens
                sumCoins += w.runCoins + w.heightBonusCoins
                bestScreens = maxOf(bestScreens, w.screens)
                bestScore = maxOf(bestScore, w.score)
            }
            val avgSeconds = totalFrames / 60f / runs
            val avgScreens = sumScreens / runs
            val ok = avgScreens > 10f && bestScreens > 20f
            if (!ok) failures++
            println(
                String.format(
                    "%-16s w=%-6.0f best=%7d pts %6.1f screens | avg %5.1f screens, %4.1f coins/run, %5.1fs | plats %3d | bounce %d break %d stomp %d death %d | %s",
                    names[i], widths[i], bestScore, bestScreens, avgScreens, sumCoins / runs.toFloat(), avgSeconds,
                    maxPlatforms, stats.bounces, stats.breaks, stats.stomps, stats.deaths,
                    if (ok) "OK" else "TOO HARD"
                )
            )
        }
        println(if (failures == 0) "\nAll aspect ratios playable." else "\n$failures configuration(s) failed")
    }
}
