package com.blacklab.buddybounce.render

import com.blacklab.buddybounce.game.MathX.clamp01

/** ARGB helpers, kept local so the game has no androidx dependency. */
object ColorX {

    fun argb(a: Int, r: Int, g: Int, b: Int): Int =
        ((a and 0xFF) shl 24) or ((r and 0xFF) shl 16) or ((g and 0xFF) shl 8) or (b and 0xFF)

    fun alpha(c: Int): Int = (c ushr 24) and 0xFF
    fun red(c: Int): Int = (c ushr 16) and 0xFF
    fun green(c: Int): Int = (c ushr 8) and 0xFF
    fun blue(c: Int): Int = c and 0xFF

    fun lerp(c0: Int, c1: Int, t: Float): Int {
        val u = clamp01(t)
        val a = (alpha(c0) + (alpha(c1) - alpha(c0)) * u).toInt()
        val r = (red(c0) + (red(c1) - red(c0)) * u).toInt()
        val g = (green(c0) + (green(c1) - green(c0)) * u).toInt()
        val b = (blue(c0) + (blue(c1) - blue(c0)) * u).toInt()
        return argb(a, r, g, b)
    }

    fun withAlpha(c: Int, a: Float): Int = argb((clamp01(a) * 255f).toInt(), red(c), green(c), blue(c))

    fun scaleAlpha(c: Int, f: Float): Int = argb((alpha(c) * clamp01(f)).toInt(), red(c), green(c), blue(c))

    /** Multiplies brightness, keeping alpha. */
    fun shade(c: Int, f: Float): Int {
        val r = (red(c) * f).toInt().coerceIn(0, 255)
        val g = (green(c) * f).toInt().coerceIn(0, 255)
        val b = (blue(c) * f).toInt().coerceIn(0, 255)
        return argb(alpha(c), r, g, b)
    }

    /** Pushes a colour toward white by [t]. */
    fun tint(c: Int, t: Float): Int = lerp(c, 0xFFFFFFFF.toInt(), t)
}

/** Which backdrop flourish a band draws. */
object BandStyle {
    const val HILLS = 0
    const val TREES = 1
    const val CLOUDS = 2
    const val AURORA = 3
    const val NEBULA = 4
    const val KELP = 5
    const val REEF = 6
    const val CITY = 7
    const val PEAKS = 8
    const val LAVA = 9
    /** Snow-laden conifers. Separate from TREES so the frozen world cannot look like the yard. */
    const val PINES = 10
    const val DUNES = 11        // rolling sand with wind-carved crests
    const val CANOPY = 12       // layered jungle leaf with hanging vines
    const val SWEETS = 13       // stacked confectionery
    const val TOMBS = 14        // leaning headstones and bare branches
}

/** How the very bottom of a run is dressed, before the camera leaves it behind. */
object GroundStyle {
    const val YARD = 0
    const val SEABED = 1
    const val STREET = 2
    const val SNOW = 3
    const val ASH = 4
    const val SAND = 5
    const val LOAM = 6
    const val FROSTING = 7
    const val GRAVE = 8
}

/**
 * One altitude band: the sky, the parallax silhouettes, and how platforms are skinned inside it.
 */
class BiomePalette(
    val name: String,
    val style: Int,
    val skyTop: Int,
    val skyMid: Int,
    val skyLow: Int,
    val farShape: Int,
    val midShape: Int,
    val nearShape: Int,
    val platTop: Int,
    val platBody: Int,
    val platShade: Int,
    val platAccent: Int,
    val rim: Int,
    val starAlpha: Float = 0f,
    val cloudAlpha: Float = 0.8f,
    val haze: Int = skyLow,
    val sunColor: Int = rim
)

/**
 * Which set of creatures a scene is populated with. A hazard's *role* never changes - the
 * drifter drifts, the patroller patrols, the two static ones sit there and hurt - but what it
 * looks like belongs to the world you are climbing. Bees over a lawn; pufferfish underwater.
 */
object Fauna {
    const val YARD = 0
    const val OCEAN = 1
    const val NEON = 2
    const val FROST = 3
    const val EMBER = 4
    const val HEAVEN = 5
    const val DESERT = 6
    const val JUNGLE = 7
    const val CANDY = 8
    const val HAUNT = 9
}

/**
 * A scene is a full set of altitude bands - a whole look for a run. The yard is the default;
 * the rest are the rarest thing the prize machine can hand out.
 */
class Scene(
    val id: String,
    val name: String,
    val blurb: String,
    val groundStyle: Int,
    val cardTint: Int,
    val fauna: Int,
    val bands: List<BiomePalette>
)

object Scenes {

    const val DEFAULT_ID = "yard"

    // ---- Backyard Skies -------------------------------------------------------------------

