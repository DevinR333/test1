import com.blacklab.buddybounce.data.Outfits
import com.blacklab.buddybounce.game.Tuning
import com.blacklab.buddybounce.render.Palettes
import com.blacklab.buddybounce.render.Scenes

/**
 * The two secret Buddies are earned by climbing into the second and third lap of the world
 * cycle - and EVERY world has to be able to hand them over, not just the Backyard.
 *
 * A lap is a scene's own band list coming round again, so the altitude it happens at is the
 * scene's band count times the length of a band. If any scene had a different number of bands
 * its laps would land somewhere else, and if the biome index were capped below a scene's second
 * lap that world could never give the skins up at all. Both are worth a check rather than an
 * assumption.
 */
private var failures = 0

private fun check(label: String, ok: Boolean) {
    println((if (ok) "PASS  " else "FAIL  ") + label)
    if (!ok) failures++
}

/** The rule as Game.onBiomeChange applies it. */
private fun unlocksAt(lap: Int): Set<String> {
    val out = HashSet<String>()
    if (lap >= 1) out.add(Outfits.TWO_HEAD_ID)
    if (lap >= 2) out.add(Outfits.CERBERUS_ID)
    return out
}

fun main() {
    val cap = Tuning.BIOME_COUNT * 6
    println("biome index is capped at $cap; a band is ${Tuning.BIOME_SPAN} screens\n")

    for (scene in Scenes.ALL) {
        Palettes.current = scene
        val bands = scene.bands.size
        println("--- ${scene.name} ($bands bands) ---")

        check("  lap 0 at the bottom", Palettes.lapOf(0) == 0)
        check("  still lap 0 at the last band of the first round", Palettes.lapOf(bands - 1) == 0)
        check("  lap 1 the band after that", Palettes.lapOf(bands) == 1)
        check("  lap 2 a round later", Palettes.lapOf(bands * 2) == 2)

        // both laps have to be reachable before the biome index stops counting
        check("  lap 1 is inside the cap", bands <= cap)
        check("  lap 2 is inside the cap", bands * 2 <= cap)

        // and walking the whole climb hands both over, in order, exactly once
        val owned = HashSet<String>()
        var twoAt = -1
        var cerbAt = -1
        for (b in 0..cap) {
            for (id in unlocksAt(Palettes.lapOf(b))) {
                if (owned.add(id)) {
                    if (id == Outfits.TWO_HEAD_ID) twoAt = b else cerbAt = b
                }
            }
        }
        check("  Two-Headed Buddy arrives, at band $twoAt", twoAt == bands)
        check("  Cerberus Buddy arrives, at band $cerbAt", cerbAt == bands * 2)
        check("  and it is the later of the two", cerbAt > twoAt)

        val screens = bands * Tuning.BIOME_SPAN
        println("  -> second round at %.0f screens, third at %.0f".format(screens, screens * 2))
    }

    // a rocket can cross more than one band in a single update
    Palettes.current = Scenes.ALL[0]
    val jumped = unlocksAt(Palettes.lapOf(Scenes.ALL[0].bands.size * 2))
    check("skipping straight to the third round still grants both", jumped.size == 2)

    println()
    if (failures == 0) println("ALL CHECKS PASSED - every world can hand over both skins")
    else println("$failures CHECK(S) FAILED")
}
