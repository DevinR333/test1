package com.blacklab.buddybounce

import android.graphics.Canvas
import com.blacklab.buddybounce.audio.Audio
import com.blacklab.buddybounce.audio.Music
import com.blacklab.buddybounce.data.Outfits
import com.blacklab.buddybounce.data.Powerups
import com.blacklab.buddybounce.data.NoStore
import com.blacklab.buddybounce.data.Save
import com.blacklab.buddybounce.data.Store
import com.blacklab.buddybounce.data.Trails
import com.blacklab.buddybounce.game.Buddy
import com.blacklab.buddybounce.game.DeathCause
import com.blacklab.buddybounce.game.Enemy
import com.blacklab.buddybounce.game.EnemyKind
import com.blacklab.buddybounce.game.Flight
import com.blacklab.buddybounce.game.MathX
import com.blacklab.buddybounce.game.Pickup
import com.blacklab.buddybounce.game.PickupKind
import com.blacklab.buddybounce.game.Platform
import com.blacklab.buddybounce.game.Tuning
import com.blacklab.buddybounce.game.World
import com.blacklab.buddybounce.input.Controls
import com.blacklab.buddybounce.render.Art
import com.blacklab.buddybounce.render.Backdrop
import com.blacklab.buddybounce.render.BuddyArt
import com.blacklab.buddybounce.render.ColorX
import com.blacklab.buddybounce.render.Fx
import com.blacklab.buddybounce.render.GameRenderer
import com.blacklab.buddybounce.render.Palettes
import com.blacklab.buddybounce.render.Pose
import com.blacklab.buddybounce.render.Scenes
import com.blacklab.buddybounce.render.TrailArt
import com.blacklab.buddybounce.ui.GachaScreen
import com.blacklab.buddybounce.ui.GameOverScreen
import com.blacklab.buddybounce.ui.Hud
import com.blacklab.buddybounce.ui.MenuScreen
import com.blacklab.buddybounce.ui.PreRunScreen
import com.blacklab.buddybounce.ui.ScenesScreen
import com.blacklab.buddybounce.ui.ScoresScreen
import com.blacklab.buddybounce.ui.SettingsScreen
import com.blacklab.buddybounce.ui.Theme
import com.blacklab.buddybounce.ui.UnlockPopup
import com.blacklab.buddybounce.ui.Ui
import com.blacklab.buddybounce.ui.WardrobeScreen
import kotlin.math.abs
import kotlin.math.sin

/**
 * Owns the screen the player is on, the simulation, and the draw order.
 *
 * Two coordinate spaces: the UI is laid out in a 1600-unit-tall space that fills the screen,
 * and the world is drawn inside it at [Theme.SCREEN_H] / [Tuning.VIEW_H] scale. That ratio is
 * the camera zoom - the world view is 2560 units tall, so Buddy and the platforms sit small in
 * a lot of sky, while the menus stay exactly the size they were.
 */
class Game(val save: Save, val audio: Audio, val music: Music, val host: Host) : World.Events {

    /**
     * Rewarded ads and in-app purchases. [NoStore] until an SDK is wired in, and while it is
     * NoStore the prize machine simply does not show the two buttons. See data/Store.kt.
     */
    var store: Store = NoStore

    interface Host {
        fun setLandscape(landscape: Boolean)
        fun promptName(current: String, title: String)
        fun vibrate(ms: Long, amplitude: Int)

        /** Same, but after a delay - used to land the second beat of a two-beat buzz. */
        fun vibrateLater(delayMs: Long, ms: Long, amplitude: Int)
    }

    enum class Screen { NAME, MENU, PRERUN, PLAY, PAUSE, GAMEOVER, WARDROBE, GACHA, SCENES, SCORES, SETTINGS }

    /** What the pre-run screen is doing. */
    enum class PreRun { PICK, COUNTDOWN }

    val controls = Controls(save)
    val fx = Fx()

    var art = Art(1f)
        private set
    var ui = Ui(art)
        private set
    private var buddyArt = BuddyArt(art)
    private var gameArt = GameRenderer(art)
    private var backdrop = Backdrop(art)

    var world = World(Tuning.REF_W, this)
        private set

    private val menu = MenuScreen(this)
    private val wardrobe = WardrobeScreen(this)
    private val gacha = GachaScreen(this)
    private val scenes = ScenesScreen(this)
    private val scores = ScoresScreen(this)
    private val settings = SettingsScreen(this)
    private val gameOver = GameOverScreen(this)
    private val preRun = PreRunScreen(this)
    private val hud = Hud(this)

    var screen = Screen.MENU
        private set
    var previousScreen = Screen.MENU
        private set
    var screenAnim = 1f
        private set

    /** Width of the UI space (height is always [Theme.SCREEN_H]). */
    var worldW = Theme.SCREEN_H
        private set
    /** Width of the world space. */
    var playW = Tuning.REF_W
        private set
    var scale = 1f
        private set
    var widthPx = 0
        private set
    var heightPx = 0
        private set
    var time = 0f
        private set

    /** UI units per world unit. */
    val zoom: Float get() = Theme.SCREEN_H / Tuning.VIEW_H

    /** World units of drag per UI unit of finger travel. Recomputed on every surface change. */
    private var dragWorldPerUi = 0f

    // ---- pre-run ----
    var preRunPhase = PreRun.PICK
        private set
    var countdown = 0f
        private set
    /** True while a finger is on the glass during the countdown, which fast-forwards it. */
    var holdingToSkip = false
        private set
    /**
     * True when the countdown is a RESUME from pause rather than the start of a run. Same beat,
     * same hold-to-skip - the point is identical either way: a moment to find the platforms
     * again before anything moves.
     */
    var resuming = false
        private set
    var chosenPowerup: String? = null
        private set
    private var pendingPowerup: String? = null
    private var lastCountdownTick = -1

    // ---- run bookkeeping, read by the game-over screen ----
    var lastScore = 0
    var lastRunCoins = 0
    var lastBonusCoins = 0
    /** Halos collected in the last run. Only ever non-zero after a run in Heaven. */
    var lastHalos = 0
    var lastCoins = 0
    var lastRank = -1
    var lastNewBest = false
    var lastBiome = 0
    var lastPowerup: String? = null

    private var shake = 0f
    private var shakeSeed = 0f
    private var flash = 0f
    private var biomeToast = 0f
    private var biomeToastName = ""

        private set

    private val pose = Pose()
    private val menuBuddy = Buddy()
    private var menuCamY = 0f
    private var started = false

    val equippedOutfit: String get() = save.equippedOutfit
    val rimColor: Int get() = Palettes.get(world.biome).rim

    // -------------------------------------------------------------------------------------
    // lifecycle
    // -------------------------------------------------------------------------------------

