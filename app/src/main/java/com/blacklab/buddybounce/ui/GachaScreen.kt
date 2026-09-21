package com.blacklab.buddybounce.ui

import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import com.blacklab.buddybounce.Game
import com.blacklab.buddybounce.audio.Audio
import com.blacklab.buddybounce.data.Outfits
import com.blacklab.buddybounce.data.Powerups
import com.blacklab.buddybounce.data.Trails
import com.blacklab.buddybounce.game.Hash
import com.blacklab.buddybounce.game.MathX.clamp01
import com.blacklab.buddybounce.game.MathX.smoothstep
import com.blacklab.buddybounce.game.Tuning
import com.blacklab.buddybounce.render.ColorX
import com.blacklab.buddybounce.render.Scenes
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

/**
 * The prize machine. One pull costs [Tuning.GACHA_COST] coins and dispenses one of three things:
 *
 *  - a **power-up** (89%) - the bread, so a pull is never a total loss
 *  - an **outfit** (10%) - rarity weighted, duplicates refund part of the cost
 *  - a **world** (1%) - the rare one, a whole new set of biomes to climb
 */
class GachaScreen(private val g: Game) {

    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val path = Path()
    private val rect = RectF()
    private val rng = Random(System.nanoTime())
    private val icons = PowerupIcon()

    private object Id {
        const val BACK = 4001
        const val PULL = 4002
        const val AGAIN = 4003
        const val DONE = 4004
        const val EQUIP = 4005
    }

    private object Kind {
        const val OUTFIT = 0
        const val POWERUP = 1
        const val SCENE = 2
        const val TRAIL = 3
    }

    private enum class State { IDLE, CRANK, DROP, REVEAL }

    private companion object {
        // The machine's split. Scenes are the jackpot, trails are the regular treat, outfits
        // sit between them, and everything else is a power-up so most pulls still give you
        // something to spend next run.
        const val SCENE_CHANCE = 0.01f
        const val TRAIL_CHANCE = 0.15f
        const val OUTFIT_CHANCE = 0.10f
        /** Seconds the coin takes to fall into the slot. */
        const val COIN_DROP_TIME = 0.55f
    }

    // Where drawMachine put the controls this frame. The crank IS the button, so the press
    // target has to follow the machine's layout rather than being a bar at the bottom of the
    // screen - pressing the thing you are looking at is the whole point of a gachapon.
    private var crankX = 0f
    private var crankY = 0f
    private var crankR = 0f
    private var slotX = 0f
    private var slotY = 0f
    /** Counts down while a coin is visibly dropping into the slot. */
    private var coinDropT = 0f

    private var state = State.IDLE
    private var timer = 0f
    private var prizeKind = Kind.OUTFIT
    private var prizeId = ""
    private var duplicate = false
    private var revealAnim = 0f
    private var crankAngle = 0f

    // -------------------------------------------------------------------------------------

    fun update(dt: Float) {
        when (state) {
            State.CRANK -> {
                crankAngle += dt * 520f
                timer -= dt
                if (timer <= 0f) { state = State.DROP; timer = 0.75f }
            }
            State.DROP -> {
                timer -= dt
                if (timer <= 0f) {
                    state = State.REVEAL
                    revealAnim = 0f
                    g.audio.play(Audio.GACHA_REVEAL, 0.9f)
                    if (isBigPrize()) {
                        g.shakeScreen(if (prizeKind == Kind.SCENE) 0.9f else 0.5f)
                        g.flashScreen(if (prizeKind == Kind.SCENE) 0.6f else 0.35f)
                        if (prizeKind == Kind.SCENE) g.audio.play(Audio.FANFARE, 0.9f)
                    }
                }
            }
            State.REVEAL -> revealAnim = (revealAnim + dt * 2.2f).coerceAtMost(1f)
            State.IDLE -> {}
        }
        if (coinDropT > 0f) coinDropT -= dt
    }

    private fun isBigPrize(): Boolean = when (prizeKind) {
        Kind.SCENE -> true
        Kind.TRAIL -> !duplicate && (Trails.of(prizeId)?.rarity ?: 0) >= Trails.Rarity.EPIC
        Kind.OUTFIT -> !duplicate && (Outfits.of(prizeId).rarity == Outfits.Rarity.EPIC ||
            Outfits.of(prizeId).rarity == Outfits.Rarity.LEGENDARY)
        else -> false
    }

