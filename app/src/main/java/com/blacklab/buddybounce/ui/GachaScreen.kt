package com.blacklab.buddybounce.ui

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import com.blacklab.buddybounce.Game
import com.blacklab.buddybounce.audio.Audio
import com.blacklab.buddybounce.data.Outfits
import com.blacklab.buddybounce.game.Hash
import com.blacklab.buddybounce.game.MathX.clamp01
import com.blacklab.buddybounce.game.MathX.smoothstep
import com.blacklab.buddybounce.game.Tuning
import com.blacklab.buddybounce.render.ColorX
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

/**
 * The prize machine. One pull costs [Tuning.GACHA_COST] coins and dispenses a capsule with a
 * random outfit, weighted by rarity; a duplicate hands some of the coins back.
 */
class GachaScreen(private val g: Game) {

    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val path = Path()
    private val rect = RectF()
    private val rng = Random(System.nanoTime())

    private object Id {
        const val BACK = 4001
        const val PULL = 4002
        const val AGAIN = 4003
        const val DONE = 4004
    }

    private enum class State { IDLE, CRANK, DROP, REVEAL }

    private var state = State.IDLE
    private var timer = 0f
    private var prize: Outfits.Outfit? = null
    private var duplicate = false
    private var revealAnim = 0f
    private var crankAngle = 0f

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
                    val r = prize?.rarity
                    if (!duplicate && (r == Outfits.Rarity.EPIC || r == Outfits.Rarity.LEGENDARY)) {
                        g.shakeScreen(0.5f)
                        g.flashScreen(0.35f)
                    }
                }
            }
            State.REVEAL -> revealAnim = (revealAnim + dt * 2.2f).coerceAtMost(1f)
            State.IDLE -> {}
        }
    }

    fun draw(c: Canvas) {
        val ui = g.ui
        val wide = g.worldW > Tuning.VIEW_H * 1.12f

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
        val areaBottom = Tuning.VIEW_H - ui.safeBottom - 200f
        val machineH = (areaBottom - areaTop).coerceAtLeast(320f)
        val machineW = min(g.worldW * (if (wide) 0.42f else 0.86f), machineH * 0.72f)
        val cx = if (wide) g.worldW * 0.32f else g.worldW * 0.5f
        val top = areaTop + (machineH - machineW / 0.72f).coerceAtLeast(0f) * 0.5f

        drawMachine(c, cx, top, machineW, machineW / 0.72f)

        val btnW = min(g.worldW - ui.safeLeft - ui.safeRight - 100f, 560f)
        val btnX = if (wide) g.worldW * 0.60f else (g.worldW - btnW) * 0.5f
        val btnY = if (wide) Tuning.VIEW_H * 0.5f else Tuning.VIEW_H - ui.safeBottom - 168f

        if (state == State.REVEAL) {
            drawReveal(c, wide)
        } else {
            val everythingOwned = g.ownedCount() >= Outfits.collectableCount
            val canPull = coins >= Tuning.GACHA_COST && state == State.IDLE && !everythingOwned
            val label = when {
                everythingOwned -> "ALL COLLECTED"
                state != State.IDLE -> "..."
                coins >= Tuning.GACHA_COST -> "PULL THE CRANK"
                else -> "NOT ENOUGH COINS"
            }
            val sub = when {
                everythingOwned -> "Buddy has every outfit. Show-off."
                coins >= Tuning.GACHA_COST -> "-${Tuning.GACHA_COST} coins"
                else -> "collect ${Tuning.GACHA_COST - coins} more"
            }
            val pullW = if (wide) min(btnW, g.worldW - btnX - ui.safeRight - 40f) else btnW
            if (ui.button(c, Id.PULL, btnX, btnY, pullW, 118f, label, Ui.ButtonStyle.PRIMARY, canPull, sub)) {
                pull()
            }
        }
    }

    private fun reset() {
        state = State.IDLE
        prize = null
        duplicate = false
        revealAnim = 0f
    }

    // -----------------------------------------------------------------------------------
    // the pull
    // -----------------------------------------------------------------------------------

    private fun pull() {
        if (!g.save.spendCoins(Tuning.GACHA_COST)) return
        g.audio.play(Audio.GACHA_SPIN, 0.8f)
        prize = rollPrize()
        val outfit = prize
        duplicate = outfit != null && g.save.owns(outfit.id)
        if (outfit != null && !duplicate) g.save.unlock(outfit.id)
        if (duplicate) g.save.addCoins(Tuning.DUPLICATE_REFUND)
        state = State.CRANK
        timer = 0.85f
        crankAngle = 0f
    }

    /**
     * Weighted by rarity. A rarity the player has completed rolls down into one they haven't,
     * so late pulls keep feeling like progress; inside a rarity a duplicate is still possible
     * and refunds part of the cost.
     */
    private fun rollPrize(): Outfits.Outfit? {
        val rarities = Outfits.Rarity.values()
        val open = rarities.filter { r -> Outfits.inRarity(r).any { !g.save.owns(it.id) } }
        val pool = if (open.isEmpty()) rarities.toList() else open
        var total = 0
        for (r in pool) total += r.weight
        if (total <= 0) return null
        var roll = rng.nextInt(total)
        var chosen = pool[0]
        for (r in pool) {
            roll -= r.weight
            if (roll < 0) { chosen = r; break }
        }
        val items = Outfits.inRarity(chosen)
        if (items.isEmpty()) return null
        return items[rng.nextInt(items.size)]
    }

    // -----------------------------------------------------------------------------------
    // machine art
    // -----------------------------------------------------------------------------------

    private fun drawMachine(c: Canvas, cx: Float, top: Float, w: Float, h: Float) {
        val ui = g.ui
        val domeR = w * 0.42f
        val domeCY = top + domeR + w * 0.06f
        val bodyTop = domeCY + domeR * 0.72f
        val bodyBottom = top + h

        g.art.drawShadow(c, cx, bodyBottom + 12f, w * 1.15f, h * 0.22f, 0.55f)
        p.reset(); p.isAntiAlias = true

        // body
        p.color = 0xFFC0392B.toInt()
        rect.set(cx - w * 0.5f, bodyTop - w * 0.12f, cx + w * 0.5f, bodyBottom)
        c.drawRoundRect(rect, w * 0.1f, w * 0.1f, p)
        p.color = 0xFFA5302A.toInt()
        rect.set(cx - w * 0.5f, bodyBottom - h * 0.09f, cx + w * 0.5f, bodyBottom)
        c.drawRoundRect(rect, w * 0.1f, w * 0.1f, p)
        p.color = ColorX.withAlpha(0xFFFFFFFF.toInt(), 0.16f)
        rect.set(cx - w * 0.42f, bodyTop - w * 0.06f, cx - w * 0.28f, bodyBottom - h * 0.06f)
        c.drawRoundRect(rect, w * 0.05f, w * 0.05f, p)

        // plaque
        p.color = Theme.ACCENT
        rect.set(cx - w * 0.36f, bodyTop + w * 0.02f, cx + w * 0.36f, bodyTop + w * 0.20f)
        c.drawRoundRect(rect, w * 0.05f, w * 0.05f, p)
        ui.text(c, "BUDDY PRIZES", cx, bodyTop + w * 0.155f, w * 0.10f, 0xFF2A1D04.toInt(), ui.title, false)

        // dome + capsules
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
            val px = cx + kotlin.math.cos(a) * rr + jx
            val py = domeCY + sin(a) * rr * 0.86f + jy
            val col = capsuleColor(i)
            p.color = col
            c.drawCircle(px, py, domeR * 0.15f, p)
            p.color = ColorX.withAlpha(0xFFFFFFFF.toInt(), 0.55f)
            c.drawCircle(px - domeR * 0.05f, py - domeR * 0.05f, domeR * 0.045f, p)
        }

        // glass highlight + rim
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

        // crank
        val crankCX = cx + w * 0.26f
        val crankCY = bodyTop + w * 0.36f
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
        p.color = 0xFF3A2320.toInt()
        rect.set(cx - w * 0.36f, bodyTop + w * 0.30f, cx - w * 0.16f, bodyTop + w * 0.36f)
        c.drawRoundRect(rect, w * 0.03f, w * 0.03f, p)

        // chute
        val chuteY = bodyBottom - h * 0.19f
        p.color = 0xFF7E241F.toInt()
        rect.set(cx - w * 0.28f, chuteY, cx + w * 0.28f, chuteY + h * 0.11f)
        c.drawRoundRect(rect, w * 0.05f, w * 0.05f, p)
        p.color = ColorX.withAlpha(0xFF000000.toInt(), 0.35f)
        rect.set(cx - w * 0.24f, chuteY + h * 0.012f, cx + w * 0.24f, chuteY + h * 0.085f)
        c.drawRoundRect(rect, w * 0.04f, w * 0.04f, p)

        // the capsule on its way out
        if (state == State.DROP || state == State.REVEAL) {
            val k = if (state == State.DROP) smoothstep(0f, 1f, 1f - clamp01(timer / 0.75f)) else 1f
            val capY = domeCY + (chuteY + h * 0.05f - domeCY) * k
            val bounce = if (k > 0.92f) sin((k - 0.92f) * 60f) * 8f else 0f
            drawCapsule(c, cx, capY + bounce, w * 0.15f, prize?.rarity?.tint ?: Theme.ACCENT)
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

    // -----------------------------------------------------------------------------------
    // reveal
    // -----------------------------------------------------------------------------------

    private fun drawReveal(c: Canvas, wide: Boolean) {
        val ui = g.ui
        val outfit = prize ?: return
        val k = smoothstep(0f, 1f, revealAnim)
        val pop = 1f + (1f - k) * 0.25f

        ui.scrim(c, g.worldW, Tuning.VIEW_H, 0.72f * k)

        val w = min(g.worldW - ui.safeLeft - ui.safeRight - 80f, 720f)
        val h = min(Tuning.VIEW_H - ui.safeTop - ui.safeBottom - 120f, 980f)
        val x = (g.worldW - w) * 0.5f
        val y = (Tuning.VIEW_H - h) * 0.5f

        c.save()
        c.scale(pop, pop, g.worldW * 0.5f, Tuning.VIEW_H * 0.5f)

        // rarity burst
        val burst = outfit.rarity.tint
        g.art.drawGlow(c, g.worldW * 0.5f, y + h * 0.42f, w * 1.1f, burst, 0.35f * k)
        if (outfit.rarity == Outfits.Rarity.LEGENDARY || outfit.rarity == Outfits.Rarity.EPIC) {
            p.reset(); p.isAntiAlias = true
            for (i in 0 until 12) {
                val a = ui.time * 0.5f + i * 0.5236f
                p.color = ColorX.withAlpha(burst, 0.16f * k)
                path.reset()
                path.moveTo(g.worldW * 0.5f, y + h * 0.42f)
                path.lineTo(
                    g.worldW * 0.5f + kotlin.math.cos(a) * w * 1.2f - 40f,
                    y + h * 0.42f + sin(a) * w * 1.2f
                )
                path.lineTo(
                    g.worldW * 0.5f + kotlin.math.cos(a) * w * 1.2f + 40f,
                    y + h * 0.42f + sin(a) * w * 1.2f
                )
                path.close()
                c.drawPath(path, p)
            }
        }

        ui.panel(c, x, y, w, h)
        ui.shimmer(c, x, y, w, h, Theme.RADIUS, if (outfit.rarity == Outfits.Rarity.LEGENDARY) 1f else 0.5f)

        ui.text(
            c, if (duplicate) "ALREADY HAD IT" else "NEW OUTFIT!",
            g.worldW * 0.5f, y + 96f, 54f, if (duplicate) Theme.TEXT_DIM else Theme.ACCENT, ui.title
        )
        ui.pill(c, g.worldW * 0.5f - 110f, y + 122f, 220f, 52f, ColorX.withAlpha(outfit.rarity.tint, 0.25f))
        ui.text(c, outfit.rarity.label.uppercase(), g.worldW * 0.5f, y + 158f, 30f, outfit.rarity.tint, ui.title, false)

        g.drawPosedBuddy(c, g.worldW * 0.5f, y + h * 0.74f, min(w / 300f, h / 620f) * 1.55f, outfit.id, ui.time)

        ui.text(c, outfit.name.uppercase(), g.worldW * 0.5f, y + h - 168f, 50f, Theme.TEXT, ui.title)
        ui.text(
            c, if (duplicate) "+${Tuning.DUPLICATE_REFUND} coins back" else outfit.blurb,
            g.worldW * 0.5f, y + h - 124f, 30f,
            if (duplicate) Theme.ACCENT else Theme.TEXT_DIM, ui.body, false
        )

        val bw = (w - 120f) * 0.5f
        val by = y + h - 96f
        val canAgain = g.save.coins >= Tuning.GACHA_COST
        if (ui.button(c, Id.AGAIN, x + 40f, by, bw, 78f, "AGAIN", Ui.ButtonStyle.PRIMARY, canAgain,
                sublabel = "-${Tuning.GACHA_COST}") && canAgain) {
            reset(); pull()
        }
        if (ui.button(c, Id.DONE, x + 80f + bw, by, bw, 78f, if (duplicate) "OK" else "WEAR IT")) {
            g.tap()
            if (!duplicate) g.save.equippedOutfit = outfit.id
            reset()
        }
        c.restore()
    }
}