    fun onSurface(wPx: Int, hPx: Int) {
        if (wPx <= 0 || hPx <= 0) return
        val newScale = hPx / Theme.SCREEN_H
        val scaleChanged = abs(newScale - scale) > 0.0001f

        widthPx = wPx
        heightPx = hPx
        scale = newScale
        worldW = wPx / newScale
        playW = worldW / zoom
        // World units of travel per UI unit of finger movement. Anchored to the short edge of
        // the display so a swipe means the same thing in portrait and landscape - see
        // Tuning.DRAG_SPAN. (UI units x scale = pixels, hence the scale here.)
        dragWorldPerUi = if (wPx > 0 && hPx > 0) {
            newScale / minOf(wPx, hPx).toFloat() * Tuning.DRAG_SPAN
        } else {
            0f
        }

        if (scaleChanged) {
            art.dispose()
            art = Art(scale)
            ui = Ui(art).also {
                it.screenW = worldW
                it.safeTop = uiInsets[0]; it.safeBottom = uiInsets[1]
                it.safeLeft = uiInsets[2]; it.safeRight = uiInsets[3]
            }
            buddyArt = BuddyArt(art)
            gameArt = GameRenderer(art)
            backdrop = Backdrop(art)
        }
        // Set unconditionally: the width can change without the scale doing so, and this is the
        // backstop that stops any string running off the edge.
        ui.screenW = worldW

        world.resize(playW)
        if (!started) {
            started = true
            applyScene()
            world.reset()
            menuBuddy.reset(0f, 0f)
            screen = if (save.hasName) Screen.MENU else Screen.NAME
            if (screen == Screen.NAME) host.promptName("", "What's your name?")
            // A save can qualify for Heaven while the app is closed - the last unlock might have
            // been the very thing the player quit after. Catch that here, and catch a reveal
            // that was queued but never reached the menu, so it is still waiting on next launch.
            if (save.refreshHeaven() || (save.ownsScene(Scenes.HEAVEN_ID) && !save.heavenAnnounced)) {
                queueHeavenAnnounce()
            }
            // Opening on the menu skips goto(), which is what normally spends the queue.
            if (screen == Screen.MENU) askControlsOnce()
            if (screen == Screen.MENU && heavenAnnouncePending) {
                heavenAnnouncePending = false
                save.heavenAnnounced = true
                announceHeaven()
            }
        }
    }

    private val uiInsets = FloatArray(4)

    fun setInsets(topPx: Int, bottomPx: Int, leftPx: Int, rightPx: Int) {
        uiInsets[0] = topPx / scale
        uiInsets[1] = bottomPx / scale
        uiInsets[2] = leftPx / scale
        uiInsets[3] = rightPx / scale
        ui.safeTop = uiInsets[0]
        ui.safeBottom = uiInsets[1]
        ui.safeLeft = uiInsets[2]
        ui.safeRight = uiInsets[3]
    }

    /** Points the renderer at the scene the player has selected. */
    fun applyScene() {
        Palettes.current = Scenes.of(save.selectedScene)
        // Heaven changes the economy, not just the wallpaper: halos on the ground, and height
        // pays nothing at all.
        world.haloMode = save.selectedScene == Scenes.HEAVEN_ID
        // The soundtrack belongs to the world, so it changes the moment the world does.
        music.setWorld(save.selectedScene)
    }

    /** True when the dog should be drawn blessed. See [Outfits.isBlessed] for the rule. */
    private fun ghostAmount(): Boolean =
        Outfits.isBlessed(equippedOutfit, world.haloMode, secondLifeActive, save.ghostEnabled)

    /**
     * Heaven opening is the payoff for the whole collection, so it gets the full reveal rather
     * than a line of toast over whatever screen happened to trip it.
     *
     * The unlock can fire anywhere - buying the last trail at the machine, or simply launching
     * the game with a save that already qualifies - so the announcement is QUEUED here and
     * spent the next time the player reaches the main menu. That is also the only screen where
     * the reveal makes sense: they are looking at the world list they are about to go and use.
     */
    /**
     * Asks once, the first time they reach the menu, how they want to steer.
     *
     * Tilt and drag feel completely different and neither is obviously right - tilt is hands-off
     * but useless lying down, drag is exact but covers the glass. Guessing for them and burying
     * the switch in Settings means most players never find out the other one exists.
     */
    private fun askControlsOnce() {
        if (save.controlsAsked) return
        save.controlsAsked = true
        unlockPopup.queueControls()
    }

    fun queueHeavenAnnounce() {
        heavenAnnouncePending = true
    }

    private fun announceHeaven() {
        unlockPopup.queue(UnlockPopup.Kind.SCENE, Scenes.HEAVEN_ID, "HEAVEN IS OPEN")
    }

    /** Called by the popup as each card comes up, so the fanfare lands with the reveal. */
    fun onUnlockRevealed(prize: Boolean) {
        if (!prize) {
            audio.play(Audio.TAP, 0.6f)
            return
        }
        flashScreen(0.8f)
        shakeScreen(0.4f)
        audio.play(Audio.FANFARE, 1f)
    }

    fun goto(s: Screen) {
        if (s == screen) return
        previousScreen = screen
        screen = s
        screenAnim = 0f
        if (s == Screen.MENU) askControlsOnce()
        // queued from wherever the last unlock happened; spent here, on arrival at the menu
        if (s == Screen.MENU && heavenAnnouncePending) {
            heavenAnnouncePending = false
            save.heavenAnnounced = true
            announceHeaven()
        }
        if (s != Screen.PLAY) {
            controls.clearTouch()
            audio.stopJet()
        }
        if (s == Screen.MENU || s == Screen.GAMEOVER) fx.clear()
    }

    fun tap() = audio.play(Audio.TAP, 0.45f)

    /**
     * Pressing PLAY doesn't start the run: it lays out a fresh world and hands over to the
     * pre-run screen, so you can spend a power-up and see the ground before the countdown.
     */
    fun startRun() {
        fx.clear()
        applyScene()
        world.reset()
        lastScore = 0; lastRunCoins = 0; lastBonusCoins = 0; lastCoins = 0; lastHalos = 0
        coinFlash = 0; coinFlashT = 0f
        secondLifeActive = false
        bankedCoinsThisRun = 0
        continuedStamp = 0L
        lastRank = -1; lastNewBest = false
        chosenPowerup = null
        pendingPowerup = null
        lastCountdownTick = -1
        resuming = false
        preRunPhase = if (save.totalPowerups() > 0) PreRun.PICK else PreRun.COUNTDOWN
        countdown = COUNTDOWN_SECONDS
        goto(Screen.PRERUN)
    }

    /** Called by the pre-run picker: spend the chosen power-up (or none) and count down. */
    fun beginCountdown(powerupId: String?) {
        chosenPowerup = powerupId
        lastPowerup = powerupId
        if (powerupId != null && save.consumePowerup(powerupId)) {
            applyPowerupNow(powerupId)
        } else {
            chosenPowerup = null
        }
        preRunPhase = PreRun.COUNTDOWN
        countdown = COUNTDOWN_SECONDS
        holdingToSkip = false
        resuming = false
        cosmeticAccum = 0f
        lastCountdownTick = -1
    }

