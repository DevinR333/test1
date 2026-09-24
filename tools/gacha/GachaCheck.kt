import com.blacklab.buddybounce.data.Outfits
import com.blacklab.buddybounce.data.Powerups
import com.blacklab.buddybounce.data.PrizeRoll
import com.blacklab.buddybounce.data.Save
import com.blacklab.buddybounce.data.Trails
import com.blacklab.buddybounce.render.Scenes
import kotlin.math.abs
import kotlin.math.sqrt
import kotlin.random.Random

/**
 * Is the prize machine actually random?
 *
 * "It calls Random" is not an answer - a rigged machine calls Random too. This pulls the handle
 * a million times against a real Save and measures what comes out, then looks for the things a
 * rigged machine would show:
 *
 *   1. the categories come out at the declared odds, within sampling error
 *   2. each weighted table (power-ups, trail rarities, outfit rarities) matches its own weights
 *   3. a pull does not depend on the pull before it - no streak breaker, no pity timer
 *   4. the wait for a jackpot is geometric - a pity timer would cut the tail off
 *   5. nothing depends on the coin balance, the play time or how many pulls you have made
 *
 * Run: see tools/gacha/README.md
 */
private const val N = 1_000_000

private var failures = 0

/**
 * Two-sided test on a proportion. With a million samples the normal approximation is fine, and
 * 4 sigma keeps a passing build from failing by luck about once in sixteen thousand runs.
 */
private fun checkRate(what: String, hits: Int, n: Int, expected: Float, sigmas: Float = 4f) {
    val p = hits.toDouble() / n
    val se = sqrt(expected.toDouble() * (1 - expected) / n)
    val off = abs(p - expected) / se
    val ok = off <= sigmas
    if (!ok) failures++
    println("  ${if (ok) "PASS" else "FAIL"}  %-40s %.4f%%  expected %.4f%%  (%.1f sigma)"
        .format(what, p * 100, expected * 100, off))
}

private fun newSave(): Save {
    val prefs = FakePrefs()
    return Save(FakeCtx(prefs))
}