    private val YARD = Scene(
        DEFAULT_ID, "Backyard Skies",
        "Where every good dog starts: the lawn, the treetops, and everything above them.",
        GroundStyle.YARD, 0xFF7FC25C.toInt(), Fauna.YARD,
        listOf(
            BiomePalette(
                "Backyard", BandStyle.HILLS,
                0xFF63B8E8.toInt(), 0xFFA6DCF2.toInt(), 0xFFE7F5D8.toInt(),
                0xFF7FB77E.toInt(), 0xFF5E9A66.toInt(), 0xFF3F7A53.toInt(),
                0xFFC08A4E.toInt(), 0xFF8E5F35.toInt(), 0xFF6B462A.toInt(), 0xFF7FC25C.toInt(),
                0xFFFFE6A8.toInt(), cloudAlpha = 0.85f
            ),
            BiomePalette(
                "Treetops", BandStyle.TREES,
                0xFF4FA3DE.toInt(), 0xFF8FD0EC.toInt(), 0xFFCFEDD6.toInt(),
                0xFF69A97A.toInt(), 0xFF468C5F.toInt(), 0xFF2E6B48.toInt(),
                0xFF8FA95A.toInt(), 0xFF6B7F3E.toInt(), 0xFF4C5C2C.toInt(), 0xFF9BD96B.toInt(),
                0xFFE8F5B8.toInt(), cloudAlpha = 0.9f
            ),
            BiomePalette(
                "Cloudline", BandStyle.CLOUDS,
                0xFF2E7FC4.toInt(), 0xFF6BB6E6.toInt(), 0xFFC3E7F7.toInt(),
                0xFFFFFFFF.toInt(), 0xFFEAF6FE.toInt(), 0xFFCFE6F6.toInt(),
                0xFFF4FAFF.toInt(), 0xFFCFE2F2.toInt(), 0xFFA7C2DC.toInt(), 0xFF8FD3F4.toInt(),
                0xFFFFFFFF.toInt(), starAlpha = 0.08f, cloudAlpha = 1f
            ),
            BiomePalette(
                "Aurora", BandStyle.AURORA,
                0xFF0B1030.toInt(), 0xFF17264F.toInt(), 0xFF2A3F72.toInt(),
                0xFF35D6B0.toInt(), 0xFF4E8CE8.toInt(), 0xFF1B2A4E.toInt(),
                0xFFBFF3E6.toInt(), 0xFF4FBFA8.toInt(), 0xFF2B7E72.toInt(), 0xFF7BE3FF.toInt(),
                0xFFA9FFEA.toInt(), starAlpha = 0.85f, cloudAlpha = 0.35f
            ),
            BiomePalette(
                "Orbit", BandStyle.NEBULA,
                0xFF03030C.toInt(), 0xFF0A0A1C.toInt(), 0xFF161033.toInt(),
                0xFF6C4BCE.toInt(), 0xFF3B2A7A.toInt(), 0xFF120D28.toInt(),
                0xFF8D93AE.toInt(), 0xFF585E7C.toInt(), 0xFF373C52.toInt(), 0xFFFFB35C.toInt(),
                0xFFCBD4FF.toInt(), starAlpha = 1f, cloudAlpha = 0.12f
            )
        )
    )

    // ---- Deep Blue ------------------------------------------------------------------------

    private val OCEAN = Scene(
        "ocean", "Deep Blue",
        "Down on the seabed and all the way up through the reef to open sky.",
        GroundStyle.SEABED, 0xFF2A9BC4.toInt(), Fauna.OCEAN,
        listOf(
            BiomePalette(
                "Seabed", BandStyle.REEF,
                0xFF04203A.toInt(), 0xFF0A3A5C.toInt(), 0xFF14567F.toInt(),
                0xFF2E8BA8.toInt(), 0xFF1E6F8C.toInt(), 0xFF123F55.toInt(),
                0xFFD9C79A.toInt(), 0xFFA89263.toInt(), 0xFF6E6142.toInt(), 0xFFFF9E6B.toInt(),
                0xFF9FE8FF.toInt(), cloudAlpha = 0.25f
            ),
            BiomePalette(
                "Kelp Forest", BandStyle.KELP,
                0xFF0A3A5C.toInt(), 0xFF12608A.toInt(), 0xFF2A87A8.toInt(),
                0xFF2E7F52.toInt(), 0xFF1E6B44.toInt(), 0xFF14452F.toInt(),
                0xFF7FBF8F.toInt(), 0xFF4E8C64.toInt(), 0xFF33603F.toInt(), 0xFFB7F0C0.toInt(),
                0xFFBFFFD8.toInt(), cloudAlpha = 0.3f
            ),
            BiomePalette(
                "Coral Reef", BandStyle.REEF,
                0xFF12608A.toInt(), 0xFF2A9BC4.toInt(), 0xFF7FD4E8.toInt(),
                0xFFFF8FA8.toInt(), 0xFFFFB05C.toInt(), 0xFF6FD6C0.toInt(),
                0xFFFFD9B0.toInt(), 0xFFE8A36B.toInt(), 0xFFA8663F.toInt(), 0xFFFF7FA8.toInt(),
                0xFFFFF0D0.toInt(), cloudAlpha = 0.4f
            ),
            BiomePalette(
                "Sunlit Shallows", BandStyle.CLOUDS,
                0xFF2A9BC4.toInt(), 0xFF7FD4E8.toInt(), 0xFFDFF6FF.toInt(),
                0xFFEAFBFF.toInt(), 0xFFC4EEFA.toInt(), 0xFF9FDCEF.toInt(),
                0xFFFFF4E0.toInt(), 0xFFE0D2B8.toInt(), 0xFFAFA189.toInt(), 0xFF6FD6C0.toInt(),
                0xFFFFFFFF.toInt(), cloudAlpha = 0.9f
            ),
            BiomePalette(
                "Open Sky", BandStyle.CLOUDS,
                0xFF4FA6DC.toInt(), 0xFF9FDDF7.toInt(), 0xFFEAF7FF.toInt(),
                0xFFFFFFFF.toInt(), 0xFFEAF6FE.toInt(), 0xFFCFE6F6.toInt(),
                0xFFF4FAFF.toInt(), 0xFFCFE2F2.toInt(), 0xFFA7C2DC.toInt(), 0xFF8FD3F4.toInt(),
                0xFFFFFFFF.toInt(), cloudAlpha = 1f
            )
        )
    )

