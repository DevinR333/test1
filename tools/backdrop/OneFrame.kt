import com.blacklab.buddybounce.render.Art
import com.blacklab.buddybounce.render.Backdrop
import com.blacklab.buddybounce.render.Scenes
import com.blacklab.buddybounce.render.Palettes
import com.blacklab.buddybounce.game.Tuning
import java.io.File

/**
 * One scene, one band, one camera height, at full size. The filmstrips are for comparing
 * frames; this is for looking at a single frame the way a player sees it.
 *
 * Args: <out.svg> <sceneId> <band> <screensIntoBand>
 */
fun main(args: Array<String>) {
    val out = File(args[0])
    val sceneId = args[1]
    val band = args[2].toInt()
    val into = if (args.size > 3) args[3].toFloat() else 2f
    val art = Art(1f)
    val backdrop = Backdrop(art)
    Palettes.current = Scenes.ALL.first { it.id == sceneId }
    val screens = band * Tuning.BIOME_SPAN + into
    val camY = (screens - 0.5f) * VIEW
    val sb = StringBuilder()
    sb.append("<svg xmlns=\"http://www.w3.org/2000/svg\" viewBox=\"0 0 %.0f %.0f\" width=\"900\" height=\"%d\">"
        .format(W, VIEW, (900f * VIEW / W).toInt()))
    sb.append(panel(backdrop, Tuning.biomeIndex(screens), camY, 0f, Tuning.biomeBlend(screens)))
    sb.append("</svg>")
    out.writeText(sb.toString())
    println("${out.path}  $sceneId band $band  +$into screens")
}
