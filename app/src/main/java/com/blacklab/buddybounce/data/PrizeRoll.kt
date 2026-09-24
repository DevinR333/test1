package com.blacklab.buddybounce.data

import com.blacklab.buddybounce.render.Scenes
import kotlin.random.Random

/**
 * What the prize machine hands out, and with what odds.
 *
 * This lives apart from the screen that draws the machine so the odds can be MEASURED rather
 * than asserted - see tools/gacha/GachaCheck.kt, which pulls the handle a million times against
 * a real save and checks the distribution against the numbers below. A fairness claim you
 * cannot run is not a fairness claim.
 *
 * The draw is a plain PRNG, seeded from the clock and never reseeded. There is no pity timer,
 * no streak breaker, no "your first pull is always a good one", and nothing reads the coin
 * balance, the clock, the score or how long you have played. Every pull is independent of every
 * other pull. The only thing the machine knows about you is what you already own, and it uses
 * that in exactly two documented ways - see [rollTrail] and [rollOutfit].
 */
class PrizeRoll(private val rng: Random) {

    object Kind {
        const val OUTFIT = 0
        const val POWERUP = 1
        const val SCENE = 2
        const val TRAIL = 3
    }

    class Result(val kind: Int, val id: String, val duplicate: Boolean)

    companion object {
        // The machine's split. Scenes are the jackpot, trails are the regular treat, outfits
        // sit between them, and everything else is a power-up so most pulls still give you
        // something to spend next run.
        const val SCENE_CHANCE = 0.01f
        const val TRAIL_CHANCE = 0.15f
        const val OUTFIT_CHANCE = 0.10f
    }

    /**
     * One pull.
     *
     * A single uniform draw picks the category, so the bands are exactly the constants above.
     * A band with nothing left in it falls through to the next one rather than handing out a
     * prize that does not exist: with every world owned the scene 1% becomes trail, and with
     * every outfit owned the outfit 10% becomes power-ups.
     */
    fun roll(save: Save): Result {
        val lockedScenes = Scenes.unlockable.filter { !save.ownsScene(it.id) }
        val roll = rng.nextFloat()

        if (lockedScenes.isNotEmpty() && roll < SCENE_CHANCE) {
            return Result(Kind.SCENE, lockedScenes[rng.nextInt(lockedScenes.size)].id, false)
        }

        if (roll < SCENE_CHANCE + TRAIL_CHANCE) {
            val id = rollTrail(save)
            return Result(Kind.TRAIL, id, save.ownsTrail(id))
        }

        val anyOutfitLeft = Outfits.ALL.any {
            it.id != Outfits.DEFAULT_ID && it.id != Outfits.HEAVEN_ONLY_ID && !save.owns(it.id)
        }
        if (roll < SCENE_CHANCE + TRAIL_CHANCE + OUTFIT_CHANCE && anyOutfitLeft) {
            val id = rollOutfit(save)
            return Result(Kind.OUTFIT, id, save.owns(id))
        }

        return Result(Kind.POWERUP, rollPowerup(), false)
    }

    /**
     * Rarity weighted, and it draws from the ones you do not own yet so the set actually fills
     * up. Once you own them all there is nothing left to draw but duplicates, and it says so.
     */
    fun rollTrail(save: Save): String {
        val locked = Trails.collectable.filter { !save.ownsTrail(it.id) }
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

    fun rollPowerup(): String {
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
    fun rollOutfit(save: Save): String {
        val rarities = Outfits.Rarity.values()
        val open = rarities.filter { r -> Outfits.inRarity(r).any { !save.owns(it.id) } }
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
}