    private fun prizeTint(): Int = when (prizeKind) {
        Kind.SCENE -> Scenes.of(prizeId).cardTint
        Kind.TRAIL -> Trails.of(prizeId)?.hot ?: Theme.ACCENT
        Kind.POWERUP -> Powerups.of(prizeId).tint
        else -> Outfits.of(prizeId).rarity.tint
    }

    private fun prizeName(): String = when (prizeKind) {
        Kind.SCENE -> Scenes.of(prizeId).name
        Kind.TRAIL -> Trails.of(prizeId)?.name ?: "Trail"
        Kind.POWERUP -> Powerups.of(prizeId).name
        else -> Outfits.of(prizeId).name
    }

    private fun prizeBlurb(): String = when (prizeKind) {
        Kind.SCENE -> Scenes.of(prizeId).blurb
        Kind.TRAIL -> Trails.of(prizeId)?.blurb ?: ""
        Kind.POWERUP -> Powerups.of(prizeId).blurb
        else -> Outfits.of(prizeId).blurb
    }

    private fun prizeBanner(): String = when {
        prizeKind == Kind.SCENE -> "A WHOLE NEW WORLD!"
        prizeKind == Kind.POWERUP -> "POWER-UP!"
        prizeKind == Kind.TRAIL -> if (duplicate) "ALREADY HAD IT" else "NEW TRAIL!"
        duplicate -> "ALREADY HAD IT"
        else -> "NEW OUTFIT!"
    }

    private fun prizeRarityLabel(): String = when (prizeKind) {
        Kind.SCENE -> "WORLD"
        Kind.TRAIL -> Trails.of(prizeId)?.let { Trails.rarityName(it.rarity) + " TRAIL" } ?: "TRAIL"
        Kind.POWERUP -> "POWER-UP"
        else -> Outfits.of(prizeId).rarity.label.uppercase()
    }

    // -------------------------------------------------------------------------------------

    fun draw(c: Canvas) {
        val ui = g.ui
        val wide = g.worldW > Theme.SCREEN_H * 1.12f

        if (ui.backButton(c, Id.BACK, ui.safeLeft + 78f, ui.safeTop + 78f, 52f)) {
            g.tap(); reset(); g.goto(Game.Screen.MENU)
        }
        ui.text(c, "PRIZE MACHINE", g.worldW * 0.5f, ui.safeTop + 96f, 58f, Theme.TEXT, ui.title)

        val coins = g.save.coins
        ui.text(
            c, "$coins coins  •  ${Tuning.GACHA_COST} a pull",
            g.worldW * 0.5f, ui.safeTop + 142f, 32f,
            if (coins >= Tuning.GACHA_COST) Theme.ACCENT else Theme.TEXT_DIM, ui.body, false
        )

        val areaTop = ui.safeTop + 176f
        val areaBottom = Theme.SCREEN_H - ui.safeBottom - 150f
        val machineH = (areaBottom - areaTop).coerceAtLeast(320f)
        val machineW = min(g.worldW * (if (wide) 0.42f else 0.86f), machineH * 0.72f)
        val cx = if (wide) g.worldW * 0.32f else g.worldW * 0.5f
        val top = areaTop + (machineH - machineW / 0.72f).coerceAtLeast(0f) * 0.5f

        drawMachine(c, cx, top, machineW, machineW / 0.72f)

        if (state == State.REVEAL) {
            drawReveal(c)
        } else {
            val canPull = (g.save.freeSpins || coins >= Tuning.GACHA_COST) && state == State.IDLE

            // The crank itself is the press target. A ghost button laid over wherever
            // drawMachine just put it, so it lines up whatever size the machine came out.
            // Pressing the thing you are looking at is the whole point of a gachapon; a bar at
            // the bottom of the screen was both a stretch for the thumb and less fun.
            if (crankR > 0f) {
                if (canPull) {
                    val pulse = 0.45f + 0.35f * sin(ui.time * 3.4f)
                    g.art.drawGlow(c, crankX, crankY, crankR * 4.4f, Theme.ACCENT, 0.38f * pulse)
                    p.reset(); p.isAntiAlias = true
                    p.style = Paint.Style.STROKE
                    p.strokeWidth = 5f
                    p.color = ColorX.withAlpha(Theme.ACCENT, 0.5f + 0.4f * pulse)
                    c.drawCircle(crankX, crankY, crankR * (1.12f + 0.1f * pulse), p)
                    p.style = Paint.Style.FILL
                }
                if (ui.button(
                        c, Id.PULL, crankX - crankR, crankY - crankR, crankR * 2f, crankR * 2f,
                        "", Ui.ButtonStyle.GHOST, canPull
                    )
                ) {
                    pull()
                }
            }

            val hint = when {
                state != State.IDLE -> "..."
                g.save.freeSpins -> "turn the crank (free spins)"
                canPull -> "turn the crank"
                else -> "collect ${Tuning.GACHA_COST - coins} more coins"
            }
            val room = g.worldW - ui.safeLeft - ui.safeRight - 60f
            val hintY = Theme.SCREEN_H - ui.safeBottom - 92f
            ui.text(
                c, hint, g.worldW * 0.5f, hintY, 38f,
                if (canPull) Theme.ACCENT else Theme.TEXT_DIM, ui.title, true, room
            )
            ui.text(
                c, "power-ups \u2022 trails \u2022 outfits \u2022 and very rarely, a new world",
                g.worldW * 0.5f, hintY + 42f, 26f, Theme.TEXT_DIM, ui.body, false, room
            )
        }
    }

