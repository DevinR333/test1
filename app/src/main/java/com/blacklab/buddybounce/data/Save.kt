package com.blacklab.buddybounce.data

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject

/**
 * Everything the game remembers between launches, backed by a single SharedPreferences file.
 *
 * COIN RULE: coins are banked **only** when a run finishes, and that write is a synchronous
 * commit of one atomic edit (see [bankRun]). Coins picked up during a run live in [World] and
 * nowhere else, so killing the app mid-run loses that run's coins and cannot duplicate them.
 * Everything already banked survives an impulsive swipe-away, because it is on disk before
 * the game-over screen is even drawn.
 */
class Save(ctx: Context) {

    data class ScoreEntry(val name: String, val score: Int, val whenMs: Long)

    private val prefs: SharedPreferences =
        ctx.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    /** Fire-and-forget: fine for settings, never used for currency or progress. */
    private inline fun editAsync(block: (SharedPreferences.Editor) -> Unit) {
        val e = prefs.edit()
        block(e)
        e.apply()
    }

    /** Blocks until the value is on disk. Used for anything the player would be upset to lose. */
    private inline fun editSync(block: (SharedPreferences.Editor) -> Unit) {
        val e = prefs.edit()
        block(e)
        e.commit()
    }

    // ---- identity -------------------------------------------------------------------------

    var playerName: String
        get() = prefs.getString(KEY_NAME, "") ?: ""
        set(value) = editSync { it.putString(KEY_NAME, sanitizeName(value)) }

    val hasName: Boolean get() = playerName.isNotEmpty()

    // ---- currency & progress --------------------------------------------------------------

    /** The banked total. Only [bankRun] and [spendCoins] ever change it. */
    val coins: Int get() = prefs.getInt(KEY_COINS, 0)

    val bestScore: Int get() = prefs.getInt(KEY_BEST, 0)
    val totalRuns: Int get() = prefs.getInt(KEY_RUNS, 0)
    val totalCoinsEarned: Int get() = prefs.getInt(KEY_COINS_EARNED, 0)

    var highestBiome: Int
        get() = prefs.getInt(KEY_BIOME, 0)
        set(value) = editAsync { it.putInt(KEY_BIOME, maxOf(value, prefs.getInt(KEY_BIOME, 0))) }

    /**
     * Banks a finished run: coins, score, leaderboard and run count, in one committed edit.
     * @return the 0-based rank the run landed at, or -1 if it missed the table.
     */
    fun bankRun(score: Int, coinsEarned: Int): Int {
        val earned = coinsEarned.coerceAtLeast(0)
        val table = scores().toMutableList()
        val stamp = System.currentTimeMillis()
        table.add(ScoreEntry(playerName.ifEmpty { "BUDDY" }, score, stamp))
        table.sortWith(compareByDescending<ScoreEntry> { it.score }.thenBy { it.whenMs })
        while (table.size > MAX_SCORES) table.removeAt(table.size - 1)

        editSync { e ->
            e.putInt(KEY_COINS, coins + earned)
            e.putInt(KEY_COINS_EARNED, totalCoinsEarned + earned)
            e.putInt(KEY_RUNS, totalRuns + 1)
            if (score > bestScore) e.putInt(KEY_BEST, score)
            e.putString(KEY_SCORES, encodeScores(table))
        }

        for (i in table.indices) {
            if (table[i].score == score && table[i].whenMs == stamp) return i
        }
        return -1
    }

    fun spendCoins(amount: Int): Boolean {
        if (amount <= 0 || coins < amount) return false
        editSync { it.putInt(KEY_COINS, coins - amount) }
        return true
    }

    /** Refunds from the machine (duplicates). Committed for the same reason as everything else. */
    fun grantCoins(amount: Int) {
        if (amount <= 0) return
        editSync {
            it.putInt(KEY_COINS, coins + amount)
            it.putInt(KEY_COINS_EARNED, totalCoinsEarned + amount)
        }
    }

    // ---- leaderboard ----------------------------------------------------------------------

