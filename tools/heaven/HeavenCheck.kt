import android.content.Context
import android.content.SharedPreferences
import com.blacklab.buddybounce.data.Outfits
import com.blacklab.buddybounce.data.Save
import com.blacklab.buddybounce.data.Trails
import com.blacklab.buddybounce.render.Scenes

var failures = 0
fun check(label: String, actual: Boolean, expected: Boolean) {
    val ok = actual == expected
    if (!ok) failures++
    println("${if (ok) "PASS" else "FAIL"}  $label  (got $actual, want $expected)")
}

fun main() {
    val prefs = FakePrefs()
    val save = Save(FakeCtx(prefs))
    val ETERNAL = Outfits.HEAVEN_ONLY_ID

    println("--- 1. unlocking literally everything else opens Heaven ---")
    for (o in Outfits.ALL) if (o.id != ETERNAL) save.unlock(o.id)
    for (t in Trails.ALL) save.unlockTrail(t.id)
    for (sc in Scenes.unlockable) save.unlockScene(sc.id)
    check("everything-else is complete", save.hasUnlockedEverything(), true)
    check("refreshHeaven opens Heaven", save.refreshHeaven(), true)
    check("Heaven is owned", save.ownsScene(Scenes.HEAVEN_ID), true)
    check("Eternal is STILL LOCKED", save.owns(ETERNAL), false)
    check("ghost look still locked", save.ghostUnlocked, false)

    println("--- 2. a Second Life cannot grant it ---")
    // the whole revive path, as Game.reviveWithSecondLife drives it
    save.grantPowerup(com.blacklab.buddybounce.data.Powerups.SECOND_LIFE, 2)
    repeat(2) {
        check("consume a Second Life", save.consumePowerup(com.blacklab.buddybounce.data.Powerups.SECOND_LIFE), true)
        check("  Eternal still locked after revive", save.owns(ETERNAL), false)
    }
    check("blessed DURING a revive", Outfits.isBlessed(Outfits.DEFAULT_ID, false, true, false), true)
    check("not blessed once the run ends", Outfits.isBlessed(Outfits.DEFAULT_ID, false, false, false), false)

    println("--- 3. halos are the only key, and 999 is not enough ---")
    save.addHalos(999)
    check("999 halos: still locked", save.owns(ETERNAL), false)
    check("999 halos: toggle still locked", save.ghostUnlocked, false)
    save.addHalos(1)
    check("1000 halos: Eternal unlocked", save.owns(ETERNAL), true)
    check("1000 halos: toggle unlocked", save.ghostUnlocked, true)

    println("--- 4. equipping it shows in game, not just the wardrobe ---")
    save.equippedOutfit = ETERNAL
    check("it equips", save.equippedOutfit == ETERNAL, true)
    check("blessed IN GAME (plain world, no revive, toggle off)",
        Outfits.isBlessed(save.equippedOutfit, false, false, save.ghostEnabled), true)
    save.ghostEnabled = false
    check("blessed with the toggle explicitly off",
        Outfits.isBlessed(ETERNAL, false, false, false), true)
    check("another outfit is not blessed",
        Outfits.isBlessed("fighter", false, false, false), false)

    println("--- 5. the u7d%4+ back door parks you one halo short ---")
    // what Game.applyUnlockCode does for HALO_PRIME_CODE
    save.unlockScene(Scenes.HEAVEN_ID)
    save.primeHalosForTest()
    check("halos parked at 999", save.halos == 999, true)
    check("Eternal taken back", save.owns(ETERNAL), false)
    check("toggle wound back", save.ghostUnlocked, false)
    check("no longer equipped", save.equippedOutfit == Outfits.DEFAULT_ID, true)
    check("Heaven is open so halos are reachable", save.ownsScene(Scenes.HEAVEN_ID), true)
    save.addHalos(1)
    check("the thousandth halo unlocks it", save.owns(ETERNAL), true)
    check("the thousandth halo unlocks the toggle", save.ghostUnlocked, true)
    save.primeHalosForTest()
    check("re-runnable: locked again", save.owns(ETERNAL), false)

    println("--- 6. the Glory Beam is Heaven's too, at half the price ---")
    val fresh = FakePrefs()
    val s2 = Save(FakeCtx(fresh))
    val GLORY = Trails.HEAVEN_ONLY_ID
    check("Glory is not in the machine's pool", Trails.collectable.any { it.id == GLORY }, false)
    check("Eternal is not in the machine's pool", Outfits.inRarity(Outfits.Rarity.LEGENDARY).any { it.id == Outfits.HEAVEN_ONLY_ID }, false)
    check("the dev skin is not in the pool either", Outfits.inRarity(Outfits.Rarity.LEGENDARY).any { it.id == Outfits.DEV_ID }, false)
    s2.addHalos(499)
    check("499 halos: Glory locked", s2.ownsTrail(GLORY), false)
    s2.addHalos(1)
    check("500 halos: Glory unlocked", s2.ownsTrail(GLORY), true)
    check("500 halos: Eternal still locked", s2.owns(Outfits.HEAVEN_ONLY_ID), false)
    s2.addHalos(500)
    check("1000 halos: Eternal unlocked", s2.owns(Outfits.HEAVEN_ONLY_ID), true)

    println("--- 6b. the back door path that lost the trail ---")
    // Exactly what the player did: prime to 999 with u7d%4+, then collect one halo in Heaven.
    val s2b = Save(FakeCtx(FakePrefs()))
    s2b.unlockScene(Scenes.HEAVEN_ID)
    s2b.primeHalosForTest()
    check("primed to 999", s2b.halos == 999, true)
    check("primed: Glory locked", s2b.ownsTrail(GLORY), false)
    check("primed: Eternal locked", s2b.owns(Outfits.HEAVEN_ONLY_ID), false)
    s2b.addHalos(1)
    check("1000 via the back door: Glory unlocked", s2b.ownsTrail(GLORY), true)
    check("1000 via the back door: Eternal unlocked", s2b.owns(Outfits.HEAVEN_ONLY_ID), true)

    println("--- 6c. a save already above a threshold is settled on load ---")
    val stale = FakePrefs()
    stale.map["halos"] = 640            // enough for Glory, not for Eternal
    val s2c = Save(FakeCtx(stale))
    check("Glory granted on load", s2c.ownsTrail(GLORY), true)
    check("Eternal still not", s2c.owns(Outfits.HEAVEN_ONLY_ID), false)

    println("--- 6d. the blessed toggle belongs to the outfit ---")
    check("no outfit, no toggle", s2c.ghostUnlocked, false)
    s2c.addHalos(360)
    check("1000 halos: outfit", s2c.owns(Outfits.HEAVEN_ONLY_ID), true)
    check("and the toggle comes with it", s2c.ghostUnlocked, true)

    println("--- 7. the dev skin is hidden until the back door opens it ---")
    val s3 = Save(FakeCtx(FakePrefs()))
    check("absent from the wardrobe list", Outfits.visible(false).any { it.id == Outfits.DEV_ID }, false)
    check("absent from the total", Outfits.collectableCount(false) == Outfits.collectableCount(true) - 1, true)
    check("does not block Heaven", run {
        for (o in Outfits.ALL) if (o.id != Outfits.HEAVEN_ONLY_ID && o.id != Outfits.DEV_ID) s3.unlock(o.id)
        for (t in Trails.collectable) s3.unlockTrail(t.id)
        for (sc in Scenes.unlockable) s3.unlockScene(sc.id)
        s3.hasUnlockedEverything()
    }, true)
    s3.unlock(Outfits.DEV_ID)
    check("present once unlocked", Outfits.visible(true).any { it.id == Outfits.DEV_ID }, true)
    check("and it is last in the list", Outfits.visible(true).last().id == Outfits.DEV_ID, true)
    check("Anti-Buddy sits just before Eternal",
        Outfits.visible(false).let { it[it.size - 2].id } == "anti", true)

    println("--- 7b. counting, ordering and the Developer rarity ---")
    val fresh2 = Save(FakeCtx(FakePrefs()))
    check("a fresh save owns exactly one outfit (Buddy)",
        Outfits.visible(false).count { fresh2.owns(it.id) } == 1, true)
    check("the total counts Buddy too",
        Outfits.collectableCount(false) == Outfits.visible(false).size, true)
    check("grid size and total agree",
        Outfits.visible(false).size == Outfits.collectableCount(false), true)
    check("the dev skin is one more, once owned",
        Outfits.collectableCount(true) == Outfits.collectableCount(false) + 1, true)
    check("the dev skin has its own rarity",
        Outfits.of(Outfits.DEV_ID).rarity == Outfits.Rarity.DEVELOPER, true)
    check("which is white", Outfits.Rarity.DEVELOPER.tint == 0xFFFFFFFF.toInt(), true)
    check("and can never be rolled", Outfits.Rarity.DEVELOPER.weight == 0, true)
    check("nothing else uses it",
        Outfits.ALL.count { it.rarity == Outfits.Rarity.DEVELOPER } == 1, true)

    println("--- 7c. the trails page is ordered by rarity, Heaven last ---")
    check("display holds every trail", Trails.display.size == Trails.ALL.size, true)
    check("Glory is last", Trails.display.last().id == Trails.HEAVEN_ONLY_ID, true)
    check("rarity never goes backwards", run {
        val body = Trails.display.filter { it.id != Trails.HEAVEN_ONLY_ID }
        body.zipWithNext().all { (a, b) -> a.rarity <= b.rarity }
    }, true)
    check("eight new trails landed", Trails.ALL.size == 50, true)
    check("every style is unique",
        Trails.ALL.map { it.style }.toSet().size == Trails.ALL.size, true)

    println("--- 8. a save from the old build gets it taken back ---")
    val old = FakePrefs()
    old.map["owned"] = hashSetOf("fighter", ETERNAL)
    old.map["equipped"] = ETERNAL
    val repaired = Save(FakeCtx(old))
    check("old save: Eternal revoked", repaired.owns(ETERNAL), false)
    check("old save: other outfits kept", repaired.owns("fighter"), true)
    check("old save: no longer equipped", repaired.equippedOutfit == Outfits.DEFAULT_ID, true)

    println()
    println(if (failures == 0) "ALL CHECKS PASSED" else "$failures CHECK(S) FAILED")
}
