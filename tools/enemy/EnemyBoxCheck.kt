import android.graphics.Rec
import com.blacklab.buddybounce.game.Enemy
import com.blacklab.buddybounce.game.EnemyBox
import com.blacklab.buddybounce.game.EnemyKind
import com.blacklab.buddybounce.game.Tuning
import com.blacklab.buddybounce.render.Art
import com.blacklab.buddybounce.render.EnemyArt
import com.blacklab.buddybounce.render.Palettes
import com.blacklab.buddybounce.render.Scenes

/**
 * Every enemy's hit box against the creature actually drawn inside it.
 *
 * The simulation has four enemy ROLES and one box per role, but each of the ten worlds dresses
 * those roles as its own creature - forty pictures sharing four boxes. Nothing kept them
 * honest, so a box could stand well clear of the thing it belongs to, and the player gets
 * killed by, or kills, empty air.
 *
 * The art is drawn at the enemy's position with no scale, so the ink bounds ARE the creature's
 * extent around its centre. Faint ops are left out on purpose: a glow, an aura, a trailing
 * sparkle is not a body and must not be lethal.
 */
private const val BODY_ALPHA = 0.5f      // below this it is atmosphere, not creature

/**
 * How much of the drawn creature the box takes. Strictly under 1: a hit box that pokes out past
 * the picture kills the player with nothing on screen to blame, which is the one thing it must
 * never do. A little slack inside the outline is invisible and always reads as fair.
 */
private const val INSET = 0.85f
private const val MIN_HALF = 26f         // nothing so small it cannot be landed on

private class Box {
    var x0 = Float.MAX_VALUE; var y0 = Float.MAX_VALUE
    var x1 = -Float.MAX_VALUE; var y1 = -Float.MAX_VALUE
    val valid: Boolean get() = x1 > x0
    val halfW: Float get() = maxOf(-x0, x1)
    val halfH: Float get() = maxOf(-y0, y1)
    fun add(a: Float, b: Float, c: Float, d: Float) {
        if (a < x0) x0 = a; if (b < y0) y0 = b
        if (c > x1) x1 = c; if (d > y1) y1 = d
    }
}

/** The drawn body of one creature, over a whole cycle of its animation. */
private fun measure(art: EnemyArt, kind: Int, fauna: Int): Box {
    val box = Box()
    var t = 0f
    while (t < 4f) {
        var ph = 0f
        while (ph < 6.3f) {
            for (facing in listOf(1f, -1f)) {
                Rec.clear()
                art.draw(android.graphics.Canvas(), kind, fauna, t, ph, facing, 1f)
                for (op in Rec.ops) {
                    if (op.kind == "color" || op.kind == "text") continue
                    val a = ((op.color ushr 24) and 0xFF) / 255f
                    if (a < BODY_ALPHA) continue
                    box.add(op.x0, op.y0, op.x1, op.y1)
                }
            }
            ph += 1.57f
        }
        t += 0.31f
    }
    return box
}

private val KIND_NAME = mapOf(
    EnemyKind.BEE to "bee", EnemyKind.CROW to "crow",
    EnemyKind.STORM to "storm", EnemyKind.RIFT to "rift"
)

fun main() {
    val art = EnemyArt(Art(1f))
    val e = Enemy()
    var failures = 0
    println("Buddy's hurt box is ${Tuning.BUDDY_HURT_HALF_W} x ${Tuning.BUDDY_HURT_HALF_H}")
    println("boxes are measured at ${(INSET * 100).toInt()}% of the drawn body\n")
    println("%-15s %-6s %9s %9s %9s %9s  %s".format(
        "world", "role", "box hw", "art hw", "box hh", "art hh", ""))

    // Rows are indexed by FAUNA, which is not the order the scenes are listed in - Heaven's
    // fauna is 5 while it is the last scene in the menu. Writing the table in scene order put
    // Heaven's boxes on Hollow Hill and Hollow Hill's on Heaven.
    // By FAUNA, not by scene: a band can borrow another world's creatures, and one fauna
    // (the seabirds over Deep Blue's Open Sky) is no scene's default at all.
    val rows = arrayOfNulls<String>(EnemyBox.ROWS)
    val names = arrayOfNulls<String>(EnemyBox.ROWS)
    for (fauna in 0 until EnemyBox.ROWS) {
        val scene = Scenes.ALL.firstOrNull { it.fauna == fauna } ?: Scenes.ALL[0]
        val sceneName = Scenes.ALL.flatMap { sc -> sc.bands.map { sc to it } }
            .firstOrNull { it.second.fauna == fauna }?.let { "${it.first.name}/${it.second.name}" }
            ?: scene.name
        Palettes.current = scene
        val row = StringBuilder()
        for (kind in listOf(EnemyKind.BEE, EnemyKind.CROW, EnemyKind.STORM, EnemyKind.RIFT)) {
            e.kind = kind
            e.fauna = fauna
            val m = measure(art, kind, fauna)
            if (!m.valid) {
                println("%-15s %-6s   NOTHING DRAWN".format(sceneName, KIND_NAME[kind]))
                failures++
                continue
            }
            val wantW = (m.halfW * INSET).coerceAtLeast(MIN_HALF)
            val wantH = (m.halfH * INSET).coerceAtLeast(MIN_HALF)
            row.append("%.0ff, %.0ff, ".format(wantW, wantH))

            // The rule, and the only one that matters: the box may never reach past the picture.
            val outW = e.halfW > m.halfW + 0.5f
            val outH = e.halfH > m.halfH + 0.5f
            // and it must not be so far inside that half the creature is walk-through
            val thinW = e.halfW < m.halfW * 0.6f
            val thinH = e.halfH < m.halfH * 0.6f
            val note = when {
                outW || outH -> { failures++; "BOX REACHES PAST THE CREATURE" }
                thinW || thinH -> { failures++; "box far inside the creature" }
                else -> "ok"
            }
            println("%-15s %-6s %9.0f %9.0f %9.0f %9.0f  %s".format(
                sceneName, KIND_NAME[kind], e.halfW, m.halfW, e.halfH, m.halfH, note))
        }
        rows[fauna] = row.toString().trimEnd(' ', ',')
        names[fauna] = sceneName
    }
    val table = StringBuilder()
    for (i in 0 until EnemyBox.ROWS) {
        table.append("        ").append(rows[i] ?: "").append(",   // ")
            .append(names[i] ?: "?").append("  (fauna $i)\n")
    }

    println()
    if (failures > 0) {
        println("$failures box(es) no longer match the art. The table the probe would write now:")
        println()
        print(table)
        println()
        println("FAIL  paste that into game/EnemyBox.kt")
        System.exit(1)
    }
    println("ALL GOOD - every enemy's box is inside the creature drawn in it, " +
        "across all ${EnemyBox.ROWS} sets of creatures")
}