    // ---- Neon City ------------------------------------------------------------------------

    private val NEON = Scene(
        "neon", "Neon City",
        "Alleyways, rooftops and a skyline that never switches the lights off.",
        GroundStyle.STREET, 0xFFFF3CAC.toInt(), Fauna.NEON,
        listOf(
            BiomePalette(
                "Back Alley", BandStyle.CITY,
                0xFF0A0A14.toInt(), 0xFF14142A.toInt(), 0xFF221B3A.toInt(),
                0xFF2A2A4A.toInt(), 0xFF1A1A32.toInt(), 0xFF101020.toInt(),
                0xFF4A4A66.toInt(), 0xFF2E2E44.toInt(), 0xFF1A1A28.toInt(), 0xFFFF3CAC.toInt(),
                0xFFFF7AD9.toInt(), starAlpha = 0.12f, cloudAlpha = 0.25f
            ),
            BiomePalette(
                "Rooftops", BandStyle.CITY,
                0xFF140F2E.toInt(), 0xFF2A1B4A.toInt(), 0xFF4A2A6E.toInt(),
                0xFF3A2A6A.toInt(), 0xFF261A4A.toInt(), 0xFF150F2E.toInt(),
                0xFF6E5AA8.toInt(), 0xFF453473.toInt(), 0xFF2A1F4A.toInt(), 0xFF4FE8FF.toInt(),
                0xFF8FF6FF.toInt(), starAlpha = 0.3f, cloudAlpha = 0.3f
            ),
            BiomePalette(
                "Skyline", BandStyle.CITY,
                0xFF1E0F3A.toInt(), 0xFF4A1B6E.toInt(), 0xFF8A2A7E.toInt(),
                0xFFFF3CAC.toInt(), 0xFF7A2A9E.toInt(), 0xFF2A1040.toInt(),
                0xFFBFC6E0.toInt(), 0xFF7A83A8.toInt(), 0xFF454B6E.toInt(), 0xFFFFE07A.toInt(),
                0xFFFFC8F0.toInt(), starAlpha = 0.5f, cloudAlpha = 0.45f
            ),
            BiomePalette(
                "Smog Layer", BandStyle.CLOUDS,
                0xFF2A1040.toInt(), 0xFF5A2060.toInt(), 0xFFA03A6E.toInt(),
                0xFFFF6FC4.toInt(), 0xFFB04FA8.toInt(), 0xFF5A2060.toInt(),
                0xFFE8D0F5.toInt(), 0xFFB08FC8.toInt(), 0xFF6E5288.toInt(), 0xFF4FE8FF.toInt(),
                0xFFFFB0EC.toInt(), starAlpha = 0.6f, cloudAlpha = 0.8f
            ),
            BiomePalette(
                "Cyber Orbit", BandStyle.NEBULA,
                0xFF05030F.toInt(), 0xFF0C0820.toInt(), 0xFF1A0F35.toInt(),
                0xFF4FE8FF.toInt(), 0xFFFF3CAC.toInt(), 0xFF120A28.toInt(),
                0xFF9FA8D0.toInt(), 0xFF5A6390.toInt(), 0xFF333A5E.toInt(), 0xFF4FE8FF.toInt(),
                0xFFCFF6FF.toInt(), starAlpha = 1f, cloudAlpha = 0.1f
            )
        )
    )

    // ---- Frozen Peaks ---------------------------------------------------------------------

