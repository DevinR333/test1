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

/**
 * One altitude band. Buddy Bounce cycles through five of these and then repeats with a slight
 * hue drift, so a very long run keeps changing without needing infinite art.
 */
class BiomePalette(
    val name: String,
    val skyTop: Int,
    val skyMid: Int,
    val skyLow: Int,
    val haze: Int,
    val farShape: Int,
    val midShape: Int,
    val nearShape: Int,
    val platTop: Int,
    val platBody: Int,
    val platShade: Int,
    val platAccent: Int,
    val rim: Int,
    val sunColor: Int,
    val starAlpha: Float,
    val cloudAlpha: Float
)

object Palettes {

    val BACKYARD = BiomePalette(
        name = "Backyard",
        skyTop = 0xFF63B8E8.toInt(),
        skyMid = 0xFFA6DCF2.toInt(),
        skyLow = 0xFFE7F5D8.toInt(),
        haze = 0xFFD9F0E4.toInt(),
        farShape = 0xFF7FB77E.toInt(),
        midShape = 0xFF5E9A66.toInt(),
        nearShape = 0xFF3F7A53.toInt(),
        platTop = 0xFFC08A4E.toInt(),
        platBody = 0xFF8E5F35.toInt(),
        platShade = 0xFF6B462A.toInt(),
        platAccent = 0xFF7FC25C.toInt(),
        rim = 0xFFFFE6A8.toInt(),
        sunColor = 0xFFFFF0B8.toInt(),
        starAlpha = 0f,
        cloudAlpha = 0.85f
    )

    val TREETOPS = BiomePalette(
        name = "Treetops",
        skyTop = 0xFF4FA3DE.toInt(),
        skyMid = 0xFF8FD0EC.toInt(),
        skyLow = 0xFFCFEDD6.toInt(),
        haze = 0xFFBDE4CE.toInt(),
        farShape = 0xFF69A97A.toInt(),
        midShape = 0xFF468C5F.toInt(),
        nearShape = 0xFF2E6B48.toInt(),
        platTop = 0xFF8FA95A.toInt(),
        platBody = 0xFF6B7F3E.toInt(),
        platShade = 0xFF4C5C2C.toInt(),
        platAccent = 0xFF9BD96B.toInt(),
        rim = 0xFFE8F5B8.toInt(),
        sunColor = 0xFFFFF4C4.toInt(),
        starAlpha = 0f,
        cloudAlpha = 0.9f
    )

    val CLOUDLINE = BiomePalette(
        name = "Cloudline",
        skyTop = 0xFF2E7FC4.toInt(),
        skyMid = 0xFF6BB6E6.toInt(),
        skyLow = 0xFFC3E7F7.toInt(),
        haze = 0xFFDDF1FB.toInt(),
        farShape = 0xFFFFFFFF.toInt(),
        midShape = 0xFFEAF6FE.toInt(),
        nearShape = 0xFFCFE6F6.toInt(),
        platTop = 0xFFF4FAFF.toInt(),
        platBody = 0xFFCFE2F2.toInt(),
        platShade = 0xFFA7C2DC.toInt(),
        platAccent = 0xFF8FD3F4.toInt(),
        rim = 0xFFFFFFFF.toInt(),
        sunColor = 0xFFFFF7DD.toInt(),
        starAlpha = 0.08f,
        cloudAlpha = 1f
    )

    val AURORA = BiomePalette(
        name = "Aurora",
        skyTop = 0xFF0B1030.toInt(),
        skyMid = 0xFF17264F.toInt(),
        skyLow = 0xFF2A3F72.toInt(),
        haze = 0xFF2B4C74.toInt(),
        farShape = 0xFF35D6B0.toInt(),
        midShape = 0xFF4E8CE8.toInt(),
        nearShape = 0xFF1B2A4E.toInt(),
        platTop = 0xFFBFF3E6.toInt(),
        platBody = 0xFF4FBFA8.toInt(),
        platShade = 0xFF2B7E72.toInt(),
        platAccent = 0xFF7BE3FF.toInt(),
        rim = 0xFFA9FFEA.toInt(),
        sunColor = 0xFFDDE8FF.toInt(),
        starAlpha = 0.85f,
        cloudAlpha = 0.35f
    )

    val SPACE = BiomePalette(
        name = "Orbit",
        skyTop = 0xFF03030C.toInt(),
        skyMid = 0xFF0A0A1C.toInt(),
        skyLow = 0xFF161033.toInt(),
        haze = 0xFF2A1D50.toInt(),
        farShape = 0xFF6C4BCE.toInt(),
        midShape = 0xFF3B2A7A.toInt(),
        nearShape = 0xFF120D28.toInt(),
        platTop = 0xFF8D93AE.toInt(),
        platBody = 0xFF585E7C.toInt(),
        platShade = 0xFF373C52.toInt(),
        platAccent = 0xFFFFB35C.toInt(),
        rim = 0xFFCBD4FF.toInt(),
        sunColor = 0xFFFFFFFF.toInt(),
        starAlpha = 1f,
        cloudAlpha = 0.12f
    )

    val list = listOf(BACKYARD, TREETOPS, CLOUDLINE, AURORA, SPACE)

    fun get(biome: Int): BiomePalette {
        var i = biome % list.size
        if (i < 0) i += list.size
        return list[i]
    }

    /** Band name including the lap number, e.g. "Orbit II". */
    fun label(biome: Int): String {
        val lap = biome / list.size
        val base = get(biome).name
        return if (lap <= 0) base else base + " " + roman(lap + 1)
    }

    private fun roman(n: Int): String = when (n) {
        2 -> "II"; 3 -> "III"; 4 -> "IV"; 5 -> "V"; 6 -> "VI"; 7 -> "VII"; 8 -> "VIII"
        else -> n.toString()
    }
}
