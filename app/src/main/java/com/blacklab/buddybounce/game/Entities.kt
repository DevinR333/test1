package com.blacklab.buddybounce.game

/** Anything that lives in a [Pool]. */
interface Poolable {
    var alive: Boolean
    fun reset()
}

/**
 * Dead-simple object pool. The game loop must not allocate - a GC pause in the middle of a
 * 10,000 point run is the worst possible bug.
 */
class Pool<T : Poolable>(private val factory: () -> T) {
    val items = ArrayList<T>(64)
    private val free = ArrayList<T>(64)

    fun obtain(): T {
        val t = if (free.isEmpty()) factory() else free.removeAt(free.size - 1)
        t.reset()
        t.alive = true
        items.add(t)
        return t
    }

    /** Moves dead items back to the free list. Call once per frame. */
    fun sweep() {
        var i = items.size - 1
        while (i >= 0) {
            val it = items[i]
            if (!it.alive) {
                items.removeAt(i)
                free.add(it)
            }
            i--
        }
    }

    fun clear() {
        for (i in items.indices) free.add(items[i])
        items.clear()
    }
}

object PlatKind {
    const val SOLID = 0
    const val SLIDER = 1
    const val HOVER = 2
    const val CRUMBLE = 3
    const val FRAGILE = 4
}

object Boost {
    const val NONE = 0
    const val SPRING = 1
    const val TRAMPOLINE = 2
}

class Platform : Poolable {
    override var alive: Boolean = false

    var x = 0f
    var y = 0f            // world Y of the platform's top surface
    var w = 0f
    var kind = PlatKind.SOLID
    var boost = Boost.NONE

    var vx = 0f           // sliders
    var baseY = 0f        // hover origin
    var phase = 0f        // hover phase
    var biome = 0
    var seed = 0

    /** 0 = intact, 1 = breaking/crumbling away. */
    var state = 0
    var timer = 0f
    /** Drives the squash animation of the plank, the spring and the trampoline. */
    var hitAnim = 0f
    var boostAnim = 0f
    var alpha = 1f
    var fallVy = 0f
    var tilt = 0f

    val left: Float get() = x - w * 0.5f
    val right: Float get() = x + w * 0.5f

    /** Whether landing on it launches the player at all. */
    val bounces: Boolean get() = kind != PlatKind.FRAGILE && state == 0

    override fun reset() {
        x = 0f; y = 0f; w = 0f
        kind = PlatKind.SOLID; boost = Boost.NONE
        vx = 0f; baseY = 0f; phase = 0f; biome = 0; seed = 0
        state = 0; timer = 0f; hitAnim = 0f; boostAnim = 0f
        alpha = 1f; fallVy = 0f; tilt = 0f
    }
}

object PickupKind {
    const val COIN = 0
    const val BONE = 1
    const val PROPELLER = 2
    const val JETPACK = 3
    const val ROCKET = 4
    const val SHIELD = 5
    const val MAGNET = 6

    fun isCurrency(kind: Int): Boolean = kind == COIN || kind == BONE
}

class Pickup : Poolable {
    override var alive: Boolean = false

    var x = 0f
    var y = 0f
    var kind = PickupKind.COIN
    var t = 0f            // animation time
    var vx = 0f
    var vy = 0f
    var magnetised = false
    /** Power-ups sit on a platform and ride it; coins float free. */
    var carrier: Platform? = null
    var carrierOffsetX = 0f
    var carrierOffsetY = 0f
    var bob = 0f

    val radius: Float
        get() = when (kind) {
            PickupKind.COIN -> 34f
            PickupKind.BONE -> 44f
            else -> 52f
        }

    override fun reset() {
        x = 0f; y = 0f; kind = PickupKind.COIN; t = 0f
        vx = 0f; vy = 0f; magnetised = false
        carrier = null; carrierOffsetX = 0f; carrierOffsetY = 0f; bob = 0f
    }
}

object EnemyKind {
    const val BEE = 0
    const val CROW = 1
    const val STORM = 2
    const val RIFT = 3

    /** Can the player kill it by landing on its head? */
    fun stompable(kind: Int): Boolean = kind == BEE || kind == CROW
}

class Enemy : Poolable {
    override var alive: Boolean = false

    var x = 0f
    var y = 0f
    var kind = EnemyKind.BEE
    var t = 0f
    var vx = 0f
    var baseX = 0f
    var baseY = 0f
    var amp = 0f
    var phase = 0f
    var facing = 1f
    var dying = false
    var dieT = 0f
    var seed = 0

    val halfW: Float
        get() = when (kind) {
            EnemyKind.BEE -> 52f
            EnemyKind.CROW -> 64f
            EnemyKind.STORM -> 86f
            else -> 92f
        }

    val halfH: Float
        get() = when (kind) {
            EnemyKind.BEE -> 44f
            EnemyKind.CROW -> 46f
            EnemyKind.STORM -> 62f
            else -> 92f
        }

    override fun reset() {
        x = 0f; y = 0f; kind = EnemyKind.BEE; t = 0f; vx = 0f
        baseX = 0f; baseY = 0f; amp = 0f; phase = 0f; facing = 1f
        dying = false; dieT = 0f; seed = 0
    }
}

object Flight {
    const val NONE = 0
    const val PROPELLER = 1
    const val JETPACK = 2
    const val ROCKET = 3
}

object DeathCause {
    const val FELL = 0
    const val ENEMY = 1
}
