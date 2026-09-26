import com.blacklab.buddybounce.Game
import com.blacklab.buddybounce.audio.Audio
import com.blacklab.buddybounce.audio.Music
import com.blacklab.buddybounce.data.Outfits
import com.blacklab.buddybounce.data.Powerups
import com.blacklab.buddybounce.data.Save
import com.blacklab.buddybounce.game.Flight
import com.blacklab.buddybounce.game.Tuning
import com.blacklab.buddybounce.game.World
import com.blacklab.buddybounce.render.Palettes
import com.blacklab.buddybounce.render.Scenes

/**
 * Both lap Buddies, through every power-up and every way a run can go.
 *
 * [LapCheck] proves the arithmetic in all ten worlds and [GameLapCheck] proves one climb end to
 * end; this one is the breadth. Every power-up in the shop is spent on a run that then climbs
 * past the third round, plus the flights that only turn up mid-level, the Second Life continue,
 * the infinite-power-ups back door, a single update that crosses three bands at once, a run
 * abandoned from the pause menu instead of died out of, a death on the very frame a band
 * changes, and the back door that just hands both over.
 *
 * The rocket held under him through the climb is the PROBE'S hands, not the player's: nothing
 * steers here, and ninety screens of no input is its own way to die. The power-up under test is
 * live the whole way up regardless, which is what is being checked - that whatever else is going
 * on, reaching the round still pays.
 *
 * Every case checks the LAP PURSE as well as the skins: past the third round is two rounds gone
 * by, so exactly two purses should have been paid, whatever carried him there.
 */
private val TWO_LAPS = 2 * Tuning.LAP_COINS
private const val DT = 1f / 60f

private var failures = 0
private fun check(label: String, ok: Boolean) {
    println((if (ok) "PASS  " else "FAIL  ") + label)
    if (!ok) failures++
}

private class Host : Game.Host {
    override fun setLandscape(landscape: Boolean) {}
    override fun promptName(current: String, title: String) {}
    override fun vibrate(ms: Long, amplitude: Int) {}
    override fun vibrateLater(delayMs: Long, ms: Long, amplitude: Int) {}
}

private class Rig(scene: String = Scenes.DEFAULT_ID) {
    val prefs = FakePrefs()
    val save = Save(FakeCtx(prefs))
    val game: Game

    init {
        save.controlsAsked = true          // the steering chooser is not what is being tested
        save.playerName = "PROBE"
        save.unlockScene(scene)
        save.selectedScene = scene
        val ctx = FakeCtx(prefs)
        game = Game(save, Audio(ctx, save), Music(ctx, save), Host())
        game.onSurface(1080, 2400)
    }

    /** Fresh save, reloaded from the same prefs - what the next launch would see. */
    fun reloaded(): Save = Save(FakeCtx(prefs))

    fun step(n: Int) { repeat(n) { game.update(DT) } }

    /** Starts a run, spending [powerup] if given, and holds the rocket until it is live. */
    fun launch(powerup: String? = null) {
        if (powerup != null) save.grantPowerupCapped(powerup, 1)
        game.startRun()
        game.beginCountdown(powerup)
        var f = 0
        while (f < 60 * 12 && game.screen != Game.Screen.PLAY) { assist(); game.update(DT); f++ }
    }

    private fun assist() {
        game.world.buddy.flight = Flight.ROCKET
        game.world.buddy.flightTime = 5f
    }

    fun climbTo(screens: Float): Boolean {
        var f = 0
        while (f < 60 * 1200 && game.world.screens < screens && game.screen == Game.Screen.PLAY) {
            assist()
            game.update(DT)
            f++
        }
        return game.world.screens >= screens
    }

    /** Drops him out of the bottom of the view and runs on until the run is over. */
    fun fallToDeath() {
        game.world.buddy.flight = Flight.NONE
        game.world.buddy.flightTime = 0f
        game.world.buddy.y = game.world.camY - Tuning.VIEW_H
        game.world.buddy.vy = -3000f
        var f = 0
        while (f < 60 * 40 && game.screen == Game.Screen.PLAY) { game.update(DT); f++ }
        step(2)
    }

    val bothOwned: Boolean
        get() = save.owns(Outfits.TWO_HEAD_ID) && save.owns(Outfits.CERBERUS_ID)

