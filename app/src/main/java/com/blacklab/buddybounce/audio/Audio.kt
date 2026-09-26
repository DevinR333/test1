package com.blacklab.buddybounce.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import com.blacklab.buddybounce.data.Save
import java.io.File
import java.io.FileOutputStream
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin
import kotlin.random.Random

/**
 * All sound effects are synthesised at first launch and cached as small WAV files, so the APK
 * ships with no audio assets at all and every sound is tuned in code next to the thing that
 * plays it.
 */
class Audio(private val ctx: Context, private val save: Save) {

    companion object {
        const val BOUNCE = 0
        const val SPRING = 1
        const val TRAMPOLINE = 2
        const val COIN = 3
        const val BONE = 4
        const val POWERUP = 5
        const val BREAK = 6
        const val STOMP = 7
        const val HURT = 8
        const val DEATH = 9
        const val TAP = 10
        const val GACHA_SPIN = 11
        const val GACHA_REVEAL = 12
        const val FANFARE = 13
        const val JET = 14
        const val COUNT = 15

        private const val RATE = 22050

        /** Offset into the sample table for the muffled copies. */
        private const val SUBMERGED = COUNT
        private val NAMES = arrayOf(
            "bounce", "spring", "tramp", "coin", "bone", "powerup", "break", "stomp",
            "hurt", "death", "tap", "gacha_spin", "gacha_reveal", "fanfare", "jet"
        )
    }

