package com.example.ui.components

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.os.Build
import android.util.Log
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Synthesizes a clean, futuristic crystalline harmonic chime when Nova AI opens.
 * Uses pure PCM synthesis on a background thread so there are zero external binary asset
 * dependencies and zero risk of main-thread blocking.
 */
object NovaSoundEffects {
    private const val TAG = "NovaSoundEffects"
    private const val SAMPLE_RATE = 44_100
    private val soundScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    fun playStartupChime() {
        if (Build.FINGERPRINT.equals("robolectric", ignoreCase = true)) return

        soundScope.launch {
            var track: AudioTrack? = null
            try {
                val durationSec = 0.78
                val numSamples = (SAMPLE_RATE * durationSec).toInt()
                val pcm = ShortArray(numSamples)

                // Warm futuristic two-step harmonic bloom:
                // Note 1 (0.00s..): F4 (349.23 Hz) + C5 (523.25 Hz)
                // Note 2 (0.11s..): A5 (880.00 Hz) + G5 (783.99 Hz) crystalline shimmer
                for (i in 0 until numSamples) {
                    val t = i.toDouble() / SAMPLE_RATE

                    // Smooth raised-cosine attack (25ms) + silky exponential decay
                    val env1 = smoothAttack(t, 0.025) * exp(-t * 4.4)
                    val w1 = 0.52 * sin(2.0 * PI * 349.23 * t) +
                        0.40 * sin(2.0 * PI * 523.25 * t)

                    val t2 = (t - 0.11).coerceAtLeast(0.0)
                    val env2 = if (t >= 0.11) {
                        smoothAttack(t2, 0.022) * exp(-t2 * 4.8)
                    } else {
                        0.0
                    }
                    val w2 = 0.45 * sin(2.0 * PI * 783.99 * t2) +
                        0.38 * sin(2.0 * PI * 1046.50 * t2)

                    // Gentle tail fade-out on final 40ms to guarantee zero click
                    val tailFade = ((durationSec - t) / 0.04).coerceIn(0.0, 1.0)
                    val mixed = (w1 * env1 + w2 * env2) * 0.26 * tailFade
                    pcm[i] = (mixed.coerceIn(-1.0, 1.0) * Short.MAX_VALUE).toInt().toShort()
                }

                val byteCount = pcm.size * 2
                track = AudioTrack.Builder()
                    .setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_MEDIA)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                            .build()
                    )
                    .setAudioFormat(
                        AudioFormat.Builder()
                            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                            .setSampleRate(SAMPLE_RATE)
                            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                            .build()
                    )
                    .setBufferSizeInBytes(byteCount)
                    .setTransferMode(AudioTrack.MODE_STATIC)
                    .build()

                track.write(pcm, 0, pcm.size)
                track.play()
                delay(860L)
            } catch (e: Exception) {
                Log.w(TAG, "Startup chime skipped: ${e.message}")
            } finally {
                runCatching {
                    track?.stop()
                    track?.release()
                }
            }
        }
    }

    private fun smoothAttack(t: Double, attackSec: Double): Double {
        if (t <= 0.0) return 0.0
        if (t >= attackSec) return 1.0
        return 0.5 * (1.0 - kotlin.math.cos(PI * (t / attackSec)))
    }
}
