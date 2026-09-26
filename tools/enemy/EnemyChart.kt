import android.graphics.Rec
import com.blacklab.buddybounce.game.EnemyKind
import com.blacklab.buddybounce.render.Art
import com.blacklab.buddybounce.render.EnemyArt
import com.blacklab.buddybounce.game.EnemyBox
import com.blacklab.buddybounce.render.Palettes
import com.blacklab.buddybounce.render.Scenes
import java.io.File

/**
 * Every enemy in the game, laid out by the world and band that uses it.
 *
 * Four roles - the small one, the flier, the standing hazard and the rift - dressed differently
 * by each set of creatures, and one set (the seabirds) belonging to a single band rather than a
 * world. This draws all of them with the real EnemyArt so they can be looked at side by side.
 *
 * Args: <out.svg>
 */
private const val CELL_W = 300f
private const val CELL_H = 240f
private val ROLES = intArrayOf(EnemyKind.BEE, EnemyKind.CROW, EnemyKind.STORM, EnemyKind.RIFT)
private val ROLE_NAME = arrayOf("small", "flier", "hazard", "rift")

fun main(args: Array<String>) {
    val out = File(if (args.isNotEmpty()) args[0] else "enemies.svg")
    val art = EnemyArt(Art(1f))
    Palettes.current = Scenes.ALL[0]

    // who uses each set of creatures
    val label = arrayOfNulls<String>(EnemyBox.ROWS)
    for (sc in Scenes.ALL) if (label[sc.fauna] == null) label[sc.fauna] = sc.name
    for (sc in Scenes.ALL) for (b in sc.bands) if (b.fauna >= 0) label[b.fauna] = "${sc.name} / ${b.name}"

    val totalW = CELL_W * ROLES.size + 240f
    val totalH = CELL_H * EnemyBox.ROWS + 90f
    val sb = StringBuilder()
    sb.append("<svg xmlns=\"http://www.w3.org/2000/svg\" viewBox=\"0 0 %.0f %.0f\" width=\"%.0f\">"
        .format(totalW, totalH, totalW))
    sb.append("<rect width=\"100%\" height=\"100%\" fill=\"#EEF1F5\"/>")
    for (k in ROLES.indices) {
        sb.append("<text x=\"%.0f\" y=\"58\" font-family=\"monospace\" font-size=\"26\" fill=\"#2A2F3A\">%s</text>"
            .format(240f + CELL_W * k + 10f, ROLE_NAME[k]))
    }

    for (fauna in 0 until EnemyBox.ROWS) {
        val oy = 90f + CELL_H * fauna
        sb.append("<text x=\"14\" y=\"%.0f\" font-family=\"monospace\" font-size=\"24\" fill=\"#2A2F3A\">%s</text>"
            .format(oy + CELL_H * 0.5f, label[fauna] ?: "fauna $fauna"))
        for ((k, kind) in ROLES.withIndex()) {
            Rec.clear()
            val c = android.graphics.Canvas()
            c.translate(240f + CELL_W * k + CELL_W * 0.5f, oy + CELL_H * 0.5f)
            c.scale(0.8f, 0.8f)
            art.draw(c, kind, fauna, 0.45f, 0f, 1f, 1f)
            val defs = StringBuilder()
            val body = opsToSvg(defs, CELL_W, CELL_H)
            sb.append("<g><defs>$defs</defs>")
            sb.append("<rect x=\"%.0f\" y=\"%.0f\" width=\"$CELL_W\" height=\"$CELL_H\" fill=\"#FFFFFF\" stroke=\"#C6CED8\"/>"
                .format(240f + CELL_W * k, oy))
            sb.append(body).append("</g>")
        }
    }
    sb.append("</svg>")
    out.writeText(sb.toString())
    println("${out.path}  ${EnemyBox.ROWS} sets x ${ROLES.size} roles")
}