    private fun reset() {
        state = State.IDLE
        coinDropT = 0f
        prizeId = ""
        duplicate = false
        revealAnim = 0f
    }

    // -------------------------------------------------------------------------------------
    // the pull
    // -------------------------------------------------------------------------------------

    private fun pull() {
        if (!g.save.freeSpins && !g.save.spendCoins(Tuning.GACHA_COST)) return
        coinDropT = COIN_DROP_TIME
        g.audio.play(Audio.COIN, 0.7f, 0.8f)
        g.audio.play(Audio.GACHA_SPIN, 0.8f)
        rollPrize()
        grantPrize()
        state = State.CRANK
        timer = 0.85f
        crankAngle = 0f
    }

    private fun rollPrize() {
        duplicate = false
        val lockedScenes = Scenes.unlockable.filter { !g.save.ownsScene(it.id) }
        val roll = rng.nextFloat()

        if (lockedScenes.isNotEmpty() && roll < SCENE_CHANCE) {
            prizeKind = Kind.SCENE
            prizeId = lockedScenes[rng.nextInt(lockedScenes.size)].id
            return
        }

        if (roll < SCENE_CHANCE + TRAIL_CHANCE) {
            prizeKind = Kind.TRAIL
            prizeId = rollTrail()
            duplicate = g.save.ownsTrail(prizeId)
            return
        }

        val anyOutfitLeft = Outfits.ALL.any {
            it.id != Outfits.DEFAULT_ID && it.id != Outfits.HEAVEN_ONLY_ID && !g.save.owns(it.id)
        }
        if (roll < SCENE_CHANCE + TRAIL_CHANCE + OUTFIT_CHANCE && anyOutfitLeft) {
            prizeKind = Kind.OUTFIT
            prizeId = rollOutfit()
            duplicate = g.save.owns(prizeId)
            return
        }

        prizeKind = Kind.POWERUP
        prizeId = rollPowerup()
    }

    /** Rarity weighted, and it prefers one you do not own yet so the set actually fills up. */
    private fun rollTrail(): String {
        val locked = Trails.collectable.filter { !g.save.ownsTrail(it.id) }
        val pool = if (locked.isEmpty()) Trails.collectable else locked
        var total = 0
        for (t in pool) total += Trails.weightOf(t)
        if (total <= 0) return pool[0].id
        var roll = rng.nextInt(total)
        for (t in pool) {
            roll -= Trails.weightOf(t)
            if (roll < 0) return t.id
        }
        return pool[0].id
    }

    private fun rollPowerup(): String {
        var roll = rng.nextInt(Powerups.totalWeight)
        for (pu in Powerups.ALL) {
            roll -= pu.weight
            if (roll < 0) return pu.id
        }
        return Powerups.ALL[0].id
    }

    /**
     * Rarity weighted. A rarity the player has completed rolls down into one they haven't, so
     * late pulls keep feeling like progress; inside a rarity a duplicate is still possible and
     * refunds part of the cost.
     */
    private fun rollOutfit(): String {
        val rarities = Outfits.Rarity.values()
        val open = rarities.filter { r -> Outfits.inRarity(r).any { !g.save.owns(it.id) } }
        val pool = if (open.isEmpty()) rarities.toList() else open
        var total = 0
        for (r in pool) total += r.weight
        if (total <= 0) return Outfits.ALL[1].id
        var roll = rng.nextInt(total)
        var chosen = pool[0]
        for (r in pool) {
            roll -= r.weight
            if (roll < 0) { chosen = r; break }
        }
        val items = Outfits.inRarity(chosen)
        if (items.isEmpty()) return Outfits.ALL[1].id
        return items[rng.nextInt(items.size)].id
    }

