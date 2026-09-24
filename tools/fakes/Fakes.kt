import android.content.Context
import android.content.SharedPreferences

// Shared by every headless probe that needs a real Save. Lives on its own so the probes do not
// each carry their own copy.

/** A SharedPreferences that actually stores, so Save can be driven for real. */
class FakePrefs : SharedPreferences {
    val map = HashMap<String, Any?>()
    override fun getString(k: String, d: String?) = map[k] as? String ?: d
    override fun getInt(k: String, d: Int) = map[k] as? Int ?: d
    override fun getLong(k: String, d: Long) = map[k] as? Long ?: d
    override fun getFloat(k: String, d: Float) = map[k] as? Float ?: d
    override fun getBoolean(k: String, d: Boolean) = map[k] as? Boolean ?: d
    @Suppress("UNCHECKED_CAST")
    override fun getStringSet(k: String, d: Set<String>?) = map[k] as? Set<String> ?: d
    override fun edit(): SharedPreferences.Editor = Ed(this)

    class Ed(val p: FakePrefs) : SharedPreferences.Editor {
        val pending = HashMap<String, Any?>()
        override fun putString(k: String, v: String?) = apply { pending[k] = v }
        override fun putInt(k: String, v: Int) = apply { pending[k] = v }
        override fun putLong(k: String, v: Long) = apply { pending[k] = v }
        override fun putFloat(k: String, v: Float) = apply { pending[k] = v }
        override fun putBoolean(k: String, v: Boolean) = apply { pending[k] = v }
        override fun putStringSet(k: String, v: Set<String>?) = apply { pending[k] = v }
        override fun apply() { p.map.putAll(pending) }
        override fun commit(): Boolean { p.map.putAll(pending); return true }
    }
}

class FakeCtx(private val prefs: FakePrefs) : Context() {
    override fun getSharedPreferences(name: String, mode: Int): SharedPreferences = prefs
}