    private val FROST = Scene(
        "frost", "Frozen Peaks",
        "Snowfields, pines and ice cliffs, with the northern lights at the top.",
        GroundStyle.SNOW, 0xFF9FE0F5.toInt(), Fauna.FROST,
        listOf(
            BiomePalette(
                "Snowfield", BandStyle.PEAKS,
                0xFF6FAEDC.toInt(), 0xFFBFE0F5.toInt(), 0xFFF0FAFF.toInt(),
                0xFFC8DCEC.toInt(), 0xFFA8C4DC.toInt(), 0xFF8FA8C0.toInt(),
                0xFFFFFFFF.toInt(), 0xFFD8E8F5.toInt(), 0xFFA8BFD4.toInt(), 0xFF7FD4FF.toInt(),
                0xFFFFFFFF.toInt(), cloudAlpha = 0.85f
            ),
            BiomePalette(
                "Pine Woods", BandStyle.PINES,
                0xFF5A93C4.toInt(), 0xFF8FC0E0.toInt(), 0xFFDCEEF7.toInt(),
                0xFF2E5A4A.toInt(), 0xFF1E4038.toInt(), 0xFF16302A.toInt(),
                0xFFE8F4FF.toInt(), 0xFFB8CFE0.toInt(), 0xFF86A0B8.toInt(), 0xFF6FBF9F.toInt(),
                0xFFE8FFFF.toInt(), cloudAlpha = 0.8f
            ),
            BiomePalette(
                "Ice Cliffs", BandStyle.PEAKS,
                0xFF2E6E9E.toInt(), 0xFF5FA8CE.toInt(), 0xFFA8DCEF.toInt(),
                0xFF9FE0F5.toInt(), 0xFF6FB8D8.toInt(), 0xFF3A6E8E.toInt(),
                0xFFDFF8FF.toInt(), 0xFF9FD8EF.toInt(), 0xFF5F93B0.toInt(), 0xFFB0F0FF.toInt(),
                0xFFFFFFFF.toInt(), starAlpha = 0.1f, cloudAlpha = 0.7f
            ),
            BiomePalette(
                "Blizzard", BandStyle.CLOUDS,
                0xFF3A5E80.toInt(), 0xFF7F9FBA.toInt(), 0xFFC0D4E0.toInt(),
                0xFFFFFFFF.toInt(), 0xFFDCE8F0.toInt(), 0xFFB0C4D4.toInt(),
                0xFFF7FCFF.toInt(), 0xFFC8DCE8.toInt(), 0xFF93AABC.toInt(), 0xFFBFEAFF.toInt(),
                0xFFFFFFFF.toInt(), starAlpha = 0.25f, cloudAlpha = 1f
            ),
            BiomePalette(
                "Northern Lights", BandStyle.AURORA,
                0xFF050C22.toInt(), 0xFF0E2046.toInt(), 0xFF1A3060.toInt(),
                0xFF6FFFC4.toInt(), 0xFF8F7FFF.toInt(), 0xFF12203E.toInt(),
                0xFFDFF8FF.toInt(), 0xFF8FC8E8.toInt(), 0xFF4E7A99.toInt(), 0xFF9FFFE0.toInt(),
                0xFFCFFFF0.toInt(), starAlpha = 0.95f, cloudAlpha = 0.2f
            )
        )
    )

    // ---- Emberfall ------------------------------------------------------------------------

    private val EMBER = Scene(
        "ember", "Emberfall",
        "Up out of the magma vents, past the obsidian spires, into the cinder void.",
        GroundStyle.ASH, 0xFFFF7A3C.toInt(), Fauna.EMBER,
        listOf(
            BiomePalette(
                "Magma Vents", BandStyle.LAVA,
                0xFF1C0608.toInt(), 0xFF3A0C0A.toInt(), 0xFF5E1408.toInt(),
                0xFFFF5A1E.toInt(), 0xFFA82A10.toInt(), 0xFF2A0A08.toInt(),
                0xFF6E3A28.toInt(), 0xFF45221A.toInt(), 0xFF2A1410.toInt(), 0xFFFF7A2E.toInt(),
                0xFFFFB37A.toInt(), cloudAlpha = 0.3f
            ),
            BiomePalette(
                "Obsidian Spires", BandStyle.PEAKS,
                0xFF140610.toInt(), 0xFF2A0C1A.toInt(), 0xFF46142A.toInt(),
                0xFF6E2A44.toInt(), 0xFF3A1428.toInt(), 0xFF1A0812.toInt(),
                0xFF4A3A48.toInt(), 0xFF2E2030.toInt(), 0xFF1A1020.toInt(), 0xFFFF4FA8.toInt(),
                0xFFFF9FD0.toInt(), starAlpha = 0.2f, cloudAlpha = 0.35f
            ),
            BiomePalette(
                "Ash Clouds", BandStyle.CLOUDS,
                0xFF241614.toInt(), 0xFF4A302A.toInt(), 0xFF6E4638.toInt(),
                0xFF8A6450.toInt(), 0xFF5E4034.toInt(), 0xFF33211C.toInt(),
                0xFF6E5A50.toInt(), 0xFF443630.toInt(), 0xFF281E1A.toInt(), 0xFFFFA14F.toInt(),
                0xFFFFCFA0.toInt(), starAlpha = 0.3f, cloudAlpha = 0.9f
            ),
            BiomePalette(
                "Ember Sky", BandStyle.NEBULA,
                0xFF180810.toInt(), 0xFF3A1418.toInt(), 0xFF6E2A20.toInt(),
                0xFFFF7A3C.toInt(), 0xFFB03A6E.toInt(), 0xFF2A1018.toInt(),
                0xFF7A5A50.toInt(), 0xFF4E3630.toInt(), 0xFF2E2020.toInt(), 0xFFFFD07A.toInt(),
                0xFFFFC49F.toInt(), starAlpha = 0.7f, cloudAlpha = 0.25f
            ),
            BiomePalette(
                "Cinder Void", BandStyle.NEBULA,
                0xFF06040A.toInt(), 0xFF120818.toInt(), 0xFF1E0C22.toInt(),
                0xFFFF6A2E.toInt(), 0xFF7A2ABF.toInt(), 0xFF120A18.toInt(),
                0xFF8F8296.toInt(), 0xFF5A5162.toInt(), 0xFF342F3C.toInt(), 0xFFFF9A4F.toInt(),
                0xFFFFD4C0.toInt(), starAlpha = 1f, cloudAlpha = 0.1f
            )
        )
    )