    private fun grantPrize() {
        when (prizeKind) {
            Kind.SCENE -> g.save.unlockScene(prizeId)
            Kind.POWERUP -> g.save.grantPowerup(prizeId, 1)
            Kind.TRAIL ->
                if (duplicate) g.save.grantCoins(Tuning.DUPLICATE_REFUND) else g.save.unlockTrail(prizeId)
            else -> if (duplicate) g.save.grantCoins(Tuning.DUPLICATE_REFUND) else g.save.unlock(prizeId)
        }
        // Any unlock might have been the last one, and completing the set is what opens Heaven.
        if (g.save.refreshHeaven()) g.queueHeavenAnnounce()
    }

    // -------------------------------------------------------------------------------------
    // machine art
    // -------------------------------------------------------------------------------------

    private fun drawMachine(c: Canvas, cx: Float, top: Float, w: Float, h: Float) {
        val ui = g.ui
        val domeR = w * 0.42f
        val domeCY = top + domeR + w * 0.06f
        val bodyTop = domeCY + domeR * 0.72f
        val bodyBottom = top + h

        g.art.drawShadow(c, cx, bodyBottom + 12f, w * 1.15f, h * 0.22f, 0.55f)
        p.reset(); p.isAntiAlias = true

        p.color = 0xFFC0392B.toInt()
        rect.set(cx - w * 0.5f, bodyTop - w * 0.12f, cx + w * 0.5f, bodyBottom)
        c.drawRoundRect(rect, w * 0.1f, w * 0.1f, p)
        p.color = 0xFFA5302A.toInt()
        rect.set(cx - w * 0.5f, bodyBottom - h * 0.09f, cx + w * 0.5f, bodyBottom)
        c.drawRoundRect(rect, w * 0.1f, w * 0.1f, p)
        p.color = ColorX.withAlpha(0xFFFFFFFF.toInt(), 0.16f)
        rect.set(cx - w * 0.42f, bodyTop - w * 0.06f, cx - w * 0.28f, bodyBottom - h * 0.06f)
        c.drawRoundRect(rect, w * 0.05f, w * 0.05f, p)

        p.color = Theme.ACCENT
        rect.set(cx - w * 0.36f, bodyTop + w * 0.02f, cx + w * 0.36f, bodyTop + w * 0.20f)
        c.drawRoundRect(rect, w * 0.05f, w * 0.05f, p)
        ui.text(c, "BUDDY PRIZES", cx, bodyTop + w * 0.155f, w * 0.10f, 0xFF2A1D04.toInt(), ui.title, false)

        p.color = 0xFFE2E8F2.toInt()
        rect.set(cx - domeR * 1.06f, domeCY + domeR * 0.52f, cx + domeR * 1.06f, domeCY + domeR * 0.92f)
        c.drawRoundRect(rect, domeR * 0.2f, domeR * 0.2f, p)

        p.color = ColorX.withAlpha(0xFFBDE8FF.toInt(), 0.35f)
        c.drawCircle(cx, domeCY, domeR, p)

        val jiggle = if (state == State.CRANK) 1f else 0f
        for (i in 0 until 16) {
            val a = Hash.range(i, 301, 0f, 6.28f)
            val rr = domeR * (0.22f + Hash.f(i, 303) * 0.62f)
            val jx = sin(ui.time * 14f + i) * 5f * jiggle
            val jy = sin(ui.time * 11f + i * 2f) * 5f * jiggle
            val px = cx + cos(a) * rr + jx
            val py = domeCY + sin(a) * rr * 0.86f + jy
            p.color = capsuleColor(i)
            c.drawCircle(px, py, domeR * 0.15f, p)
            p.color = ColorX.withAlpha(0xFFFFFFFF.toInt(), 0.55f)
            c.drawCircle(px - domeR * 0.05f, py - domeR * 0.05f, domeR * 0.045f, p)
        }

        p.color = ColorX.withAlpha(0xFFFFFFFF.toInt(), 0.28f)
        path.reset()
        path.moveTo(cx - domeR * 0.75f, domeCY - domeR * 0.15f)
        path.quadTo(cx - domeR * 0.6f, domeCY - domeR * 0.95f, cx + domeR * 0.1f, domeCY - domeR * 0.9f)
        path.quadTo(cx - domeR * 0.42f, domeCY - domeR * 0.55f, cx - domeR * 0.5f, domeCY - domeR * 0.05f)
        path.close()
        c.drawPath(path, p)
        p.style = Paint.Style.STROKE
        p.strokeWidth = w * 0.022f
        p.color = 0xFFF2F5FA.toInt()
        c.drawCircle(cx, domeCY, domeR, p)
        p.style = Paint.Style.FILL

        val crankCX = cx + w * 0.26f
        val crankCY = bodyTop + w * 0.36f
        crankX = crankCX; crankY = crankCY; crankR = w * 0.16f
        p.color = 0xFFD8DEE9.toInt()
        c.drawCircle(crankCX, crankCY, w * 0.12f, p)
        p.color = 0xFF98A2B3.toInt()
        c.drawCircle(crankCX, crankCY, w * 0.09f, p)
        c.save()
        c.rotate(crankAngle, crankCX, crankCY)
        p.color = 0xFF5B6478.toInt()
        rect.set(crankCX - w * 0.015f, crankCY - w * 0.085f, crankCX + w * 0.015f, crankCY + w * 0.085f)
        c.drawRoundRect(rect, w * 0.015f, w * 0.015f, p)
        p.color = 0xFFF2F5FA.toInt()
        c.drawCircle(crankCX, crankCY - w * 0.085f, w * 0.028f, p)
        c.restore()

        // coin slot
        val slotCX = cx - w * 0.26f
        val slotCY = bodyTop + w * 0.33f
        slotX = slotCX; slotY = slotCY
        p.color = 0xFF8E9AAE.toInt()
        rect.set(cx - w * 0.38f, bodyTop + w * 0.27f, cx - w * 0.14f, bodyTop + w * 0.39f)
        c.drawRoundRect(rect, w * 0.03f, w * 0.03f, p)
        p.color = 0xFF3A2320.toInt()
        rect.set(cx - w * 0.36f, bodyTop + w * 0.30f, cx - w * 0.16f, bodyTop + w * 0.36f)
        c.drawRoundRect(rect, w * 0.03f, w * 0.03f, p)

        // the coin going in, on its way to buying this pull
        if (coinDropT > 0f) {
            val k = clamp01(1f - coinDropT / COIN_DROP_TIME)
            val fall = smoothstep(0f, 1f, k)
            val coinY = slotCY - w * 0.42f + w * 0.42f * fall
            // it narrows to an edge as it turns into the slot, then vanishes inside
            val squeeze = if (k > 0.72f) (1f - (k - 0.72f) / 0.28f).coerceAtLeast(0.05f) else 1f
            val alpha = if (k > 0.86f) (1f - (k - 0.86f) / 0.14f).coerceAtLeast(0f) else 1f
            c.save()
            c.translate(slotCX, coinY)
            c.scale(squeeze, 1f)
            c.rotate(k * 260f)
            p.color = ColorX.withAlpha(Theme.ACCENT_DEEP, alpha)
            c.drawCircle(0f, 0f, w * 0.055f, p)
            p.color = ColorX.withAlpha(Theme.ACCENT, alpha)
            c.drawCircle(0f, 0f, w * 0.045f, p)
            p.color = ColorX.withAlpha(Theme.ACCENT_DEEP, alpha)
            c.drawCircle(0f, w * 0.008f, w * 0.015f, p)
            c.restore()
        }

        val chuteY = bodyBottom - h * 0.19f
        p.color = 0xFF7E241F.toInt()
        rect.set(cx - w * 0.28f, chuteY, cx + w * 0.28f, chuteY + h * 0.11f)
        c.drawRoundRect(rect, w * 0.05f, w * 0.05f, p)
        p.color = ColorX.withAlpha(0xFF000000.toInt(), 0.35f)
        rect.set(cx - w * 0.24f, chuteY + h * 0.012f, cx + w * 0.24f, chuteY + h * 0.085f)
        c.drawRoundRect(rect, w * 0.04f, w * 0.04f, p)

        if (state == State.DROP || state == State.REVEAL) {
            val k = if (state == State.DROP) smoothstep(0f, 1f, 1f - clamp01(timer / 0.75f)) else 1f
            val capY = domeCY + (chuteY + h * 0.05f - domeCY) * k
            val bounce = if (k > 0.92f) sin((k - 0.92f) * 60f) * 8f else 0f
            drawCapsule(c, cx, capY + bounce, w * 0.15f, prizeTint())
        }
    }