    /**
     * Anything that changes the layout is applied immediately so the countdown shows the player
     * what they are about to jump into; anything that is pure velocity waits for "GO".
     */
    private fun applyPowerupNow(id: String) {
        when (id) {
            Powerups.HEAD_START -> world.startWithHeadStart(6f)
            Powerups.MAGNET_RUN -> world.startWithMagnet()
            Powerups.COIN_DOUBLER -> world.startWithCoinMultiplier(2)
            Powerups.LUCKY_PAWS -> world.startWithLuckyCoins(0.34f)
            Powerups.FEATHER_FALL -> world.startWithLowGravity(30f)
            Powerups.SAFETY_NET -> world.startWithSafetyNets(1)
            Powerups.SHIELD_START -> world.startWithShield(Tuning.SHIELD_TIME * 1.8f)
            else -> pendingPowerup = id
        }
    }

    private fun applyPowerupOnGo() {
        when (pendingPowerup) {
            Powerups.MOON_JUMP -> { world.startWithMoonJump(4.2f); shake = 0.8f }
            Powerups.ROCKET_START -> world.startWithFlight(Flight.ROCKET, Tuning.ROCKET_TIME)
            Powerups.JETPACK_START -> world.startWithFlight(Flight.JETPACK, Tuning.JETPACK_TIME)
        }
        pendingPowerup = null
    }

    // -------------------------------------------------------------------------------------
    // testing back doors
    //
    // Three names, all checked against the RAW text before the sanitiser strips punctuation:
    //
    //   u7d%4>   unlock everything, which also opens Heaven
    //   u7d%4<   unlock everything EXCEPT one trail, so the final unlock - and Heaven opening
    //            as a result of it - can actually be watched happening
    //   u7d%4=   free spins on the prize machine, to grind out that last trail
    //   u7d%4+   parks you on 999 halos with Heaven open, so the thousandth halo - and
    //            the outfit it unlocks - can be tested without the grind
    //
    // And one that is not a testing aid at all:
    //
    //   uu4*=^7  the developer skin. The ONLY way to get it - it is not in the prize
    //            pool, and the wardrobe does not admit it exists until this is entered.
    //
    //   u7d%4~   every power-up, always, and spending one costs nothing. Second Life
    //            included, so a run can be continued as many times as you like.
    // -------------------------------------------------------------------------------------

    fun isUnlockCode(raw: String): Boolean {
        val t = raw.trim()
        return t.equals(UNLOCK_CODE, ignoreCase = true) ||
            t.equals(ALMOST_CODE, ignoreCase = true) ||
            t.equals(FREE_SPINS_CODE, ignoreCase = true) ||
            t.equals(HALO_PRIME_CODE, ignoreCase = true) ||
            t.equals(DEV_SKIN_CODE, ignoreCase = true) ||
            t.equals(INFINITE_POWERUPS_CODE, ignoreCase = true)
    }

    fun applyUnlockCode(raw: String) {
        val t = raw.trim()
        when {
            t.equals(FREE_SPINS_CODE, ignoreCase = true) -> {
                save.freeSpins = true
                unlockPopup.queueMessage(
                    "BACK DOOR", "Free spins on",
                    "Every pull at the coin machine is free from now on. Keep going until the " +
                        "last thing you are missing turns up."
                )
            }
            t.equals(INFINITE_POWERUPS_CODE, ignoreCase = true) -> {
                save.infinitePowerups = true
                unlockPopup.queueMessage(
                    "BACK DOOR", "Power-ups unlimited",
                    "Every power-up is stocked and nothing you spend runs out - Second Life " +
                        "too, so a run can be continued as often as you like."
                )
            }
            t.equals(DEV_SKIN_CODE, ignoreCase = true) -> {
                save.unlock(Outfits.DEV_ID)
                unlockPopup.queue(UnlockPopup.Kind.OUTFIT, Outfits.DEV_ID, "APPROVED")
            }
            t.equals(HALO_PRIME_CODE, ignoreCase = true) -> {
                // Halos only drop in Heaven, so the code is useless unless Heaven is open. This
                // opens the world and nothing else - the other codes are there for the rest.
                save.unlockScene(Scenes.HEAVEN_ID)
                save.primeHalosForTest()
                unlockPopup.queueMessage(
                    "BACK DOOR", "999 halos",
                    "Heaven is open and you are one halo short of everything it pays out. " +
                        "Collect one more in there and watch both unlock."
                )
            }
            t.equals(ALMOST_CODE, ignoreCase = true) -> unlockAllBut(1)
            else -> unlockAllBut(0)
        }
        if (!save.hasName) save.playerName = "TESTER"
        if (screen == Screen.NAME) goto(Screen.MENU)
    }

    /**
     * @param holdBackTrails how many trails to deliberately leave locked. One means the
     *   collection is complete except for a single trail, so unlocking it is what trips the
     *   "everything unlocked" check and opens Heaven - the exact moment worth testing.
     */
    private fun unlockAllBut(holdBackTrails: Int) {
        for (o in Outfits.ALL) {
            // The Heaven outfit lives behind Heaven, so granting it here would be cheating in
            // the wrong direction - and it is excluded from the completion check anyway. The
            // developer skin has exactly one door, and this is not it.
            if (o.id == Outfits.HEAVEN_ONLY_ID || o.id == Outfits.DEV_ID) continue
            save.unlock(o.id)
        }
        for (sc in Scenes.unlockable) save.unlockScene(sc.id)
        val pool = Trails.collectable
        val keep = pool.size - holdBackTrails.coerceIn(0, pool.size)
        for (i in 0 until keep) save.unlockTrail(pool[i].id)
        for (pu in Powerups.ALL) save.grantPowerupCapped(pu.id, Tuning.POWERUP_MAX)
        save.grantCoins(1000)

        if (holdBackTrails > 0) {
            unlockPopup.queueMessage(
                "BACK DOOR", "Almost everything",
                "Every outfit, world and trail is yours except $holdBackTrails. Win the last " +
                    "one from the coin machine and see what it opens."
            )
        } else {
            unlockPopup.queueMessage(
                "BACK DOOR", "Everything unlocked",
                "Every outfit, trail and world, plus five of each power-up and a thousand coins."
            )
        }
        if (save.refreshHeaven()) queueHeavenAnnounce()
    }

    fun onNameEntered(name: String) {
        val clean = Save.sanitizeName(name)
        if (clean.isNotEmpty()) save.playerName = clean
        if (screen == Screen.NAME) goto(Screen.MENU)
    }

    fun onBackPressed(): Boolean = when (screen) {
        Screen.PLAY -> { goto(Screen.PAUSE); true }
        Screen.PAUSE -> { goto(Screen.PLAY); true }
        Screen.PRERUN -> { goto(Screen.MENU); true }
        Screen.WARDROBE, Screen.GACHA, Screen.SCENES, Screen.SCORES, Screen.SETTINGS -> { goto(Screen.MENU); true }
        Screen.GAMEOVER -> { goto(Screen.MENU); true }
        else -> false
    }

    fun onPauseApp() {
        if (screen == Screen.PLAY) goto(Screen.PAUSE)
        audio.stopJet()
        save.flush()
    }

    // -------------------------------------------------------------------------------------
    // update
    // -------------------------------------------------------------------------------------

