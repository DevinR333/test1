package com.blacklab.buddybounce.data

/**
 * The prize pool for the coin machine. Rarity drives both the pull odds and the presentation
 * (card colour, glow, the length of the drum roll before the reveal).
 */
object Outfits {

    const val DEFAULT_ID = "collar"

    enum class Rarity(
        val label: String,
        val weight: Int,
        val tint: Int,
        val glow: Int,
        val revealSpins: Int
    ) {
        COMMON("Common", 60, 0xFF8FA7C4.toInt(), 0x408FA7C4, 2),
        RARE("Rare", 27, 0xFF4FC3F7.toInt(), 0x554FC3F7, 3),
        EPIC("Epic", 10, 0xFFB388FF.toInt(), 0x66B388FF, 4),
        LEGENDARY("Legendary", 3, 0xFFFFC24B.toInt(), 0x77FFC24B, 6)
    }

    data class Outfit(
        val id: String,
        val name: String,
        val rarity: Rarity,
        val blurb: String
    )

    val ALL: List<Outfit> = listOf(
        Outfit(DEFAULT_ID, "Just Buddy", Rarity.COMMON, "The classic. A good dog and his collar."),

        Outfit("bandana", "Red Bandana", Rarity.COMMON, "Adventure-ready neckwear."),
        Outfit("ball", "Tennis Ball", Rarity.COMMON, "He is NOT dropping this."),
        Outfit("party", "Party Hat", Rarity.COMMON, "Every bounce is a celebration."),
        Outfit("shades", "Cool Shades", Rarity.COMMON, "Sunlight at 40,000 wu is brutal."),
        Outfit("flowers", "Flower Crown", Rarity.COMMON, "Picked from the backyard himself."),
        Outfit("sweater", "Cozy Sweater", Rarity.COMMON, "Knitted by a neighbour who adores him."),

        Outfit("aviator", "Aviator Goggles", Rarity.RARE, "Scarf optional. He insists otherwise."),
        Outfit("chef", "Chef's Hat", Rarity.RARE, "Specialty: anything left on the counter."),
        Outfit("cowboy", "Cowboy Hat", Rarity.RARE, "Yeehaw, respectfully."),
        Outfit("snorkel", "Snorkel Set", Rarity.RARE, "In case the clouds turn out to be wet."),
        Outfit("backpack", "School Backpack", Rarity.RARE, "Packed with treats and one sock."),
        Outfit("raincoat", "Rain Slicker", Rarity.RARE, "Storm clouds hate this one trick."),

        Outfit("cape", "Super Buddy", Rarity.EPIC, "Faster than a thrown ball."),
        Outfit("knight", "Knight's Helm", Rarity.EPIC, "Defender of the food bowl."),
        Outfit("dino", "Dino Hoodie", Rarity.EPIC, "Rawr. Tail included."),
        Outfit("bee", "Bumblebee Suit", Rarity.EPIC, "Confuses actual bees. Mostly."),

        Outfit("astro", "Astronaut Suit", Rarity.LEGENDARY, "One small bounce for dog."),
        Outfit("cosmic", "Cosmic Coat", Rarity.LEGENDARY, "His fur holds actual starlight."),
        Outfit("crown", "Good Boy Crown", Rarity.LEGENDARY, "Officially the best. It's in writing.")
    )

    private val byId: Map<String, Outfit> = ALL.associateBy { it.id }

    fun byId(id: String): Outfit? = byId[id]

    fun of(id: String): Outfit = byId[id] ?: ALL[0]

    fun inRarity(r: Rarity): List<Outfit> = ALL.filter { it.rarity == r && it.id != DEFAULT_ID }

    /** How many outfits a completionist needs (the default collar doesn't count). */
    val collectableCount: Int = ALL.count { it.id != DEFAULT_ID }
}