    // ---- Heaven ----------------------------------------------------------------------------

    /**
     * The secret world. It is never a prize: it appears once EVERYTHING else is unlocked, and it
     * is the only world that changes Buddy himself - see Coats/Pose.ghost.
     */
    const val HEAVEN_ID = "heaven"

    private val HEAVEN = Scene(
        HEAVEN_ID, "Heaven",
        "Nothing left to unlock, so the clouds opened. Collect halos, not coins.",
        GroundStyle.YARD, 0xFFFFE9A8.toInt(), Fauna.HEAVEN,
        listOf(
            BiomePalette(
                "Gates", BandStyle.CLOUDS,
                0xFFBFD9F2.toInt(), 0xFFDCEAF8.toInt(), 0xFFF6F2E4.toInt(),
                0xFFFFFFFF.toInt(), 0xFFF2F6FC.toInt(), 0xFFDCE6F2.toInt(),
                0xFFFFF6DC.toInt(), 0xFFEBDCB4.toInt(), 0xFFC9B68A.toInt(), 0xFFFFE9A8.toInt(),
                0xFFFFFFFF.toInt(), starAlpha = 0.1f, cloudAlpha = 1f
            ),
            BiomePalette(
                "Cloudgarden", BandStyle.TREES,
                0xFFA9CCEE.toInt(), 0xFFCFE4F6.toInt(), 0xFFEFF6E8.toInt(),
                0xFFBFE6C4.toInt(), 0xFF8FCCA4.toInt(), 0xFF6AA98A.toInt(),
                0xFFFFF4D8.toInt(), 0xFFE2D0A8.toInt(), 0xFFBFA87E.toInt(), 0xFFFFE9A8.toInt(),
                0xFFFFFFFF.toInt(), starAlpha = 0.12f, cloudAlpha = 0.95f
            ),
            BiomePalette(
                "Choir", BandStyle.AURORA,
                0xFF8FB6E4.toInt(), 0xFFBBD6F0.toInt(), 0xFFE6EFFA.toInt(),
                0xFFFFE9A8.toInt(), 0xFFEFD9F2.toInt(), 0xFFC9D8F0.toInt(),
                0xFFFFF8E6.toInt(), 0xFFE8D4A8.toInt(), 0xFFC0A87C.toInt(), 0xFFFFF0C0.toInt(),
                0xFFFFFFFF.toInt(), starAlpha = 0.4f, cloudAlpha = 0.7f
            ),
            BiomePalette(
                "Halo Reach", BandStyle.NEBULA,
                0xFF6E96CE.toInt(), 0xFF9FBEE6.toInt(), 0xFFD6E4F6.toInt(),
                0xFFFFE9A8.toInt(), 0xFFE2C8F0.toInt(), 0xFFB6C6E6.toInt(),
                0xFFFFF6E0.toInt(), 0xFFE0CCA0.toInt(), 0xFFB8A076.toInt(), 0xFFFFDE8A.toInt(),
                0xFFFFFFFF.toInt(), starAlpha = 0.75f, cloudAlpha = 0.45f
            ),
            BiomePalette(
                "The Light", BandStyle.AURORA,
                0xFFF2EEDC.toInt(), 0xFFFAF6E8.toInt(), 0xFFFFFDF4.toInt(),
                0xFFFFE9A8.toInt(), 0xFFFFF4CC.toInt(), 0xFFEDE0BC.toInt(),
                0xFFFFFCF0.toInt(), 0xFFEAD9B0.toInt(), 0xFFC4B084.toInt(), 0xFFFFD87A.toInt(),
                0xFFFFFFFF.toInt(), starAlpha = 1f, cloudAlpha = 0.3f
            )
        )
    )

