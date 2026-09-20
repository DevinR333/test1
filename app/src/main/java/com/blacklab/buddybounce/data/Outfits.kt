package com.blacklab.buddybounce.data

/**
 * The wardrobe. Rarity drives both the pull odds and the presentation (card colour, glow, the
 * length of the drum roll before the reveal).
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

        // ---- common ----
        Outfit("bandana", "Red Bandana", Rarity.COMMON, "Adventure-ready neckwear."),
        Outfit("ball", "Tennis Ball", Rarity.COMMON, "He is NOT dropping this."),
        Outfit("party", "Party Hat", Rarity.COMMON, "Every bounce is a celebration."),
        Outfit("shades", "Cool Shades", Rarity.COMMON, "Sunlight at 40,000 wu is brutal."),
        Outfit("flowers", "Flower Crown", Rarity.COMMON, "Picked from the backyard himself."),
        Outfit("sweater", "Cozy Sweater", Rarity.COMMON, "Knitted by a neighbour who adores him."),
        Outfit("beanie", "Winter Beanie", Rarity.COMMON, "Ears out. Obviously."),
        Outfit("towel", "Bath Towel", Rarity.COMMON, "Fresh from the tub and furious about it."),
        Outfit("cone", "Cone of Shame", Rarity.COMMON, "He did nothing wrong and he'd do it again."),

        // ---- rare ----
        Outfit("aviator", "Aviator Goggles", Rarity.RARE, "Scarf optional. He insists otherwise."),
        Outfit("chef", "Chef's Hat", Rarity.RARE, "Specialty: anything left on the counter."),
        Outfit("cowboy", "Cowboy Outfit", Rarity.RARE, "Yeehaw, respectfully."),
        Outfit("snorkel", "Snorkel Set", Rarity.RARE, "In case the clouds turn out to be wet."),
        Outfit("backpack", "School Backpack", Rarity.RARE, "Packed with treats and one sock."),
        Outfit("raincoat", "Rain Slicker", Rarity.RARE, "Storm clouds hate this one trick."),
        Outfit("clown", "Clown Outfit", Rarity.RARE, "Red nose, big ruff, absolutely no notes."),
        Outfit("plumber", "Plumber Outfit", Rarity.RARE, "Overalls, red cap, suspicious moustache."),
        Outfit("detective", "Detective Coat", Rarity.RARE, "The case of the missing tennis ball."),
        Outfit("vet", "Lab Coat", Rarity.RARE, "He is, technically, a lab."),
        Outfit("racer", "Race Driver", Rarity.RARE, "Fastest paws in the paddock."),
        Outfit("pirate", "Pirate", Rarity.RARE, "Arrr. Where's the biscuit."),
        Outfit("french", "French Buddy", Rarity.RARE, "Beret, ascot, baguette. Formidable."),
        Outfit("mike", "Mike's Outfit", Rarity.RARE, "A black tee with a very familiar mouth on it."),

        // ---- epic ----
        Outfit("cape", "Super Buddy", Rarity.EPIC, "Faster than a thrown ball."),
        Outfit("knight", "Knight's Helm", Rarity.EPIC, "Defender of the food bowl."),
        Outfit("dino", "Dino Hoodie", Rarity.EPIC, "Rawr. Tail spikes included."),
        Outfit("bee", "Bumblebee Suit", Rarity.EPIC, "Confuses actual bees. Mostly."),
        Outfit("santa", "Santa Outfit", Rarity.EPIC, "Ho ho ho. He checked the list twice."),
        Outfit("mafia", "Mafia Suit", Rarity.EPIC, "Pinstripes. Fedora. Nothing personal."),
        Outfit("ninja", "Ninja Gi", Rarity.EPIC, "You never saw him take the treat."),
        Outfit("wizard", "Wizard Robes", Rarity.EPIC, "He has read exactly one spellbook. It was tasty."),
        Outfit("viking", "Viking Helm", Rarity.EPIC, "To Valhalla, and then the park."),
        Outfit("lucha", "Luchador Mask", Rarity.EPIC, "El Perro Negro, undefeated."),
        Outfit("shark", "Shark Onesie", Rarity.EPIC, "Doot doo doo doo doo doo."),
        Outfit("firefighter", "Firefighter", Rarity.EPIC, "Rescues tennis balls from high places."),

        // ---- legendary ----
        Outfit("astro", "Astronaut Suit", Rarity.LEGENDARY, "One small bounce for dog."),
        Outfit("cosmic", "Cosmic Coat", Rarity.LEGENDARY, "His fur holds actual starlight."),
        Outfit("crown", "Good Boy Crown", Rarity.LEGENDARY, "Officially the best. It's in writing."),
        Outfit("hero", "Hero Outfit", Rarity.LEGENDARY, "Purple tunic, pointed cap, one very small sword."),
        Outfit("robot", "Robo-Buddy", Rarity.LEGENDARY, "Beep. Boop. Good. Boy."),
        Outfit("unicorn", "Unicorn Onesie", Rarity.LEGENDARY, "Majestic. Slightly chewed."),
        Outfit("anti", "Anti-Buddy", Rarity.LEGENDARY, "Same dog. Opposite dog.")
    )

    private val index: Map<String, Outfit> = ALL.associateBy { it.id }

    fun byId(id: String): Outfit? = index[id]

    fun of(id: String): Outfit = index[id] ?: ALL[0]

    fun inRarity(r: Rarity): List<Outfit> = ALL.filter { it.rarity == r && it.id != DEFAULT_ID }

    /** How many outfits a completionist needs (the default collar doesn't count). */
    val collectableCount: Int = ALL.count { it.id != DEFAULT_ID }
}