    private fun capsuleColor(i: Int): Int {
        val palette = intArrayOf(
            0xFFF2C14E.toInt(), 0xFFE8595B.toInt(), 0xFF57C4E5.toInt(),
            0xFF7BE3A0.toInt(), 0xFFB388FF.toInt(), 0xFFF7A8C4.toInt()
        )
        return palette[Hash.int(i, 307, palette.size)]
    }

    private fun drawCapsule(c: Canvas, cx: Float, cy: Float, r: Float, tint: Int) {
        p.reset(); p.isAntiAlias = true
        p.color = ColorX.withAlpha(0xFFFFFFFF.toInt(), 0.92f)
        rect.set(cx - r, cy - r, cx + r, cy + r)
        c.drawArc(rect, 180f, 180f, true, p)
        p.color = tint
        c.drawArc(rect, 0f, 180f, true, p)
        p.color = ColorX.withAlpha(0xFF000000.toInt(), 0.18f)
        rect.set(cx - r, cy - r * 0.12f, cx + r, cy + r * 0.12f)
        c.drawRoundRect(rect, r * 0.12f, r * 0.12f, p)
        p.color = ColorX.withAlpha(0xFFFFFFFF.toInt(), 0.7f)
        c.drawCircle(cx - r * 0.35f, cy - r * 0.42f, r * 0.16f, p)
    }

