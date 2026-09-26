import android.graphics.Rec
import com.blacklab.buddybounce.game.Boost
import com.blacklab.buddybounce.game.PlatKind
import com.blacklab.buddybounce.game.Platform
import com.blacklab.buddybounce.game.Tuning
import com.blacklab.buddybounce.render.Art
import com.blacklab.buddybounce.render.EnemyArt
import com.blacklab.buddybounce.render.GameRenderer
import com.blacklab.buddybounce.render.Palettes
import com.blacklab.buddybounce.render.Scenes
import java.io.File

/**
 * Platforms mid-bounce, and the hive with its swarm, drawn by the real renderer.
 *
 * A plank squashes when it is landed on - shorter and a touch wider - and anything inside its
 * design drawn at a fixed offset rather than off that height comes loose as it squashes. That is
 * a line appearing on a platform the moment you touch it, and it is only visible in the squashed
 * frames, which nothing was rendering.
 *
 * Args: <out.svg>
 */
private const val CELL_W = 460f
private const val CELL_H = 260f

private fun cell(body: String, ox: Float, oy: Float, label: String, defs: String): String {
    val sb = StringBuilder()
    sb.append("<g transform=\"translate(%.0f,%.0f)\">".format(ox, oy))
    sb.append("<defs>$defs</defs>")
    sb.append("<rect width=\"$CELL_W\" height=\"$CELL_H\" fill=\"#E9EDF2\"/>")
    sb.append(body)
    sb.append("<rect width=\"$CELL_W\" height=\"$CELL_H\" fill=\"none\" stroke=\"#2A2F3A\" stroke-width=\"2\"/>")
    sb.append("<text x=\"8\" y=\"22\" font-family=\"monospace\" font-size=\"18\" fill=\"#2A2F3A\">$label</text>")
    sb.append("</g>")
    return sb.toString()
}

fun main(args: Array<String>) {
    val out = File(if (args.isNotEmpty()) args[0] else "plat-shot.svg")
    val art = Art(1f)
    val renderer = GameRenderer(art)
    val enemyArt = EnemyArt(art)
    val squashes = floatArrayOf(0f, 0.5f, 1f)

    val rows = Scenes.ALL.size + 1
    val totalW = CELL_W * squashes.size
    val totalH = CELL_H * rows
    val sb = StringBuilder()
    sb.append("<svg xmlns=\"http://www.w3.org/2000/svg\" viewBox=\"0 0 %.0f %.0f\" width=\"%.0f\">"
        .format(totalW, totalH, totalW))

    for ((rI, scene) in Scenes.ALL.withIndex()) {
        Palettes.current = scene
        for ((cI, sq) in squashes.withIndex()) {
            val plat = Platform()
            plat.reset()
            plat.alive = true
            plat.kind = PlatKind.SOLID
            plat.boost = Boost.NONE
            plat.w = 300f
            plat.x = CELL_W * 0.5f
            plat.y = 0f
            plat.hitAnim = sq
            plat.alpha = 1f
            plat.seed = 7
            Rec.clear()
            // viewTop - plat.y is the screen y, so viewTop alone places it
            renderer.drawPlatform(android.graphics.Canvas(), plat, Palettes.get(0), CELL_H * 0.55f)
            val defs = StringBuilder()
            val body = opsToSvg(defs, CELL_W, CELL_H)
            sb.append(cell(body, CELL_W * cI, CELL_H * rI,
                "${scene.name}  squash ${"%.1f".format(sq)}", defs.toString()))
        }
    }

    // the hive and its swarm, at three points of the circuit
    Palettes.current = Scenes.ALL[0]
    for ((cI, t) in floatArrayOf(0f, 0.6f, 1.2f).withIndex()) {
        Rec.clear()
        val c = android.graphics.Canvas()
        c.translate(CELL_W * 0.5f, CELL_H * 0.5f)
        // Overgrown's rift role is the hive and its swarm
        enemyArt.draw(c, com.blacklab.buddybounce.game.EnemyKind.RIFT,
            com.blacklab.buddybounce.render.Fauna.JUNGLE, t, 0f, 1f, 1f)
        val defs = StringBuilder()
        val body = opsToSvg(defs, CELL_W, CELL_H)
        sb.append(cell(body, CELL_W * cI, CELL_H * Scenes.ALL.size,
            "hive swarm  t=${"%.1f".format(t)}", defs.toString()))
    }

    sb.append("</svg>")
    out.writeText(sb.toString())
    println("${out.path}  ${Scenes.ALL.size} worlds x ${squashes.size} squash steps + the hive")
}