    fun update(dtRaw: Float) {
        val dt = dtRaw.coerceIn(0f, 0.05f)
        time += dt
        screenAnim = MathX.approach(screenAnim, 1f, 9f, dt)
        shake = MathX.approach(shake, 0f, 6f, dt)
        flash = MathX.approach(flash, 0f, 5f, dt)
        if (biomeToast > 0f) biomeToast -= dt
        if (coinFlashT > 0f) coinFlashT -= dt
        unlockPopup.update(dt)
        ui.beginFrame(dt)

        when (screen) {
            Screen.PLAY -> updatePlay(dt)
            Screen.PRERUN -> updatePreRun(dt)
            Screen.GACHA -> { gacha.update(dt); fx.update(dt); updateMenuBuddy(dt) }
            else -> { fx.update(dt); updateMenuBuddy(dt) }
        }
    }

    private fun updatePlay(dt: Float) {
        val steer = controls.steer()
        // Finger travel arrives in UI units; this converts it to world units at a rate anchored
        // to the screen's short edge, so the same swipe goes the same distance whatever the
        // orientation or the resolution.
        val dragDx = controls.consumeDragDx() * dragWorldPerUi
        world.update(dt, steer, controls.lastInputDigital, dragDx, controls.dragging)
        fx.update(dt)
        emitFlightTrail(dt)
        emitCosmeticTrail(dt)
        if (world.deathSettled) finishRun()
    }

    private fun updatePreRun(dt: Float) {
        fx.update(dt)
        world.buddy.updateAnim(dt, world.metrics.maxVx)
        if (preRunPhase != PreRun.COUNTDOWN) return
        // Hold a finger anywhere and the count runs down fast - if you are ready, you are ready.
        countdown -= dt * (if (holdingToSkip) COUNTDOWN_SKIP_RATE else 1f)
        val tick = countdown.toInt()
        if (tick != lastCountdownTick && countdown > 0f) {
            lastCountdownTick = tick
            audio.play(Audio.TAP, 0.6f, if (tick <= 0) 1.5f else 1f)
        }
        if (countdown <= 0f) {
            applyPowerupOnGo()
            goto(Screen.PLAY)
            // A finger held to skip the count should carry straight into steering, rather than
            // going dead until they lift it and put it back down.
            if (holdingToSkip) {
                holdingToSkip = false
                beginTouchSteer(holdX, holdY)
            }
        }
    }

    private fun updateMenuBuddy(dt: Float) {
        menuCamY += dt * 70f
        menuBuddy.vy -= Tuning.GRAVITY * 0.4f * dt
        menuBuddy.y += menuBuddy.vy * dt
        if (menuBuddy.y <= 0f && menuBuddy.vy < 0f) {
            menuBuddy.y = 0f
            menuBuddy.vy = Tuning.JUMP_V * 0.38f
            menuBuddy.onBounce(1f)
        }
        menuBuddy.updateAnim(dt, 1000f)
    }

    /**
     * True once a Second Life has been spent this run. Purely cosmetic from then on - the dog is
     * drawn blessed for the rest of the run - but it is also what stops a single death being
     * revived twice: [reviveWithSecondLife] only fires from the death screen, one press at a
     * time, and each press spends another one from the shelf.
     */
    var secondLifeActive = false

    /** Reveal cards for the unlocks that do not come out of the prize machine. */
    val unlockPopup = UnlockPopup(this)

    /** True while a menu stick is pushed, so one push is one move rather than a stampede. */
    private var stickLatched = false

    /** Set when Heaven unlocks; spent on the next arrival at the main menu. */
    private var heavenAnnouncePending = false

        private set

    /** Coins already banked for the run in progress, and the entry they were banked under. */
    private var bankedCoinsThisRun = 0
    private var continuedStamp = 0L

    private var trailAccum = 0f
    private var cosmeticAccum = 0f
    private var holdX = 0f
    private var holdY = 0f

    /**
     * The equipped cosmetic trail, metered by DISTANCE rather than by time: standing still lays
     * down nothing, and a rocket climb lays down the same spacing as a slow bounce instead of a
     * dense wall of particles. Combined with the short lifetimes in [Fx.cosmetic] that keeps the
     * ribbon readable without ever covering the platforms.
     */
    private fun emitCosmeticTrail(dt: Float) {
        val trail = Trails.of(save.equippedTrail) ?: return
        val b = world.buddy
        if (b.dying) return
        val speed = MathX.sqrtf(b.vx * b.vx + b.vy * b.vy)
        cosmeticAccum += speed * dt
        // One particle roughly every 58 world units travelled, capped so a single long frame
        // cannot dump a burst all at once.
        var budget = 6
        while (cosmeticAccum > 58f && budget > 0) {
            cosmeticAccum -= 58f
            budget--
            fx.cosmetic(
                b.x, b.y + 24f, trail.style, trail.motion,
                trail.hot, trail.cool, trail.accent, b.vx, b.vy
            )
        }
        if (budget == 0) cosmeticAccum = 0f
    }

    private fun emitFlightTrail(dt: Float) {
        val b = world.buddy
        if (!b.flying) return
        trailAccum += dt
        val period = when (b.flight) {
            Flight.PROPELLER -> 0.05f
            Flight.JETPACK -> 0.028f
            else -> 0.018f
        }
        val color = when (b.flight) {
            Flight.PROPELLER -> 0xFF9FDDF7.toInt()
            Flight.JETPACK -> 0xFFFFB347.toInt()
            else -> 0xFFFF7A3C.toInt()
        }
        while (trailAccum > period) {
            trailAccum -= period
            fx.trail(b.x, b.y + 8f, color, if (b.flight == Flight.ROCKET) 60f else 40f)
        }
    }

    /**
     * End of a run. This is the ONLY place coins are banked, and [Save.bankRun] commits score,
     * coins and the leaderboard in one synchronous write - so quitting mid-run loses that run's
     * coins, and anything banked survives the app being killed.
     */
    private fun finishRun() {
        lastScore = world.score
        lastRunCoins = world.runCoins
        lastBonusCoins = world.heightBonusCoins
        lastCoins = lastRunCoins + lastBonusCoins
        lastHalos = world.runHalos
        lastBiome = world.biome
        lastNewBest = lastScore > save.bestScore
        save.highestBiome = world.biome
        // A run that has already been part-banked (because a Second Life reopened it) replaces
        // its own earlier entry instead of adding a second one, and only the new coins are added.
        lastRank = save.bankRun(lastScore, lastCoins - bankedCoinsThisRun, continuedStamp)
        bankedCoinsThisRun = lastCoins
        continuedStamp = save.lastBankedStamp
        if (lastHalos > 0) {
            val hadGlory = save.ownsTrail(Trails.HEAVEN_ONLY_ID)
            val hadEternal = save.owns(Outfits.HEAVEN_ONLY_ID)
            save.addHalos(lastHalos)
            if (!hadGlory && save.ownsTrail(Trails.HEAVEN_ONLY_ID)) {
                unlockPopup.queue(UnlockPopup.Kind.TRAIL, Trails.HEAVEN_ONLY_ID, "500 HALOS")
            }
            if (!hadEternal && save.owns(Outfits.HEAVEN_ONLY_ID)) {
                unlockPopup.queue(UnlockPopup.Kind.OUTFIT, Outfits.HEAVEN_ONLY_ID, "1000 HALOS")
            }
        }
        if (lastNewBest && lastScore > 0) audio.play(Audio.FANFARE, 0.7f)
        goto(Screen.GAMEOVER)
    }

