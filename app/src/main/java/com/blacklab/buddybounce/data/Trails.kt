package com.blacklab.buddybounce.data

/**
 * Trails: the ribbon of particles Buddy leaves behind him as he climbs.
 *
 * Forty of them, built from fourteen draw *styles* crossed with colour pairs, so each one reads
 * differently in motion rather than being the same puff in a new tint. The style decides the
 * shape, how it drifts and how it dies; the two colours are the hot core and the cool tail, and
 * every particle fades between them as it ages.
 *
 * They are deliberately SHORT-lived. A trail is a flourish behind the dog, not a smokescreen
 * over the platforms you are trying to land on, so nothing here lives much past a third of a
 * second and the emitters are metered by distance travelled rather than by frame.
 */
object Trails {

    /** The equipped-trail id meaning "none". Always owned, always the default. */
    const val NONE_ID = "none"

    /** Draw styles. [com.blacklab.buddybounce.render.TrailArt] renders each one. */
    object Style {
        const val EMBER = 0     // rising motes that shrink as they cool
        const val BUBBLE = 1    // hollow rings that swell and pop
        const val PETAL = 2     // flat ovals that tumble end over end
        const val STAR = 3      // four-point sparkles
        const val RIBBON = 4    // soft wide streaks stretched along travel
        const val SMOKE = 5     // slow expanding puffs
        const val FLAKE = 6     // six-spoke crystals, drifting
        const val BOLT = 7      // short jagged shards
        const val PAW = 8       // little paw prints, fading in place
        const val NOTE = 9      // music notes, bobbing
        const val HEART = 10    // hearts, rising
        const val PIXEL = 11    // hard squares on a grid, no rotation
        const val ORB = 12      // soft glowing balls with a bright core
        const val DROP = 13     // teardrops falling away

        const val COUNT = 14
    }

    class Trail(
        val id: String,
        val name: String,
        val rarity: Int,
        val style: Int,
        /** The colour a particle is born. */
        val hot: Int,
        /** The colour it fades to before it disappears. */
        val cool: Int,
        val blurb: String
    )

    object Rarity {
        const val COMMON = 0
        const val RARE = 1
        const val EPIC = 2
        const val LEGENDARY = 3
    }

    private fun t(id: String, name: String, rarity: Int, style: Int, hot: Long, cool: Long, blurb: String) =
        Trail(id, name, rarity, style, hot.toInt(), cool.toInt(), blurb)

