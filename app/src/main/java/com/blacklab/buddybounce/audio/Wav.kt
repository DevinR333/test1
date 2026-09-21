package com.blacklab.buddybounce.audio

import java.io.File
import java.io.FileOutputStream

/**
 * Writes a mono 16-bit PCM WAV.
 *
 * Shared by the sound effects and the music, both of which are synthesised in code and cached
 * as files - the app ships with no audio assets at all, so every sound in the game is tuned
 * next to the thing that plays it.
 */
object Wav {

    fun write(file: File, samples: FloatArray, rate: Int) {
        val bytes = ByteArray(samples.size * 2)
        for (i in samples.indices) {
            val v = (samples[i].coerceIn(-1f, 1f) * 32000f).toInt()
            bytes[i * 2] = (v and 0xFF).toByte()
            bytes[i * 2 + 1] = ((v shr 8) and 0xFF).toByte()
        }
        FileOutputStream(file).use { out ->
            val dataLen = bytes.size
            val header = ByteArray(44)
            fun put(offset: Int, s: String) {
                for (i in s.indices) header[offset + i] = s[i].code.toByte()
            }
            fun putInt(offset: Int, value: Int) {
                header[offset] = (value and 0xFF).toByte()
                header[offset + 1] = ((value shr 8) and 0xFF).toByte()
                header[offset + 2] = ((value shr 16) and 0xFF).toByte()
                header[offset + 3] = ((value shr 24) and 0xFF).toByte()
            }
            fun putShort(offset: Int, value: Int) {
                header[offset] = (value and 0xFF).toByte()
                header[offset + 1] = ((value shr 8) and 0xFF).toByte()
            }
            put(0, "RIFF"); putInt(4, 36 + dataLen); put(8, "WAVE")
            put(12, "fmt "); putInt(16, 16); putShort(20, 1); putShort(22, 1)
            putInt(24, rate); putInt(28, rate * 2); putShort(32, 2); putShort(34, 16)
            put(36, "data"); putInt(40, dataLen)
            out.write(header)
            out.write(bytes)
        }
    }
}