    // ---- Dust Run ---------------------------------------------------------------------------

    private val DESERT = Scene(
        "desert", "Dust Run",
        "Out of the dunes, up past the canyon rim and the storm, into a sky bleached white.",
        GroundStyle.SAND, 0xFFE8B45C.toInt(), Fauna.DESERT,
        listOf(
            BiomePalette(
                "Dunes", BandStyle.DUNES,
                0xFFE8A54C.toInt(), 0xFFF2C888.toInt(), 0xFFFCEBC4.toInt(),
                0xFFD89A52.toInt(), 0xFFB87C3E.toInt(), 0xFF8E5C2C.toInt(),
                0xFFD8B278.toInt(), 0xFFAE8450.toInt(), 0xFF7A5C34.toInt(), 0xFFFFD98A.toInt(),
                0xFFFFF0C8.toInt(), cloudAlpha = 0.4f
            ),
            BiomePalette(
                "Canyon", BandStyle.PEAKS,
                0xFFD07A44.toInt(), 0xFFE2A268.toInt(), 0xFFF4CE9E.toInt(),
                0xFFA85436.toInt(), 0xFF7E3A28.toInt(), 0xFF56261C.toInt(),
                0xFFC08454.toInt(), 0xFF8E5C36.toInt(), 0xFF5E3C22.toInt(), 0xFFFFB055.toInt(),
                0xFFFFDCA8.toInt(), cloudAlpha = 0.35f
            ),
            BiomePalette(
                "Sandstorm", BandStyle.CLOUDS,
                0xFFC69A5E.toInt(), 0xFFDCB77E.toInt(), 0xFFF0D8A8.toInt(),
                0xFFE8CC96.toInt(), 0xFFC4A068.toInt(), 0xFF9E7A46.toInt(),
                0xFFE0C08E.toInt(), 0xFFAE8A56.toInt(), 0xFF7E6236.toInt(), 0xFFFFE0A0.toInt(),
                0xFFFFF4D8.toInt(), cloudAlpha = 1f
            ),
            BiomePalette(
                "Mirage", BandStyle.HILLS,
                0xFF7FB6D8.toInt(), 0xFFAFD4E8.toInt(), 0xFFE4E6D0.toInt(),
                0xFFC8CC96.toInt(), 0xFF9AA870.toInt(), 0xFF6E7E50.toInt(),
                0xFFE8D8A8.toInt(), 0xFFB89C68.toInt(), 0xFF867040.toInt(), 0xFFFFE28E.toInt(),
                0xFFFFF6D0.toInt(), starAlpha = 0.15f, cloudAlpha = 0.5f
            ),
            BiomePalette(
                "White Sky", BandStyle.NEBULA,
                0xFFDCE8F4.toInt(), 0xFFEEF4FA.toInt(), 0xFFFDFCF6.toInt(),
                0xFFE8D8B8.toInt(), 0xFFC6B48E.toInt(), 0xFF9E8C68.toInt(),
                0xFFF2E6C8.toInt(), 0xFFC8B48C.toInt(), 0xFF968260.toInt(), 0xFFFFCC66.toInt(),
                0xFFFFFFFF.toInt(), starAlpha = 0.5f, cloudAlpha = 0.2f
            )
        )
    )

    // ---- Overgrown -------------------------------------------------------------------------

