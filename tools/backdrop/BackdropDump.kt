import android.graphics.Rec
import com.blacklab.buddybounce.game.Tuning
import com.blacklab.buddybounce.render.Art
import com.blacklab.buddybounce.render.Backdrop
import com.blacklab.buddybounce.render.Scenes
import com.blacklab.buddybounce.render.Palettes
import java.io.File

/**
 * Writes every world's every band out as an SVG, so the backdrop can be LOOKED at.
 *
 * The counting probe in BackdropAudit says whether a band puts marks in each third of the
 * screen. It cannot say whether those marks are the right picture - the jungle's root floor was
 * drawing the treetop canopy and counted perfectly well. This drives the real Backdrop against
 * the recording canvas and turns the recorded geometry into a picture.
 *
 * Run: see tools/backdrop/README.md. Output: one SVG per world, five bands side by side.
 */
internal const val W = 900f
internal const val VIEW = Tuning.VIEW_H

internal fun hex(c: Int): String = "#%06X".format(c and 0xFFFFFF)
internal fun alphaOf(c: Int): Float = ((c ushr 24) and 0xFF) / 255f

internal var gradId = 0

/** Turns a recorded gradient into an SVG <defs> entry and gives back its id. */
internal fun gradDef(sh: android.graphics.Shader, alpha: Float, defs: StringBuilder): String {
    val id = "g${gradId++}"
    val stops = StringBuilder()
    for (i in sh.colors.indices) {
        val off = sh.stops?.getOrNull(i) ?: (if (sh.colors.size == 1) 0f else i / (sh.colors.size - 1f))
        val c = sh.colors[i]
        stops.append("<stop offset=\"%.3f\" stop-color=\"%s\" stop-opacity=\"%.3f\"/>"
            .format(off, hex(c), alphaOf(c) * alpha))
    }
    if (sh.radial) {
        defs.append("<radialGradient id=\"$id\" gradientUnits=\"userSpaceOnUse\" cx=\"%.1f\" cy=\"%.1f\" r=\"%.1f\">"
            .format(sh.x0, sh.y0, sh.radius)).append(stops).append("</radialGradient>")
    } else {
        defs.append("<linearGradient id=\"$id\" gradientUnits=\"userSpaceOnUse\" x1=\"%.1f\" y1=\"%.1f\" x2=\"%.1f\" y2=\"%.1f\">"
            .format(sh.x0, sh.y0, sh.x1, sh.y1)).append(stops).append("</linearGradient>")
    }
    return id
}