    /** Can the death screen offer a continue? */
    fun canSecondLife(): Boolean = save.powerupCount(Powerups.SECOND_LIFE) > 0

    /**
     * Spend one Second Life and carry on from where he fell.
     *
     * One press revives one death; dying again offers it again while any remain, which is what
     * the brief asked for - two in the bank means two continues in the same run.
     */
    fun reviveWithSecondLife() {
        if (!save.consumePowerup(Powerups.SECOND_LIFE)) return
        secondLifeActive = true
        world.revive()
        flashScreen(0.7f)
        audio.play(Audio.FANFARE, 0.8f)
        // Straight into the countdown, so the player gets their bearings before it moves again.
        resumeFromPause()
    }

    // -------------------------------------------------------------------------------------
    // draw
    // -------------------------------------------------------------------------------------

    fun draw(c: Canvas) {
        c.save()
        c.scale(scale, scale)
        if (shake > 0.004f) {
            shakeSeed += 0.7f
            c.translate(sin(shakeSeed * 3.1f) * shake * 26f, sin(shakeSeed * 2.3f) * shake * 20f)
        }

        when (screen) {
            Screen.PLAY, Screen.PAUSE -> {
                drawWorld(c)
                hud.draw(c, biomeToast, biomeToastName)
                if (screen == Screen.PAUSE) hud.drawPause(c)
            }
            Screen.PRERUN -> {
                drawWorld(c)
                preRun.draw(c)
            }
            Screen.GAMEOVER -> {
                drawWorld(c)
                ui.scrim(c, worldW, Theme.SCREEN_H, 0.62f * screenAnim)
                gameOver.draw(c)
            }
            else -> {
                drawMenuBackdrop(c)
                when (screen) {
                    Screen.NAME -> menu.drawNamePrompt(c)
                    Screen.MENU -> menu.draw(c)
                    Screen.WARDROBE -> wardrobe.draw(c)
                    Screen.GACHA -> gacha.draw(c)
                    Screen.SCENES -> scenes.draw(c)
                    Screen.SCORES -> scores.draw(c)
                    Screen.SETTINGS -> settings.draw(c)
                    else -> {}
                }
            }
        }

        // Over everything, and only outside a run - a reveal that covered the screen mid-climb
        // would be a death sentence.
        if (screen != Screen.PLAY) {
            unlockPopup.draw(c)
            ui.drawFocusRing(c)
        }

        if (flash > 0.01f) {
            ui.fill(c, worldW, Theme.SCREEN_H, ColorX.withAlpha(0xFFFFFFFF.toInt(), flash * 0.55f))
        }
        c.restore()
        ui.endFrame()
    }

    /** Draws the simulation, zoomed out inside the UI space. */
    private fun drawWorld(c: Canvas) {
        val camY = world.camY
        val viewTop = camY + Tuning.VIEW_H
        val screensNow = world.screensAt(camY + Tuning.VIEW_H * 0.5f)
        val biome = Tuning.biomeIndex(screensNow)
        val blend = Tuning.biomeBlend(screensNow)
        val pal = Palettes.get(biome)

        c.save()
        c.scale(zoom, zoom)

        backdrop.draw(c, playW, camY, time, biome, blend)
        backdrop.drawGround(c, playW, viewTop, Tuning.GROUND_Y, Palettes.current)

        for (p in world.platforms.items) {
            if (p.alive) gameArt.drawPlatform(c, p, Palettes.get(p.biome), viewTop)
        }
        for (pk in world.pickups.items) {
            if (pk.alive) gameArt.drawPickup(c, pk, pal, viewTop, time)
        }
        for (e in world.enemies.items) {
            if (e.alive) gameArt.drawEnemy(c, e, pal, viewTop, time)
        }

        fx.draw(c, art, viewTop)
        drawBuddy(c, viewTop)
        fx.drawPops(c, ui.title, viewTop)
        backdrop.drawVignette(c, playW, 0.85f)

        c.restore()
    }

    private fun drawBuddy(c: Canvas, viewTop: Float) {
        val b = world.buddy
        posePlayer(b)
        pose.ghost = if (ghostAmount()) 1f else 0f
        val sy = viewTop - b.y
        buddyArt.draw(c, b.x, sy, 1f, pose, equippedOutfit, rimColor)
        // The copy on the far side waits until he is mostly across, rather than appearing the
        // moment his nose clears the edge. See Tuning.WRAP_SHOW.
        val span = world.wrapW
        val show = Tuning.BUDDY_W * Tuning.WRAP_SHOW
        if (b.x < show) buddyArt.draw(c, b.x + span, sy, 1f, pose, equippedOutfit, rimColor)
        if (b.x > span - show) buddyArt.draw(c, b.x - span, sy, 1f, pose, equippedOutfit, rimColor)
    }

    private fun posePlayer(b: Buddy) {
        pose.squash = b.squash
        pose.lean = b.lean
        pose.earFlap = b.earFlap
        pose.tail = b.tailPhase
        pose.blink = b.blinkAmount
        pose.mouth = b.mouthOpen
        pose.facing = b.facing
        pose.flight = b.flight
        pose.flightT = b.flightTime
        pose.shield = b.shieldTime
        pose.invuln = b.invulnT
        pose.hurt = b.hurtFlash
        pose.spin = if (b.dying) b.deathSpin else 0f
        pose.dead = b.dying
        pose.time = b.t
    }

    /** The idle Buddy used by every menu. Menus are in UI space, so he is drawn smaller. */
    fun drawMenuBuddy(c: Canvas, cx: Float, groundY: Float, scaleFactor: Float, outfit: String) {
        pose.reset()
        pose.squash = menuBuddy.squash
        pose.lean = sin(time * 0.9f) * 0.2f
        pose.earFlap = menuBuddy.earFlap
        pose.tail = menuBuddy.tailPhase
        pose.blink = menuBuddy.blinkAmount
        pose.mouth = menuBuddy.mouthOpen
        pose.facing = 1f
        pose.time = time
        if (Outfits.isBlessed(outfit, false, false, save.ghostEnabled)) pose.ghost = 1f
        buddyArt.draw(c, cx, groundY - menuBuddy.y * 0.28f, scaleFactor, pose, outfit, 0xFFFFE6A8.toInt())
    }

    /** A gently idling Buddy for wardrobe cards and the prize reveal. */
    fun drawPosedBuddy(c: Canvas, cx: Float, pawY: Float, scaleFactor: Float, outfit: String, phase: Float) {
        pose.reset()
        if (Outfits.isBlessed(outfit, false, false, save.ghostEnabled)) pose.ghost = 1f
        pose.squash = sin(phase) * 0.1f
        pose.lean = sin(phase * 0.7f) * 0.14f
        pose.tail = phase * 3.4f
        pose.time = phase
        pose.facing = 1f
        buddyArt.draw(c, cx, pawY, scaleFactor, pose, outfit, 0xFFFFE6A8.toInt())
    }

