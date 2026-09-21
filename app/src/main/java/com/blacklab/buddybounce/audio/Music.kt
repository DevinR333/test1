package com.blacklab.buddybounce.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import com.blacklab.buddybounce.data.Save
import java.io.File
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin

/**
 * One looping piece of music per world, synthesised in code.
 *
 * Nothing is shipped as an asset - each world's loop is written out as a WAV the first time that
 * world is played and cached from then on, the same way the sound effects work. Rendering is
 * done on a background thread because it is a few million float operations; the music simply
 * starts a moment later the very first time, and instantly ever after.
 *
 * Every world gets its own tempo, scale, voice and drum pattern, so switching worlds in the
 * menu changes the music with it. They are meant to sit underneath the game rather than demand
 * attention: mostly four or eight bars, no big arrivals, nothing that grates on the hundredth
 * loop.
 */
class Music(private val ctx: Context, private val save: Save) {

    private var player: MediaPlayer? = null
    private var currentWorld: String? = null
    private var pendingWorld: String? = null
    @Volatile private var rendering = false
    /** True while the activity is backgrounded, so we do not restart on a world change. */
    private var paused = false

    /**
     * Switch to a world's track. Cheap to call repeatedly - if it is already playing that
     * world's loop, nothing happens.
     */
    fun setWorld(worldId: String) {
        if (worldId == currentWorld && player != null) {
            applyVolume()
            return
        }
        pendingWorld = worldId
        if (!save.musicOn) {
            stop()
            currentWorld = worldId
            return
        }
        startWorld(worldId)
    }

    /** Called when the music setting or its volume changes. */
    fun refresh() {
        if (!save.musicOn) {
            stop()
            return
        }
        val want = pendingWorld ?: currentWorld
        if (player == null && want != null) startWorld(want) else applyVolume()
    }

    fun pause() {
        paused = true
        try {
            player?.let { if (it.isPlaying) it.pause() }
        } catch (t: Throwable) {
            // A player in a bad state is not worth taking the game down for.
        }
    }

    fun resume() {
        paused = false
        if (!save.musicOn) return
        val p = player
        if (p == null) {
            val want = pendingWorld ?: currentWorld
            if (want != null) startWorld(want)
            return
        }
        try {
            applyVolume()
            if (!p.isPlaying) p.start()
        } catch (t: Throwable) {
        }
    }

    fun stop() {
        val p = player
        player = null
        currentWorld = null
        try {
            p?.stop()
            p?.release()
        } catch (t: Throwable) {
        }
    }

    fun release() = stop()

    private fun applyVolume() {
        val v = if (save.musicOn) save.musicVolume.coerceIn(0f, 1f) * BASE_GAIN else 0f
        try {
            player?.setVolume(v, v)
        } catch (t: Throwable) {
        }
    }

    private fun startWorld(worldId: String) {
        if (rendering) return
        val dir = File(ctx.cacheDir, "music")
        if (!dir.exists()) dir.mkdirs()
        val file = File(dir, "$worldId.wav")
        if (file.exists() && file.length() > 1024L) {
            play(worldId, file)
            return
        }
        // Bake it once, off the render thread, then start.
        rendering = true
        Thread({
            try {
                Wav.write(file, render(worldId), RATE)
            } catch (t: Throwable) {
                // Music is a nice-to-have. If it cannot be written, the game is silent and fine.
            }
            rendering = false
            if (save.musicOn && !paused && pendingWorld == worldId && file.exists()) {
                play(worldId, file)
            }
        }, "music-bake").start()
    }

