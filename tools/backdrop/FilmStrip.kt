import com.blacklab.buddybounce.render.Art
import com.blacklab.buddybounce.render.Backdrop
import com.blacklab.buddybounce.render.Scenes
import com.blacklab.buddybounce.render.Palettes
import com.blacklab.buddybounce.game.Tuning
import java.io.File

/**
 * One band, many frames, side by side in a single SVG.
 *
 * Rendered and then compared frame to frame this says whether the backdrop POPS as you climb -
 * which is the one thing a still cannot show, and the thing every probe built from the draw
 * calls got wrong, because ink hidden behind something nearer counts in the ops list and counts
 * for nothing on screen.
 *
 * Args: <outDir> [startScreens] [frames] [climbPerFrame, in screens]
 */
fun main(args: Array<String>) {
    val outDir = File(if (args.isNotEmpty()) args[0] else "strip")
    val start = if (args.size > 1) args[1].toFloat() else 0f
    val frames = if (args.size > 2) args[2].toInt() else 14
    val climb = if (args.size > 3) args[3].toFloat() else 0.25f
    outDir.mkdirs()
    val art = Art(1f)
    val backdrop = Backdrop(art)

    for (scene in Scenes.ALL) {
        Palettes.current = scene
        val totalW = W * frames
        val sb = StringBuilder()
        // a fixed on-screen size, so the rasteriser's window matches exactly
        val outW = frames * 180
        sb.append("<svg xmlns=\"http://www.w3.org/2000/svg\" viewBox=\"0 0 %.0f %.0f\" width=\"%d\" height=\"%d\">"
            .format(totalW, VIEW, outW, (180f * VIEW / W).toInt()))
        sb.append("<rect width=\"100%\" height=\"100%\" fill=\"#000\"/>")
        for (f in 0 until frames) {
            // A CONTINUOUS climb, with the band index and the cross-fade worked out exactly the
            // way the game works them out. Sampling one band at blend 0 never sees a transition,
            // and a transition is where a background would most obviously jump.
            val screens = start + f * climb
            val camY = (screens - 0.5f) * VIEW
            sb.append(panel(backdrop, Tuning.biomeIndex(screens), camY, f * W,
                Tuning.biomeBlend(screens)))
        }
        sb.append("</svg>")
        File(outDir, "${scene.id}.svg").writeText(sb.toString())
    }
    println("${Scenes.ALL.size} strips from $start screens, $frames x $climb screens")
}
