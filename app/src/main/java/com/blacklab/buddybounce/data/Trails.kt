package com.blacklab.buddybounce.data

/**
 * Trails: the ribbon of particles Buddy leaves behind him as he climbs.
 *
 * **Every one of the forty has its own shape.** The first pass built them from fourteen shared
 * draw styles crossed with colour pairs, and it showed - "Flame" and "Ember" were the same mote
 * in different oranges, and the three leafy ones were one oval recoloured three times. A trail
 * you can only tell apart by its tint is not a trail, it is a palette swap.
 *
 * So each entry here names a [Style] used by nothing else, drawn by its own function in
 * `render/TrailArt.kt`: the flame is a licking tongue, the ember is a cracked chunk of char, the
 * leaf has a vein and a serrated edge, the clover has three lobes and a stem. On top of that,
 * a [Motion] profile decides how it moves once it is born - rising, falling, hanging still,
 * streaking along behind him - so two trails that happen to sit near each other in the palette
 * still read differently the moment anything moves.
 *
 * They are deliberately SHORT-lived. A trail is a flourish behind the dog, not a smokescreen
 * over the platforms you are trying to land on, so nothing here lives much past 0.45 s and the
 * emitters are metered by distance travelled rather than by frame.
 */
object Trails {

    /** The equipped-trail id meaning "none". Always owned, always the default. */
    const val NONE_ID = "none"

    /**
     * One per trail - no sharing. [com.blacklab.buddybounce.render.TrailArt] has a function for
     * each, and the names below are the drawing, not a category.
     */
    object Style {
        // fire
        const val FLAME = 0          // a licking tongue with a hot inner core
        const val EMBER = 1          // a cracked chunk of char, glowing through the splits
        const val MAGMA = 2          // a heavy blob that sags and skins over as it cools
        const val SOLAR = 3          // a disc with a rotating corona of spikes
        const val PHOENIX = 4        // a feather whose trailing edge is on fire
        // water
        const val WAVE = 5           // a curling crest with a foam lip
        const val BUBBLE = 6         // a hollow sphere that swells and bursts into fragments
        const val SPLASH = 7         // one droplet plus the satellites it threw off
        const val TIDE = 8           // a flat ripple line that spreads and thins
        const val ABYSS = 9          // a dark sphere ringed by a pulsing bioluminescent band
        // nature
        const val LEAF = 10          // veined, serrated, tumbling end over end
        const val PETAL = 11         // a curved blossom petal with a notched tip
        const val CLOVER = 12        // three lobes and a stem
        const val POLLEN = 13        // a fuzzy mote inside a soft halo
        const val VINE = 14          // a curling tendril
        const val MUD = 15           // an irregular splat with spatter around it
        // weather
        const val SNOW = 16          // six spokes, each with branchlets
        const val FROST = 17         // an angular hexagonal shard, drawn as an outline
        const val STORM = 18         // a small dark cloud with a bolt under it
        const val CLOUD = 19         // three lumps of cumulus
        const val MIST = 20          // a wide horizontal band that stretches as it fades
        // light and space
        const val RAINBOW = 21       // a banded arc, all its colours at once
        const val STAR = 22          // a five-pointed star, spinning
        const val COMET = 23         // a bright head with a tapering tail along travel
        const val NEBULA = 24        // overlapping gas clouds with stars inside them
        const val AURORA = 25        // a hanging curtain with a rippled lower edge
        const val VOID = 26          // a black disc with a bright rim, collapsing inward
        const val MOON = 27          // a crescent
        // neon and tech
        const val NEON = 28          // a glass tube segment with a white-hot core
        const val PIXEL = 29         // a hard square snapped to a grid
        const val GLITCH = 30        // channel-split rectangles that jitter apart
        const val CIRCUIT = 31       // an L-shaped trace with a via at the corner
        const val LASER = 32         // a thin beam with a lens flare at the head
        const val HOLOGRAM = 33      // a panel cut through by scanlines
        // treats and nonsense
        const val BONE = 34          // an actual bone
        const val PAW = 35           // a paw print, pressed where he was
        const val GUM = 36           // a bubble that inflates, then a sticky splat
        const val CONFETTI = 37      // a twisted rectangle of paper
        const val HEART = 38         // two lobes and a point
        const val NOTE = 39          // a quaver