    fun scores(): List<ScoreEntry> {
        val raw = prefs.getString(KEY_SCORES, null) ?: return emptyList()
        return try {
            val arr = JSONArray(raw)
            val out = ArrayList<ScoreEntry>(arr.length())
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                out.add(ScoreEntry(o.optString("n", "BUDDY"), o.optInt("s", 0), o.optLong("t", 0L)))
            }
            out
        } catch (t: Throwable) {
            emptyList()
        }
    }

    private fun encodeScores(list: List<ScoreEntry>): String {
        val arr = JSONArray()
        for (e in list) {
            val o = JSONObject()
            o.put("n", e.name)
            o.put("s", e.score)
            o.put("t", e.whenMs)
            arr.put(o)
        }
        return arr.toString()
    }

    // ---- wardrobe -------------------------------------------------------------------------

    fun ownedOutfits(): MutableSet<String> {
        val stored = prefs.getStringSet(KEY_OWNED, null)
        val set = HashSet<String>()
        if (stored != null) set.addAll(stored)
        set.add(Outfits.DEFAULT_ID) // Buddy always owns his own collar
        return set
    }

    fun owns(id: String): Boolean = id == Outfits.DEFAULT_ID || ownedOutfits().contains(id)

    fun unlock(id: String) {
        val set = ownedOutfits()
        set.add(id)
        editSync { it.putStringSet(KEY_OWNED, set) }
    }

    var equippedOutfit: String
        get() {
            val id = prefs.getString(KEY_EQUIPPED, Outfits.DEFAULT_ID) ?: Outfits.DEFAULT_ID
            return if (owns(id) && Outfits.byId(id) != null) id else Outfits.DEFAULT_ID
        }
        set(value) {
            if (owns(value) && Outfits.byId(value) != null) {
                editAsync { it.putString(KEY_EQUIPPED, value) }
            }
        }

    // ---- scenes ---------------------------------------------------------------------------

    fun ownedScenes(): MutableSet<String> {
        val stored = prefs.getStringSet(KEY_SCENES, null)
        val set = HashSet<String>()
        if (stored != null) set.addAll(stored)
        set.add(SCENE_DEFAULT)
        return set
    }

    fun ownsScene(id: String): Boolean = id == SCENE_DEFAULT || ownedScenes().contains(id)

    fun unlockScene(id: String) {
        val set = ownedScenes()
        set.add(id)
        editSync { it.putStringSet(KEY_SCENES, set) }
    }

    var selectedScene: String
        get() {
            val id = prefs.getString(KEY_SCENE_PICK, SCENE_DEFAULT) ?: SCENE_DEFAULT
            return if (ownsScene(id)) id else SCENE_DEFAULT
        }
        set(value) {
            if (ownsScene(value)) editSync { it.putString(KEY_SCENE_PICK, value) }
        }

    // ---- consumable power-ups ---------------------------------------------------------------

    fun powerupCount(id: String): Int = prefs.getInt(KEY_POWERUP_PREFIX + id, 0)

    fun grantPowerup(id: String, count: Int = 1) {
        if (count <= 0) return
        editSync { it.putInt(KEY_POWERUP_PREFIX + id, powerupCount(id) + count) }
    }

    /** Spends one. Returns false (and changes nothing) if the shelf is empty. */
    fun consumePowerup(id: String): Boolean {
        val have = powerupCount(id)
        if (have <= 0) return false
        editSync { it.putInt(KEY_POWERUP_PREFIX + id, have - 1) }
        return true
    }

    fun totalPowerups(): Int {
        var n = 0
        for (p in Powerups.ALL) n += powerupCount(p.id)
        return n
    }

    // ---- settings -------------------------------------------------------------------------

    var landscape: Boolean
        get() = prefs.getBoolean(KEY_LANDSCAPE, false)
        set(value) = editAsync { it.putBoolean(KEY_LANDSCAPE, value) }

    /** 0 = tilt only, 1 = touch gauge only, 2 = both at once. */
    var controlMode: Int
        get() = prefs.getInt(KEY_CONTROL, CONTROL_BOTH).coerceIn(0, 2)
        set(value) = editAsync { it.putInt(KEY_CONTROL, value.coerceIn(0, 2)) }

    var tiltSensitivity: Float
        get() = prefs.getFloat(KEY_TILT_SENS, 1.0f).coerceIn(0.5f, 1.8f)
        set(value) = editAsync { it.putFloat(KEY_TILT_SENS, value.coerceIn(0.5f, 1.8f)) }

    /** Neutral tilt captured by "recalibrate", in m/s^2 along the screen's horizontal axis. */
    var tiltCalibration: Float
        get() = prefs.getFloat(KEY_TILT_CAL, 0f).coerceIn(-6f, 6f)
        set(value) = editAsync { it.putFloat(KEY_TILT_CAL, value.coerceIn(-6f, 6f)) }

    var invertTilt: Boolean
        get() = prefs.getBoolean(KEY_TILT_INVERT, false)
        set(value) = editAsync { it.putBoolean(KEY_TILT_INVERT, value) }

    var soundOn: Boolean
        get() = prefs.getBoolean(KEY_SOUND, true)
        set(value) = editAsync { it.putBoolean(KEY_SOUND, value) }

    var hapticsOn: Boolean
        get() = prefs.getBoolean(KEY_HAPTICS, true)
        set(value) = editAsync { it.putBoolean(KEY_HAPTICS, value) }

    /** Called when the app is going away, so nothing is left in flight. */
    fun flush() {
        editSync { it.putLong(KEY_LAST_SEEN, System.currentTimeMillis()) }
    }

    companion object {
        const val FILE = "buddy_bounce_save"
        const val MAX_SCORES = 10
        const val CONTROL_TILT = 0
        const val CONTROL_TOUCH = 1
        const val CONTROL_BOTH = 2
        const val SCENE_DEFAULT = "yard"

        private const val KEY_NAME = "name"
        private const val KEY_COINS = "coins"
        private const val KEY_BEST = "best"
        private const val KEY_RUNS = "runs"
        private const val KEY_COINS_EARNED = "coinsEarned"
        private const val KEY_BIOME = "topBiome"
        private const val KEY_SCORES = "scores"
        private const val KEY_OWNED = "owned"
        private const val KEY_EQUIPPED = "equipped"
        private const val KEY_SCENES = "scenesOwned"
        private const val KEY_SCENE_PICK = "scenePick"
        private const val KEY_POWERUP_PREFIX = "pu_"
        private const val KEY_LANDSCAPE = "landscape"
        private const val KEY_CONTROL = "control"
        private const val KEY_TILT_SENS = "tiltSens"
        private const val KEY_TILT_CAL = "tiltCal"
        private const val KEY_TILT_INVERT = "tiltInvert"
        private const val KEY_SOUND = "sound"
        private const val KEY_HAPTICS = "haptics"
        private const val KEY_LAST_SEEN = "lastSeen"

        fun sanitizeName(raw: String): String {
            val trimmed = raw.trim()
            val sb = StringBuilder()
            for (ch in trimmed) {
                if (sb.length >= 12) break
                if (ch.isLetterOrDigit() || ch == ' ' || ch == '-' || ch == '_' || ch == '\'') sb.append(ch)
            }
            return sb.toString().trim()
        }
    }
}
