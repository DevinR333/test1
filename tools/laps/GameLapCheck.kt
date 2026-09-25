import com.blacklab.buddybounce.Game
import com.blacklab.buddybounce.audio.Audio
import com.blacklab.buddybounce.audio.Music
import com.blacklab.buddybounce.data.Outfits
import com.blacklab.buddybounce.data.Save
import com.blacklab.buddybounce.game.Flight
import com.blacklab.buddybounce.game.Tuning
import com.blacklab.buddybounce.render.Palettes

/**
 * The lap unlock, driven through the REAL [Game] rather than through [World] on its own.
 *
 * World's own probe proves the biome events fire; this one proves what Game does with them -
 * that the skin is written to the save, that it is still written after a reload, and that the
 * reveal card is still queued by the time the game-over screen is up.
 */
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

private const val DT = 1f / 60f

private fun step(g: Game, n: Int) { repeat(n) { g.update(DT) } }

/** Holds a rocket under him so he climbs, one frame at a time. */
private fun climbTo(g: Game, screens: Float, maxFrames: Int = 60 * 900): Boolean {
    var f = 0
    while (f < maxFrames && g.world.screens < screens) {
        g.world.buddy.flight = Flight.ROCKET
        g.world.buddy.flightTime = 5f
        g.update(DT)
        f++
    }
    return g.world.screens >= screens
}

