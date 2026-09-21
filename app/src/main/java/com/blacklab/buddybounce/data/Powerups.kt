package com.blacklab.buddybounce.data

/**
 * Consumable power-ups. Unlike the in-run pick-ups Buddy grabs off a platform, these are items
 * won from the prize machine, stockpiled, and spent one per run: after you press PLAY you pick
 * one (or none) and it is applied before the countdown.
 *
 * They are the machine's "bread" prize - common enough that a pull is never a total loss.
 */
object Powerups {

    data class Powerup(
        val id: String,
        val name: String,
        val blurb: String,
        /** Relative chance of being the prize when the machine rolls a power-up. */
        val weight: Int,
        val tint: Int
    )

    const val MOON_JUMP = "moonjump"
    const val ROCKET_START = "rocket"
    const val JETPACK_START = "jetpack"
    const val SHIELD_START = "shield"
    const val MAGNET_RUN = "magnet"
    const val COIN_DOUBLER = "doubler"
    const val LUCKY_PAWS = "lucky"
    const val FEATHER_FALL = "feather"
    const val SAFETY_NET = "net"
    const val HEAD_START = "headstart"

    /**
     * Second Life is the odd one out: it is NOT offered in the pre-run picker, because it is not
     * something you set up before a run. It is spent at the moment you die, to carry on from
     * where you fell. See [preRunChoices].
     */
    const val SECOND_LIFE = "secondlife"

    val ALL: List<Powerup> = listOf(
        Powerup(MOON_JUMP, "Moon Jump", "Open with one colossal bounce - about four screens of free height.", 16, 0xFFBFD4FF.toInt()),
        Powerup(SHIELD_START, "Bubble Start", "Begin inside a bubble that eats the first hit.", 15, 0xFF8FD3F4.toInt()),
        Powerup(MAGNET_RUN, "Magnet Paws", "Coins come to you, for the whole run.", 13, 0xFFE8595B.toInt()),
        Powerup(FEATHER_FALL, "Feather Fall", "Thirty seconds of floaty, forgiving gravity.", 13, 0xFFD9C7FF.toInt()),
        Powerup(LUCKY_PAWS, "Lucky Paws", "Three times as many coins on the way up.", 11, 0xFFFFE07A.toInt()),
        Powerup(SAFETY_NET, "Safety Net", "One free save - a ledge appears under you as you fall.", 10, 0xFF7BE3A0.toInt()),
        Powerup(COIN_DOUBLER, "Coin Doubler", "Every coin, and the height bonus, counts double.", 9, 0xFFF2C14E.toInt()),
        Powerup(SECOND_LIFE, "Second Life", "Not for the start of a run - spend it when you die and carry on from where you fell.", 5, 0xFFFFF3C2.toInt()),
        Powerup(JETPACK_START, "Jetpack Start", "Start the run already flying.", 7, 0xFFFFB347.toInt()),
        Powerup(HEAD_START, "Head Start", "Begin six screens up, with the height already scored.", 4, 0xFFB388FF.toInt()),
        Powerup(ROCKET_START, "Rocket Start", "Start on a rocket. Ten screens before you touch a platform.", 2, 0xFFFF7A3C.toInt())
    )

    private val index: Map<String, Powerup> = ALL.associateBy { it.id }

    fun byId(id: String): Powerup? = index[id]

    fun of(id: String): Powerup = index[id] ?: ALL[0]

    val totalWeight: Int = ALL.sumOf { it.weight }

    /**
     * The ones the pre-run picker offers. Second Life is deliberately absent: it is spent from
     * the death screen, not chosen in advance.
     */
    val preRunChoices: List<Powerup> = ALL.filter { it.id != SECOND_LIFE }
}