    private val JUNGLE = Scene(
        "jungle", "Overgrown",
        "Up through the roots and the canopy to the mist above the treetops.",
        GroundStyle.LOAM, 0xFF6ABE5C.toInt(), Fauna.JUNGLE,
        listOf(
            BiomePalette(
                "Root Floor", BandStyle.CANOPY,
                0xFF16301C.toInt(), 0xFF244A2C.toInt(), 0xFF396A40.toInt(),
                0xFF2E6B38.toInt(), 0xFF1E4A28.toInt(), 0xFF132E1A.toInt(),
                0xFF7A5A34.toInt(), 0xFF56401F.toInt(), 0xFF342612.toInt(), 0xFF8FE060.toInt(),
                0xFFCFF2A0.toInt(), cloudAlpha = 0.3f
            ),
            BiomePalette(
                "Understorey", BandStyle.CANOPY,
                0xFF1E4426.toInt(), 0xFF2F6636.toInt(), 0xFF4D8C4A.toInt(),
                0xFF418A44.toInt(), 0xFF2A6030.toInt(), 0xFF1A3C1E.toInt(),
                0xFF6E8A3E.toInt(), 0xFF4C6428.toInt(), 0xFF2E3E18.toInt(), 0xFFA8E863.toInt(),
                0xFFDCF8B0.toInt(), cloudAlpha = 0.45f
            ),
            BiomePalette(
                "Canopy", BandStyle.TREES,
                0xFF3C7E4E.toInt(), 0xFF5FA463.toInt(), 0xFF96C87E.toInt(),
                0xFF68B45E.toInt(), 0xFF43863E.toInt(), 0xFF2A5C28.toInt(),
                0xFF8FA84E.toInt(), 0xFF647C30.toInt(), 0xFF3E4E1C.toInt(), 0xFFC4F06A.toInt(),
                0xFFE8FCC0.toInt(), cloudAlpha = 0.7f
            ),
            BiomePalette(
                "Flowerline", BandStyle.HILLS,
                0xFF6FA8D0.toInt(), 0xFF9CC8E2.toInt(), 0xFFD8EEDC.toInt(),
                0xFFE070A8.toInt(), 0xFFB04E84.toInt(), 0xFF7E3660.toInt(),
                0xFFA8C468.toInt(), 0xFF7A9444.toInt(), 0xFF4E6428.toInt(), 0xFFFF9CC8.toInt(),
                0xFFFFD8EC.toInt(), cloudAlpha = 0.8f
            ),
            BiomePalette(
                "Mistline", BandStyle.CLOUDS,
                0xFF8FBCCE.toInt(), 0xFFBCD8E4.toInt(), 0xFFE8F2F0.toInt(),
                0xFFDCEAE4.toInt(), 0xFFB4CCC4.toInt(), 0xFF88A49C.toInt(),
                0xFFD8E4D0.toInt(), 0xFFA8B89E.toInt(), 0xFF76866C.toInt(), 0xFF9FE8C0.toInt(),
                0xFFE4FFF2.toInt(), starAlpha = 0.2f, cloudAlpha = 1f
            )
        )
    )

    // ---- Sugar Rush -------------------------------------------------------------------------

    private val CANDY = Scene(
        "candy", "Sugar Rush",
        "Gingerbread, gumdrops and a sky made of spun sugar. Do not lick the platforms.",
        GroundStyle.FROSTING, 0xFFFF8FC4.toInt(), Fauna.CANDY,
        listOf(
            BiomePalette(
                "Bakery Floor", BandStyle.SWEETS,
                0xFFFFC9DE.toInt(), 0xFFFFE0EC.toInt(), 0xFFFFF4F8.toInt(),
                0xFFE8A05C.toInt(), 0xFFC47C3E.toInt(), 0xFF96582A.toInt(),
                0xFFFFE8C0.toInt(), 0xFFE0BC8A.toInt(), 0xFFAE8C5E.toInt(), 0xFFFF6FA8.toInt(),
                0xFFFFF0F6.toInt(), cloudAlpha = 0.6f
            ),
            BiomePalette(
                "Gumdrop Hills", BandStyle.SWEETS,
                0xFFFFB0D8.toInt(), 0xFFFFD0E6.toInt(), 0xFFFFF0F6.toInt(),
                0xFF8FE0C8.toInt(), 0xFF5EBCA4.toInt(), 0xFF3E8E7C.toInt(),
                0xFFFFDCEE.toInt(), 0xFFE8AECC.toInt(), 0xFFB07E96.toInt(), 0xFFFFE05C.toInt(),
                0xFFFFFAE0.toInt(), cloudAlpha = 0.7f
            ),
            BiomePalette(
                "Liquorice Spires", BandStyle.PEAKS,
                0xFFC98FD8.toInt(), 0xFFE0B4EC.toInt(), 0xFFF6E0F8.toInt(),
                0xFF4A3252.toInt(), 0xFF342438.toInt(), 0xFF201622.toInt(),
                0xFFF2C8E0.toInt(), 0xFFC496B2.toInt(), 0xFF8E6880.toInt(), 0xFFFF9CE0.toInt(),
                0xFFFFE4F6.toInt(), cloudAlpha = 0.5f
            ),
            BiomePalette(
                "Candyfloss", BandStyle.CLOUDS,
                0xFFFFA8D4.toInt(), 0xFFFFC8E4.toInt(), 0xFFFFEAF4.toInt(),
                0xFFFFD8EC.toInt(), 0xFFF2B0D4.toInt(), 0xFFD088AE.toInt(),
                0xFFFFE4F2.toInt(), 0xFFE4B2D0.toInt(), 0xFFB0809C.toInt(), 0xFFFFF07A.toInt(),
                0xFFFFFFFF.toInt(), cloudAlpha = 1f
            ),
            BiomePalette(
                "Sugar Sky", BandStyle.AURORA,
                0xFF6E5AC8.toInt(), 0xFFA890E8.toInt(), 0xFFE0D4F8.toInt(),
                0xFFFF9CD8.toInt(), 0xFF8FE0E8.toInt(), 0xFF4E3E96.toInt(),
                0xFFFFDCF0.toInt(), 0xFFC8A0D8.toInt(), 0xFF8E70A4.toInt(), 0xFFFFE87A.toInt(),
                0xFFFFFFFF.toInt(), starAlpha = 0.8f, cloudAlpha = 0.35f
            )
        )
    )