    @Synchronized
    private fun play(worldId: String, file: File) {
        stop()
        if (paused) return
        try {
            val p = MediaPlayer()
            p.setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_GAME)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build()
            )
            p.setDataSource(file.absolutePath)
            p.isLooping = true
            p.prepare()
            val v = save.musicVolume.coerceIn(0f, 1f) * BASE_GAIN
            p.setVolume(v, v)
            p.start()
            player = p
            currentWorld = worldId
        } catch (t: Throwable) {
            player = null
        }
    }

    // =====================================================================================
    // synthesis
    // =====================================================================================

    /**
     * Each world's track, as a bar length, a scale, and a handful of parts.
     *
     * The parts are deliberately simple - a bass, a lead figure and some percussion - because
     * what distinguishes these from one another is the TIMBRE and the rhythm, not the harmony.
     * A square-wave bass on straight eighths under a four-to-the-floor kick is a techno loop
     * whatever notes it plays; the same notes on a soft sine with a shaker is the backyard.
     */
    private fun render(worldId: String): FloatArray = when (worldId) {
        "ocean" -> ocean()
        "neon" -> neon()
        "frost" -> frost()
        "ember" -> ember()
        "desert" -> desert()
        "jungle" -> jungle()
        "candy" -> candy()
        "haunt" -> haunt()
        "heaven" -> heaven()
        else -> yard()
    }

    // ---- the ten tracks ---------------------------------------------------------------------

    /** Backyard Skies: light and breezy. Major, gentle, a shaker and a soft bass. */
    private fun yard(): FloatArray {
        val t = Track(bpm = 104f, bars = 8, beatsPerBar = 4)
        val root = 57                                     // A
        val chords = intArrayOf(0, 5, 7, 5)               // I  IV  V  IV
        for (bar in 0 until t.bars) {
            val ch = root + chords[bar % 4]
            t.note(bar, 0f, 2f, ch - 12, Voice.SOFT, 0.32f)
            t.note(bar, 2f, 2f, ch - 12, Voice.SOFT, 0.26f)
            // an arpeggio that drifts up and back down across the bar
            val steps = intArrayOf(0, 4, 7, 12, 7, 4)
            for (i in steps.indices) {
                t.note(bar, i * 0.5f + 0.5f, 0.45f, ch + steps[i], Voice.BELL, 0.17f)
            }
            for (b in 0 until 4) t.perc(bar, b + 0.5f, Perc.SHAKER, 0.14f)
            t.perc(bar, 0f, Perc.KICK, 0.3f)
            t.perc(bar, 2f, Perc.KICK, 0.24f)
        }
        return t.finish()
    }

    /** Deep Blue: slow, wide, and a long way under. */
    private fun ocean(): FloatArray {
        val t = Track(bpm = 74f, bars = 8, beatsPerBar = 4)
        val root = 50                                     // D
        val chords = intArrayOf(0, 0, -4, -2)
        for (bar in 0 until t.bars) {
            val ch = root + chords[bar % 4]
            t.note(bar, 0f, 4f, ch - 12, Voice.PAD, 0.3f)
            t.note(bar, 0f, 4f, ch - 5, Voice.PAD, 0.18f)
            t.note(bar, 0f, 4f, ch + 3, Voice.PAD, 0.14f)
            // slow bell figure, sparse, on the off-beats
            if (bar % 2 == 0) {
                t.note(bar, 1.5f, 1.5f, ch + 12, Voice.BELL, 0.15f)
                t.note(bar, 3f, 1f, ch + 15, Voice.BELL, 0.12f)
            } else {
                t.note(bar, 2.5f, 1.5f, ch + 10, Voice.BELL, 0.13f)
            }
        }
        return t.finish()
    }

    /** Neon City: techno. Four on the floor, square bass on eighths, a bright arp on top. */
    private fun neon(): FloatArray {
        val t = Track(bpm = 128f, bars = 8, beatsPerBar = 4)
        val root = 45                                     // A, low
        val riff = intArrayOf(0, 0, 12, 0, 7, 0, 10, 0)
        for (bar in 0 until t.bars) {
            for (i in 0 until 8) {
                t.note(bar, i * 0.5f, 0.42f, root + riff[i], Voice.SQUARE, 0.22f)
            }
            for (b in 0 until 4) {
                t.perc(bar, b.toFloat(), Perc.KICK, 0.42f)
                t.perc(bar, b + 0.5f, Perc.HAT, 0.16f)
            }
            t.perc(bar, 1f, Perc.SNARE, 0.2f)
            t.perc(bar, 3f, Perc.SNARE, 0.2f)
            // the arp only arrives in the second half, so eight bars do not feel like one
            if (bar >= 4) {
                val arp = intArrayOf(12, 19, 24, 19, 22, 19, 16, 19)
                for (i in 0 until 8) {
                    t.note(bar, i * 0.5f + 0.25f, 0.3f, root + arp[i], Voice.SAW, 0.1f)
                }
            }
        }
        return t.finish()
    }

    /** Frozen Peaks: sparse, high, cold. Bells with a lot of air between them. */
    private fun frost(): FloatArray {
        val t = Track(bpm = 84f, bars = 8, beatsPerBar = 4)
        val root = 60                                     // C
        val figure = intArrayOf(0, 7, 12, 10, 7, 3, 7, 12)
        for (bar in 0 until t.bars) {
            t.note(bar, 0f, 4f, root - 24, Voice.PAD, 0.2f)
            t.note(bar, 0f, 4f, root - 12, Voice.PAD, 0.12f)
            val n = figure[bar % figure.size]
            t.note(bar, 0f, 1.6f, root + n, Voice.GLASS, 0.2f)
            t.note(bar, 2f, 1.6f, root + n + 5, Voice.GLASS, 0.15f)
            if (bar % 4 == 3) t.note(bar, 3f, 1f, root + n + 12, Voice.GLASS, 0.12f)
        }
        return t.finish()
    }

    /** Emberfall: driving and low, with toms rather than a kit. */
    private fun ember(): FloatArray {
        val t = Track(bpm = 112f, bars = 8, beatsPerBar = 4)
        val root = 43                                     // G, low
        val riff = intArrayOf(0, 3, 0, 5, 0, 3, 7, 5)
        for (bar in 0 until t.bars) {
            for (i in 0 until 8) {
                t.note(bar, i * 0.5f, 0.46f, root + riff[(i + bar) % 8], Voice.SAW, 0.2f)
            }
            t.perc(bar, 0f, Perc.KICK, 0.44f)
            t.perc(bar, 1.5f, Perc.TOM, 0.24f)
            t.perc(bar, 2f, Perc.KICK, 0.36f)
            t.perc(bar, 2.75f, Perc.TOM, 0.2f)
            t.perc(bar, 3.5f, Perc.TOM, 0.26f)
            if (bar % 2 == 1) t.note(bar, 3f, 1f, root + 12, Voice.SQUARE, 0.14f)
        }
        return t.finish()
    }

    /** Dust Run: phrygian plucks over a hand drum. */
    private fun desert(): FloatArray {
        val t = Track(bpm = 96f, bars = 8, beatsPerBar = 4)
        val root = 52                                     // E
        val scale = intArrayOf(0, 1, 4, 5, 7, 8, 11)      // phrygian dominant flavour
        for (bar in 0 until t.bars) {
            t.note(bar, 0f, 4f, root - 12, Voice.PAD, 0.22f)
            for (i in 0 until 6) {
                val deg = scale[(bar * 3 + i * 2) % scale.size]
                t.note(bar, i * 0.66f, 0.5f, root + deg + (if (i > 3) 12 else 0), Voice.PLUCK, 0.16f)
            }
            t.perc(bar, 0f, Perc.TOM, 0.32f)
            t.perc(bar, 0.75f, Perc.HAT, 0.12f)
            t.perc(bar, 1.5f, Perc.TOM, 0.2f)
            t.perc(bar, 2f, Perc.TOM, 0.3f)
            t.perc(bar, 2.75f, Perc.HAT, 0.12f)
            t.perc(bar, 3.5f, Perc.TOM, 0.22f)
        }
        return t.finish()
    }

    /** Overgrown: percussion-led and syncopated, with a marimba-ish lead. */
    private fun jungle(): FloatArray {
        val t = Track(bpm = 110f, bars = 8, beatsPerBar = 4)
        val root = 55                                     // G
        val penta = intArrayOf(0, 2, 4, 7, 9)
        for (bar in 0 until t.bars) {
            t.note(bar, 0f, 2f, root - 12, Voice.SOFT, 0.26f)
            t.note(bar, 2.5f, 1.5f, root - 7, Voice.SOFT, 0.2f)
            val offs = floatArrayOf(0f, 0.75f, 1.25f, 2f, 2.75f, 3.25f)
            for (i in offs.indices) {
                val deg = penta[(bar * 2 + i) % penta.size]
                t.note(bar, offs[i], 0.4f, root + deg + 12, Voice.PLUCK, 0.15f)
            }
            t.perc(bar, 0f, Perc.KICK, 0.34f)
            t.perc(bar, 0.5f, Perc.SHAKER, 0.14f)
            t.perc(bar, 1f, Perc.TOM, 0.22f)
            t.perc(bar, 1.5f, Perc.SHAKER, 0.12f)
            t.perc(bar, 2.25f, Perc.TOM, 0.26f)
            t.perc(bar, 2.5f, Perc.SHAKER, 0.14f)
            t.perc(bar, 3f, Perc.KICK, 0.28f)
            t.perc(bar, 3.5f, Perc.SHAKER, 0.12f)
        }
        return t.finish()
    }

    /** Sugar Rush: fast, bouncy, major, xylophone-bright. */
    private fun candy(): FloatArray {
        val t = Track(bpm = 134f, bars = 8, beatsPerBar = 4)
        val root = 60                                     // C
        val chords = intArrayOf(0, 9, 5, 7)               // I  vi  IV  V
        for (bar in 0 until t.bars) {
            val ch = root + chords[bar % 4]
            t.note(bar, 0f, 0.4f, ch - 24, Voice.SOFT, 0.3f)
            t.note(bar, 1f, 0.4f, ch - 24, Voice.SOFT, 0.2f)
            t.note(bar, 2f, 0.4f, ch - 17, Voice.SOFT, 0.26f)
            t.note(bar, 3f, 0.4f, ch - 24, Voice.SOFT, 0.2f)
            val hop = intArrayOf(0, 4, 7, 4, 9, 7, 4, 0)
            for (i in 0 until 8) {
                t.note(bar, i * 0.5f, 0.3f, ch + hop[i], Voice.BELL, 0.16f)
            }
            t.perc(bar, 0f, Perc.KICK, 0.34f)
            t.perc(bar, 1f, Perc.SNARE, 0.22f)
            t.perc(bar, 2f, Perc.KICK, 0.3f)
            t.perc(bar, 3f, Perc.SNARE, 0.22f)
            for (i in 0 until 8) t.perc(bar, i * 0.5f + 0.25f, Perc.HAT, 0.1f)
        }
        return t.finish()
    }

    /** Hollow Hill: a slow minor waltz on a music box, with a very tired bell. */
    private fun haunt(): FloatArray {
        val t = Track(bpm = 88f, bars = 8, beatsPerBar = 3)
        val root = 57                                     // A minor
        val figure = intArrayOf(0, 3, 7, 3, 8, 7, 3, 0)
        for (bar in 0 until t.bars) {
            t.note(bar, 0f, 1f, root - 24, Voice.SOFT, 0.3f)
            t.note(bar, 1f, 0.8f, root - 12, Voice.SOFT, 0.16f)
            t.note(bar, 2f, 0.8f, root - 7, Voice.SOFT, 0.14f)
            for (i in 0 until 3) {
                val deg = figure[(bar * 3 + i) % figure.size]
                t.note(bar, i.toFloat(), 0.9f, root + deg + 12, Voice.GLASS, 0.17f)
            }
            if (bar % 4 == 0) t.note(bar, 0f, 3f, root - 36, Voice.PAD, 0.24f)
        }
        return t.finish()
    }

    /** Heaven: almost nothing. Long sustained chords and a distant bell. */
    private fun heaven(): FloatArray {
        val t = Track(bpm = 60f, bars = 8, beatsPerBar = 4)
        val root = 60
        val chords = intArrayOf(0, 5, 9, 7)
        for (bar in 0 until t.bars) {
            val ch = root + chords[bar % 4]
            t.note(bar, 0f, 4f, ch - 24, Voice.PAD, 0.26f)
            t.note(bar, 0f, 4f, ch - 12, Voice.PAD, 0.2f)
            t.note(bar, 0f, 4f, ch - 5, Voice.PAD, 0.16f)
            t.note(bar, 0f, 4f, ch, Voice.PAD, 0.12f)
            if (bar % 2 == 0) t.note(bar, 2f, 2f, ch + 12, Voice.GLASS, 0.13f)
        }
        return t.finish()
    }

    // ---- the tiny sequencer ------------------------------------------------------------------

    private object Voice {
        const val SOFT = 0      // rounded sine, for basses
        const val BELL = 1      // struck, bright, quick decay
        const val PAD = 2       // slow attack, long sustain
        const val SQUARE = 3    // hollow and buzzy
        const val SAW = 4       // bright and harsh
        const val PLUCK = 5     // short, woody
        const val GLASS = 6     // high, pure, long ring
    }

    private object Perc {
        const val KICK = 0
        const val SNARE = 1
        const val HAT = 2
        const val SHAKER = 3
        const val TOM = 4
    }

    /**
     * Renders notes into one buffer. Everything is additive: a note writes its own envelope and
     * waveform straight into the mix, and the whole thing is normalised at the end.
     */
    private class Track(bpm: Float, val bars: Int, val beatsPerBar: Int) {
        private val secPerBeat = 60f / bpm
        private val total = (secPerBeat * beatsPerBar * bars * RATE).toInt()
        private val buf = FloatArray(total)

        fun note(bar: Int, beat: Float, lenBeats: Float, midi: Int, voice: Int, gain: Float) {
            val start = ((bar * beatsPerBar + beat) * secPerBeat * RATE).toInt()
            val len = (lenBeats * secPerBeat * RATE).toInt()
            if (start >= total || len <= 0) return
            val freq = 440.0 * Math.pow(2.0, (midi - 69) / 12.0)
            val w = 2.0 * PI * freq / RATE
            for (i in 0 until len) {
                // The loop must join itself cleanly, so anything running past the end wraps
                // round to the beginning rather than being cut off.
                val idx = (start + i) % total
                val t = i.toFloat() / len
                val env = envelope(voice, i, len)
                if (env <= 0.0005f) continue
                buf[idx] += (wave(voice, w * (start + i), t) * env * gain).toFloat()
            }
        }

        fun perc(bar: Int, beat: Float, kind: Int, gain: Float) {
            val start = ((bar * beatsPerBar + beat) * secPerBeat * RATE).toInt()
            if (start >= total) return
            val len = when (kind) {
                Perc.KICK -> (0.16f * RATE).toInt()
                Perc.SNARE -> (0.13f * RATE).toInt()
                Perc.TOM -> (0.17f * RATE).toInt()
                else -> (0.05f * RATE).toInt()
            }
            var seed = (start * 1103515245 + 12345)
            for (i in 0 until len) {
                val idx = (start + i) % total
                val t = i.toFloat() / len
                seed = seed * 1103515245 + 12345
                val noise = ((seed ushr 16) and 0x7FFF) / 16384f - 1f
                val env = exp(-t * when (kind) {
                    Perc.KICK -> 5f
                    Perc.SNARE -> 9f
                    Perc.TOM -> 6f
                    Perc.HAT -> 22f
                    else -> 26f
                })
                val v = when (kind) {
                    // a kick is a fast downward sweep, not noise
                    Perc.KICK -> sin(2.0 * PI * (110.0 - 70.0 * t) * i / RATE).toFloat()
                    Perc.TOM -> sin(2.0 * PI * (190.0 - 90.0 * t) * i / RATE).toFloat()
                    Perc.SNARE -> noise * 0.8f + sin(2.0 * PI * 190.0 * i / RATE).toFloat() * 0.3f
                    else -> noise
                }
                buf[idx] += v * env * gain
            }
        }

        private fun envelope(voice: Int, i: Int, len: Int): Float {
            val t = i.toFloat() / len
            return when (voice) {
                Voice.PAD -> {
                    val attack = (t / 0.25f).coerceAtMost(1f)
                    val release = ((1f - t) / 0.3f).coerceAtMost(1f)
                    attack * release * 0.9f
                }
                Voice.BELL -> exp(-t * 4.5f) * (t / 0.01f).coerceAtMost(1f)
                Voice.GLASS -> exp(-t * 2.2f) * (t / 0.02f).coerceAtMost(1f)
                Voice.PLUCK -> exp(-t * 7f) * (t / 0.006f).coerceAtMost(1f)
                else -> {
                    val attack = (t / 0.02f).coerceAtMost(1f)
                    val release = ((1f - t) / 0.12f).coerceAtMost(1f)
                    attack * release
                }
            }
        }

        private fun wave(voice: Int, phase: Double, t: Float): Double = when (voice) {
            Voice.SQUARE -> if (sin(phase) >= 0.0) 0.55 else -0.55
            Voice.SAW -> {
                val x = (phase / (2.0 * PI)) % 1.0
                (x * 2.0 - 1.0) * 0.5
            }
            Voice.BELL, Voice.GLASS ->
                sin(phase) * 0.7 + sin(phase * 2.0) * 0.2 + sin(phase * 3.01) * 0.1
            Voice.PLUCK -> sin(phase) * 0.6 + sin(phase * 2.0) * 0.25 * (1f - t)
            Voice.PAD -> sin(phase) * 0.6 + sin(phase * 2.0) * 0.18 + sin(phase * 0.5) * 0.2
            else -> sin(phase) * 0.8 + sin(phase * 2.0) * 0.12
        }

        fun finish(): FloatArray {
            var peak = 0f
            for (v in buf) {
                val a = if (v < 0f) -v else v
                if (a > peak) peak = a
            }
            if (peak > 0.0001f) {
                val k = 0.82f / peak
                for (i in buf.indices) buf[i] *= k
            }
            return buf
        }
    }

    private companion object {
        /** Music is mixed under the effects, and the slider scales from here. */
        const val BASE_GAIN = 0.75f
        const val RATE = 22050
    }
}
