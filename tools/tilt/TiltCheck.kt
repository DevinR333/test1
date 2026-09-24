import com.blacklab.buddybounce.input.TiltMap
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * Checks that the capsules in the prize machine fall the way the phone is actually pointing.
 *
 * The dome used to read one accelerometer axis and derive the other as "whatever is left of one
 * g", which is always positive - so it knew left from right and nothing else, and turning the
 * phone upside down still rolled the capsules toward the bottom of the screen.
 *
 * Sign conventions across four display rotations and two accelerometer axes are not something to
 * eyeball, so this builds each pose from first principles instead. For every display rotation it
 * works out where the screen's own right and down axes point in DEVICE coordinates, puts the
 * phone in a physical pose, computes what the accelerometer would really read in that pose, and
 * asserts that TiltMap resolves it back to the direction gravity is pulling on screen.
 *
 * An accelerometer at rest reports the UP direction in device axes, times one g.
 */
private const val G = TiltMap.G

/** Screen right and screen down, in device axes, for each display rotation. */
private fun screenAxes(rotation: Int): Pair<Pair<Float, Float>, Pair<Float, Float>> {
    // Natural orientation: device +x is screen right, device +y is screen UP, so screen down is
    // -y. Each further rotation step turns the screen a quarter turn within the device, and
    // screen-down is always screen-right turned a quarter turn: (a, b) -> (b, -a).
    val right = when (rotation) {
        TiltMap.ROTATION_90 -> 0f to -1f
        TiltMap.ROTATION_180 -> -1f to 0f
        TiltMap.ROTATION_270 -> 0f to 1f
        else -> 1f to 0f
    }
    val down = right.second to -right.first
    return right to down
}

private class Pose(val name: String, val sx: Float, val sy: Float, val sz: Float)

/**
 * The poses, given as the direction gravity pulls IN SCREEN TERMS - which is the whole point:
 * whatever way up the phone is, "toward the bottom of the screen" has to mean the same thing to
 * the person holding it. sz is the part of gravity going into or out of the screen, which no
 * amount of on-screen tumbling can represent.
 */
private val POSES = listOf(
    Pose("held upright", 0f, 1f, 0f),
    Pose("upside down", 0f, -1f, 0f),
    Pose("on its right side", 1f, 0f, 0f),
    Pose("on its left side", -1f, 0f, 0f),
    Pose("leaning bottom-right", 0.707f, 0.707f, 0f),
    Pose("leaning top-left", -0.707f, -0.707f, 0f),
    Pose("leaning bottom-left", -0.707f, 0.707f, 0f),
    Pose("leaning top-right", 0.707f, -0.707f, 0f),
    Pose("flat on a table, face up", 0f, 0f, -1f),
    Pose("flat on a table, face down", 0f, 0f, 1f),
    Pose("tipped back 45 degrees", 0f, 0.707f, -0.707f)
)

private var failures = 0

private fun check(what: String, got: Float, want: Float, tol: Float = 0.02f) {
    val ok = abs(got - want) <= tol
    if (!ok) failures++
    println("  ${if (ok) "PASS" else "FAIL"}  %-46s got %+.3f  want %+.3f".format(what, got, want))
}

fun main() {
    val rotations = listOf(
        TiltMap.ROTATION_0 to "ROTATION_0",
        TiltMap.ROTATION_90 to "ROTATION_90",
        TiltMap.ROTATION_180 to "ROTATION_180",
        TiltMap.ROTATION_270 to "ROTATION_270"
    )
    for ((rot, rotName) in rotations) {
        val (right, down) = screenAxes(rot)
        println("--- $rotName ---")
        for (p in POSES) {
            // gravity in device axes: the screen components along the screen's own axes, plus
            // whatever is going through the screen (device +z is out of the screen)
            val gxDev = p.sx * right.first + p.sy * down.first
            val gyDev = p.sx * right.second + p.sy * down.second
            // an accelerometer at rest reads UP, which is the opposite of where gravity pulls
            val ax = -gxDev * G
            val ay = -gyDev * G

            val steer = TiltMap.steer(ax, ay, rot) / G
            val fall = TiltMap.down(ax, ay, rot) / G
            check("${p.name}: rolls sideways", steer, p.sx)
            check("${p.name}: rolls up/down", fall, p.sy)

            // and what the dome does with it: never more than one g of pull, and near nothing
            // when the phone is flat, which is what a horizontal glass ball really does
            var cgx = steer
            var cgy = fall
            val mag = sqrt(cgx * cgx + cgy * cgy)
            if (mag > 1f) { cgx /= mag; cgy /= mag }
            val strength = sqrt(cgx * cgx + cgy * cgy)
            val wantStrength = sqrt(p.sx * p.sx + p.sy * p.sy)
            check("${p.name}: pull strength", strength, wantStrength)
        }
    }

    // The old behaviour, for the record: one axis, the other derived as sqrt(1 - gx^2). Upside
    // down and face down both came out as "falls toward the bottom of the screen".
    println("--- the bug this replaced ---")
    for (p in listOf(POSES[1], POSES[5], POSES[7])) {
        val oldGy = sqrt((1f - p.sx * p.sx).coerceAtLeast(0f))
        println("  %-28s old vertical %+.3f, actual %+.3f  %s"
            .format(p.name, oldGy, p.sy, if (oldGy * p.sy < 0f) "<- fell the wrong way" else ""))
    }

    println()
    println(if (failures == 0) "ALL CHECKS PASSED" else "$failures CHECK(S) FAILED")
    if (failures != 0) kotlin.system.exitProcess(1)
}