fun main() {
    val prefs = FakePrefs()
    val save = Save(FakeCtx(prefs))
    save.playerName = "PROBE"
    save.controlsAsked = true   // a real player has dismissed the steering chooser by now
    val ctx = FakeCtx(prefs)
    val game = Game(save, Audio(ctx, save), Music(ctx, save), Host())
    game.onSurface(1080, 2400)

    check("nothing owned at the start", !save.owns(Outfits.TWO_HEAD_ID))

    game.startRun()
    // through the countdown and into the run
    step(game, 60 * 8)
    check("the run is live", game.screen == Game.Screen.PLAY)
    check("no card up before the climb", !game.unlockPopup.isShowing)

    val bands = Palettes.current.bands.size
    val lapScreens = bands * Tuning.BIOME_SPAN
    println("lap 1 lands at ${lapScreens} screens (${(lapScreens * Tuning.VIEW_H * Tuning.SCORE_PER_WU).toInt()} points)")

    check("climbed past lap 1", climbTo(game, lapScreens + 1.5f))
    println("  screens=%.1f biome=${game.world.biome} lap=${Palettes.lapOf(game.world.biome)} score=${game.world.score}"
        .format(game.world.screens))

    check("the second head is in the save", save.owns(Outfits.TWO_HEAD_ID))
    check("it survives a reload", Save(FakeCtx(prefs)).owns(Outfits.TWO_HEAD_ID))
    check("no card thrown over the run in progress", !game.unlockPopup.isShowing)

    // now die for real and see whether the card is still there on the death screen
    game.world.buddy.flight = Flight.NONE
    game.world.buddy.flightTime = 0f
    game.world.buddy.y = game.world.camY - Tuning.VIEW_H
    game.world.buddy.vy = -3000f
    var f = 0
    while (f < 60 * 40 && game.screen == Game.Screen.PLAY) { game.update(DT); f++ }
    check("the run ended", game.screen == Game.Screen.GAMEOVER)
    step(game, 2)   // the first frame after the transition is the one that promotes it
    check("the card comes up on the death screen", game.unlockPopup.isShowing)

    println("\n--- and the third head, one more lap up ---")
    game.startRun()
    step(game, 60 * 8)
    check("climbed past lap 2", climbTo(game, lapScreens * 2f + 1.5f))
    println("  screens=%.1f biome=${game.world.biome} lap=${Palettes.lapOf(game.world.biome)}"
        .format(game.world.screens))
    check("Cerberus is in the save", save.owns(Outfits.CERBERUS_ID))
    check("Cerberus survives a reload", Save(FakeCtx(prefs)).owns(Outfits.CERBERUS_ID))

    println("\n--- and with a Second Life spent on the way up ---")
    val prefs2 = FakePrefs()
    val save2 = Save(FakeCtx(prefs2))
    save2.controlsAsked = true
    save2.grantPowerup(com.blacklab.buddybounce.data.Powerups.SECOND_LIFE, 1)
    val ctx2 = FakeCtx(prefs2)
    val g2 = Game(save2, Audio(ctx2, save2), Music(ctx2, save2), Host())
    g2.onSurface(1080, 2400)
    g2.startRun()
    // the picker is up because there is a power-up on the shelf; take none and count down
    g2.beginCountdown(null)
    step(g2, 60 * 8)
    check("the run is live", g2.screen == Game.Screen.PLAY)
    check("climbed short of the lap", climbTo(g2, 20f))
    g2.world.buddy.flight = Flight.NONE
    g2.world.buddy.flightTime = 0f
    g2.world.buddy.y = g2.world.camY - Tuning.VIEW_H
    g2.world.buddy.vy = -3000f
    var d = 0
    while (d < 60 * 40 && g2.screen == Game.Screen.PLAY) { g2.update(DT); d++ }
    check("died short of the lap", g2.screen == Game.Screen.GAMEOVER && !save2.owns(Outfits.TWO_HEAD_ID))
    g2.reviveWithSecondLife()
    // Hold the rocket through the countdown and out the other side. Eight seconds of a run with
    // NO steering at all is its own way to die, and that is the probe's doing, not the game's.
    var c2 = 0
    while (c2 < 60 * 8 && g2.screen != Game.Screen.PLAY) {
        g2.world.buddy.flight = Flight.ROCKET
        g2.world.buddy.flightTime = 5f
        g2.update(DT)
        c2++
    }
    check("back in the run", g2.screen == Game.Screen.PLAY)
    check("climbed past lap 1 after the revive", climbTo(g2, lapScreens + 1.5f))
    check("the second head is in the save", save2.owns(Outfits.TWO_HEAD_ID))
    check("it survives a reload", Save(FakeCtx(prefs2)).owns(Outfits.TWO_HEAD_ID))

    println("\n--- a save from before the skins existed, that had already been round ---")
    val old = FakePrefs()
    val oldSave = Save(FakeCtx(old))
    oldSave.playerName = "VETERAN"
    oldSave.controlsAsked = true
    oldSave.highestBiome = 5          // reached round two in an earlier build
    check("it does not own it yet", !oldSave.owns(Outfits.TWO_HEAD_ID))
    val relaunch = Game(oldSave, Audio(FakeCtx(old), oldSave), Music(FakeCtx(old), oldSave), Host())
    relaunch.onSurface(1080, 2400)
    check("launching hands it over", oldSave.owns(Outfits.TWO_HEAD_ID))
    check("Cerberus is NOT handed over", !oldSave.owns(Outfits.CERBERUS_ID))
    step(relaunch, 4)
    check("and the card comes up on the menu", relaunch.unlockPopup.isShowing)

    println("\n--- and one that had been round twice ---")
    val old2 = FakePrefs()
    val twiceSave = Save(FakeCtx(old2))
    twiceSave.controlsAsked = true
    twiceSave.highestBiome = 11
    Game(twiceSave, Audio(FakeCtx(old2), twiceSave), Music(FakeCtx(old2), twiceSave), Host())
    check("both heads handed over", twiceSave.owns(Outfits.TWO_HEAD_ID) && twiceSave.owns(Outfits.CERBERUS_ID))

    println("\n--- a save that never got there gets nothing ---")
    val nov = FakePrefs()
    val novice = Save(FakeCtx(nov))
    novice.controlsAsked = true
    novice.highestBiome = 4
    Game(novice, Audio(FakeCtx(nov), novice), Music(FakeCtx(nov), novice), Host())
    check("nothing handed over below the first lap",
        !novice.owns(Outfits.TWO_HEAD_ID) && !novice.owns(Outfits.CERBERUS_ID))

    println()
    println(if (failures == 0) "ALL GOOD" else "$failures FAILURE(S)")
    if (failures > 0) System.exit(1)
}
