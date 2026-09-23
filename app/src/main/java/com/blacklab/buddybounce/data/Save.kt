package com.blacklab.buddybounce.data

import android.content.Context
import android.content.SharedPreferences
import com.blacklab.buddybounce.game.Tuning
import com.blacklab.buddybounce.render.Scenes
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

    init {
        // An earlier build handed out Good Boy Eternal the moment Heaven opened. Halos are the
        // only thing that has ever paid for it, so owning it below the price can only be that
        // old mistake - take it back, and let it be re-earned properly.
        if (prefs.getInt(KEY_HALOS, 0) < Tuning.HALOS_FOR_GHOST) {
            val owned = prefs.getStringSet(KEY_OWNED, null)
            if (owned != null && owned.contains(Outfits.HEAVEN_ONLY_ID)) {
                val fixed = HashSet<String>(owned)
                fixed.remove(Outfits.HEAVEN_ONLY_ID)
                val e = prefs.edit()
                e.putStringSet(KEY_OWNED, fixed)
                if (prefs.getString(KEY_EQUIPPED, null) == Outfits.HEAVEN_ONLY_ID) {
                    e.putString(KEY_EQUIPPED, Outfits.DEFAULT_ID)
                }
                e.commit()
            }
        }
        // And the other half of the same hole: anything the halo total has already paid for but
        // an earlier build never handed over - the Glory Beam, for every save that reached 500
        // by a route the old crossing test could not see.
        settleHalos()
    }

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
     *
     * Coins are banked the instant a run ends, so closing the app impulsively can never lose
     * them. That matters for the Second Life power-up, which reopens a run that was already
     * banked: passing [replacingStamp] from the earlier banking makes this call REPLACE that
     * partial entry rather than adding a second one, and skips the run counter, so continuing
     * leaves one run in the table with the full height on it - not two fragments.
     *
     * @param coinsEarned coins to add. On a continuation this is the DELTA since the last
     *   banking, because the earlier part is already in the purse.
     * @return the 0-based rank the run landed at, or -1 if it missed the table.
     */
    fun bankRun(score: Int, coinsEarned: Int, replacingStamp: Long = 0L): Int {
        val earned = coinsEarned.coerceAtLeast(0)
        val table = scores().toMutableList()
        if (replacingStamp != 0L) table.removeAll { it.whenMs == replacingStamp }
        val stamp = System.currentTimeMillis()
        table.add(ScoreEntry(playerName.ifEmpty { "BUDDY" }, score, stamp))
        table.sortWith(compareByDescending<ScoreEntry> { it.score }.thenBy { it.whenMs })
        while (table.size > MAX_SCORES) table.removeAt(table.size - 1)

        editSync { e ->
            e.putInt(KEY_COINS, coins + earned)
            e.putInt(KEY_COINS_EARNED, totalCoinsEarned + earned)
            // A continuation is the same run carrying on, so it must not be counted twice.
            if (replacingStamp == 0L) e.putInt(KEY_RUNS, totalRuns + 1)
            if (score > bestScore) e.putInt(KEY_BEST, score)
            e.putString(KEY_SCORES, encodeScores(table))
        }

        lastBankedStamp = stamp
        for (i in table.indices) {
            if (table[i].score == score && table[i].whenMs == stamp) return i
        }
        return -1
    }

    /** Timestamp of the entry the last [bankRun] wrote, for a continuation to replace. */
    var lastBankedStamp = 0L
        private set

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

    // ---- trails -----------------------------------------------------------------------------

    fun ownedTrails(): MutableSet<String> {
        val stored = prefs.getStringSet(KEY_TRAILS, null)
        val set = HashSet<String>()
        if (stored != null) set.addAll(stored)
        set.add(Trails.NONE_ID)
        return set
    }

    fun ownsTrail(id: String): Boolean = id == Trails.NONE_ID || ownedTrails().contains(id)

    /** How many real trails are unlocked - "none" is not a collectable. */
    fun trailCount(): Int {
        var n = 0
        for (t in Trails.ALL) if (ownedTrails().contains(t.id)) n++
        return n
    }

    fun unlockTrail(id: String) {
        val set = ownedTrails()
        set.add(id)
        editSync { it.putStringSet(KEY_TRAILS, set) }
    }

    var equippedTrail: String
        get() {
            val id = prefs.getString(KEY_TRAIL_PICK, Trails.NONE_ID) ?: Trails.NONE_ID
            return if (ownsTrail(id)) id else Trails.NONE_ID
        }
        set(value) {
            if (ownsTrail(value)) editAsync { it.putString(KEY_TRAIL_PICK, value) }
        }

    // ---- Heaven: halos, and the look they buy -------------------------------------------------

    val halos: Int get() = prefs.getInt(KEY_HALOS, 0)

    /**
     * Banked with the rest of a run, and once a thousand have been collected the ghost look is
     * unlocked for good - at which point it becomes a toggle in the wardrobe that works in every
     * world, not just Heaven, and Good Boy Eternal joins the wardrobe alongside it.
     *
     * A thousand halos is the ONLY way to earn either. Reaching Heaven is not enough, and neither
     * is a Second Life - that one only lends the look for the rest of the run it saved.
     */
    fun addHalos(n: Int) {
        if (n <= 0) return
        editSync { it.putInt(KEY_HALOS, halos + n) }
        settleHalos()
    }

    /**
     * Grants whatever the current halo total has paid for.
     *
     * Deliberately absolute - "do you have enough" - and not "did this payment cross the line".
     * The crossing test looked right and was wrong: anyone who arrived above a threshold by any
     * route other than stepping over it never got the reward at all. The test back door does
     * exactly that (it writes the total straight in), so priming to 999 and collecting one halo
     * paid out the outfit and silently skipped the trail, which is what went missing. A restored
     * save, or any future way of granting halos in bulk, would have hit the same hole.
     *
     * Safe to call at any time: everything here checks what is already owned first.
     */
    fun settleHalos() {
        val total = halos
        if (total >= Tuning.HALOS_FOR_GLORY && !ownsTrail(Trails.HEAVEN_ONLY_ID)) {
            unlockTrail(Trails.HEAVEN_ONLY_ID)
        }
        if (total >= Tuning.HALOS_FOR_GHOST && !owns(Outfits.HEAVEN_ONLY_ID)) {
            unlock(Outfits.HEAVEN_ONLY_ID)
        }
    }

    /** True once the halo total has paid for it, whether or not it has been collected yet. */
    fun gloryEarned(): Boolean = halos >= Tuning.HALOS_FOR_GLORY

    /** Has the player been asked how they want to steer? Asked once, on the first menu. */
    var controlsAsked: Boolean
        get() = prefs.getBoolean(KEY_CONTROLS_ASKED, false)
        set(value) = editSync { it.putBoolean(KEY_CONTROLS_ASKED, value) }

    /** Has the Heaven reveal already been shown? Kept so it plays exactly once, ever. */
    var heavenAnnounced: Boolean
        get() = prefs.getBoolean(KEY_HEAVEN_SEEN, false)
        set(value) = editSync { it.putBoolean(KEY_HEAVEN_SEEN, value) }

    /**
     * Can the blessed look be worn on any outfit, from the wardrobe toggle?
     *
     * Owning Good Boy Eternal IS the unlock - the toggle is that outfit's look lent to every
     * other one, so it cannot arrive before the outfit does. It used to be a separate flag set
     * by the halo count, which meant the two could drift apart (and did, for any save that
     * reached 1000 halos by a route that skipped the grant).
     */
    val ghostUnlocked: Boolean get() = owns(Outfits.HEAVEN_ONLY_ID)

    /**
     * Testing back door: parks the halo count one short of the threshold and winds back
     * everything crossing it grants, so the unlock can be watched happening rather than taken on
     * trust. A single halo in Heaven does the rest. Re-runnable, deliberately - the outfit it
     * takes back is one pickup away again.
     */
    fun primeHalosForTest() {
        editSync {
            it.putInt(KEY_HALOS, (Tuning.HALOS_FOR_GHOST - 1).coerceAtLeast(0))
            it.putBoolean(KEY_GHOST_ON, false)
        }
        val set = ownedOutfits()
        if (set.remove(Outfits.HEAVEN_ONLY_ID)) editSync { it.putStringSet(KEY_OWNED, set) }
        val trails = ownedTrails()
        if (trails.remove(Trails.HEAVEN_ONLY_ID)) editSync { it.putStringSet(KEY_TRAILS, trails) }
        if (prefs.getString(KEY_TRAIL_PICK, null) == Trails.HEAVEN_ONLY_ID) {
            editSync { it.putString(KEY_TRAIL_PICK, Trails.NONE_ID) }
        }
        if (prefs.getString(KEY_EQUIPPED, null) == Outfits.HEAVEN_ONLY_ID) {
            editSync { it.putString(KEY_EQUIPPED, Outfits.DEFAULT_ID) }
        }
    }

    /** Testing back door: prize-machine pulls cost nothing. */
    var freeSpins: Boolean
        get() = prefs.getBoolean(KEY_FREE_SPINS, false)
        set(value) = editSync { it.putBoolean(KEY_FREE_SPINS, value) }

    /** The wardrobe toggle. Only meaningful once [ghostUnlocked]; Heaven forces it on anyway. */
    var ghostEnabled: Boolean
        get() = ghostUnlocked && prefs.getBoolean(KEY_GHOST_ON, false)
        set(value) = editAsync { it.putBoolean(KEY_GHOST_ON, value) }

    /**
     * Has the player unlocked literally everything that Heaven waits on?
     *
     * Deliberately excludes Heaven itself (it would gate itself) and the Heaven-only outfit
     * (which sits BEHIND Heaven, so requiring it would make the world impossible to reach).
     */
    fun hasUnlockedEverything(): Boolean {
        for (o in Outfits.ALL) {
            // Heaven's own outfit sits behind Heaven, and the developer skin is not a
            // collectable at all - neither can be required to open the world.
            if (o.id == Outfits.HEAVEN_ONLY_ID || o.id == Outfits.DEV_ID) continue
            if (!owns(o.id)) return false
        }
        for (t in Trails.collectable) if (!ownsTrail(t.id)) return false
        for (sc in Scenes.unlockable) if (!ownsScene(sc.id)) return false
        return true
    }

    /** Called whenever something is unlocked; opens Heaven the moment the set is complete. */
    fun refreshHeaven(): Boolean {
        if (ownsScene(Scenes.HEAVEN_ID)) return false
        if (!hasUnlockedEverything()) return false
        unlockScene(Scenes.HEAVEN_ID)
        // The world only. Good Boy Eternal is earned inside it, a thousand halos at a time.
        return true
    }

    // ---- consumable power-ups ---------------------------------------------------------------

    /**
     * Testing back door: every power-up, always, and spending one costs nothing.
     *
     * Kept as a flag rather than a huge stored count so it cannot be whittled away by play and
     * cannot leave a save with an absurd number in it once it is turned off again.
     */
    var infinitePowerups: Boolean
        get() = prefs.getBoolean(KEY_INFINITE_POWERUPS, false)
        set(value) = editSync { it.putBoolean(KEY_INFINITE_POWERUPS, value) }

    fun powerupCount(id: String): Int =
        if (infinitePowerups) Tuning.POWERUP_MAX else prefs.getInt(KEY_POWERUP_PREFIX + id, 0)

    /**
     * Adds power-ups, up to the shelf limit.
     *
     * @return how many did NOT fit, so the caller can pay for them some other way.
     */
    fun grantPowerupCapped(id: String, count: Int = 1): Int {
        if (count <= 0) return 0
        val have = powerupCount(id)
        val room = (Tuning.POWERUP_MAX - have).coerceAtLeast(0)
        val taken = minOf(room, count)
        if (taken > 0) editSync { it.putInt(KEY_POWERUP_PREFIX + id, have + taken) }
        return count - taken
    }

    fun grantPowerup(id: String, count: Int = 1) {
        if (count <= 0) return
        editSync { it.putInt(KEY_POWERUP_PREFIX + id, powerupCount(id) + count) }
    }

    /** Spends one. Returns false (and changes nothing) if the shelf is empty. */
    fun consumePowerup(id: String): Boolean {
        // The back door hands them out without ever spending one, so the stored count is left
        // exactly as the player earned it and comes back untouched when the flag goes off.
        if (infinitePowerups) return true
        val have = powerupCount(id)
        if (have <= 0) return false
        editSync { it.putInt(KEY_POWERUP_PREFIX + id, have - 1) }
        return true
    }

    fun totalPowerups(): Int {
        var n = 0
        // Only the ones the picker can offer - a shelf holding nothing but Second Lives should
        // not make the game stop and ask what you want to use before a run.
        for (p in Powerups.preRunChoices) n += powerupCount(p.id)
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

    var musicOn: Boolean
        get() = prefs.getBoolean(KEY_MUSIC, true)
        set(value) = editAsync { it.putBoolean(KEY_MUSIC, value) }

    /** 0..1, scaling each mix independently of its on/off switch. */
    var musicVolume: Float
        get() = prefs.getFloat(KEY_MUSIC_VOL, 0.7f)
        set(value) = editAsync { it.putFloat(KEY_MUSIC_VOL, value.coerceIn(0f, 1f)) }

    var sfxVolume: Float
        get() = prefs.getFloat(KEY_SFX_VOL, 1f)
        set(value) = editAsync { it.putFloat(KEY_SFX_VOL, value.coerceIn(0f, 1f)) }

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
        private const val KEY_TRAILS = "trailsOwned"
        private const val KEY_TRAIL_PICK = "trailPick"
        private const val KEY_HALOS = "halos"
        private const val KEY_HEAVEN_SEEN = "heavenAnnounced"
        private const val KEY_CONTROLS_ASKED = "controlsAsked"
        private const val KEY_INFINITE_POWERUPS = "infinitePowerups"
        private const val KEY_GHOST_ON = "ghostOn"
        private const val KEY_FREE_SPINS = "freeSpins"
        private const val KEY_POWERUP_PREFIX = "pu_"
        private const val KEY_LANDSCAPE = "landscape"
        private const val KEY_CONTROL = "control"
        private const val KEY_TILT_SENS = "tiltSens"
        private const val KEY_TILT_CAL = "tiltCal"
        private const val KEY_TILT_INVERT = "tiltInvert"
        private const val KEY_SOUND = "sound"
        private const val KEY_HAPTICS = "haptics"
        private const val KEY_MUSIC = "music"
        private const val KEY_MUSIC_VOL = "musicVol"
        private const val KEY_SFX_VOL = "sfxVol"
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