    val ALL: List<Trail> = listOf(
        // ---- fire and heat ------------------------------------------------------------------
        t("flame", "Flame Trail", Rarity.COMMON, Style.EMBER, 0xFFFFD24A, 0xFFE0330C,
            "He runs hot. Always has."),
        t("ember", "Ember Trail", Rarity.COMMON, Style.EMBER, 0xFFFF9A3C, 0xFF5A1B00,
            "The good bits left over after the fire."),
        t("magma", "Magma Trail", Rarity.RARE, Style.DROP, 0xFFFFC14A, 0xFF8E2B08,
            "Please do not step where he has stepped."),
        t("solar", "Solar Trail", Rarity.EPIC, Style.ORB, 0xFFFFF4C2, 0xFFFF8A1E,
            "A small sun, following a small dog."),
        t("phoenix", "Phoenix Trail", Rarity.LEGENDARY, Style.RIBBON, 0xFFFFE9A8, 0xFFE83C0C,
            "Falls, gets up, falls better."),

        // ---- water --------------------------------------------------------------------------
        t("wave", "Wave Trail", Rarity.COMMON, Style.RIBBON, 0xFF8FE3FF, 0xFF1B6FA8,
            "A little bit of ocean that follows him around."),
        t("bubble", "Bubble Trail", Rarity.COMMON, Style.BUBBLE, 0xFFE0F7FF, 0xFF64C8E8,
            "Pop. Pop. Pop."),
        t("splash", "Splash Trail", Rarity.COMMON, Style.DROP, 0xFFCFEFFF, 0xFF2E8BA8,
            "He found a puddle. Of course he did."),
        t("tide", "Tide Trail", Rarity.RARE, Style.RIBBON, 0xFF6FF0D8, 0xFF0E5E6E,
            "Goes out, comes back, goes out again."),
        t("abyss", "Abyss Trail", Rarity.EPIC, Style.ORB, 0xFF9BF6D8, 0xFF041E2E,
            "Lights from somewhere very far down."),

        // ---- nature -------------------------------------------------------------------------
        t("leaf", "Leaf Trail", Rarity.COMMON, Style.PETAL, 0xFFA8E063, 0xFF3F7A53,
            "Autumn, shed one leaf at a time."),
        t("petal", "Petal Trail", Rarity.COMMON, Style.PETAL, 0xFFFFC9DE, 0xFFE0709C,
            "Blossom season, wherever he goes."),
        t("clover", "Clover Trail", Rarity.RARE, Style.PETAL, 0xFF7FE07F, 0xFF2E7A3E,
            "Three leaves. Never four. He checked."),
        t("pollen", "Pollen Trail", Rarity.COMMON, Style.STAR, 0xFFFFE9A8, 0xFFD8B24A,
            "The reason he sneezes at the top of every climb."),
        t("vine", "Vine Trail", Rarity.RARE, Style.RIBBON, 0xFF9BE06B, 0xFF2A5E34,
            "Grows behind him faster than he climbs."),
        t("mud", "Mud Trail", Rarity.COMMON, Style.PAW, 0xFF9A7047, 0xFF4A3220,
            "Somebody is getting a bath."),

        // ---- weather ------------------------------------------------------------------------
        t("snow", "Snow Trail", Rarity.COMMON, Style.FLAKE, 0xFFFFFFFF, 0xFF9FD8F0,
            "Fresh powder, all the way up."),
        t("frost", "Frost Trail", Rarity.RARE, Style.FLAKE, 0xFFDDF6FF, 0xFF4E9FD0,
            "The air behind him goes crisp."),
        t("storm", "Storm Trail", Rarity.RARE, Style.BOLT, 0xFFFFF0A8, 0xFF4A5170,
            "Rolling in, right on his tail."),
        t("cloud", "Cloud Trail", Rarity.COMMON, Style.SMOKE, 0xFFFFFFFF, 0xFFBFD4E8,
            "Soft landings not included."),
        t("mist", "Mist Trail", Rarity.COMMON, Style.SMOKE, 0xFFE8F0F8, 0xFF8FA0B8,
            "Mysterious. Or just damp."),

        // ---- light and space -----------------------------------------------------------------
        t("rainbow", "Rainbow Trail", Rarity.EPIC, Style.RIBBON, 0xFFFF4A6E, 0xFF4A9BFF,
            "Every colour, in the wrong order, at speed."),
        t("star", "Star Trail", Rarity.COMMON, Style.STAR, 0xFFFFF4C2, 0xFFFFC14A,
            "He is going where they are."),
        t("comet", "Comet Trail", Rarity.RARE, Style.RIBBON, 0xFFFFFFFF, 0xFF6C8CFF,
            "Technically he is the ice and dust."),
        t("nebula", "Nebula Trail", Rarity.EPIC, Style.SMOKE, 0xFFB98CFF, 0xFF2A1A5E,
            "Somewhere in there, stars are being made."),
        t("aurora", "Aurora Trail", Rarity.EPIC, Style.RIBBON, 0xFF7BE3FF, 0xFF35D6B0,
            "The sky, doing its best work."),
        t("void", "Void Trail", Rarity.LEGENDARY, Style.ORB, 0xFFCBB7FF, 0xFF07040F,
            "Where he has been, briefly, there is nothing."),
        t("moon", "Moonlight Trail", Rarity.RARE, Style.ORB, 0xFFF2F7FF, 0xFF8FA8D6,
            "Quiet, silver, and a little smug."),

        // ---- neon and tech ---------------------------------------------------------------------
        t("neon", "Neon Trail", Rarity.RARE, Style.RIBBON, 0xFFFF3CAC, 0xFF7CF6FF,
            "Downtown, after the rain."),
        t("pixel", "Pixel Trail", Rarity.COMMON, Style.PIXEL, 0xFF7CF6FF, 0xFF2A3A8E,
            "Rendered at a resolution he is comfortable with."),
        t("glitch", "Glitch Trail", Rarity.EPIC, Style.PIXEL, 0xFFFF2D9B, 0xFF00E5FF,
            "Something is wrong with the dog. Nobody minds."),
        t("circuit", "Circuit Trail", Rarity.RARE, Style.BOLT, 0xFF7FFFB0, 0xFF0E5E6E,
            "Traces, laid down at a run."),
        t("laser", "Laser Trail", Rarity.RARE, Style.BOLT, 0xFFFF6B6B, 0xFF8E1010,
            "Do not look directly at the good boy."),
        t("hologram", "Hologram Trail", Rarity.EPIC, Style.PIXEL, 0xFF8FE3FF, 0xFF2E5AA8,
            "A projection of a dog who is also right there."),

        // ---- treats and nonsense ----------------------------------------------------------------
        t("bone", "Bone Trail", Rarity.RARE, Style.PIXEL, 0xFFFFF2D8, 0xFFC8B08A,
            "He is not losing them. He is leaving them."),
        t("paw", "Paw Print Trail", Rarity.COMMON, Style.PAW, 0xFF4A4A52, 0xFF15161C,
            "Proof of a good boy, in sequence."),
        t("bubblegum", "Bubblegum Trail", Rarity.COMMON, Style.BUBBLE, 0xFFFFC9DE, 0xFFE0709C,
            "Strawberry. Obviously strawberry."),
        t("confetti", "Confetti Trail", Rarity.RARE, Style.PIXEL, 0xFFFFD24A, 0xFF4A9BFF,
            "Every jump is the good news."),
        t("heart", "Heart Trail", Rarity.RARE, Style.HEART, 0xFFFF7A9E, 0xFFC02A54,
            "He loves this. He loves all of this."),
        t("music", "Music Trail", Rarity.EPIC, Style.NOTE, 0xFFFFE9A8, 0xFF8E62D6,
            "Something with a good beat to bounce to.")
    )

    val byId: Map<String, Trail> = ALL.associateBy { it.id }

    val count: Int get() = ALL.size

    fun of(id: String): Trail? = byId[id]

    /** Prize-machine weights: the rarer it is, the less often the machine hands it over. */
    fun weightOf(trail: Trail): Int = when (trail.rarity) {
        Rarity.COMMON -> 60
        Rarity.RARE -> 27
        Rarity.EPIC -> 10
        else -> 3
    }

    fun rarityName(rarity: Int): String = when (rarity) {
        Rarity.COMMON -> "COMMON"
        Rarity.RARE -> "RARE"
        Rarity.EPIC -> "EPIC"
        else -> "LEGENDARY"
    }
}