    // ---- Hollow Hill -------------------------------------------------------------------------

    private val HAUNT = Scene(
        "haunt", "Hollow Hill",
        "Through the graves and the dead wood, up where the moon is far too close.",
        GroundStyle.GRAVE, 0xFF9C7BD8.toInt(), Fauna.HAUNT,
        listOf(
            BiomePalette(
                "Graveyard", BandStyle.TOMBS,
                0xFF150F20.toInt(), 0xFF241A34.toInt(), 0xFF3A2A50.toInt(),
                0xFF4E4060.toInt(), 0xFF342A44.toInt(), 0xFF1E182C.toInt(),
                0xFF5A5468.toInt(), 0xFF3C3848.toInt(), 0xFF24222E.toInt(), 0xFF8FE0A8.toInt(),
                0xFFBFF2CE.toInt(), starAlpha = 0.5f, cloudAlpha = 0.5f
            ),
            BiomePalette(
                "Dead Wood", BandStyle.TOMBS,
                0xFF1A1226.toInt(), 0xFF2C1E3C.toInt(), 0xFF46305C.toInt(),
                0xFF3E3050.toInt(), 0xFF2A2038.toInt(), 0xFF171022.toInt(),
                0xFF6A5442.toInt(), 0xFF463828.toInt(), 0xFF2A2018.toInt(), 0xFFB08FE8.toInt(),
                0xFFD8C0FF.toInt(), starAlpha = 0.6f, cloudAlpha = 0.45f
            ),
            BiomePalette(
                "Fog Bank", BandStyle.CLOUDS,
                0xFF241C34.toInt(), 0xFF3E3450.toInt(), 0xFF60566E.toInt(),
                0xFF6E6480.toInt(), 0xFF4E4660.toInt(), 0xFF322C44.toInt(),
                0xFF6E687C.toInt(), 0xFF4A4658.toInt(), 0xFF2C2A38.toInt(), 0xFF9CE0C0.toInt(),
                0xFFD0F2E0.toInt(), starAlpha = 0.4f, cloudAlpha = 1f
            ),
            BiomePalette(
                "Belfry", BandStyle.PEAKS,
                0xFF1E1830.toInt(), 0xFF342A4E.toInt(), 0xFF50406E.toInt(),
                0xFF2E2642.toInt(), 0xFF1E1830.toInt(), 0xFF120E1E.toInt(),
                0xFF585070.toInt(), 0xFF3A3450.toInt(), 0xFF221E32.toInt(), 0xFFFFD87A.toInt(),
                0xFFFFEFC0.toInt(), starAlpha = 0.85f, cloudAlpha = 0.3f
            ),
            BiomePalette(
                "Moonrise", BandStyle.NEBULA,
                0xFF0C0A18.toInt(), 0xFF181430.toInt(), 0xFF2A2250.toInt(),
                0xFF6A58B0.toInt(), 0xFF3E3276.toInt(), 0xFF1C1640.toInt(),
                0xFF8A84A8.toInt(), 0xFF5A5676.toInt(), 0xFF34324A.toInt(), 0xFFE8E0FF.toInt(),
                0xFFFFFFFF.toInt(), starAlpha = 1f, cloudAlpha = 0.15f
            )
        )
    )

    val ALL: List<Scene> =
        listOf(YARD, OCEAN, NEON, FROST, EMBER, DESERT, JUNGLE, CANDY, HAUNT, HEAVEN)

    private val index: Map<String, Scene> = ALL.associateBy { it.id }

    fun byId(id: String): Scene? = index[id]

    fun of(id: String): Scene = index[id] ?: YARD

    /**
     * Scenes the machine can hand out: everything but the default yard and Heaven. Heaven is
     * earned by completing the collection, never won, so it must never enter the prize pool -
     * and it must not count toward "have I unlocked every world", or it would gate itself.
     */
    val unlockable: List<Scene> = ALL.filter { it.id != DEFAULT_ID && it.id != HEAVEN_ID }
}

/** Band lookup for the scene currently being played. */
object Palettes {

    @Volatile
    var current: Scene = Scenes.of(Scenes.DEFAULT_ID)

    val bandCount: Int get() = current.bands.size

    fun get(biome: Int): BiomePalette {
        val bands = current.bands
        var i = biome % bands.size
        if (i < 0) i += bands.size
        return bands[i]
    }

    /** Band name including the lap number, e.g. "Orbit II". */
    fun label(biome: Int): String {
        val lap = biome / current.bands.size
        val base = get(biome).name
        return if (lap <= 0) base else base + " " + roman(lap + 1)
    }

    private fun roman(n: Int): String = when (n) {
        2 -> "II"; 3 -> "III"; 4 -> "IV"; 5 -> "V"; 6 -> "VI"; 7 -> "VII"; 8 -> "VIII"
        else -> n.toString()
    }
}