    val bothOwnedAfterReload: Boolean
        get() {
            val s = reloaded()
            return s.owns(Outfits.TWO_HEAD_ID) && s.owns(Outfits.CERBERUS_ID)
        }
}

/** Screens of climbing that the given scene's third round starts at. */
private fun lapTwoScreens(): Float = Palettes.current.bands.size * 2 * Tuning.BIOME_SPAN + 1.5f

fun main() {
    println("--- every power-up in the shop, spent on a run that goes round three times ---")
    val cases = ArrayList<Pair<String, String?>>()
    cases.add("nothing spent" to null)
    for (pu in Powerups.ALL) cases.add(pu.name to pu.id)
    for ((label, id) in cases) {
        val r = Rig()
        r.launch(id)
        val target = lapTwoScreens()
        val up = r.climbTo(target)
        r.fallToDeath()
        check("  $label: climbed past round three", up)
        check("  $label: both skins, and still there next launch", r.bothOwned && r.bothOwnedAfterReload)
        check("  $label: two lap purses (${r.game.world.lapCoins} coins)",
            r.game.world.lapCoins == TWO_LAPS)
    }

    println("\n--- the flights that only turn up mid-level ---")
    for ((name, kind) in listOf("propeller" to Flight.PROPELLER, "jetpack" to Flight.JETPACK, "rocket" to Flight.ROCKET)) {
        val r = Rig()
        r.launch(null)
        var f = 0
        while (f < 60 * 1200 && r.game.world.screens < lapTwoScreens() && r.game.screen == Game.Screen.PLAY) {
            r.game.world.buddy.flight = kind
            r.game.world.buddy.flightTime = 5f
            r.game.update(DT)
            f++
        }
        check("  on a $name the whole way: both skins and two purses",
            r.bothOwned && r.bothOwnedAfterReload && r.game.world.lapCoins == TWO_LAPS)
    }

    println("\n--- a Second Life spent on the way up, twice, on the infinite back door ---")
    val sl = Rig()
    sl.save.infinitePowerups = true
    sl.launch(null)
    sl.climbTo(20f)
    repeat(2) {
        sl.fallToDeath()
        check("  the run ended so it can be continued", sl.game.screen == Game.Screen.GAMEOVER)
        sl.game.reviveWithSecondLife()
        var f = 0
        while (f < 60 * 12 && sl.game.screen != Game.Screen.PLAY) {
            sl.game.world.buddy.flight = Flight.ROCKET
            sl.game.world.buddy.flightTime = 5f
            sl.game.update(DT)
            f++
        }
        check("  back in the same run", sl.game.screen == Game.Screen.PLAY)
    }
    check("  climbed past round three after two continues", sl.climbTo(lapTwoScreens()))
    sl.fallToDeath()
    check("  both skins, and still there next launch", sl.bothOwned && sl.bothOwnedAfterReload)
    check("  two purses across the continues (${sl.game.world.lapCoins} coins)",
        sl.game.world.lapCoins == TWO_LAPS)

    println("\n--- every world, climbed to its own third round ---")
    for (scene in Scenes.ALL) {
        val r = Rig(scene.id)
        r.launch(null)
        val up = r.climbTo(scene.bands.size * 2 * Tuning.BIOME_SPAN + 1.5f)
        r.fallToDeath()
        check("  ${scene.name}: ${if (up) "climbed and paid" else "never got there"}",
            up && r.bothOwned && r.game.world.lapCoins == TWO_LAPS)
    }

    println("\n--- three bands crossed inside ONE update ---")
    val jump = Rig()
    jump.launch(null)
    jump.climbTo(2f)
    // Straight from the first band to well inside the third round, in a single step.
    jump.game.world.buddy.flight = Flight.ROCKET
    jump.game.world.buddy.flightTime = 5f
    jump.game.world.buddy.y = jump.game.world.buddy.y + lapTwoScreens() * Tuning.VIEW_H
    jump.game.update(DT)
    check("  both skins from the one jump", jump.bothOwned)
    check("  and BOTH purses from it (${jump.game.world.lapCoins} coins)",
        jump.game.world.lapCoins == TWO_LAPS)
    jump.fallToDeath()
    check("  and still there next launch", jump.bothOwnedAfterReload)

    println("\n--- abandoned from the pause menu instead of died out of ---")
    val quit = Rig()
    quit.launch(null)
    check("  climbed past round three", quit.climbTo(lapTwoScreens()))
    quit.game.goto(Game.Screen.PAUSE)
    quit.game.goto(Game.Screen.MENU)          // the pause menu's QUIT
    quit.step(4)
    check("  both skins survive walking away from the run", quit.bothOwned && quit.bothOwnedAfterReload)

    println("\n--- killed on the very frame a band changes ---")
    var caught = 0
    for (attempt in 0 until 6) {
        val r = Rig()
        r.launch(null)
        // Stop just short of the boundary, then cross it and die in the same breath.
        val edge = Palettes.current.bands.size * 2 * Tuning.BIOME_SPAN
        r.climbTo(edge - 0.3f)
        if (r.game.screen != Game.Screen.PLAY) continue
        val before = r.game.world.biome
        r.game.world.buddy.flight = Flight.ROCKET
        r.game.world.buddy.flightTime = 5f
        r.game.world.buddy.y = r.game.world.buddy.y + Tuning.VIEW_H * 0.6f
        r.game.update(DT)
        if (r.game.world.biome == before) continue
        caught++
        r.fallToDeath()
        check("  crossed and died together: both skins", r.bothOwned && r.bothOwnedAfterReload)
    }
    check("  the frame-perfect case was actually reproduced", caught > 0)

    println("\n--- the purse itself ---")
    val pr = Rig()
    pr.launch(null)
    val lapOne = Palettes.current.bands.size * Tuning.BIOME_SPAN
    check("  nothing paid before the first round is up", pr.game.world.lapCoins == 0)
    check("  climbed to round two", pr.climbTo(lapOne + 1.5f))
    check("  one purse, exactly ${Tuning.LAP_COINS} (${pr.game.world.lapCoins})",
        pr.game.world.lapCoins == Tuning.LAP_COINS)
    val beforeSecond = pr.game.world.runCoins
    check("  it is in the run's coins too", beforeSecond >= Tuning.LAP_COINS)
    check("  climbed to round three", pr.climbTo(lapOne * 2f + 1.5f))
    check("  a second purse and no more (${pr.game.world.lapCoins})",
        pr.game.world.lapCoins == TWO_LAPS)
    val banked = pr.save.coins
    pr.fallToDeath()
    check("  banked into the save when the run ended",
        pr.save.coins - banked >= TWO_LAPS)

    println("\n--- and the next run pays again ---")
    val again = pr.game.world.lapCoins
    pr.launch(null)
    check("  the new run starts owing nothing", pr.game.world.lapCoins == 0 && again == TWO_LAPS)
    check("  climbed to round two again", pr.climbTo(lapOne + 1.5f))
    check("  paid again (${pr.game.world.lapCoins})", pr.game.world.lapCoins == Tuning.LAP_COINS)

    println("\n--- the back door ---")
    for (code in listOf("u4*=^8", "uu4*=^8", "U4*=^8", "  u4*=^8  ")) {
        val r = Rig()
        check("  '$code' is recognised", r.game.isUnlockCode(code))
        r.game.applyUnlockCode(code)
        check("  '$code' hands over both", r.bothOwned && r.bothOwnedAfterReload)
    }
    // and it must not be mistaken for the developer skin's code, or the other way round
    val dev = Rig()
    dev.game.applyUnlockCode("uu4*=^7")
    check("  the developer skin's code still gives ONLY the developer skin",
        dev.save.owns(Outfits.DEV_ID) && !dev.save.owns(Outfits.TWO_HEAD_ID) &&
            !dev.save.owns(Outfits.CERBERUS_ID))
    val lap = Rig()
    lap.game.applyUnlockCode("u4*=^8")
    check("  the lap code does NOT give the developer skin", !lap.save.owns(Outfits.DEV_ID))
    check("  nor anything else", !lap.save.owns(Outfits.HEAVEN_ONLY_ID) && lap.save.coins == 0)

    println("\n--- unlock-everything still leaves all three secrets alone ---")
    val all = Rig()
    all.game.applyUnlockCode("u7d%4>")
    check("  none of the three secret Buddies came out of it",
        Outfits.SECRET_IDS.none { all.save.owns(it) })

    println()
    println(if (failures == 0) "ALL GOOD" else "$failures FAILURE(S)")
    if (failures > 0) System.exit(1)
}