    private val pool: SoundPool = SoundPool.Builder()
        .setMaxStreams(10)
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_GAME)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
        )
        .build()

    // Two of everything: the sound as it is, and the same sound heard through water. The
    // muffled copy lives at [SUBMERGED] + i.
    private val ids = IntArray(COUNT * 2)
    private val loaded = BooleanArray(COUNT * 2)

    /**
     * True while Buddy is below the surface, which is Deep Blue's four water bands and nothing
     * else - the Open Sky above them is air, and sounds like it. Set by the game each frame.
     */
    @Volatile var submerged = false
    @Volatile private var ready = false
    private var jetStream = 0

    init {
        Thread({
            try {
                prepare()
            } catch (t: Throwable) {
                // Sound is a nice-to-have; never let it take the game down.
                ready = false
            }
        }, "sfx-bake").start()
        pool.setOnLoadCompleteListener { _, sampleId, status ->
            if (status == 0) {
                for (i in ids.indices) if (ids[i] == sampleId) loaded[i] = true
            }
        }
    }

    private fun prepare() {
        val dir = File(ctx.cacheDir, "sfx")
        if (!dir.exists()) dir.mkdirs()
        for (i in 0 until COUNT) {
            val f = File(dir, NAMES[i] + ".wav")
            var dry: FloatArray? = null
            if (!f.exists() || f.length() < 64L) {
                dry = render(i)
                writeWav(f, dry)
            }
            ids[i] = pool.load(f.absolutePath, 1)

            val wet = File(dir, NAMES[i] + "_sub.wav")
            if (!wet.exists() || wet.length() < 64L) writeWav(wet, muffle(dry ?: render(i)))
            ids[SUBMERGED + i] = pool.load(wet.absolutePath, 1)
        }
        ready = true
    }

    fun play(sound: Int, volume: Float = 1f, rate: Float = 1f) {
        if (!save.soundOn || !ready) return
        if (sound < 0 || sound >= COUNT) return
        val i = voiceOf(sound)
        if (!loaded[i]) return
        val v = (volume * save.sfxVolume).coerceIn(0f, 1f)
        pool.play(ids[i], v, v, 1, 0, rate.coerceIn(0.5f, 2f))
    }

    /** The dry sound, or the one heard through water, whichever the player is in. */
    private fun voiceOf(sound: Int): Int =
        if (submerged && loaded[SUBMERGED + sound]) SUBMERGED + sound else sound

    fun startJet(rate: Float) {
        if (!save.soundOn || !ready || jetStream != 0) return
        val i = voiceOf(JET)
        if (!loaded[i]) return
        val jv = (0.55f * save.sfxVolume).coerceIn(0f, 1f)
        jetStream = pool.play(ids[i], jv, jv, 1, -1, rate.coerceIn(0.5f, 2f))
    }

    fun stopJet() {
        if (jetStream != 0) {
            pool.stop(jetStream)
            jetStream = 0
        }
    }

    fun release() {
        stopJet()
        pool.release()
    }

    // -------------------------------------------------------------------------------------
    // synthesis
    // -------------------------------------------------------------------------------------

    private fun render(sound: Int): FloatArray = when (sound) {
        BOUNCE -> sweep(0.13f, 230f, 520f, 0.55f, 26f, square = false)
        SPRING -> vibrato(sweep(0.24f, 300f, 920f, 0.5f, 10f, square = false), 26f, 0.25f)
        TRAMPOLINE -> vibrato(sweep(0.34f, 170f, 780f, 0.55f, 7f, square = false), 17f, 0.35f)
        COIN -> concat(tone(0.06f, 988f, 0.4f, 24f, true), tone(0.10f, 1319f, 0.4f, 16f, true))
        BONE -> concat(
            tone(0.06f, 784f, 0.35f, 22f, true),
            concat(tone(0.06f, 988f, 0.35f, 22f, true), tone(0.14f, 1319f, 0.4f, 12f, true))
        )
        POWERUP -> concat(
            concat(tone(0.055f, 523f, 0.32f, 20f, false), tone(0.055f, 659f, 0.32f, 20f, false)),
            concat(tone(0.055f, 784f, 0.34f, 18f, false), tone(0.18f, 1047f, 0.36f, 9f, false))
        )
        BREAK -> mixDown(noise(0.26f, 15f, 0.5f), sweep(0.26f, 240f, 70f, 0.4f, 14f, square = false))
        STOMP -> mixDown(noise(0.12f, 30f, 0.35f), sweep(0.14f, 420f, 150f, 0.45f, 24f, square = false))
        HURT -> sweep(0.30f, 420f, 130f, 0.45f, 11f, square = true)
        DEATH -> vibrato(sweep(0.75f, 540f, 80f, 0.5f, 4.2f, square = false), 9f, 0.4f)
        TAP -> tone(0.035f, 880f, 0.28f, 45f, false)
        GACHA_SPIN -> ratchet(0.7f)
        GACHA_REVEAL -> chord(0.6f, floatArrayOf(523f, 659f, 784f, 1047f))
        FANFARE -> concat(
            concat(tone(0.10f, 523f, 0.34f, 9f, false), tone(0.10f, 659f, 0.34f, 9f, false)),
            concat(tone(0.10f, 784f, 0.34f, 9f, false), chord(0.5f, floatArrayOf(1047f, 1319f, 1568f)))
        )
        JET -> loopable(noise(0.5f, 0f, 0.28f))
        else -> FloatArray(64)
    }

    private fun tone(dur: Float, freq: Float, amp: Float, decay: Float, square: Boolean): FloatArray {
        val n = (dur * RATE).toInt().coerceAtLeast(1)
        val out = FloatArray(n)
        var phase = 0.0
        val step = 2.0 * PI * freq / RATE
        for (i in 0 until n) {
            val t = i.toFloat() / RATE
            val env = exp(-decay * t) * attack(t)
            val s = sin(phase)
            out[i] = (if (square) (if (s > 0) 1.0 else -1.0) * 0.6 else s).toFloat() * amp * env
            phase += step
        }
        return out
    }

    private fun sweep(dur: Float, f0: Float, f1: Float, amp: Float, decay: Float, square: Boolean): FloatArray {
        val n = (dur * RATE).toInt().coerceAtLeast(1)
        val out = FloatArray(n)
        var phase = 0.0
        for (i in 0 until n) {
            val t = i.toFloat() / RATE
            val k = i.toFloat() / n
            val f = f0 + (f1 - f0) * k
            phase += 2.0 * PI * f / RATE
            val env = exp(-decay * t) * attack(t)
            val s = sin(phase)
            out[i] = (if (square) (if (s > 0) 1.0 else -1.0) * 0.6 else s).toFloat() * amp * env
        }
        return out
    }

    private fun noise(dur: Float, decay: Float, amp: Float): FloatArray {
        val n = (dur * RATE).toInt().coerceAtLeast(1)
        val out = FloatArray(n)
        val rnd = Random(1234)
        var last = 0f
        for (i in 0 until n) {
            val t = i.toFloat() / RATE
            val white = rnd.nextFloat() * 2f - 1f
            last = last * 0.72f + white * 0.28f   // a gentle low-pass makes it a rush, not a hiss
            val env = if (decay > 0f) exp(-decay * t) else 1f
            out[i] = last * amp * env * attack(t)
        }
        return out
    }

    private fun ratchet(dur: Float): FloatArray {
        val n = (dur * RATE).toInt()
        val out = FloatArray(n)
        var clickAt = 0
        var gap = (RATE * 0.055f).toInt()
        var i = 0
        while (i < n) {
            if (i >= clickAt) {
                val click = tone(0.02f, 1400f - 600f * (i.toFloat() / n), 0.32f, 90f, true)
                for (k in click.indices) if (i + k < n) out[i + k] += click[k]
                clickAt = i + gap
                gap = (gap * 1.09f).toInt().coerceAtLeast(400)
            }
            i++
        }
        return out
    }

    private fun chord(dur: Float, freqs: FloatArray): FloatArray {
        val n = (dur * RATE).toInt()
        val out = FloatArray(n)
        for (f in freqs) {
            val v = tone(dur, f, 0.26f, 4.5f, false)
            for (i in 0 until minOf(n, v.size)) out[i] += v[i]
        }
        return out
    }

    private fun vibrato(src: FloatArray, hz: Float, depth: Float): FloatArray {
        for (i in src.indices) {
            val t = i.toFloat() / RATE
            src[i] *= (1f - depth) + depth * (0.5f + 0.5f * sin(2.0 * PI * hz * t).toFloat())
        }
        return src
    }

    private fun concat(a: FloatArray, b: FloatArray): FloatArray {
        val out = FloatArray(a.size + b.size)
        System.arraycopy(a, 0, out, 0, a.size)
        System.arraycopy(b, 0, out, a.size, b.size)
        return out
    }

    private fun mixDown(a: FloatArray, b: FloatArray): FloatArray {
        val out = FloatArray(maxOf(a.size, b.size))
        for (i in out.indices) {
            var v = 0f
            if (i < a.size) v += a[i]
            if (i < b.size) v += b[i]
            out[i] = v
        }
        return out
    }

    /** Cross-fades the tail into the head so a looped sample doesn't click. */
    private fun loopable(src: FloatArray): FloatArray {
        val fade = (src.size * 0.18f).toInt().coerceAtLeast(1)
        for (i in 0 until fade) {
            val k = i.toFloat() / fade
            val tail = src[src.size - fade + i]
            src[i] = src[i] * k + tail * (1f - k)
        }
        return src.copyOf(src.size - fade)
    }

    private fun attack(t: Float): Float = if (t < 0.004f) t / 0.004f else 1f

    /**
     * The same sound heard from under water.
     *
     * Water carries low frequencies and swallows high ones, so the top of every sound goes: two
     * passes of a one-pole low-pass, which rolls off twice as steeply as one and is still only
     * a multiply and an add per sample. On top of that a short feedback delay for the smear you
     * get in a big body of water, and a little off the level, because everything is quieter
     * down there.
     *
     * Done to the SAMPLES rather than at playback: SoundPool can change a sound's rate and its
     * volume and nothing else, and dropping the rate would make everything deeper AND slower,
     * which is a slow-motion effect rather than a muffled one.
     */
    private fun muffle(src: FloatArray): FloatArray {
        val out = FloatArray(src.size)
        // one-pole coefficient for a corner around 700 Hz at this sample rate
        val cutoff = 700f
        val k = 1f - exp(-2f * PI.toFloat() * cutoff / RATE)
        var a = 0f
        var b = 0f
        for (i in src.indices) {
            a += k * (src[i] - a)
            b += k * (a - b)
            out[i] = b
        }
        // a short tail, so it sounds like a room made of water rather than a blanket
        val delay = (RATE * 0.028f).toInt()
        if (delay in 1 until out.size) {
            for (i in delay until out.size) out[i] += out[i - delay] * 0.28f
        }
        var peak = 0f
        for (v in out) { val m = if (v < 0f) -v else v; if (m > peak) peak = m }
        // Quieter than dry, and never louder than it started - the tail can push a peak past 1.
        val target = 0.72f
        val g = if (peak > 0.0001f) (target / peak).coerceAtMost(1.6f) else 1f
        for (i in out.indices) out[i] *= g
        return out
    }

    private fun writeWav(file: File, samples: FloatArray) = Wav.write(file, samples, RATE)

}
