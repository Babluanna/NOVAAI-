package com.example.voice

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Build
import android.util.Log
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.sqrt
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val TAG = "NovaAudioRecorder"
private const val RECORD_SAMPLE_RATE = 16_000
private const val SILENCE_AFTER_SPEECH_MS = 1_050L
private const val MAX_WAIT_FOR_SPEECH_MS = 18_000L
private const val MAX_UTTERANCE_MS = 26_000L
private const val MIN_VALID_SPEECH_MS = 240L

sealed interface UtteranceCaptureResult {
    data class CapturedWav(val wavBytes: ByteArray, val durationMs: Long) : UtteranceCaptureResult
    data object SilenceTimeout : UtteranceCaptureResult
    data class HardwareUnavailable(val reason: String) : UtteranceCaptureResult
}

/**
 * Quiet, beep-free 16kHz mono PCM audio recorder with adaptive Voice Activity Detection (VAD).
 * Captures user speech seamlessly across English, Telugu, and Hindi without Android's single-locale
 * SpeechRecognizer beep or language-pack limitations, while driving 60fps mic amplitude to the orb.
 */
class NovaAudioRecorder(
    private val scope: CoroutineScope,
    private val onMicAmplitude: (Float) -> Unit
) {
    private var captureJob: Job? = null
    private val forceFinishTurn = AtomicBoolean(false)
    private val cancelled = AtomicBoolean(false)

    @SuppressLint("MissingPermission")
    fun startCapturingUtterance(
        onSpeechStarted: () -> Unit,
        onResult: (UtteranceCaptureResult) -> Unit
    ) {
        cancelCapture()
        if (Build.FINGERPRINT.equals("robolectric", ignoreCase = true)) {
            return
        }

        forceFinishTurn.set(false)
        cancelled.set(false)

        captureJob = scope.launch(Dispatchers.IO) {
            var recorder: AudioRecord? = null
            try {
                val minBufBytes = AudioRecord.getMinBufferSize(
                    RECORD_SAMPLE_RATE,
                    AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT
                )
                if (minBufBytes <= 0) {
                    withContext(Dispatchers.Main) {
                        onResult(UtteranceCaptureResult.HardwareUnavailable("Microphone buffer unavailable."))
                    }
                    return@launch
                }

                val bufferSizeInBytes = (minBufBytes * 2).coerceAtLeast(6400)
                recorder = createBestAudioRecord(bufferSizeInBytes)
                if (recorder == null || recorder.state != AudioRecord.STATE_INITIALIZED) {
                    recorder?.release()
                    withContext(Dispatchers.Main) {
                        onResult(
                            UtteranceCaptureResult.HardwareUnavailable(
                                "Microphone is currently in use by another app."
                            )
                        )
                    }
                    return@launch
                }

                recorder.startRecording()
                if (recorder.recordingState != AudioRecord.RECORDSTATE_RECORDING) {
                    recorder.release()
                    withContext(Dispatchers.Main) {
                        onResult(
                            UtteranceCaptureResult.HardwareUnavailable(
                                "Microphone capture is blocked by another active recording."
                            )
                        )
                    }
                    return@launch
                }

                // Read 40ms frames (640 samples = 1280 bytes at 16kHz) for responsive VAD & orb physics
                val frameSamples = 640
                val readBuffer = ShortArray(frameSamples)
                val preRollFrames = ArrayDeque<ByteArray>(10) // ~400ms pre-roll so initial consonants are never clipped
                val speechPcmOut = ByteArrayOutputStream(64 * 1024)

                var noiseFloorRms = 0.008f
                var speechStarted = false
                var speechStartMs = 0L
                var lastVoiceActiveMs = 0L
                var activeSpeechAccumulatedMs = 0L
                val listenStartMs = System.currentTimeMillis()

                while (isActive && !cancelled.get()) {
                    val readCount = recorder.read(readBuffer, 0, frameSamples)
                    if (readCount <= 0) continue

                    val now = System.currentTimeMillis()
                    val rawRms = computeRms(readBuffer, readCount)
                    val visualEnergy = (0.14f + rawRms * 5.2f).coerceIn(0.14f, 1.0f)
                    onMicAmplitude(visualEnergy)

                    val frameBytes = shortsToLittleEndianBytes(readBuffer, readCount)

                    if (!speechStarted) {
                        // Slowly adapt ambient noise floor during the first moments before speech
                        noiseFloorRms = (noiseFloorRms * 0.92f + rawRms * 0.08f).coerceIn(0.004f, 0.035f)
                        val triggerThreshold = (noiseFloorRms * 2.5f).coerceAtLeast(0.015f)

                        if (preRollFrames.size >= 9) {
                            preRollFrames.removeFirst()
                        }
                        preRollFrames.addLast(frameBytes)

                        if (rawRms >= triggerThreshold) {
                            speechStarted = true
                            speechStartMs = now
                            lastVoiceActiveMs = now
                            activeSpeechAccumulatedMs += 40L
                            for (pre in preRollFrames) {
                                speechPcmOut.write(pre)
                            }
                            preRollFrames.clear()
                            withContext(Dispatchers.Main) {
                                if (!cancelled.get()) onSpeechStarted()
                            }
                        } else if (now - listenStartMs > MAX_WAIT_FOR_SPEECH_MS) {
                            withContext(Dispatchers.Main) {
                                if (!cancelled.get()) onResult(UtteranceCaptureResult.SilenceTimeout)
                            }
                            return@launch
                        }
                    } else {
                        speechPcmOut.write(frameBytes)
                        val sustainThreshold = (noiseFloorRms * 1.75f).coerceAtLeast(0.011f)
                        if (rawRms >= sustainThreshold) {
                            lastVoiceActiveMs = now
                            activeSpeechAccumulatedMs += 40L
                        }

                        val silenceDuration = now - lastVoiceActiveMs
                        val totalSpeechWindow = now - speechStartMs
                        if (forceFinishTurn.get() ||
                            silenceDuration >= SILENCE_AFTER_SPEECH_MS ||
                            totalSpeechWindow >= MAX_UTTERANCE_MS
                        ) {
                            break
                        }
                    }
                }

                if (cancelled.get() || !isActive) return@launch

                val rawPcm = speechPcmOut.toByteArray()
                if (!speechStarted || activeSpeechAccumulatedMs < MIN_VALID_SPEECH_MS || rawPcm.size < 3200) {
                    withContext(Dispatchers.Main) {
                        if (!cancelled.get()) onResult(UtteranceCaptureResult.SilenceTimeout)
                    }
                    return@launch
                }

                val wavBytes = encodePcm16MonoToWav(rawPcm, RECORD_SAMPLE_RATE)
                val durationMs = (rawPcm.size / 2L) * 1000L / RECORD_SAMPLE_RATE
                withContext(Dispatchers.Main) {
                    if (!cancelled.get()) {
                        onResult(UtteranceCaptureResult.CapturedWav(wavBytes, durationMs))
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "AudioRecord capture error", e)
                withContext(Dispatchers.Main) {
                    if (!cancelled.get()) {
                        onResult(
                            UtteranceCaptureResult.HardwareUnavailable(
                                e.localizedMessage ?: "Audio capture unavailable."
                            )
                        )
                    }
                }
            } finally {
                runCatching {
                    if (recorder?.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
                        recorder.stop()
                    }
                    recorder?.release()
                }
                onMicAmplitude(0.14f)
            }
        }
    }

    /**
     * Completes and sends the currently spoken utterance immediately (e.g., if user taps the orb).
     */
    fun finishCurrentUtteranceNow() {
        forceFinishTurn.set(true)
    }

    fun cancelCapture() {
        cancelled.set(true)
        forceFinishTurn.set(false)
        captureJob?.cancel()
        captureJob = null
        onMicAmplitude(0.14f)
    }

    @SuppressLint("MissingPermission")
    private fun createBestAudioRecord(bufferSizeInBytes: Int): AudioRecord? {
        val sources = intArrayOf(
            MediaRecorder.AudioSource.VOICE_RECOGNITION,
            MediaRecorder.AudioSource.MIC,
            MediaRecorder.AudioSource.VOICE_COMMUNICATION
        )
        for (source in sources) {
            try {
                val rec = AudioRecord(
                    source,
                    RECORD_SAMPLE_RATE,
                    AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT,
                    bufferSizeInBytes
                )
                if (rec.state == AudioRecord.STATE_INITIALIZED) {
                    return rec
                }
                rec.release()
            } catch (_: Exception) {
            }
        }
        return null
    }

    private fun computeRms(samples: ShortArray, count: Int): Float {
        if (count <= 0) return 0f
        var sumSq = 0.0
        for (i in 0 until count) {
            val v = samples[i].toDouble() / Short.MAX_VALUE.toDouble()
            sumSq += v * v
        }
        return sqrt(sumSq / count).toFloat()
    }

    private fun shortsToLittleEndianBytes(samples: ShortArray, count: Int): ByteArray {
        val bytes = ByteArray(count * 2)
        ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer().put(samples, 0, count)
        return bytes
    }

    companion object {
        fun encodePcm16MonoToWav(pcmData: ByteArray, sampleRate: Int = RECORD_SAMPLE_RATE): ByteArray {
            val totalDataLen = pcmData.size + 36
            val byteRate = sampleRate * 2 // 16-bit mono = 2 bytes/sample
            val out = ByteArray(44 + pcmData.size)
            val header = ByteBuffer.wrap(out, 0, 44).order(ByteOrder.LITTLE_ENDIAN)

            // "RIFF" chunk descriptor
            header.put('R'.code.toByte())
            header.put('I'.code.toByte())
            header.put('F'.code.toByte())
            header.put('F'.code.toByte())
            header.putInt(totalDataLen)
            header.put('W'.code.toByte())
            header.put('A'.code.toByte())
            header.put('V'.code.toByte())
            header.put('E'.code.toByte())

            // "fmt " subchunk
            header.put('f'.code.toByte())
            header.put('m'.code.toByte())
            header.put('t'.code.toByte())
            header.put(' '.code.toByte())
            header.putInt(16) // PCM subchunk1 size
            header.putShort(1) // AudioFormat = 1 (PCM)
            header.putShort(1) // NumChannels = 1 (Mono)
            header.putInt(sampleRate)
            header.putInt(byteRate)
            header.putShort(2) // BlockAlign = 2
            header.putShort(16) // BitsPerSample = 16

            // "data" subchunk
            header.put('d'.code.toByte())
            header.put('a'.code.toByte())
            header.put('t'.code.toByte())
            header.put('a'.code.toByte())
            header.putInt(pcmData.size)

            System.arraycopy(pcmData, 0, out, 44, pcmData.size)
            return out
        }
    }
}