        const val COUNT = 40
    }

    /**
     * How a particle behaves once it exists. The shape says what it is; this says what it does,
     * and it is the difference between snow and sparks made of the same number of pixels.
     */
    object Motion {
        const val RISE = 0       // buoyant - floats up and slows
        const val FALL = 1       // heavy - gravity takes it
        const val DRIFT = 2      // near-neutral, wandering sideways
        const val HANG = 3       // stays exactly where it was dropped
        const val STREAK = 4     // keeps most of Buddy's velocity, lying along it
        const val FLUTTER = 5    // tumbles from side to side on the way down
        const val BURST = 6      // thrown outward hard, then stops dead
    }

    class Trail(
        val id: String,
        val name: String,
        val rarity: Int,
        val style: Int,
        val motion: Int,
        /** The colour a particle is born. */
        val hot: Int,
        /** The colour it fades to before it disappears. */
        val cool: Int,
        /** A third colour, used by the styles that need one (bands, cores, spatter). */
        val accent: Int,
        val blurb: String
    )

    object Rarity {
        const val COMMON = 0
        const val RARE = 1
        const val EPIC = 2
        const val LEGENDARY = 3
    }

    private fun t(
        id: String, name: String, rarity: Int, style: Int, motion: Int,
        hot: Long, cool: Long, accent: Long, blurb: String
    ) = Trail(id, name, rarity, style, motion, hot.toInt(), cool.toInt(), accent.toInt(), blurb)