    // -------------------------------------------------------------------------------------
    // reveal
    // -------------------------------------------------------------------------------------

    private fun drawReveal(c: Canvas) {
        val ui = g.ui
        if (prizeId.isEmpty()) return
        val k = smoothstep(0f, 1f, revealAnim)
        val pop = 1f + (1f - k) * 0.25f
        val tint = prizeTint()

        ui.scrim(c, g.worldW, Theme.SCREEN_H, 0.72f * k)

        val w = min(g.worldW - ui.safeLeft - ui.safeRight - 80f, 720f)
        val h = min(Theme.SCREEN_H - ui.safeTop - ui.safeBottom - 120f, 980f)
        val x = (g.worldW - w) * 0.5f
        val y = (Theme.SCREEN_H - h) * 0.5f

        c.save()
        c.scale(pop, pop, g.worldW * 0.5f, Theme.SCREEN_H * 0.5f)

        g.art.drawGlow(c, g.worldW * 0.5f, y + h * 0.42f, w * 1.1f, tint, 0.35f * k)
        if (isBigPrize()) {
            p.reset(); p.isAntiAlias = true
            for (i in 0 until 12) {
                val a = ui.time * 0.5f + i * 0.5236f
                p.color = ColorX.withAlpha(tint, 0.16f * k)
                path.reset()
                path.moveTo(g.worldW * 0.5f, y + h * 0.42f)
                path.lineTo(g.worldW * 0.5f + cos(a) * w * 1.2f - 40f, y + h * 0.42f + sin(a) * w * 1.2f)
                path.lineTo(g.worldW * 0.5f + cos(a) * w * 1.2f + 40f, y + h * 0.42f + sin(a) * w * 1.2f)
                path.close()
                c.drawPath(path, p)
            }
        }

        ui.panel(c, x, y, w, h)
        ui.shimmer(c, x, y, w, h, Theme.RADIUS, if (isBigPrize()) 1f else 0.5f)

        ui.text(
            c, prizeBanner(), g.worldW * 0.5f, y + 96f, 52f,
            if (duplicate && prizeKind != Kind.SCENE) Theme.TEXT_DIM else Theme.ACCENT, ui.title, true, w - 48f
        )
        ui.pill(c, g.worldW * 0.5f - 110f, y + 122f, 220f, 52f, ColorX.withAlpha(tint, 0.25f))
        ui.text(c, prizeRarityLabel(), g.worldW * 0.5f, y + 158f, 28f, tint, ui.title, false, 210f)

        when (prizeKind) {
            Kind.OUTFIT -> g.drawPosedBuddy(
                c, g.worldW * 0.5f, y + h * 0.72f, min(w / 300f, h / 620f) * 1.05f, prizeId, ui.time
            )
            Kind.POWERUP -> icons.draw(c, g.art, prizeId, g.worldW * 0.5f, y + h * 0.46f, h * 0.13f, tint)
            Kind.TRAIL -> g.drawTrailPreview(c, g.worldW * 0.5f, y + h * 0.46f, w * 0.52f, h * 0.22f, prizeId, ui.time)
            else -> drawScenePreview(c, g.worldW * 0.5f, y + h * 0.46f, w * 0.52f, h * 0.30f)
        }

        ui.text(c, prizeName().uppercase(), g.worldW * 0.5f, y + h - 168f, 48f, Theme.TEXT, ui.title, true, w - 60f)
        val blurb = if (duplicate && prizeKind != Kind.SCENE) {
            "+${Tuning.DUPLICATE_REFUND} coins back"
        } else {
            prizeBlurb()
        }
        ui.text(
            c, blurb, g.worldW * 0.5f, y + h - 124f, 30f,
            if (duplicate && prizeKind != Kind.SCENE) Theme.ACCENT else Theme.TEXT_DIM, ui.body, false, w - 60f
        )

        val by = y + h - 96f
        val canAgain = g.save.freeSpins || g.save.coins >= Tuning.GACHA_COST
        // Three buttons when there is something to put on, two when there is not. Winning a
        // trail you like and then having to go and find it in the wardrobe to wear it was silly.
        val canEquip = !duplicate && (prizeKind == Kind.OUTFIT || prizeKind == Kind.TRAIL ||
            prizeKind == Kind.SCENE)
        val cols = if (canEquip) 3 else 2
        val cw = (w - 80f - 20f * (cols - 1)) / cols

        if (ui.button(c, Id.AGAIN, x + 40f, by, cw, 78f, "AGAIN", Ui.ButtonStyle.PRIMARY, canAgain,
                sublabel = "-${Tuning.GACHA_COST}")) {
            reset(); pull()
        }
        if (canEquip) {
            val equipLabel = if (prizeKind == Kind.SCENE) "PLAY IT" else "EQUIP"
            if (ui.button(c, Id.EQUIP, x + 40f + cw + 20f, by, cw, 78f, equipLabel,
                    Ui.ButtonStyle.SECONDARY)) {
                g.tap()
                equipPrize()
                reset()
            }
        }
        val doneLabel = if (duplicate) "OK" else "NICE"
        if (ui.button(c, Id.DONE, x + 40f + (cw + 20f) * (cols - 1), by, cw, 78f, doneLabel)) {
            g.tap()
            reset()
        }
        c.restore()
    }