    // ---- trail previews ---------------------------------------------------------------------

    // Rebuilt whenever the Art cache is (i.e. on a resize), same as everything else that caches
    // bitmaps off it.
    private var trailArt: TrailArt? = null
    private var trailArtFor: Art? = null

    /**
     * A still sample of a trail: a short arc of particles, oldest and coolest at the left, newest
     * and hottest at the right, exactly the way it reads behind Buddy in a run. Used by the
     * wardrobe cards and by the prize machine's reveal.
     */
    fun drawTrailSample(c: Canvas, cx: Float, cy: Float, w: Float, h: Float, trailId: String, phase: Float) {
        val trail = Trails.of(trailId) ?: return
        if (trailArtFor !== art) {
            trailArt = TrailArt(art)
            trailArtFor = art
        }
        val ta = trailArt ?: return
        // A longer, finer ribbon than the one behind him in a run. The oversized version this
        // replaces was legible but crowded the cards and the prize reveal, so the MENU sample
        // goes back to the smaller particles; the in-game emitter is untouched and stays big.
        val n = 11
        for (i in 0 until n) {
            val k = i / (n - 1f)                 // 0 at the tail, 1 at the head
            val life = 0.28f + 0.72f * k          // head is freshest
            val px = cx - w * 0.5f + w * k
            val py = cy + sin(k * 3.4f - phase * 1.8f) * h * 0.34f
            val col = ColorX.lerp(trail.cool, trail.hot, life)
            ta.draw(c, trail.style, px, py, h * 0.9f * (0.6f + 0.4f * life),
                k * 5.1f + phase, col, trail.accent, life)
        }
    }

    /** Same sample, sized to fill a prize card. */
    fun drawTrailPreview(c: Canvas, cx: Float, cy: Float, w: Float, h: Float, trailId: String, phase: Float) {
        drawTrailSample(c, cx, cy, w, h, trailId, phase)
    }

    /**
     * Un-pausing runs the countdown over the frozen world instead of dropping you straight back
     * into a falling dog. Nothing is re-laid-out and no power-up is applied; the run is already
     * in progress and simply starts moving again on GO.
     */
    fun resumeFromPause() {
        resuming = true
        preRunPhase = PreRun.COUNTDOWN
        countdown = COUNTDOWN_SECONDS
        holdingToSkip = false
        pendingPowerup = null
        goto(Screen.PRERUN)
    }

    private fun drawMenuBackdrop(c: Canvas) {
        val bands = Palettes.current.bands.size
        val biome = if (screen == Screen.GACHA) 0 else save.highestBiome.coerceIn(0, bands - 1)
        c.save()
        c.scale(zoom, zoom)
        backdrop.draw(c, playW, menuCamY, time, biome, 0f)
        backdrop.drawVignette(c, playW, 0.7f)
        c.restore()
        ui.scrim(c, worldW, Theme.SCREEN_H, 0.34f)
    }

    // -------------------------------------------------------------------------------------
    // input
    // -------------------------------------------------------------------------------------

    fun onPointerDown(xPx: Float, yPx: Float) {
        val x = xPx / scale
        val y = yPx / scale
        ui.onDown(x, y)
        if (screen == Screen.PLAY) beginTouchSteer(x, y)
        if (screen == Screen.PRERUN && preRunPhase == PreRun.COUNTDOWN) {
            holdingToSkip = true
            holdX = x; holdY = y
        }
    }

    fun onPointerMove(xPx: Float, yPx: Float) {
        val x = xPx / scale
        val y = yPx / scale
        ui.onMove(x, y)
        if (screen == Screen.PLAY && controls.touchEnabled) controls.touchMove(x, y)
    }

    fun onPointerUp(xPx: Float, yPx: Float) {
        val x = xPx / scale
        val y = yPx / scale
        ui.onUp(x, y)
        controls.touchUp()
        holdingToSkip = false
    }

    /** Touching anywhere but the pause button starts steering, relative to that point. */
    private fun beginTouchSteer(x: Float, y: Float) {
        if (!controls.touchEnabled) return
        if (hud.isOverPauseButton(x, y)) return
        controls.touchDown(x, y)
    }

    fun onKeyLeft(down: Boolean) { controls.keyLeft = down }
    fun onKeyRight(down: Boolean) { controls.keyRight = down }
    fun onPadAxis(value: Float) { controls.padAxis = value }

    /**
     * Does a controller's stick and d-pad steer Buddy right now, or move a focus ring?
     *
     * Only an actual run steers. The pre-run picker and the countdown are menus - you are
     * choosing a power-up, not flying - and every other screen obviously is.
     */
    fun padSteers(): Boolean = screen == Screen.PLAY

    fun onPadNav(dx: Int, dy: Int) {
        ui.navigate(dx, dy)
    }

    /** A stick held in a menu: one step per push, re-armed when it returns to centre. */
    fun onPadStick(x: Float, y: Float) {
        controls.padAxis = 0f
        val ax = if (x > STICK_ON) 1 else if (x < -STICK_ON) -1 else 0
        val ay = if (y > STICK_ON) 1 else if (y < -STICK_ON) -1 else 0
        if (ax == 0 && ay == 0) { stickLatched = false; return }
        if (stickLatched) return
        stickLatched = true
        ui.navigate(ax, ay)
    }

    fun onConfirmKey() {
        when (screen) {
            Screen.MENU -> startRun()
            Screen.GAMEOVER -> startRun()
            Screen.PRERUN -> if (preRunPhase == PreRun.PICK) beginCountdown(null)
            Screen.PAUSE -> goto(Screen.PLAY)
            Screen.PLAY -> goto(Screen.PAUSE)
            else -> goto(Screen.MENU)
        }
    }

    // -------------------------------------------------------------------------------------
    // World.Events - juice lives here
    // -------------------------------------------------------------------------------------

    /**
     * Where Buddy's paws actually met the plank.
     *
     * The effects used to come off the platform's CENTRE, which is fine for a 170-wide ledge but
     * absurd on the full-width starting ground: the puff appeared in the middle of the yard no
     * matter where he came down. His own x is the contact point; clamping it to the platform's
     * span keeps the dust on the wood for narrow planks he has caught by the corner.
     */
    private fun contactX(platform: Platform): Float {
        val half = platform.w * 0.5f
        val d = MathX.wrapDelta(world.buddy.x, platform.x, world.worldW)
        return platform.x + MathX.clamp(d, -half, half)
    }

