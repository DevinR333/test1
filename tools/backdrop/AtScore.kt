import android.graphics.Rec
import com.blacklab.buddybounce.game.Flight
import com.blacklab.buddybounce.game.Tuning
import com.blacklab.buddybounce.game.World
import com.blacklab.buddybounce.render.Art
import com.blacklab.buddybounce.render.Backdrop
import com.blacklab.buddybounce.render.Palettes
import com.blacklab.buddybounce.render.Scenes
import java.io.File

/**
 * The backdrop exactly as the game draws it at a given SCORE, not at a guessed camera height.
 *
 * Every other backdrop probe picks its own camY, which is off from the real one by the start
 * height and the camera anchor - about four screens by the time you are a few bands up. So a
 * frame a player photographs cannot be reproduced from a score, and a fault they can see plainly
 * is somewhere the probe never looks. This climbs a REAL World to the score, then draws with the
 * camY, biome and blend that Game.drawWorld would hand over.
 *
 * Args: <out.svg> <sceneId> <score> [more scores...]
 */
private class Silent : World.Events

fun main(args: Array<String>) {
    val out = File(args[0])
    val scene = Scenes.ALL.first { it.id == args[1] }
    Palettes.current = scene
    val scores = args.drop(2).map { it.toInt() }

    val art = Art(1f)
    val backdrop = Backdrop(art)
    val sb = StringBuilder()
    val cellW = W
    val totalW = (cellW + 40f) * scores.size + 40f
    val totalH = VIEW + 160f
    sb.append("<svg xmlns=\"http://www.w3.org/2000/svg\" viewBox=\"0 0 %.0f %.0f\" width=\"%.0f\">"
        .format(totalW, totalH, totalW / 4f))
    sb.append("<rect width=\"100%\" height=\"100%\" fill=\"#222\"/>")

    for ((i, target) in scores.withIndex()) {
        val w = World(cellW, Silent())
        w.reset()
        var f = 0
        while (f < 60 * 900 && w.score < target) {
            w.buddy.flight = Flight.ROCKET
            w.buddy.flightTime = 5f
            w.update(1f / 60f, 0f, false, 0f, false)
            f++
        }
        val camY = w.camY
        val screensNow = w.screensAt(camY + Tuning.VIEW_H * 0.5f)
        val biome = Tuning.biomeIndex(screensNow)
        val blend = Tuning.biomeBlend(screensNow)

        Rec.clear()
        backdrop.draw(android.graphics.Canvas(), cellW, camY, 12.5f, biome, blend)
        val defs = StringBuilder()
        val body = opsToSvg(defs, cellW, VIEW)
        val ox = 40f + i * (cellW + 40f)
        sb.append("<g transform=\"translate(%.0f,100)\">".format(ox))
        sb.append("<defs>$defs</defs>")
        sb.append("<clipPath id=\"k$i\"><rect width=\"$cellW\" height=\"$VIEW\"/></clipPath>")
        sb.append("<g clip-path=\"url(#k$i)\">").append(body).append("</g>")
        sb.append("<rect width=\"$cellW\" height=\"$VIEW\" fill=\"none\" stroke=\"#000\" stroke-width=\"4\"/>")
        sb.append("<text x=\"8\" y=\"-24\" font-size=\"46\" fill=\"#ddd\" font-family=\"monospace\">" +
            "score $target - ${scene.bands[biome % scene.bands.size].name} (band $biome)</text>")
        sb.append("</g>")
        println("score $target -> camY=%.0f biome=$biome blend=%.2f band=${scene.bands[biome % scene.bands.size].name}"
            .format(camY, blend))
    }
    sb.append("</svg>")
    out.writeText(sb.toString())
    println(out.path)
}