    /** Puts the prize on straight from the reveal. */
    private fun equipPrize() {
        when (prizeKind) {
            Kind.SCENE -> {
                g.save.selectedScene = prizeId
                g.applyScene()
            }
            Kind.OUTFIT -> g.save.equippedOutfit = prizeId
            Kind.TRAIL -> g.save.equippedTrail = prizeId
        }
    }

    /** The won world, as a stack of its skies. */
    private fun drawScenePreview(c: Canvas, cx: Float, cy: Float, w: Float, h: Float) {
        val scene = Scenes.of(prizeId)
        val bands = scene.bands
        val colors = IntArray(bands.size + 1)
        for (i in bands.indices) colors[bands.size - 1 - i] = bands[i].skyMid
        colors[bands.size] = bands[0].skyLow
        p.reset(); p.isAntiAlias = true
        p.shader = LinearGradient(cx, cy - h * 0.5f, cx, cy + h * 0.5f, colors, null, Shader.TileMode.CLAMP)
        rect.set(cx - w * 0.5f, cy - h * 0.5f, cx + w * 0.5f, cy + h * 0.5f)
        c.drawRoundRect(rect, 22f, 22f, p)
        p.shader = null
        p.color = ColorX.withAlpha(bands[0].platTop, 0.95f)
        rect.set(cx - w * 0.5f, cy + h * 0.5f - 26f, cx + w * 0.5f, cy + h * 0.5f)
        c.drawRoundRect(rect, 12f, 12f, p)
        p.style = Paint.Style.STROKE
        p.strokeWidth = 4f
        p.color = ColorX.withAlpha(scene.cardTint, 0.8f)
        rect.set(cx - w * 0.5f, cy - h * 0.5f, cx + w * 0.5f, cy + h * 0.5f)
        c.drawRoundRect(rect, 22f, 22f, p)
        p.style = Paint.Style.FILL
    }
}