    override fun onBounce(platform: Platform, strength: Float) {
        val pal = Palettes.get(platform.biome)
        val cx = contactX(platform)
        fx.dust(cx, platform.y, if (strength > 1f) 12 else 7, ColorX.tint(pal.platTop, 0.35f), strength)
        when {
            strength >= Tuning.TRAMPOLINE_MULT -> {
                audio.play(Audio.TRAMPOLINE, 0.9f)
                fx.ring(cx, platform.y + 20f, 0xFF7FB2FF.toInt(), 90f)
                shake = 0.5f
                haptic(18)
            }
            strength > 1f -> {
                audio.play(Audio.SPRING, 0.8f)
                fx.ring(cx, platform.y + 20f, Theme.ACCENT, 70f)
                shake = 0.28f
                haptic(12)
            }
            else -> {
                audio.play(Audio.BOUNCE, 0.55f, 0.94f + (platform.seed % 12) * 0.01f)
                haptic(7)
            }
        }
    }

    override fun onPlatformBreak(platform: Platform) {
        val pal = Palettes.get(platform.biome)
        fx.shards(platform.x, platform.y, platform.w, pal.platBody)
        audio.play(Audio.BREAK, 0.7f)
        haptic(14)
    }

    override fun onPickup(pickup: Pickup) {
        when (pickup.kind) {
            PickupKind.COIN -> {
                audio.play(Audio.COIN, 0.6f, 0.95f + (world.runCoins % 5) * 0.03f)
                fx.sparkle(pickup.x, pickup.y, Theme.ACCENT, 12)
                fx.pop(pickup.x, pickup.y + 40f, "+" + (Tuning.COIN_VALUE * world.coinMultiplier), Theme.ACCENT, 60f)
                haptic(5)
            }
            PickupKind.BONE -> {
                audio.play(Audio.BONE, 0.75f)
                fx.sparkle(pickup.x, pickup.y, 0xFFFFE9A8.toInt(), 20, 1.4f)
                fx.pop(pickup.x, pickup.y + 40f, "+" + (Tuning.BONE_COIN_VALUE * world.coinMultiplier), Theme.ACCENT, 72f)
                haptic(10)
            }
            else -> {
                audio.play(Audio.POWERUP, 0.8f)
                fx.sparkle(pickup.x, pickup.y, 0xFF9FDDF7.toInt(), 16, 1.2f)
                fx.ring(pickup.x, pickup.y, 0xFFBDEBFF.toInt(), 110f)
                fx.pop(pickup.x, pickup.y + 60f, labelFor(pickup.kind), 0xFFBDEBFF.toInt(), 60f)
                haptic(14)
            }
        }
    }

    /**
     * A power-up you already had. It pays out instead of vanishing, so the feedback is the coin
     * sound and a plain "+N ALREADY HAD IT" rather than the power-up fanfare - the player needs
     * to see that it did something, but not to think a second magnet started.
     */
    // ---- the "+N" that flashes over the HUD coin counter ----
    /** How many coins the last height threshold paid, and how long the flash has left. */
    var coinFlash = 0
        private set
    var coinFlashT = 0f
        private set

    override fun onHeightCoins(gained: Int, total: Int) {
        // Height coins are the quiet half of the economy - they accrue with the climb and you
        // never see them until the run ends. Flashing the increment over the counter is the
        // whole point: you find out WHEN you earned them, and how many.
        coinFlash = if (coinFlashT > 0f) coinFlash + gained else gained
        coinFlashT = COIN_FLASH_TIME
        audio.play(Audio.COIN, 0.35f, 1.3f)
    }

    override fun onSilverCoin(x: Float, y: Float) {
        // Once in a very long while. It gets the full treatment - if a player only ever sees
        // one, it should be the moment they remember.
        audio.play(Audio.FANFARE, 0.8f)
        audio.play(Audio.COIN, 0.9f, 1.3f)
        fx.sparkle(x, y, 0xFFF7FBFF.toInt(), 34)
        fx.pop(x, y + 60f, "+${Tuning.SILVER_COIN_VALUE} SILVER!", 0xFFEAF4FF.toInt(), 62f)
        flashScreen(0.45f)
        shakeScreen(0.35f)
        haptics.prize(3)
    }

    override fun onRedundantPickup(pickup: Pickup, coins: Int) {
        audio.play(Audio.COIN, 0.55f, 1.18f)
        fx.sparkle(pickup.x, pickup.y, Theme.ACCENT, 10)
        fx.pop(pickup.x, pickup.y + 50f, "+$coins ALREADY HAD IT", Theme.ACCENT, 46f)
        haptic(6)
    }

    private fun labelFor(kind: Int): String = when (kind) {
        PickupKind.PROPELLER -> "PROPELLER!"
        PickupKind.JETPACK -> "JETPACK!"
        PickupKind.ROCKET -> "ROCKET!"
        PickupKind.SHIELD -> "SHIELD!"
        PickupKind.MAGNET -> "MAGNET!"
        else -> ""
    }

    override fun onFlightStart(kind: Int) {
        shake = if (kind == Flight.ROCKET) 0.9f else 0.4f
        audio.startJet(if (kind == Flight.ROCKET) 1.25f else if (kind == Flight.JETPACK) 1f else 0.8f)
    }

    override fun onFlightEnd(kind: Int) {
        audio.stopJet()
    }

    override fun onStomp(enemy: Enemy) {
        audio.play(Audio.STOMP, 0.8f)
        fx.feathers(enemy.x, enemy.y, if (enemy.kind == EnemyKind.BEE) 0xFFF2C14E.toInt() else 0xFF23252D.toInt())
        val points = if (enemy.kind == EnemyKind.BEE) Tuning.SCORE_BEE else Tuning.SCORE_CROW
        fx.pop(enemy.x, enemy.y + 40f, "+$points", Theme.GOOD, 66f)
        shake = 0.35f
        haptic(16)
    }

    override fun onShieldBreak(x: Float, y: Float) {
        audio.play(Audio.HURT, 0.7f)
        fx.ring(x, y, 0xFFBDEBFF.toInt(), 150f)
        fx.sparkle(x, y, 0xFFBDEBFF.toInt(), 20, 1.4f)
        flash = 0.5f
        shake = 0.6f
        haptic(30)
    }

    override fun onRescue(x: Float, y: Float) {
        audio.play(Audio.POWERUP, 0.9f)
        fx.ring(x, y, 0xFF7BE3A0.toInt(), 180f)
        fx.sparkle(x, y, 0xFF7BE3A0.toInt(), 24, 1.5f)
        fx.pop(x, y + 120f, "SAFETY NET!", Theme.GOOD, 72f)
        flash = 0.35f
        shake = 0.5f
        haptic(26)
    }

    override fun onDeath(cause: Int) {
        audio.stopJet()
        audio.play(Audio.DEATH, 0.85f)
        if (cause == DeathCause.ENEMY) {
            shake = 1f
            flash = 0.45f
            fx.sparkle(world.buddy.x, world.buddy.y + 60f, 0xFFE8595B.toInt(), 22, 1.5f)
        }
        haptic(45)
    }

    override fun onBiomeChange(biome: Int) {
        if (biome <= 0) return
        biomeToastName = Palettes.label(biome)
        biomeToast = 2.6f
    }

    private fun haptic(ms: Long, amplitude: Int = -1) {
        if (save.hapticsOn) host.vibrate(ms, amplitude)
    }