internal fun panel(
    backdrop: Backdrop, biome: Int, camY: Float, ox: Float, blend: Float = 0f
): String {
    Rec.clear()
    // the same band base the game passes: where this biome's stretch of the world starts, with
    // startY at 0. Without it the scenery would not be anchored and the dump would not match.
    val bandBase = (biome * Tuning.BIOME_SPAN - 0.5f) * Tuning.VIEW_H
    backdrop.draw(android.graphics.Canvas(), W, camY, 12.5f, biome, blend, bandBase)
    val defs = StringBuilder()
    val sb = StringBuilder()
    sb.append("<g transform=\"translate(%.0f,0)\">".format(ox))
    val defsAt = sb.length
    sb.append("<clipPath id=\"c$biome-${camY.toInt()}\"><rect width=\"$W\" height=\"$VIEW\"/></clipPath>")
    sb.append("<g clip-path=\"url(#c$biome-${camY.toInt()})\">")
    for (op in Rec.ops) {
        if (op.kind == "color") {
            sb.append("<rect width=\"$W\" height=\"$VIEW\" fill=\"${hex(op.color)}\"/>")
            continue
        }
        val svg = op.svg ?: continue
        if (op.kind == "bitmap") {
            // A blitted bitmap is a BLURRED puff or a radial glow. Drawn as a flat ellipse it
            // looks like a hard disc, which is not what the game shows and led to chasing
            // problems that were only in the dump. A radial fade is a fair likeness.
            val a = alphaOf(op.color)
            if (a <= 0.004f) continue
            val id = "b${gradId++}"
            defs.append("<radialGradient id=\"$id\">")
                .append("<stop offset=\"0.25\" stop-color=\"${hex(op.color)}\" stop-opacity=\"%.3f\"/>".format(a))
                .append("<stop offset=\"0.62\" stop-color=\"${hex(op.color)}\" stop-opacity=\"%.3f\"/>".format(a * 0.5f))
                .append("<stop offset=\"1\" stop-color=\"${hex(op.color)}\" stop-opacity=\"0\"/>")
                .append("</radialGradient>")
            sb.append("<g fill=\"url(#$id)\" stroke=\"none\">").append(svg).append("</g>")
            continue
        }
        val sh = op.shader
        if (sh != null && sh.colors.isNotEmpty()) {
            // a shaded fill: paint it with the real gradient rather than the leftover flat colour
            val id = gradDef(sh, op.shaderAlpha, defs)
            sb.append("<g fill=\"url(#$id)\" stroke=\"none\">").append(svg).append("</g>")
            continue
        }
        val a = alphaOf(op.color)
        if (a <= 0.004f) continue
        val style = if (op.stroke) {
            "fill=\"none\" stroke=\"${hex(op.color)}\" stroke-width=\"%.1f\" stroke-opacity=\"%.3f\" " +
                "stroke-linecap=\"round\" stroke-linejoin=\"round\""
        } else {
            "fill=\"${hex(op.color)}\" fill-opacity=\"%.3f\" stroke=\"none\""
        }
        val attrs = if (op.stroke) style.format(op.strokeWidth, a) else style.format(a)
        sb.append("<g $attrs>").append(svg).append("</g>")
    }
    sb.append("</g>")
    sb.append("<rect width=\"$W\" height=\"$VIEW\" fill=\"none\" stroke=\"#000\" stroke-width=\"4\"/>")
    sb.append("</g>")
    sb.insert(defsAt, "<defs>$defs</defs>")
    return sb.toString()
}

fun main(args: Array<String>) {
    val outDir = File(if (args.isNotEmpty()) args[0] else "backdrop-dump")
    outDir.mkdirs()
    val art = Art(1f)
    val backdrop = Backdrop(art)
    // three heights inside each band, so the repeat seam is visible too
    val steps = floatArrayOf(0.35f, 1.6f, 3.1f)

    for (scene in Scenes.ALL) {
        Palettes.current = scene
        val cols = scene.bands.size
        val rows = steps.size
        val totalW = W * cols + 40f * (cols + 1)
        val totalH = (VIEW + 90f) * rows + 120f
        val sb = StringBuilder()
        sb.append("<svg xmlns=\"http://www.w3.org/2000/svg\" viewBox=\"0 0 %.0f %.0f\" width=\"%.0f\">"
            .format(totalW, totalH, totalW / 4f))
        sb.append("<rect width=\"100%\" height=\"100%\" fill=\"#222\"/>")
        sb.append("<text x=\"40\" y=\"70\" font-size=\"58\" fill=\"#fff\" font-family=\"sans-serif\">" +
            "${scene.name}</text>")
        for (r in steps.indices) {
            val oy = 120f + r * (VIEW + 90f)
            sb.append("<g transform=\"translate(0,%.0f)\">".format(oy))
            for (b in scene.bands.indices) {
                val camY = (b * 4f + steps[r]) * VIEW
                sb.append(panel(backdrop, b, camY, 40f + b * (W + 40f)))
                if (r == 0) {
                    sb.append("<text x=\"%.0f\" y=\"-24\" font-size=\"44\" fill=\"#ddd\" font-family=\"sans-serif\">%s</text>"
                        .format(40f + b * (W + 40f), scene.bands[b].name))
                }
            }
            sb.append("</g>")
        }
        sb.append("</svg>")
        val f = File(outDir, scene.id + ".svg")
        f.writeText(sb.toString())
        println("${f.path}  (${scene.bands.size} bands x ${steps.size} heights)")
    }
}