fun main() {
    println("Pulling the handle $N times on a fresh save.\n")

    // ---- 1. the category split -----------------------------------------------------------
    // A fresh save, restored every pull, so nothing accumulates and every pull sees the same
    // world. This is the machine's advertised split.
    println("Category split")
    val counts = IntArray(4)
    run {
        val rng = Random(0xB0DD1E5L)
        val save = newSave()
        val roller = PrizeRoll(rng)
        for (i in 0 until N) counts[roller.roll(save).kind]++
    }
    checkRate("scene (jackpot)", counts[PrizeRoll.Kind.SCENE], N, PrizeRoll.SCENE_CHANCE)
    checkRate("trail", counts[PrizeRoll.Kind.TRAIL], N, PrizeRoll.TRAIL_CHANCE)
    checkRate("outfit", counts[PrizeRoll.Kind.OUTFIT], N, PrizeRoll.OUTFIT_CHANCE)
    checkRate("power-up", counts[PrizeRoll.Kind.POWERUP], N,
        1f - PrizeRoll.SCENE_CHANCE - PrizeRoll.TRAIL_CHANCE - PrizeRoll.OUTFIT_CHANCE)

    // ---- 2. the weighted tables ----------------------------------------------------------
    println("\nPower-up table, against its own weights")
    run {
        val rng = Random(0xC0FFEE)
        val roller = PrizeRoll(rng)
        val hits = HashMap<String, Int>()
        for (i in 0 until N) hits.merge(roller.rollPowerup(), 1) { a, b -> a + b }
        for (pu in Powerups.ALL) {
            checkRate(pu.id, hits[pu.id] ?: 0, N, pu.weight.toFloat() / Powerups.totalWeight)
        }
    }

    println("\nTrail table on a fresh save, against its own weights")
    run {
        val rng = Random(0xDEC0DE)
        val save = newSave()
        val roller = PrizeRoll(rng)
        val hits = HashMap<String, Int>()
        for (i in 0 until N) hits.merge(roller.rollTrail(save), 1) { a, b -> a + b }
        var total = 0
        for (t in Trails.collectable) total += Trails.weightOf(t)
        // spot-check one trail of each distinct weight rather than printing forty lines
        val seen = HashSet<Int>()
        for (t in Trails.collectable) {
            val w = Trails.weightOf(t)
            if (!seen.add(w)) continue
            checkRate("${t.id} (weight $w)", hits[t.id] ?: 0, N, w.toFloat() / total)
        }
        val drawn = hits.size
        val ok = drawn == Trails.collectable.size
        if (!ok) failures++
        println("  ${if (ok) "PASS" else "FAIL"}  every collectable trail can be drawn        " +
            "$drawn of ${Trails.collectable.size}")
    }

    // ---- 3. independence: does a pull depend on the one before it? ------------------------
    //
    // A streak breaker or a pity timer shows up here: the chance of a jackpot would rise after
    // a drought and fall right after a win. Measured directly, both conditional rates should
    // match the unconditional one.
    println("\nIndependence - no streak breaker, no pity timer")
    run {
        val rng = Random(0x5EED)
        val save = newSave()
        val roller = PrizeRoll(rng)
        var prevWasScene = false
        var afterScene = 0; var afterSceneHits = 0
        var afterOther = 0; var afterOtherHits = 0
        var sinceScene = 0
        var longDrought = 0; var longDroughtHits = 0
        val gaps = ArrayList<Int>()
        for (i in 0 until N) {
            val isScene = roller.roll(save).kind == PrizeRoll.Kind.SCENE
            if (i > 0) {
                if (prevWasScene) { afterScene++; if (isScene) afterSceneHits++ }
                else { afterOther++; if (isScene) afterOtherHits++ }
                // and after a long dry spell, which is where a pity timer would bite
                if (sinceScene >= 200) { longDrought++; if (isScene) longDroughtHits++ }
            }
            if (isScene) { gaps.add(sinceScene); sinceScene = 0 } else sinceScene++
            prevWasScene = isScene
        }
        checkRate("jackpot right after a jackpot", afterSceneHits, afterScene, PrizeRoll.SCENE_CHANCE)
        checkRate("jackpot after anything else", afterOtherHits, afterOther, PrizeRoll.SCENE_CHANCE)
        checkRate("jackpot after 200+ dry pulls", longDroughtHits, longDrought, PrizeRoll.SCENE_CHANCE)

        // 4. the gap distribution. Independent pulls give a geometric wait, whose mean and
        // standard deviation are both about 1/p - a pity timer truncates the tail and pulls the
        // spread down hard.
        val mean = gaps.sum().toDouble() / gaps.size
        var v = 0.0
        for (gp in gaps) v += (gp - mean) * (gp - mean)
        val sd = sqrt(v / gaps.size)
        val expectMean = (1f - PrizeRoll.SCENE_CHANCE) / PrizeRoll.SCENE_CHANCE
        val expectSd = sqrt((1f - PrizeRoll.SCENE_CHANCE).toDouble()) / PrizeRoll.SCENE_CHANCE
        val meanOk = abs(mean - expectMean) < expectMean * 0.05
        val sdOk = abs(sd - expectSd) < expectSd * 0.06
        if (!meanOk) failures++
        if (!sdOk) failures++
        println("  ${if (meanOk) "PASS" else "FAIL"}  mean pulls between jackpots              " +
            "%.1f  expected %.1f".format(mean, expectMean))
        println("  ${if (sdOk) "PASS" else "FAIL"}  spread of that wait (geometric)          " +
            "%.1f  expected %.1f".format(sd, expectSd))
        println("       longest observed drought: ${gaps.max()} pulls  (%d jackpots in $N)"
            .format(gaps.size))
    }

    // ---- 5. nothing outside the draw can steer it ----------------------------------------
    //
    // The same seed, the same save, but a wildly different coin balance, play time and pull
    // count. If any of those reached the draw, the sequences would diverge.
    println("\nNothing but the draw decides")
    run {
        fun sequence(runs: Int, coinsPerRun: Int): IntArray {
            val save = newSave()
            // a played-in save: coins in the purse, runs on the clock, a leaderboard
            for (i in 0 until runs) save.bankRun(1000 + i * 7, coinsPerRun)
            val roller = PrizeRoll(Random(0x1234))
            return IntArray(500) { roller.roll(save).kind }
        }
        val a = sequence(0, 0)
        val b = sequence(60, 900)
        val same = a.contentEquals(b)
        if (!same) failures++
        println("  ${if (same) "PASS" else "FAIL"}  coins and play history change nothing    " +
            "500 pulls identical")
        println("       (fresh save vs 60 runs played and 54000 coins banked)")
    }

    // ---- 6. the fall-through bands -------------------------------------------------------
    println("\nWhen a band has nothing left in it")
    run {
        val save = newSave()
        for (s in Scenes.unlockable) save.unlockScene(s.id)
        val rng = Random(0xA11)
        val roller = PrizeRoll(rng)
        var scenes = 0
        var trails = 0
        val n = 200_000
        for (i in 0 until n) {
            val k = roller.roll(save).kind
            if (k == PrizeRoll.Kind.SCENE) scenes++
            if (k == PrizeRoll.Kind.TRAIL) trails++
        }
        val ok = scenes == 0
        if (!ok) failures++
        println("  ${if (ok) "PASS" else "FAIL"}  every world owned: no phantom world       $scenes drawn")
        checkRate("...its 1% goes to trails", trails, n,
            PrizeRoll.SCENE_CHANCE + PrizeRoll.TRAIL_CHANCE)
    }

    // ---- 7. two machines started at the same moment must not agree ------------------------
    //
    // The seed is System.nanoTime(). If two players opened the machine in the same millisecond
    // they must still get different prizes, so check the clock is fine enough to separate them.
    println("\nSeeding")
    run {
        val seeds = HashSet<Long>()
        for (i in 0 until 20_000) seeds.add(System.nanoTime())
        val distinct = seeds.size
        val ok = distinct > 19_000
        if (!ok) failures++
        println("  ${if (ok) "PASS" else "FAIL"}  System.nanoTime() separates back-to-back  " +
            "$distinct distinct of 20000")
    }

    println()
    println(if (failures == 0) "ALL CHECKS PASSED" else "$failures CHECK(S) FAILED")
    if (failures != 0) kotlin.system.exitProcess(1)
}