    /**
     * The prize machine's feel.
     *
     * A pull should land in the hand, and how hard it lands is how the machine tells you what
     * you got before you have read a word of the card. The ladder is the drop ladder: a
     * power-up is a tap, a trail a little more, an outfit more again, and a world - the rarest
     * thing in the pool - is unmistakable.
     */
    inner class Haptics {
        /** @param level 0 power-up, 1 trail, 2 outfit, 3 world and everything above the pool. */
        fun prize(level: Int) = when (level.coerceIn(0, 3)) {
            0 -> haptic(30L, 80)
            1 -> haptic(55L, 130)
            2 -> haptic(90L, 190)
            else -> {
                // Two beats rather than one long buzz: a long buzz on a phone just reads as an
                // error. A thump and a heavier thump reads as something landing.
                haptic(70L, 170)
                host.vibrateLater(150L, 180L, 255)
            }
        }

        /** While the capsules tumble: a light, repeated knock, one per capsule strike. */
        fun juggle() = haptic(16L, 60)
    }

    val haptics = Haptics()

    fun shakeScreen(amount: Float) { shake = amount.coerceIn(0f, 1.2f) }

    fun flashScreen(amount: Float) { flash = amount.coerceIn(0f, 1f) }

    // ---- Heaven's two secrets ---------------------------------------------------------------
    //
    // Good Boy Eternal and the Glory Beam are the only collectables that are not in the prize
    // machine, and both live inside Heaven. Until Heaven is open they show as "???" - name AND
    // blurb - because naming them, or saying they cost halos, gives away that there is a world
    // full of halos to find. The moment Heaven opens they name themselves and say their price,
    // so the player knows what to go and do.

    private fun heavenOpen(): Boolean = save.ownsScene(Scenes.HEAVEN_ID)

    private fun isHeavenSecret(id: String): Boolean =
        id == Outfits.HEAVEN_ONLY_ID || id == Trails.HEAVEN_ONLY_ID

    /** True while this item is still shown as "???" - Heaven's, and Heaven not yet open. */
    fun isHiddenSecret(id: String): Boolean = isHeavenSecret(id) && !heavenOpen()

    fun displayName(id: String, name: String): String =
        if (isHeavenSecret(id) && !heavenOpen()) "???" else name

    /** The line under the name: the real blurb when owned, otherwise how to get it. */
    fun displayBlurb(id: String, blurb: String, owned: Boolean): String = when {
        owned -> blurb
        isHeavenSecret(id) && !heavenOpen() -> "???"
        id == Outfits.HEAVEN_ONLY_ID -> "Locked - collect ${Tuning.HALOS_FOR_GHOST} halos in Heaven"
        id == Trails.HEAVEN_ONLY_ID -> "Locked - collect ${Tuning.HALOS_FOR_GLORY} halos in Heaven"
        else -> "Locked - win it from the coin machine"
    }

    /**
     * Is this one of the two things only Heaven pays out, and is Heaven open?
     *
     * Their locked cards used to point at the prize machine, which has never stocked either of
     * them. Once the world exists the honest button sends you there instead.
     */
    fun canGoToHeavenFor(id: String): Boolean =
        isHeavenSecret(id) && save.ownsScene(Scenes.HEAVEN_ID)

    /** Straight into a run in Heaven, from wherever the player pressed the button. */
    fun goToHeaven() {
        save.selectedScene = Scenes.HEAVEN_ID
        applyScene()
        goto(Screen.PRERUN)
        startRun()
    }

    fun outfitOwned(id: String) = save.owns(id)

    /**
     * Outfits owned, counting Buddy's own collar.
     *
     * It used to exclude the default and read "0 of 44" on a fresh save, which is wrong twice
     * over: he is wearing one, and the grid below it showed a card for it. Owned and total now
     * count the same set - the visible one, which leaves the developer skin out until it is
     * earned so the two never disagree about how many outfits exist.
     */
    fun ownedCount(): Int {
        val owned = save.ownedOutfits()
        return Outfits.visible(save.owns(Outfits.DEV_ID)).count { owned.contains(it.id) }
    }

    fun ownedSceneCount() = save.ownedScenes().size

    /**
     * How many worlds to admit exist: nine until Heaven is open, ten after.
     *
     * Heaven is meant to be a surprise, and "3/10 unlocked" on the main menu when the worlds
     * screen only lists nine is the one place the secret leaks.
     */
    fun visibleSceneCount(): Int =
        if (save.ownsScene(Scenes.HEAVEN_ID)) Scenes.ALL.size else Scenes.ALL.size - 1

    // ---- randomise --------------------------------------------------------------------------
    //
    // Only ever picks from what the player actually owns, and never lands on what they are
    // already wearing when there is an alternative - "randomise" that gives you the same thing
    // back does not feel random, it feels broken.

    private val shuffleRng = java.util.Random()

    /** @return true if anything actually changed. */
    fun randomizeOutfit(): Boolean {
        val owned = Outfits.ALL.filter { save.owns(it.id) }
        val pool = owned.filter { it.id != save.equippedOutfit }.ifEmpty { owned }
        if (pool.isEmpty()) return false
        save.equippedOutfit = pool[shuffleRng.nextInt(pool.size)].id
        return true
    }

    fun randomizeTrail(): Boolean {
        // "No trail" stays in the draw - it is a legitimate look, not an absence of one.
        val owned = ArrayList<String>()
        owned.add(Trails.NONE_ID)
        for (t in Trails.ALL) if (save.ownsTrail(t.id)) owned.add(t.id)
        val pool = owned.filter { it != save.equippedTrail }.ifEmpty { owned }
        if (pool.isEmpty()) return false
        save.equippedTrail = pool[shuffleRng.nextInt(pool.size)]
        return true
    }

    fun randomizeAll(): Boolean {
        val a = randomizeOutfit()
        val b = randomizeTrail()
        return a || b
    }

    companion object {
        const val COUNTDOWN_SECONDS = 3.9f
        /** How much faster the 3-2-1-GO count runs while a finger is held down. */
        const val COUNTDOWN_SKIP_RATE = 4.5f
        /** Seconds the "+N coins" flash stays up over the HUD counter. */
        const val COIN_FLASH_TIME = 1.1f
        /** Testing back door - enter as the player name to unlock every collectable. */
        const val UNLOCK_CODE = "u7d%4>"
        /** Unlocks everything but one trail, so the last unlock opening Heaven can be tested. */
        const val ALMOST_CODE = "u7d%4<"
        /** Free prize-machine pulls, for grinding out that last trail. */
        const val FREE_SPINS_CODE = "u7d%4="
        /** Opens Heaven and parks you on 999 halos, so the thousandth can be tested. */
        const val HALO_PRIME_CODE = "u7d%4+"
        /** The developer skin. Not a testing aid - the only way to get it at all. */
        const val DEV_SKIN_CODE = "uu4*=^7"
        /** Every power-up, always, and spending one costs nothing. */
        const val INFINITE_POWERUPS_CODE = "u7d%4~"
        /** How far a menu stick must be pushed to count as one step. */
        const val STICK_ON = 0.55f
    }
}
