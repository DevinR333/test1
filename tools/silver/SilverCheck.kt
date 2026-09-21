import com.blacklab.buddybounce.game.PickupKind
import com.blacklab.buddybounce.game.Pickup
import com.blacklab.buddybounce.game.World
import com.blacklab.buddybounce.game.Tuning
import java.util.IdentityHashMap

/**
 * How often does a silver coin actually turn up, and is it really absent from Heaven?
 *
 * Pickups are pooled, so the same object is reused - identity alone would undercount. Each run
 * tracks the instances it has already scored.
 */
fun main() {
    for (heaven in listOf(false, true)) {
        val w = World(900f, object : World.Events {})
        var runs = 0
        var runsWithSilver = 0
        var coins = 0
        var collected = 0
        var silver = 0
        repeat(40000) {
            w.reset()
            w.haloMode = heaven
            // Pickups are pooled AND the pool drops dead entries from `items`, so an instance
            // can die and be reused without ever being observed as not-alive. Anything that
            // has left the list is marked dead each frame, so its next appearance counts as the
            // fresh spawn it is - without this the count came out at one coin per run.
            val live = IdentityHashMap<Pickup, Boolean>()
            var sawSilver = false
            var frames = 0
            while (!w.deathSettled && frames < 60 * 240) {
                w.update(1f / 60f, botSteer(w), true, 0f, false)
                frames++
                val present = IdentityHashMap<Pickup, Boolean>()
                for (p in w.pickups.items) {
                    present[p] = true
                    val before = live[p] ?: false
                    if (p.alive && !before) {
                        if (p.kind == PickupKind.COIN || p.kind == PickupKind.BONE) coins++
                        if (p.kind == PickupKind.SILVER) { silver++; sawSilver = true }
                    }
                    live[p] = p.alive
                }
                for (e in live.entries) if (present[e.key] != true) e.setValue(false)
            }
            runs++
            collected += w.runCoins
            if (sawSilver) runsWithSilver++
        }
        val where = if (heaven) "Heaven      " else "normal world"
        println("$where: $silver silver / $coins coins over $runs runs" +
            "  ->  ${if (silver == 0) "never seen" else "one every " + (runs.toDouble() / silver).let { String.format("%.1f", it) } + " runs"}" +
            "  (${coins / runs} placed, ${collected / runs} collected per run)")
    }
    println("configured chance per coin = 1 in ${(1f / Tuning.SILVER_COIN_CHANCE).toInt()}")
}

/** The balance sim's bot, copied so this probe plays as well as that one does. */
private var target: com.blacklab.buddybounce.game.Platform? = null
private var lastVy = 0f

private fun pickTarget(w: World): com.blacklab.buddybounce.game.Platform? {
    val b = w.buddy
    var best: com.blacklab.buddybounce.game.Platform? = null
    var bestScore = Float.MAX_VALUE
    for (p in w.platforms.items) {
        if (!p.alive || p.state != 0) continue
        val dy = p.y - b.y
        if (dy < 30f || dy > 760f) continue
        val dx = kotlin.math.abs(com.blacklab.buddybounce.game.MathX.wrapDelta(p.x, b.x, w.worldW))
        val score = dx * 0.6f + dy
        if (score < bestScore) { bestScore = score; best = p }
    }
    return best
}

private fun botSteer(w: World): Float {
    val b = w.buddy
    if (b.vy > 0f && lastVy <= 0f) target = pickTarget(w)
    lastVy = b.vy
    var t = target
    if (t == null || !t.alive || t.state != 0) { t = pickTarget(w); target = t }
    if (t == null) return 0f
    val dx = com.blacklab.buddybounce.game.MathX.wrapDelta(t.x, b.x, w.worldW)
    return (dx / 45f).coerceIn(-1f, 1f)
}
