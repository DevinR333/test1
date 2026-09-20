package com.blacklab.buddybounce.data

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject

/**
 * Everything the game remembers between launches, backed by a single SharedPreferences file.
 * Writes are cheap and rare (end of a run, a purchase, a settings change), so every setter
 * commits immediately rather than batching - losing a legendary outfit to a process death
 * would be unforgivable.
 */
class Save(ctx: Context) {

    data class ScoreEntry(val name: String, val score: Int, val whenMs: Long)

    private val prefs: SharedPreferences =
        ctx.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    // ---- identity -------------------------------------------------------------------------

    var playerName: String
        get() = prefs.getString(KEY_NAME, "") ?: ""
        set(value) = prefs.edit().putString(KEY_NAME, sanitizeName(value)).apply()

    val hasName: Boolean get() = playerName.isNotEmpty()

    // ---- currency & progress --------------------------------------------------------------

    var coins: Int
        get() = prefs.getInt(KEY_COINS, 0)
        set(value) = prefs.edit().putInt(KEY_COINS, value.coerceAtLeast(0)).apply()

    var bestScore: Int
        get() = prefs.getInt(KEY_BEST, 0)
        private set(value) = prefs.edit().putInt(KEY_BEST, value).apply()

    var totalRuns: Int
        get() = prefs.getInt(KEY_RUNS, 0)
        private set(value) = prefs.edit().putInt(KEY_RUNS, value).apply()

    var totalCoinsEarned: Int
        get() = prefs.getInt(KEY_COINS_EARNED, 0)
        private set(value) = prefs.edit().putInt(KEY_COINS_EARNED, value).apply()

    var highestBiome: Int
        get() = prefs.getInt(KEY_BIOME, 0)
        set(value) = prefs.edit().putInt(KEY_BIOME, maxOf(value, prefs.getInt(KEY_BIOME, 0))).apply()

    fun addCoins(amount: Int) {
        if (amount <= 0) return
        coins += amount
        totalCoinsEarned += amount
    }

    fun spendCoins(amount: Int): Boolean {
        if (amount <= 0 || coins < amount) return false
        coins -= amount
        return true
    }

    // ---- leaderboard ----------------------------------------------------------------------

    /** @return the 0-based rank the run landed at, or -1 if it missed the table. */
    fun submitRun(score: Int): Int {
        totalRuns += 1
        if (score > bestScore) bestScore = score
        val table = scores().toMutableList()
        table.add(ScoreEntry(playerName.ifEmpty { "BUDDY" }, score, System.currentTimeMillis()))
        table.sortWith(compareByDescending<ScoreEntry> { it.score }.thenBy { it.whenMs })
        while (table.size > MAX_SCORES) table.removeAt(table.size - 1)
        writeScores(table)
        // The rank of *this* run: first entry with our exact score/timestamp pair.
        for (i in table.indices) {
            if (table[i].score == score && table[i].whenMs > System.currentTimeMillis() - 5000) return i
        }
        return -1
    }

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

    private fun writeScores(list: List<ScoreEntry>) {
        val arr = JSONArray()
        for (e in list) {
            val o = JSONObject()
            o.put("n", e.name)
            o.put("s", e.score)
            o.put("t", e.whenMs)
            arr.put(o)
        }
        prefs.edit().putString(KEY_SCORES, arr.toString()).apply()
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
        prefs.edit().putStringSet(KEY_OWNED, set).apply()
    }

    var equippedOutfit: String
        get() {
            val id = prefs.getString(KEY_EQUIPPED, Outfits.DEFAULT_ID) ?: Outfits.DEFAULT_ID
            return if (owns(id) && Outfits.byId(id) != null) id else Outfits.DEFAULT_ID
        }
        set(value) {
            if (owns(value) && Outfits.byId(value) != null) {
                prefs.edit().putString(KEY_EQUIPPED, value).apply()
            }
        }

    // ---- settings -------------------------------------------------------------------------

    var landscape: Boolean
        get() = prefs.getBoolean(KEY_LANDSCAPE, false)
        set(value) = prefs.edit().putBoolean(KEY_LANDSCAPE, value).apply()

    /** 0 = tilt only, 1 = touch gauge only, 2 = both at once. */
    var controlMode: Int
        get() = prefs.getInt(KEY_CONTROL, CONTROL_BOTH).coerceIn(0, 2)
        set(value) = prefs.edit().putInt(KEY_CONTROL, value.coerceIn(0, 2)).apply()

    var tiltSensitivity: Float
        get() = prefs.getFloat(KEY_TILT_SENS, 1.0f).coerceIn(0.5f, 1.8f)
        set(value) = prefs.edit().putFloat(KEY_TILT_SENS, value.coerceIn(0.5f, 1.8f)).apply()

    /** Neutral tilt captured by "recalibrate", in m/s^2 along the screen's horizontal axis. */
    var tiltCalibration: Float
        get() = prefs.getFloat(KEY_TILT_CAL, 0f).coerceIn(-6f, 6f)
        set(value) = prefs.edit().putFloat(KEY_TILT_CAL, value.coerceIn(-6f, 6f)).apply()

    var invertTilt: Boolean
        get() = prefs.getBoolean(KEY_TILT_INVERT, false)
        set(value) = prefs.edit().putBoolean(KEY_TILT_INVERT, value).apply()

    var soundOn: Boolean
        get() = prefs.getBoolean(KEY_SOUND, true)
        set(value) = prefs.edit().putBoolean(KEY_SOUND, value).apply()

    var hapticsOn: Boolean
        get() = prefs.getBoolean(KEY_HAPTICS, true)
        set(value) = prefs.edit().putBoolean(KEY_HAPTICS, value).apply()

    var showGaugeAlways: Boolean
        get() = prefs.getBoolean(KEY_GAUGE, true)
        set(value) = prefs.edit().putBoolean(KEY_GAUGE, value).apply()

    companion object {
        const val FILE = "buddy_bounce_save"
        const val MAX_SCORES = 10
        const val CONTROL_TILT = 0
        const val CONTROL_TOUCH = 1
        const val CONTROL_BOTH = 2

        private const val KEY_NAME = "name"
        private const val KEY_COINS = "coins"
        private const val KEY_BEST = "best"
        private const val KEY_RUNS = "runs"
        private const val KEY_COINS_EARNED = "coinsEarned"
        private const val KEY_BIOME = "topBiome"
        private const val KEY_SCORES = "scores"
        private const val KEY_OWNED = "owned"
        private const val KEY_EQUIPPED = "equipped"
        private const val KEY_LANDSCAPE = "landscape"
        private const val KEY_CONTROL = "control"
        private const val KEY_TILT_SENS = "tiltSens"
        private const val KEY_TILT_CAL = "tiltCal"
        private const val KEY_TILT_INVERT = "tiltInvert"
        private const val KEY_SOUND = "sound"
        private const val KEY_HAPTICS = "haptics"
        private const val KEY_GAUGE = "gauge"

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
