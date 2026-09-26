import android.graphics.Rec
import com.blacklab.buddybounce.data.Outfits
import com.blacklab.buddybounce.render.Art
import com.blacklab.buddybounce.render.BuddyArt
import com.blacklab.buddybounce.render.Pose
import java.io.File

/**
 * Buddy, big, on a plain background, so the way he is drawn can be LOOKED at.
 *
 * Counting draw calls says nothing about whether a picture reads: the two-headed skin passed
 * every check while looking like one head with a smudge behind it. This drives the real
 * BuddyArt against the recording canvas and turns the geometry into a picture.
 *
 * Compiled against the RECORDING stubs, alongside tools/backdrop/BackdropDump.kt for its SVG
 * emitter. Args: <out.svg> [outfit ids...]
 */
private const val CELL_W = 460f
private const val CELL_H = 520f

private fun cell(art: BuddyArt, outfit: String, pose: Pose, ox: Float, oy: Float, label: String): String {
    Rec.clear()
    art.draw(android.graphics.Canvas(), CELL_W * 0.44f, CELL_H - 90f, 1.55f, pose, outfit, 0xFF8FA8C8.toInt())
    val defs = StringBuilder()
    val body = opsToSvg(defs, CELL_W, CELL_H)
    val sb = StringBuilder()
    sb.append("<g transform=\"translate(%.0f,%.0f)\">".format(ox, oy))
    sb.append("<defs>$defs</defs>")
    sb.append("<rect width=\"$CELL_W\" height=\"$CELL_H\" fill=\"#E9EDF2\"/>")
    // a ground line, so a head hanging below the chest is obvious
    sb.append("<line x1=\"0\" y1=\"%.0f\" x2=\"$CELL_W\" y2=\"%.0f\" stroke=\"#B9C2CE\" stroke-width=\"3\"/>"
        .format(CELL_H - 90f, CELL_H - 90f))
    sb.append(body)
    sb.append("<rect width=\"$CELL_W\" height=\"$CELL_H\" fill=\"none\" stroke=\"#2A2F3A\" stroke-width=\"3\"/>")
    sb.append("<text x=\"12\" y=\"28\" font-family=\"monospace\" font-size=\"20\" fill=\"#2A2F3A\">$label</text>")
    sb.append("</g>")
    return sb.toString()
}

fun main(args: Array<String>) {
    val out = File(if (args.isNotEmpty()) args[0] else "buddy-shot.svg")
    val ids = if (args.size > 1) args.drop(1) else
        listOf(Outfits.DEFAULT_ID, "bandana", Outfits.TWO_HEAD_ID, Outfits.CERBERUS_ID)
    val art = BuddyArt(Art(1f))

    // four poses across: still, mid-rise, landing, and turned the other way
    val poses = listOf<Pair<String, Pose>>(
        "still" to Pose().reset(),
        "rising" to Pose().reset().also { it.squash = 0.8f; it.earFlap = 0.7f; it.mouth = 0.6f },
        "landing" to Pose().reset().also { it.squash = -0.7f; it.lean = 0.5f },
        "facing left" to Pose().reset().also { it.facing = -1f }
    )

    val totalW = CELL_W * poses.size
    val totalH = CELL_H * ids.size
    val sb = StringBuilder()
    sb.append("<svg xmlns=\"http://www.w3.org/2000/svg\" viewBox=\"0 0 %.0f %.0f\" width=\"%.0f\">"
        .format(totalW, totalH, totalW))
    for ((r, id) in ids.withIndex()) {
        for ((k, pp) in poses.withIndex()) {
            pp.second.time = 1.3f
            sb.append(cell(art, id, pp.second, CELL_W * k, CELL_H * r, "$id / ${pp.first}"))
        }
    }
    sb.append("</svg>")
    out.writeText(sb.toString())
    println("${out.path}  ${ids.size} outfits x ${poses.size} poses")
}