    val ALL: List<Trail> = listOf(
        // ---- fire and heat ------------------------------------------------------------------
        t("flame", "Flame Trail", Rarity.COMMON, Style.FLAME, Motion.RISE,
            0xFFFFD24A, 0xFFE0330C, 0xFFFFF6DC, "He runs hot. Always has."),
        t("ember", "Ember Trail", Rarity.COMMON, Style.EMBER, Motion.FALL,
            0xFFFF9A3C, 0xFF3A1508, 0xFFFFE07A, "The good bits left over after the fire."),
        t("magma", "Magma Trail", Rarity.RARE, Style.MAGMA, Motion.FALL,
            0xFFFFC14A, 0xFF2B2320, 0xFFFF5E2E, "Please do not step where he has stepped."),
        t("solar", "Solar Trail", Rarity.EPIC, Style.SOLAR, Motion.DRIFT,
            0xFFFFF4C2, 0xFFFF8A1E, 0xFFFFFFFF, "A small sun, following a small dog."),
        t("phoenix", "Phoenix Trail", Rarity.LEGENDARY, Style.PHOENIX, Motion.FLUTTER,
            0xFFFFE9A8, 0xFFE83C0C, 0xFFFFFFFF, "Falls, gets up, falls better."),

        // ---- water --------------------------------------------------------------------------
        t("wave", "Wave Trail", Rarity.COMMON, Style.WAVE, Motion.STREAK,
            0xFF8FE3FF, 0xFF1B6FA8, 0xFFFFFFFF, "A little bit of ocean that follows him around."),
        t("bubble", "Bubble Trail", Rarity.COMMON, Style.BUBBLE, Motion.RISE,
            0xFFE0F7FF, 0xFF64C8E8, 0xFFFFFFFF, "Pop. Pop. Pop."),
        t("splash", "Splash Trail", Rarity.COMMON, Style.SPLASH, Motion.BURST,
            0xFFCFEFFF, 0xFF2E8BA8, 0xFFFFFFFF, "He found a puddle. Of course he did."),
        t("tide", "Tide Trail", Rarity.RARE, Style.TIDE, Motion.STREAK,
            0xFF6FF0D8, 0xFF0E5E6E, 0xFFCFFFF4, "Goes out, comes back, goes out again."),
        t("abyss", "Abyss Trail", Rarity.EPIC, Style.ABYSS, Motion.DRIFT,
            0xFF9BF6D8, 0xFF041E2E, 0xFF5AE0FF, "Lights from somewhere very far down."),

        // ---- nature -------------------------------------------------------------------------
        t("leaf", "Leaf Trail", Rarity.COMMON, Style.LEAF, Motion.FLUTTER,
            0xFFA8E063, 0xFF3F7A53, 0xFF2A5E34, "Autumn, shed one leaf at a time."),
        t("petal", "Petal Trail", Rarity.COMMON, Style.PETAL, Motion.FLUTTER,
            0xFFFFC9DE, 0xFFE0709C, 0xFFFFF0F6, "Blossom season, wherever he goes."),
        t("clover", "Clover Trail", Rarity.RARE, Style.CLOVER, Motion.FALL,
            0xFF7FE07F, 0xFF2E7A3E, 0xFF4FA85E, "Three leaves. Never four. He checked."),
        t("pollen", "Pollen Trail", Rarity.COMMON, Style.POLLEN, Motion.DRIFT,
            0xFFFFE9A8, 0xFFD8B24A, 0xFFFFFBE8, "The reason he sneezes at the top of every climb."),
        t("vine", "Vine Trail", Rarity.RARE, Style.VINE, Motion.HANG,
            0xFF9BE06B, 0xFF2A5E34, 0xFFCFF2A0, "Grows behind him faster than he climbs."),
        t("mud", "Mud Trail", Rarity.COMMON, Style.MUD, Motion.FALL,
            0xFF9A7047, 0xFF4A3220, 0xFF6B4C2E, "Somebody is getting a bath."),

        // ---- weather ------------------------------------------------------------------------
        t("snow", "Snow Trail", Rarity.COMMON, Style.SNOW, Motion.FLUTTER,
            0xFFFFFFFF, 0xFF9FD8F0, 0xFFE8F8FF, "Fresh powder, all the way up."),
        t("frost", "Frost Trail", Rarity.RARE, Style.FROST, Motion.DRIFT,
            0xFFDDF6FF, 0xFF4E9FD0, 0xFFFFFFFF, "The air behind him goes crisp."),
        t("storm", "Storm Trail", Rarity.RARE, Style.STORM, Motion.HANG,
            0xFF4A5170, 0xFF2F3550, 0xFFFFF0A8, "Rolling in, right on his tail."),
        t("cloud", "Cloud Trail", Rarity.COMMON, Style.CLOUD, Motion.DRIFT,
            0xFFFFFFFF, 0xFFBFD4E8, 0xFFF4FAFF, "Soft landings not included."),
        t("mist", "Mist Trail", Rarity.COMMON, Style.MIST, Motion.STREAK,
            0xFFE8F0F8, 0xFF8FA0B8, 0xFFFFFFFF, "Mysterious. Or just damp."),

        // ---- light and space -----------------------------------------------------------------
        t("rainbow", "Rainbow Trail", Rarity.EPIC, Style.RAINBOW, Motion.STREAK,
            0xFFFF4A6E, 0xFF4A9BFF, 0xFFFFD24A, "Every colour, in the wrong order, at speed."),
        t("star", "Star Trail", Rarity.COMMON, Style.STAR, Motion.DRIFT,
            0xFFFFF4C2, 0xFFFFC14A, 0xFFFFFFFF, "He is going where they are."),
        t("comet", "Comet Trail", Rarity.RARE, Style.COMET, Motion.STREAK,
            0xFFFFFFFF, 0xFF6C8CFF, 0xFFCFE0FF, "Technically he is the ice and dust."),
        t("nebula", "Nebula Trail", Rarity.EPIC, Style.NEBULA, Motion.DRIFT,
            0xFFB98CFF, 0xFF2A1A5E, 0xFFFFFFFF, "Somewhere in there, stars are being made."),
        t("aurora", "Aurora Trail", Rarity.EPIC, Style.AURORA, Motion.HANG,
            0xFF7BE3FF, 0xFF35D6B0, 0xFFC9FFF0, "The sky, doing its best work."),
        t("void", "Void Trail", Rarity.LEGENDARY, Style.VOID, Motion.HANG,
            0xFFCBB7FF, 0xFF07040F, 0xFFFFFFFF, "Where he has been, briefly, there is nothing."),
        t("moon", "Moonlight Trail", Rarity.RARE, Style.MOON, Motion.RISE,
            0xFFF2F7FF, 0xFF8FA8D6, 0xFFFFFFFF, "Quiet, silver, and a little smug."),

        // ---- neon and tech ---------------------------------------------------------------------
        t("neon", "Neon Trail", Rarity.RARE, Style.NEON, Motion.STREAK,
            0xFFFF3CAC, 0xFF7CF6FF, 0xFFFFFFFF, "Downtown, after the rain."),
        t("pixel", "Pixel Trail", Rarity.COMMON, Style.PIXEL, Motion.HANG,
            0xFF7CF6FF, 0xFF2A3A8E, 0xFFFFFFFF, "Rendered at a resolution he is comfortable with."),
        t("glitch", "Glitch Trail", Rarity.EPIC, Style.GLITCH, Motion.HANG,
            0xFFFF2D9B, 0xFF00E5FF, 0xFF17181D, "Something is wrong with the dog. Nobody minds."),
        t("circuit", "Circuit Trail", Rarity.RARE, Style.CIRCUIT, Motion.HANG,
            0xFF7FFFB0, 0xFF0E5E6E, 0xFFDFFFE8, "Traces, laid down at a run."),
        t("laser", "Laser Trail", Rarity.RARE, Style.LASER, Motion.STREAK,
            0xFFFF6B6B, 0xFF8E1010, 0xFFFFFFFF, "Do not look directly at the good boy."),
        t("hologram", "Hologram Trail", Rarity.EPIC, Style.HOLOGRAM, Motion.HANG,
            0xFF8FE3FF, 0xFF2E5AA8, 0xFFFFFFFF, "A projection of a dog who is also right there."),

        // ---- treats and nonsense ----------------------------------------------------------------
        t("bone", "Bone Trail", Rarity.RARE, Style.BONE, Motion.FLUTTER,
            0xFFFFF2D8, 0xFFC8B08A, 0xFF9A8460, "He is not losing them. He is leaving them."),
        t("paw", "Paw Print Trail", Rarity.COMMON, Style.PAW, Motion.HANG,
            0xFF4A4A52, 0xFF15161C, 0xFF6B6B75, "Proof of a good boy, in sequence."),
        t("bubblegum", "Bubblegum Trail", Rarity.COMMON, Style.GUM, Motion.RISE,
            0xFFFFC9DE, 0xFFE0709C, 0xFFFFFFFF, "Strawberry. Obviously strawberry."),
        t("confetti", "Confetti Trail", Rarity.RARE, Style.CONFETTI, Motion.FLUTTER,
            0xFFFFD24A, 0xFF4A9BFF, 0xFFFF5E8A, "Every jump is the good news."),
        t("heart", "Heart Trail", Rarity.RARE, Style.HEART, Motion.RISE,
            0xFFFF7A9E, 0xFFC02A54, 0xFFFFD6E2, "He loves this. He loves all of this."),
        t("music", "Music Trail", Rarity.EPIC, Style.NOTE, Motion.RISE,
            0xFFFFE9A8, 0xFF8E62D6, 0xFFFFFFFF, "Something with a good beat to bounce to.")
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
