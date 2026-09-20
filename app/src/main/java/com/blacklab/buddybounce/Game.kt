package com.blacklab.buddybounce

import android.graphics.Canvas
import com.blacklab.buddybounce.audio.Audio
import com.blacklab.buddybounce.data.Outfits
import com.blacklab.buddybounce.data.Powerups
import com.blacklab.buddybounce.data.Save
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
import com.blacklab.buddybounce.ui.GachaScreen
import com.blacklab.buddybounce.ui.GameOverScreen
import com.blacklab.buddybounce.ui.Hud
import com.blacklab.buddybounce.ui.MenuScreen
import com.blacklab.buddybounce.ui.PreRunScreen
import com.blacklab.buddybounce.ui.ScenesScreen
import com.blacklab.buddybounce.ui.ScoresScreen
import com.blacklab.buddybounce.ui.SettingsScreen
import com.blacklab.buddybounce.ui.Theme
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
class Game(val save: Save, val audio: Audio, val host: Host) : World.Events {

    interface Host {
        fun setLandscape(landscape: Boolean)
        fun promptName(current: String, title: String)
        fun vibrate(ms: Long, amplitude: Int)
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

    // ---- pre-run ----
    var preRunPhase = PreRun.PICK
        private set
    var countdown = 0f
        private set
    var chosenPowerup: String? = null
        private set
    private var pendingPowerup: String? = null
    private var lastCountdownTick = -1

    // ---- run bookkeeping, read by the game-over screen ----
    var lastScore = 0
    var lastRunCoins = 0
    var lastBonusCoins = 0
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
    private var hintTimer = 0f

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
        controls.touchRange = (worldW * 0.22f).coerceIn(200f, 460f)

        if (scaleChanged) {
            art.dispose()
            art = Art(scale)
            ui = Ui(art).also {
                it.safeTop = uiInsets[0]; it.safeBottom = uiInsets[1]
                it.safeLeft = uiInsets[2]; it.safeRight = uiInsets[3]
            }
            buddyArt = BuddyArt(art)
            gameArt = GameRenderer(art)
            backdrop = Backdrop(art)
        }

        world.resize(playW)
        if (!started) {
            started = true
            applyScene()
            world.reset()
            menuBuddy.reset(0f, 0f)
            screen = if (save.hasName) Screen.MENU else Screen.NAME
            if (screen == Screen.NAME) host.promptName("", "What's your name?")
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
    }

    fun goto(s: Screen) {
        if (s == screen) return
        previousScreen = screen
        screen = s
        screenAnim = 0f
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
        lastScore = 0; lastRunCoins = 0; lastBonusCoins = 0; lastCoins = 0
        lastRank = -1; lastNewBest = false
        chosenPowerup = null
        pendingPowerup = null
        lastCountdownTick = -1
        hintTimer = 3.2f
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
        ui.beginFrame(dt)

        when (screen) {
            Screen.PLAY -> updatePlay(dt)
            Screen.PRERUN -> updatePreRun(dt)
            Screen.GACHA -> { gacha.update(dt); fx.update(dt); updateMenuBuddy(dt) }
            else -> { fx.update(dt); updateMenuBuddy(dt) }
        }
    }

    private fun updatePlay(dt: Float) {
        if (hintTimer > 0f) hintTimer -= dt
        val steer = controls.steer()
        world.update(dt, steer, controls.lastInputDigital)
        fx.update(dt)
        emitFlightTrail(dt)
        if (world.deathSettled) finishRun()
    }

    private fun updatePreRun(dt: Float) {
        fx.update(dt)
        world.buddy.updateAnim(dt, world.metrics.maxVx)
        if (preRunPhase != PreRun.COUNTDOWN) return
        countdown -= dt
        val tick = countdown.toInt()
        if (tick != lastCountdownTick && countdown > 0f) {
            lastCountdownTick = tick
            audio.play(Audio.TAP, 0.6f, if (tick <= 0) 1.5f else 1f)
        }
        if (countdown <= 0f) {
            applyPowerupOnGo()
            goto(Screen.PLAY)
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

    private var trailAccum = 0f

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
        lastBiome = world.biome
        lastNewBest = lastScore > save.bestScore
        save.highestBiome = world.biome
        lastRank = save.bankRun(lastScore, lastCoins)
        if (lastNewBest && lastScore > 0) audio.play(Audio.FANFARE, 0.7f)
        goto(Screen.GAMEOVER)
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
                hud.draw(c, hintTimer, biomeToast, biomeToastName)
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
        val sy = viewTop - b.y
        buddyArt.draw(c, b.x, sy, 1f, pose, equippedOutfit, rimColor)
        if (b.x < Tuning.BUDDY_W) buddyArt.draw(c, b.x + playW, sy, 1f, pose, equippedOutfit, rimColor)
        if (b.x > playW - Tuning.BUDDY_W) buddyArt.draw(c, b.x - playW, sy, 1f, pose, equippedOutfit, rimColor)
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
        buddyArt.draw(c, cx, groundY - menuBuddy.y * 0.28f, scaleFactor, pose, outfit, 0xFFFFE6A8.toInt())
    }

    /** A gently idling Buddy for wardrobe cards and the prize reveal. */
    fun drawPosedBuddy(c: Canvas, cx: Float, pawY: Float, scaleFactor: Float, outfit: String, phase: Float) {
        pose.reset()
        pose.squash = sin(phase) * 0.1f
        pose.lean = sin(phase * 0.7f) * 0.14f
        pose.tail = phase * 3.4f
        pose.time = phase
        pose.facing = 1f
        buddyArt.draw(c, cx, pawY, scaleFactor, pose, outfit, 0xFFFFE6A8.toInt())
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

    override fun onBounce(platform: Platform, strength: Float) {
        val pal = Palettes.get(platform.biome)
        fx.dust(platform.x, platform.y, if (strength > 1f) 12 else 7, ColorX.tint(pal.platTop, 0.35f), strength)
        when {
            strength >= Tuning.TRAMPOLINE_MULT -> {
                audio.play(Audio.TRAMPOLINE, 0.9f)
                fx.ring(platform.x, platform.y + 20f, 0xFF7FB2FF.toInt(), 90f)
                shake = 0.5f
                haptic(18)
            }
            strength > 1f -> {
                audio.play(Audio.SPRING, 0.8f)
                fx.ring(platform.x, platform.y + 20f, Theme.ACCENT, 70f)
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

    private fun haptic(ms: Long) {
        if (save.hapticsOn) host.vibrate(ms, -1)
    }

    fun shakeScreen(amount: Float) { shake = amount.coerceIn(0f, 1.2f) }

    fun flashScreen(amount: Float) { flash = amount.coerceIn(0f, 1f) }

    fun outfitOwned(id: String) = save.owns(id)

    fun ownedCount() = save.ownedOutfits().count { it != Outfits.DEFAULT_ID }

    fun ownedSceneCount() = save.ownedScenes().size

    companion object {
        const val COUNTDOWN_SECONDS = 3.9f
    }
}
