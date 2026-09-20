package com.blacklab.buddybounce

import android.graphics.Canvas
import com.blacklab.buddybounce.audio.Audio
import com.blacklab.buddybounce.data.Outfits
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
import com.blacklab.buddybounce.ui.GachaScreen
import com.blacklab.buddybounce.ui.GameOverScreen
import com.blacklab.buddybounce.ui.Hud
import com.blacklab.buddybounce.ui.MenuScreen
import com.blacklab.buddybounce.ui.ScoresScreen
import com.blacklab.buddybounce.ui.SettingsScreen
import com.blacklab.buddybounce.ui.Theme
import com.blacklab.buddybounce.ui.Ui
import com.blacklab.buddybounce.ui.WardrobeScreen
import kotlin.math.abs
import kotlin.math.sin

/**
 * Owns the screen the player is on, the simulation, and the draw order. Rendering happens in
 * world units and a single canvas scale maps that onto whatever device this is.
 */
class Game(val save: Save, val audio: Audio, val host: Host) : World.Events {

    interface Host {
        /** Locks the activity to the requested orientation. */
        fun setLandscape(landscape: Boolean)
        /** Shows the name-entry overlay (the one place the game uses a real View). */
        fun promptName(current: String, title: String)
        fun vibrate(ms: Long, amplitude: Int)
    }

    enum class Screen { NAME, MENU, PLAY, PAUSE, GAMEOVER, WARDROBE, GACHA, SCORES, SETTINGS }

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
    private val scores = ScoresScreen(this)
    private val settings = SettingsScreen(this)
    private val gameOver = GameOverScreen(this)
    private val hud = Hud(this)

    var screen = Screen.MENU
        private set
    var previousScreen = Screen.MENU
        private set
    var screenAnim = 1f
        private set

    var worldW = Tuning.REF_W
        private set
    val viewH = Tuning.VIEW_H
    var scale = 1f
        private set
    var widthPx = 0
        private set
    var heightPx = 0
        private set
    var time = 0f
        private set

    // run bookkeeping, read by the game-over screen
    var lastScore = 0
    var lastCoins = 0
    var lastRank = -1
    var lastNewBest = false
    var lastBiome = 0

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
        val newScale = hPx / Tuning.VIEW_H
        val newWorldW = wPx / newScale
        val scaleChanged = abs(newScale - scale) > 0.0001f

        widthPx = wPx
        heightPx = hPx
        scale = newScale
        worldW = newWorldW

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

        world.resize(worldW)
        if (!started) {
            started = true
            world.reset()
            menuBuddy.reset(0f, 0f)
            screen = if (save.hasName) Screen.MENU else Screen.NAME
            if (screen == Screen.NAME) host.promptName("", "What's your name?")
        }
    }

    private val uiInsets = FloatArray(4)

    /** Insets arrive in pixels from the view and are kept in world units. */
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

    fun goto(s: Screen) {
        if (s == screen) return
        previousScreen = screen
        screen = s
        screenAnim = 0f
        if (s != Screen.PLAY) {
            controls.clearGauge()
            audio.stopJet()
        }
        if (s == Screen.MENU || s == Screen.GAMEOVER) fx.clear()
    }

    fun tap() = audio.play(Audio.TAP, 0.45f)

    fun startRun() {
        fx.clear()
        world.reset()
        lastScore = 0; lastCoins = 0; lastRank = -1; lastNewBest = false
        hintTimer = if (controls.tiltEnabled) 2.6f else 2.2f
        goto(Screen.PLAY)
    }

    fun onNameEntered(name: String) {
        val clean = Save.sanitizeName(name)
        if (clean.isNotEmpty()) save.playerName = clean
        if (screen == Screen.NAME) goto(Screen.MENU)
    }

    fun onBackPressed(): Boolean = when (screen) {
        Screen.PLAY -> { goto(Screen.PAUSE); true }
        Screen.PAUSE -> { goto(Screen.PLAY); true }
        Screen.WARDROBE, Screen.GACHA, Screen.SCORES, Screen.SETTINGS -> { goto(Screen.MENU); true }
        Screen.GAMEOVER -> { goto(Screen.MENU); true }
        else -> false
    }

    fun onPauseApp() {
        if (screen == Screen.PLAY) goto(Screen.PAUSE)
        audio.stopJet()
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
        if (hintTimer > 0f) hintTimer -= dt
        ui.beginFrame(dt)

        when (screen) {
            Screen.PLAY -> updatePlay(dt)
            Screen.GACHA -> { gacha.update(dt); fx.update(dt); updateMenuBuddy(dt) }
            else -> { fx.update(dt); updateMenuBuddy(dt) }
        }
    }

    private fun updatePlay(dt: Float) {
        val steer = controls.steer()
        world.update(dt, steer, controls.lastInputDigital)
        fx.update(dt)
        emitFlightTrail(dt)

        if (world.deathSettled) finishRun()
    }

    private fun updateMenuBuddy(dt: Float) {
        menuCamY += dt * 70f
        // A tiny standalone bounce loop so Buddy is always alive behind the menus.
        menuBuddy.vy -= Tuning.GRAVITY * 0.55f * dt
        menuBuddy.y += menuBuddy.vy * dt
        if (menuBuddy.y <= 0f && menuBuddy.vy < 0f) {
            menuBuddy.y = 0f
            menuBuddy.vy = Tuning.JUMP_V * 0.52f
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
            fx.trail(b.x, b.y + 8f, color, if (b.flight == Flight.ROCKET) 46f else 30f)
        }
    }

    private fun finishRun() {
        lastScore = world.score
        lastCoins = world.runCoins
        lastBiome = world.biome
        lastNewBest = lastScore > save.bestScore
        save.addCoins(lastCoins)
        save.highestBiome = world.biome
        lastRank = save.submitRun(lastScore)
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
            Screen.GAMEOVER -> {
                drawWorld(c)
                ui.scrim(c, worldW, viewH, 0.62f * screenAnim)
                gameOver.draw(c)
            }
            else -> {
                drawMenuBackdrop(c)
                when (screen) {
                    Screen.NAME -> menu.drawNamePrompt(c)
                    Screen.MENU -> menu.draw(c)
                    Screen.WARDROBE -> wardrobe.draw(c)
                    Screen.GACHA -> gacha.draw(c)
                    Screen.SCORES -> scores.draw(c)
                    Screen.SETTINGS -> settings.draw(c)
                    else -> {}
                }
            }
        }

        if (flash > 0.01f) {
            ui.fill(c, worldW, viewH, ColorX.withAlpha(0xFFFFFFFF.toInt(), flash * 0.55f))
        }
        c.restore()
        ui.endFrame()
    }

    private fun drawWorld(c: Canvas) {
        val camY = world.camY
        val viewTop = camY + viewH
        val screensNow = world.screensAt(camY + viewH * 0.5f)
        val biome = Tuning.biomeIndex(screensNow)
        val blend = Tuning.biomeBlend(screensNow)
        val pal = Palettes.get(biome)

        backdrop.draw(c, worldW, camY, time, biome, blend)
        backdrop.drawGround(c, worldW, viewTop, 60f)

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
    }

    private fun drawBuddy(c: Canvas, viewTop: Float) {
        val b = world.buddy
        posePlayer(b)
        val sy = viewTop - b.y
        // Wrap-aware: draw a second copy when he straddles the seam.
        buddyArt.draw(c, b.x, sy, 1f, pose, equippedOutfit, rimColor)
        if (b.x < Tuning.BUDDY_W) buddyArt.draw(c, b.x + worldW, sy, 1f, pose, equippedOutfit, rimColor)
        if (b.x > worldW - Tuning.BUDDY_W) buddyArt.draw(c, b.x - worldW, sy, 1f, pose, equippedOutfit, rimColor)
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

    /** The idle Buddy used by every menu; [scaleFactor] lets each screen size him. */
    fun drawMenuBuddy(c: Canvas, cx: Float, groundY: Float, scaleFactor: Float, outfit: String) {
        pose.reset()
        pose.squash = menuBuddy.squash
        pose.lean = sin(time * 0.9f) * 0.25f
        pose.earFlap = menuBuddy.earFlap
        pose.tail = menuBuddy.tailPhase
        pose.blink = menuBuddy.blinkAmount
        pose.mouth = menuBuddy.mouthOpen
        pose.facing = 1f
        pose.time = time
        buddyArt.draw(c, cx, groundY - menuBuddy.y * 0.5f, scaleFactor, pose, outfit, 0xFFFFE6A8.toInt())
    }

    /** A static Buddy for wardrobe cards and the prize reveal. */
    fun drawPosedBuddy(c: Canvas, cx: Float, pawY: Float, scaleFactor: Float, outfit: String, phase: Float) {
        pose.reset()
        pose.squash = sin(phase) * 0.12f
        pose.lean = sin(phase * 0.7f) * 0.18f
        pose.tail = phase * 3.4f
        pose.time = phase
        pose.facing = 1f
        buddyArt.draw(c, cx, pawY, scaleFactor, pose, outfit, 0xFFFFE6A8.toInt())
    }

    private fun drawMenuBackdrop(c: Canvas) {
        val biome = if (screen == Screen.GACHA) 0 else (save.highestBiome).coerceIn(0, 4)
        backdrop.draw(c, worldW, menuCamY, time, biome, 0f)
        ui.scrim(c, worldW, viewH, 0.34f)
    }

    // -------------------------------------------------------------------------------------
    // input
    // -------------------------------------------------------------------------------------

    fun onPointerDown(xPx: Float, yPx: Float) {
        val x = xPx / scale
        val y = yPx / scale
        ui.onDown(x, y)
        if (screen == Screen.PLAY) handleGauge(x, y, down = true)
    }

    fun onPointerMove(xPx: Float, yPx: Float) {
        val x = xPx / scale
        val y = yPx / scale
        ui.onMove(x, y)
        if (screen == Screen.PLAY) handleGauge(x, y, down = true)
    }

    fun onPointerUp(xPx: Float, yPx: Float) {
        val x = xPx / scale
        val y = yPx / scale
        ui.onUp(x, y)
        if (screen == Screen.PLAY) controls.releaseGauge()
    }

    private fun handleGauge(x: Float, y: Float, down: Boolean) {
        if (!controls.touchEnabled) return
        if (hud.isOverPauseButton(x, y)) return
        if (y < hud.gaugeBandTop()) return
        val half = hud.gaugeHalfWidth()
        val centre = hud.gaugeCentreX()
        controls.onGauge(((x - centre) / half).coerceIn(-1f, 1f))
    }

    fun onKeyLeft(down: Boolean) { controls.keyLeft = down }
    fun onKeyRight(down: Boolean) { controls.keyRight = down }
    fun onPadAxis(value: Float) { controls.padAxis = value }

    /** Start / A button and the like. */
    fun onConfirmKey() {
        when (screen) {
            Screen.MENU -> startRun()
            Screen.GAMEOVER -> startRun()
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
                fx.ring(platform.x, platform.y + 20f, 0xFF7FB2FF.toInt(), 70f)
                shake = 0.5f
                haptic(18)
            }
            strength > 1f -> {
                audio.play(Audio.SPRING, 0.8f)
                fx.ring(platform.x, platform.y + 20f, Theme.ACCENT, 55f)
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
                fx.sparkle(pickup.x, pickup.y, Theme.ACCENT, 8)
                haptic(5)
            }
            PickupKind.BONE -> {
                audio.play(Audio.BONE, 0.75f)
                fx.sparkle(pickup.x, pickup.y, 0xFFFFE9A8.toInt(), 18, 1.4f)
                fx.pop(pickup.x, pickup.y, "+" + Tuning.BONE_COIN_VALUE, Theme.ACCENT, 54f)
                haptic(10)
            }
            else -> {
                audio.play(Audio.POWERUP, 0.8f)
                fx.sparkle(pickup.x, pickup.y, 0xFF9FDDF7.toInt(), 16, 1.2f)
                fx.ring(pickup.x, pickup.y, 0xFFBDEBFF.toInt(), 80f)
                fx.pop(pickup.x, pickup.y + 40f, labelFor(pickup.kind), 0xFFBDEBFF.toInt(), 46f)
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
        fx.pop(enemy.x, enemy.y + 40f, "+$points", Theme.GOOD, 50f)
        shake = 0.35f
        haptic(16)
    }

    override fun onShieldBreak(x: Float, y: Float) {
        audio.play(Audio.HURT, 0.7f)
        fx.ring(x, y, 0xFFBDEBFF.toInt(), 110f)
        fx.sparkle(x, y, 0xFFBDEBFF.toInt(), 20, 1.4f)
        flash = 0.5f
        shake = 0.6f
        haptic(30)
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

    fun shakeScreen(amount: Float) {
        shake = amount.coerceIn(0f, 1.2f)
    }

    fun flashScreen(amount: Float) {
        flash = amount.coerceIn(0f, 1f)
    }

    // helpers used by screens
    fun outfitOwned(id: String) = save.owns(id)
    fun ownedCount() = save.ownedOutfits().count { it != Outfits.DEFAULT_ID }
}
