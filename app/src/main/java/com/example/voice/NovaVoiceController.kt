package com.example.voice

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.speech.tts.Voice as AndroidTtsVoice
import android.util.Log
import androidx.core.content.ContextCompat
import com.example.ai.NovaAiService
import com.example.ai.PendingMediaAttachment
import com.example.data.model.NovaVoiceLanguage
import com.example.data.model.NovaVoicePersona
import java.util.Locale
import java.util.UUID
import kotlin.math.sin
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

private const val TAG = "NovaVoiceController"
private const val VOICE_REPLY_UTTERANCE_PREFIX = "nova_voice_reply_"
private const val PREFS_VOICE_SETTINGS = "com.aistudio.novaai.voice.prefs"
private const val KEY_VOICE_PERSONA = "selected_voice_persona"
private const val KEY_VOICE_LANGUAGE = "selected_voice_language"
private const val KEY_SHOW_CAPTIONS = "show_live_captions"

enum class VoiceSessionStatus {
    IDLE,
    LISTENING,
    PROCESSING,
    AI_SPEAKING,
    PAUSED,
    PLAYING_RECORDING,
    ERROR
}

data class VoiceUiState(
    val status: VoiceSessionStatus = VoiceSessionStatus.IDLE,
    val isMuted: Boolean = false,
    val showCaptions: Boolean = true,
    val selectedPersona: NovaVoicePersona = NovaVoicePersona.NOVA_WARM,
    val selectedLanguage: NovaVoiceLanguage = NovaVoiceLanguage.AUTO,
    val pendingVoicePhoto: PendingMediaAttachment? = null,
    val elapsedSeconds: Long = 0L,
    val waveformEnergy: Float = 0.14f,
    val liveUserTranscript: String = "",
    val liveAiTranscript: String = "",
    val statusHint: String = "Listening…",
    val speakingMessageId: String? = null,
    val playingRecordingId: String? = null,
    val errorMessage: String? = null
)

class NovaVoiceController(
    context: Context,
    private val scope: CoroutineScope,
    private val aiService: NovaAiService
) {
    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences(PREFS_VOICE_SETTINGS, Context.MODE_PRIVATE)

    private val _state = MutableStateFlow(loadInitialState())
    val state: StateFlow<VoiceUiState> = _state.asStateFlow()

    private var timerJob: Job? = null
    private var pulseJob: Job? = null
    private var neuralSynthesisJob: Job? = null

    private var speechRecognizer: SpeechRecognizer? = null
    private var fallbackTts: TextToSpeech? = null
    private var fallbackTtsReady = false
    private var autoContinueConversation = false

    private val neuralAudioPlayer = NovaNeuralAudioPlayer(
        scope = scope,
        onAmplitudeChanged = { rmsEnergy ->
            val s = _state.value.status
            if (s == VoiceSessionStatus.AI_SPEAKING || s == VoiceSessionStatus.PLAYING_RECORDING) {
                _state.update { it.copy(waveformEnergy = rmsEnergy) }
            }
        }
    )

    private val audioRecorder = NovaAudioRecorder(
        scope = scope,
        onMicAmplitude = { micRms ->
            if (_state.value.status == VoiceSessionStatus.LISTENING && !_state.value.isMuted) {
                _state.update { it.copy(waveformEnergy = micRms) }
            }
        }
    )

    // Invoked when Voice Mode captures a real 16kHz WAV spoken turn for native multimodal Gemini understanding
    var onUserVoiceAudioCaptured: ((ByteArray, Long) -> Unit)? = null

    // Invoked when fallback SpeechRecognizer finishes recognizing a spoken user turn
    var onUserVoiceTurnRecognized: ((String) -> Unit)? = null

    // Invoked when inline dictation in Chat Mode finishes recognizing speech
    var onDictationResult: ((String) -> Unit)? = null

    init {
        initFallbackTextToSpeech()
    }

    private fun loadInitialState(): VoiceUiState {
        val personaName = prefs.getString(KEY_VOICE_PERSONA, NovaVoicePersona.NOVA_WARM.name)
        val persona = runCatching { NovaVoicePersona.valueOf(personaName.orEmpty()) }
            .getOrDefault(NovaVoicePersona.NOVA_WARM)

        val langName = prefs.getString(KEY_VOICE_LANGUAGE, NovaVoiceLanguage.AUTO.name)
        val lang = runCatching { NovaVoiceLanguage.valueOf(langName.orEmpty()) }
            .getOrDefault(NovaVoiceLanguage.AUTO)

        val showCaps = prefs.getBoolean(KEY_SHOW_CAPTIONS, true)
        return VoiceUiState(
            selectedPersona = persona,
            selectedLanguage = lang,
            showCaptions = showCaps
        )
    }

    fun selectVoicePersona(persona: NovaVoicePersona) {
        prefs.edit().putString(KEY_VOICE_PERSONA, persona.name).apply()
        _state.update { it.copy(selectedPersona = persona) }
    }

    fun selectVoiceLanguage(language: NovaVoiceLanguage) {
        prefs.edit().putString(KEY_VOICE_LANGUAGE, language.name).apply()
        _state.update { it.copy(selectedLanguage = language) }
    }

    fun toggleCaptions() {
        val next = !_state.value.showCaptions
        prefs.edit().putBoolean(KEY_SHOW_CAPTIONS, next).apply()
        _state.update { it.copy(showCaptions = next) }
    }

    fun setPendingVoicePhoto(attachment: PendingMediaAttachment?) {
        _state.update { it.copy(pendingVoicePhoto = attachment) }
    }

    private fun initFallbackTextToSpeech() {
        try {
            fallbackTts = TextToSpeech(appContext) { initStatus ->
                if (initStatus == TextToSpeech.SUCCESS) {
                    configureBestFallbackVoice(Locale.US)
                    fallbackTtsReady = true
                    fallbackTts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                        override fun onStart(utteranceId: String?) {
                            _state.update { current ->
                                current.copy(
                                    waveformEnergy = 0.66f,
                                    status = when {
                                        current.playingRecordingId != null -> VoiceSessionStatus.PLAYING_RECORDING
                                        utteranceId?.startsWith(VOICE_REPLY_UTTERANCE_PREFIX) == true -> VoiceSessionStatus.AI_SPEAKING
                                        else -> current.status
                                    }
                                )
                            }
                        }

                        override fun onDone(utteranceId: String?) {
                            val isVoiceReply = utteranceId?.startsWith(VOICE_REPLY_UTTERANCE_PREFIX) == true
                            handleSpeechPlaybackCompleted(isVoiceSessionReply = isVoiceReply)
                        }

                        @Deprecated("Deprecated in Java")
                        override fun onError(utteranceId: String?) {
                            val isVoiceReply = utteranceId?.startsWith(VOICE_REPLY_UTTERANCE_PREFIX) == true
                            handleSpeechPlaybackCompleted(isVoiceSessionReply = isVoiceReply)
                        }
                    })
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Fallback TextToSpeech initialization failed", e)
        }
    }

    private fun configureBestFallbackVoice(targetLocale: Locale) {
        val engine = fallbackTts ?: return
        runCatching {
            val result = engine.setLanguage(targetLocale)
            if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                engine.language = Locale.US
            }
            val voices = engine.voices.orEmpty()
            val best: AndroidTtsVoice? = voices
                .filter { v ->
                    v.locale.language.equals(targetLocale.language, ignoreCase = true) &&
                        !v.isNetworkConnectionRequired
                }
                .maxByOrNull { it.quality }
            if (best != null) {
                engine.voice = best
            }
            engine.setSpeechRate(1.0f)
            engine.setPitch(1.02f)
        }
    }

    fun hasRecordAudioPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            appContext,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
    }

    fun startVoiceConversation() {
        if (!hasRecordAudioPermission()) {
            _state.update {
                it.copy(
                    status = VoiceSessionStatus.IDLE,
                    statusHint = "Tap Enable Microphone to talk with Nova AI"
                )
            }
            return
        }

        stopSpeakingInternal()
        autoContinueConversation = true
        startTimerAndWaveformDriver()

        _state.update {
            it.copy(
                status = VoiceSessionStatus.LISTENING,
                isMuted = false,
                errorMessage = null,
                playingRecordingId = null,
                speakingMessageId = null,
                statusHint = "Listening…"
            )
        }

        beginRawAudioListeningPass()
    }

    private fun beginRawAudioListeningPass() {
        if (!hasRecordAudioPermission() || _state.value.isMuted) return

        audioRecorder.startCapturingUtterance(
            onSpeechStarted = {
                _state.update {
                    it.copy(
                        status = VoiceSessionStatus.LISTENING,
                        liveAiTranscript = "",
                        statusHint = "Listening… tap orb to send"
                    )
                }
            },
            onResult = { captureResult ->
                when (captureResult) {
                    is UtteranceCaptureResult.CapturedWav -> {
                        _state.update {
                            it.copy(
                                status = VoiceSessionStatus.PROCESSING,
                                liveUserTranscript = "",
                                liveAiTranscript = "",
                                waveformEnergy = 0.44f,
                                statusHint = "Thinking…"
                            )
                        }
                        onUserVoiceAudioCaptured?.invoke(captureResult.wavBytes, captureResult.durationMs)
                    }

                    UtteranceCaptureResult.SilenceTimeout -> {
                        if (autoContinueConversation && !_state.value.isMuted) {
                            // Seamlessly keep listening without interrupting the user unless paused
                            beginRawAudioListeningPass()
                        } else {
                            _state.update {
                                it.copy(
                                    status = VoiceSessionStatus.PAUSED,
                                    waveformEnergy = 0.14f,
                                    statusHint = "Tap orb to talk"
                                )
                            }
                        }
                    }

                    is UtteranceCaptureResult.HardwareUnavailable -> {
                        Log.w(TAG, "Raw AudioRecord unavailable (${captureResult.reason}); trying SpeechRecognizer")
                        if (SpeechRecognizer.isRecognitionAvailable(appContext)) {
                            beginSpeechRecognizerPass(isInlineDictation = false)
                        } else {
                            _state.update {
                                it.copy(
                                    status = VoiceSessionStatus.PAUSED,
                                    waveformEnergy = 0.14f,
                                    errorMessage = "${captureResult.reason} You can still use + Ask Nova AI below to hear Nova speak!",
                                    statusHint = "Use + Ask Nova AI or tap orb to retry mic"
                                )
                            }
                        }
                    }
                }
            }
        )
    }

    fun finishCurrentListeningTurnEarly() {
        if (_state.value.status == VoiceSessionStatus.LISTENING) {
            audioRecorder.finishCurrentUtteranceNow()
            scope.launch(Dispatchers.Main) {
                runCatching { speechRecognizer?.stopListening() }
            }
        }
    }

    fun toggleMute() {
        val currentlyMuted = _state.value.isMuted
        if (!currentlyMuted) {
            // Mute microphone immediately
            autoContinueConversation = false
            audioRecorder.cancelCapture()
            scope.launch(Dispatchers.Main) {
                runCatching {
                    speechRecognizer?.cancel()
                }
            }
            _state.update {
                it.copy(
                    isMuted = true,
                    waveformEnergy = if (it.status == VoiceSessionStatus.AI_SPEAKING) it.waveformEnergy else 0.12f,
                    status = if (it.status == VoiceSessionStatus.LISTENING) VoiceSessionStatus.PAUSED else it.status,
                    statusHint = "Mic muted · Tap mic to unmute"
                )
            }
        } else {
            // Unmute microphone and resume listening if not currently speaking
            _state.update { it.copy(isMuted = false, errorMessage = null) }
            autoContinueConversation = true
            if (_state.value.status != VoiceSessionStatus.AI_SPEAKING &&
                _state.value.status != VoiceSessionStatus.PROCESSING
            ) {
                startVoiceConversation()
            }
        }
    }

    fun prepareForTypedOrPhotoVoiceTurn(displayPrompt: String) {
        stopSpeakingInternal()
        audioRecorder.cancelCapture()
        startTimerAndWaveformDriver()
        _state.update {
            it.copy(
                status = VoiceSessionStatus.PROCESSING,
                liveUserTranscript = displayPrompt,
                liveAiTranscript = "",
                errorMessage = null,
                waveformEnergy = 0.46f,
                statusHint = "Thinking…"
            )
        }
    }

    fun startInlineDictation() {
        if (!hasRecordAudioPermission()) return
        scope.launch(Dispatchers.Main) {
            autoContinueConversation = false
            stopSpeakingInternal()
            _state.update {
                it.copy(
                    status = VoiceSessionStatus.LISTENING,
                    errorMessage = null,
                    statusHint = "Listening for voice input…"
                )
            }
            if (SpeechRecognizer.isRecognitionAvailable(appContext)) {
                beginSpeechRecognizerPass(isInlineDictation = true)
            } else {
                // Fallback to raw AudioRecord + Gemini multilingual transcription!
                audioRecorder.startCapturingUtterance(
                    onSpeechStarted = {},
                    onResult = { res ->
                        if (res is UtteranceCaptureResult.CapturedWav) {
                            scope.launch {
                                val text = aiService.transcribeUserAudio(
                                    res.wavBytes,
                                    _state.value.selectedLanguage
                                )
                                _state.update {
                                    it.copy(status = VoiceSessionStatus.IDLE, waveformEnergy = 0.12f)
                                }
                                if (text.isNotBlank()) {
                                    onDictationResult?.invoke(text)
                                }
                            }
                        } else {
                            _state.update {
                                it.copy(status = VoiceSessionStatus.IDLE, waveformEnergy = 0.12f)
                            }
                        }
                    }
                )
            }
        }
    }

    private fun beginSpeechRecognizerPass(isInlineDictation: Boolean) {
        scope.launch(Dispatchers.Main) {
            try {
                speechRecognizer?.cancel()
                speechRecognizer?.destroy()
                val recognizer = SpeechRecognizer.createSpeechRecognizer(appContext)
                speechRecognizer = recognizer

                val langTag = _state.value.selectedLanguage.bcp47Code ?: Locale.getDefault().toLanguageTag()
                val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                    putExtra(
                        RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                        RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
                    )
                    putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE, langTag)
                    putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
                }

                recognizer.setRecognitionListener(object : RecognitionListener {
                    override fun onReadyForSpeech(params: Bundle?) {
                        _state.update {
                            it.copy(status = VoiceSessionStatus.LISTENING, errorMessage = null)
                        }
                    }

                    override fun onBeginningOfSpeech() {
                        _state.update { it.copy(waveformEnergy = 0.72f) }
                    }

                    override fun onRmsChanged(rmsdB: Float) {
                        val normalized = ((rmsdB + 2f) / 12f).coerceIn(0.16f, 1.0f)
                        _state.update { it.copy(waveformEnergy = normalized) }
                    }

                    override fun onBufferReceived(buffer: ByteArray?) = Unit

                    override fun onEndOfSpeech() {
                        _state.update { it.copy(waveformEnergy = 0.28f) }
                    }

                    override fun onError(error: Int) {
                        Log.w(TAG, "SpeechRecognizer code=$error")
                        if (isInlineDictation) {
                            _state.update {
                                it.copy(status = VoiceSessionStatus.IDLE, waveformEnergy = 0.12f)
                            }
                            return
                        }
                        if (error != SpeechRecognizer.ERROR_CLIENT) {
                            _state.update {
                                it.copy(
                                    status = VoiceSessionStatus.PAUSED,
                                    waveformEnergy = 0.14f,
                                    statusHint = "Tap orb to speak again"
                                )
                            }
                        }
                    }

                    override fun onPartialResults(partialResults: Bundle?) {
                        val partial = partialResults
                            ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                            ?.firstOrNull()
                            .orEmpty()
                        if (partial.isNotBlank()) {
                            _state.update { it.copy(liveUserTranscript = partial) }
                        }
                    }

                    override fun onResults(results: Bundle?) {
                        val recognized = results
                            ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                            ?.firstOrNull()
                            ?.trim()
                            .orEmpty()

                        if (recognized.isBlank()) {
                            _state.update {
                                it.copy(
                                    status = if (isInlineDictation) VoiceSessionStatus.IDLE else VoiceSessionStatus.PAUSED,
                                    waveformEnergy = 0.12f
                                )
                            }
                            return
                        }

                        if (isInlineDictation) {
                            _state.update {
                                it.copy(
                                    status = VoiceSessionStatus.IDLE,
                                    liveUserTranscript = recognized,
                                    waveformEnergy = 0.12f
                                )
                            }
                            onDictationResult?.invoke(recognized)
                        } else {
                            _state.update {
                                it.copy(
                                    status = VoiceSessionStatus.PROCESSING,
                                    liveUserTranscript = recognized,
                                    liveAiTranscript = "",
                                    waveformEnergy = 0.45f,
                                    statusHint = "Thinking…"
                                )
                            }
                            onUserVoiceTurnRecognized?.invoke(recognized)
                        }
                    }

                    override fun onEvent(eventType: Int, params: Bundle?) = Unit
                })

                recognizer.startListening(intent)
            } catch (e: Exception) {
                Log.e(TAG, "Failed starting SpeechRecognizer", e)
                _state.update {
                    it.copy(
                        status = VoiceSessionStatus.PAUSED,
                        statusHint = "Tap orb to retry"
                    )
                }
            }
        }
    }

    fun updateLiveUserTranscript(transcript: String) {
        if (transcript.isNotBlank()) {
            _state.update { it.copy(liveUserTranscript = transcript) }
        }
    }

    fun updateLiveAiTranscript(partialReply: String) {
        _state.update {
            it.copy(
                liveAiTranscript = partialReply,
                waveformEnergy = if (it.status == VoiceSessionStatus.AI_SPEAKING) it.waveformEnergy else 0.48f,
                statusHint = if (it.status == VoiceSessionStatus.AI_SPEAKING) "Tap orb to interrupt" else "Thinking…"
            )
        }
    }

    fun resumeListeningAfterSilentAudio() {
        if (autoContinueConversation && !_state.value.isMuted && hasRecordAudioPermission()) {
            _state.update {
                it.copy(
                    status = VoiceSessionStatus.LISTENING,
                    statusHint = "Listening…"
                )
            }
            beginRawAudioListeningPass()
        } else {
            _state.update {
                it.copy(
                    status = VoiceSessionStatus.PAUSED,
                    waveformEnergy = 0.14f,
                    statusHint = "Tap orb to talk"
                )
            }
        }
    }

    /**
     * Speaks Nova's voice reply using pipelined `gemini-3.1-flash-tts-preview` neural speech audio,
     * falling back automatically to multilingual Android TTS only if neural audio is unavailable.
     */
    fun speakVoiceReply(replyText: String, resumeListeningWhenFinished: Boolean = true) {
        val cleanText = sanitizeForSpeech(replyText)
        if (cleanText.isBlank()) {
            handleSpeechPlaybackCompleted(isVoiceSessionReply = resumeListeningWhenFinished)
            return
        }

        autoContinueConversation = resumeListeningWhenFinished && !_state.value.isMuted
        audioRecorder.cancelCapture()
        stopSpeakingInternal()

        _state.update {
            it.copy(
                status = VoiceSessionStatus.AI_SPEAKING,
                liveAiTranscript = replyText,
                errorMessage = null,
                waveformEnergy = 0.56f,
                statusHint = "Tap orb to interrupt"
            )
        }

        playWithNeuralPipelineOrFallback(
            cleanText = cleanText,
            isVoiceSessionReply = true,
            messageId = null,
            recordingId = null
        )
    }

    fun previewSelectedVoicePersona(persona: NovaVoicePersona) {
        selectVoicePersona(persona)
        val sampleLine = when (_state.value.selectedLanguage) {
            NovaVoiceLanguage.TELUGU -> "నమస్తే! నేను నోవా ఏఐ. ఈరోజు మనం ఏ విషయం గురించి మాట్లాడుకుందాం?"
            NovaVoiceLanguage.HINDI -> "नमस्ते! मैं नोवा एआई हूँ। बताइए, आज आपके मन में क्या चल रहा है?"
            else -> "Hey there! I'm Nova AI. What's on your mind today?"
        }
        speakText(text = sampleLine)
    }

    private fun playWithNeuralPipelineOrFallback(
        cleanText: String,
        isVoiceSessionReply: Boolean,
        messageId: String?,
        recordingId: String?
    ) {
        val persona = _state.value.selectedPersona
        val segments = splitIntoNaturalSpeechSegments(cleanText)
        if (segments.isEmpty()) {
            handleSpeechPlaybackCompleted(isVoiceSessionReply)
            return
        }

        neuralSynthesisJob?.cancel()
        val generationId = neuralAudioPlayer.startStreamingSession(
            onSessionFinished = {
                handleSpeechPlaybackCompleted(isVoiceSessionReply)
            }
        )

        neuralSynthesisJob = scope.launch {
            var anyClipSucceeded = false
            try {
                for ((index, segment) in segments.withIndex()) {
                    if (!isActive) break
                    val clip = aiService.synthesizeNeuralSpeech(
                        spokenText = segment,
                        persona = persona
                    )
                    if (clip != null) {
                        anyClipSucceeded = true
                        neuralAudioPlayer.enqueueClip(generationId, clip)
                    } else if (index == 0 && !anyClipSucceeded) {
                        // First segment failed neural synthesis (e.g., offline) — immediately switch to multilingual Android TTS
                        neuralAudioPlayer.stopImmediately()
                        speakViaFallbackAndroidTts(
                            cleanText = cleanText,
                            isVoiceSessionReply = isVoiceSessionReply,
                            messageId = messageId,
                            recordingId = recordingId
                        )
                        return@launch
                    }
                }
                if (anyClipSucceeded) {
                    neuralAudioPlayer.markAllClipsEnqueued(generationId)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Neural speech pipeline error; falling back to on-device TTS", e)
                if (!anyClipSucceeded) {
                    neuralAudioPlayer.stopImmediately()
                    speakViaFallbackAndroidTts(
                        cleanText = cleanText,
                        isVoiceSessionReply = isVoiceSessionReply,
                        messageId = messageId,
                        recordingId = recordingId
                    )
                } else {
                    neuralAudioPlayer.markAllClipsEnqueued(generationId)
                }
            }
        }
    }

    private fun speakViaFallbackAndroidTts(
        cleanText: String,
        isVoiceSessionReply: Boolean,
        messageId: String?,
        recordingId: String?
    ) {
        if (!fallbackTtsReady) {
            handleSpeechPlaybackCompleted(isVoiceSessionReply)
            return
        }
        try {
            val targetLocale = detectPreferredLocaleForText(cleanText)
            configureBestFallbackVoice(targetLocale)
            fallbackTts?.stop()
            val prefix = if (isVoiceSessionReply) VOICE_REPLY_UTTERANCE_PREFIX else "tts_"
            val utteranceId = "$prefix${UUID.randomUUID()}"
            _state.update {
                it.copy(
                    speakingMessageId = messageId,
                    playingRecordingId = recordingId,
                    status = when {
                        recordingId != null -> VoiceSessionStatus.PLAYING_RECORDING
                        isVoiceSessionReply -> VoiceSessionStatus.AI_SPEAKING
                        else -> it.status
                    },
                    waveformEnergy = 0.68f
                )
            }
            fallbackTts?.speak(cleanText.take(3900), TextToSpeech.QUEUE_FLUSH, null, utteranceId)
        } catch (e: Exception) {
            Log.e(TAG, "Fallback TTS failed", e)
            handleSpeechPlaybackCompleted(isVoiceSessionReply)
        }
    }

    private fun handleSpeechPlaybackCompleted(isVoiceSessionReply: Boolean) {
        if (isVoiceSessionReply && autoContinueConversation && !_state.value.isMuted && hasRecordAudioPermission()) {
            scope.launch(Dispatchers.Main) {
                delay(220L)
                if (autoContinueConversation && !_state.value.isMuted) {
                    _state.update {
                        it.copy(
                            speakingMessageId = null,
                            playingRecordingId = null,
                            status = VoiceSessionStatus.LISTENING,
                            waveformEnergy = 0.18f,
                            statusHint = "Listening…"
                        )
                    }
                    beginRawAudioListeningPass()
                }
            }
        } else {
            _state.update { current ->
                current.copy(
                    speakingMessageId = null,
                    playingRecordingId = null,
                    waveformEnergy = 0.14f,
                    status = if (current.isMuted) {
                        VoiceSessionStatus.PAUSED
                    } else if (current.status == VoiceSessionStatus.PLAYING_RECORDING ||
                        current.status == VoiceSessionStatus.AI_SPEAKING
                    ) {
                        VoiceSessionStatus.IDLE
                    } else {
                        current.status
                    },
                    statusHint = if (current.isMuted) "Mic muted · Tap mic to unmute" else "Tap orb to talk"
                )
            }
        }
    }

    private fun detectPreferredLocaleForText(text: String): Locale {
        return when {
            text.any { it in '\u0C00'..'\u0C7F' } -> Locale.forLanguageTag("te-IN")
            text.any { it in '\u0900'..'\u097F' } -> Locale.forLanguageTag("hi-IN")
            else -> Locale.US
        }
    }

    /**
     * Splits text into natural conversational speech units:
     * - Segment 0 is a concise lead sentence (~40–160 chars) for snappy Time-to-First-Audio (~1.3s).
     * - Subsequent segments group complete sentences (~220–560 chars) for rich, uninterrupted prosody.
     */
    private fun splitIntoNaturalSpeechSegments(cleanText: String): List<String> {
        if (cleanText.length <= 210) return listOf(cleanText)

        val rawSentences = cleanText
            .split(Regex("(?<=[.!?।\\n])\\s+"))
            .map { it.trim() }
            .filter { it.isNotBlank() }

        if (rawSentences.size <= 1) {
            return cleanText.chunked(480)
        }

        val result = mutableListOf<String>()
        val currentBucket = StringBuilder()

        for (sentence in rawSentences) {
            val targetLimit = if (result.isEmpty()) 155 else 480
            if (currentBucket.isEmpty()) {
                currentBucket.append(sentence)
            } else if (currentBucket.length + 1 + sentence.length <= targetLimit) {
                currentBucket.append(' ').append(sentence)
            } else {
                result.add(currentBucket.toString())
                currentBucket.clear()
                currentBucket.append(sentence)
            }
        }
        if (currentBucket.isNotEmpty()) {
            result.add(currentBucket.toString())
        }
        return result
    }

    fun onVoiceAiError(message: String) {
        autoContinueConversation = false
        stopSpeakingInternal()
        _state.update {
            it.copy(
                status = VoiceSessionStatus.ERROR,
                waveformEnergy = 0.14f,
                errorMessage = message,
                statusHint = "Tap orb to retry"
            )
        }
    }

    fun pauseVoiceSession() {
        autoContinueConversation = false
        audioRecorder.cancelCapture()
        scope.launch(Dispatchers.Main) {
            runCatching {
                speechRecognizer?.stopListening()
                speechRecognizer?.cancel()
            }
        }
        stopSpeakingInternal()
        _state.update {
            it.copy(
                status = VoiceSessionStatus.PAUSED,
                waveformEnergy = 0.14f,
                statusHint = if (it.isMuted) "Mic muted · Tap mic to unmute" else "Paused · Tap orb to continue"
            )
        }
    }

    fun resumeVoiceSession() {
        _state.update { it.copy(isMuted = false) }
        startVoiceConversation()
    }

    fun stopAndFinishSession() {
        autoContinueConversation = false
        teardownActiveVoiceResources()
        _state.update { current ->
            current.copy(
                status = VoiceSessionStatus.IDLE,
                isMuted = false,
                pendingVoicePhoto = null,
                elapsedSeconds = 0L,
                waveformEnergy = 0.14f,
                speakingMessageId = null,
                playingRecordingId = null,
                errorMessage = null,
                statusHint = "Tap orb to talk"
            )
        }
    }

    fun cancelVoiceSession() {
        stopAndFinishSession()
    }

    private fun teardownActiveVoiceResources() {
        stopTimerAndWaveformDriver()
        audioRecorder.cancelCapture()
        stopSpeakingInternal()
        scope.launch(Dispatchers.Main) {
            runCatching {
                speechRecognizer?.cancel()
                speechRecognizer?.destroy()
                speechRecognizer = null
            }
        }
    }

    fun speakText(
        text: String,
        messageId: String? = null,
        recordingId: String? = null
    ) {
        val cleanText = sanitizeForSpeech(text)
        if (cleanText.isBlank()) return

        autoContinueConversation = false
        audioRecorder.cancelCapture()
        stopSpeakingInternal()

        if (recordingId != null) {
            startTimerAndWaveformDriver()
        }
        _state.update {
            it.copy(
                speakingMessageId = messageId,
                playingRecordingId = recordingId,
                liveAiTranscript = if (recordingId != null) cleanText.take(260) else it.liveAiTranscript,
                status = if (recordingId != null) VoiceSessionStatus.PLAYING_RECORDING else it.status,
                waveformEnergy = 0.58f
            )
        }

        playWithNeuralPipelineOrFallback(
            cleanText = cleanText,
            isVoiceSessionReply = false,
            messageId = messageId,
            recordingId = recordingId
        )
    }

    fun stopSpeaking() {
        autoContinueConversation = false
        stopSpeakingInternal()
        _state.update {
            it.copy(
                speakingMessageId = null,
                playingRecordingId = null,
                waveformEnergy = if (it.status == VoiceSessionStatus.LISTENING) it.waveformEnergy else 0.14f,
                status = if (it.status == VoiceSessionStatus.PLAYING_RECORDING ||
                    it.status == VoiceSessionStatus.AI_SPEAKING
                ) {
                    VoiceSessionStatus.IDLE
                } else {
                    it.status
                }
            )
        }
    }

    private fun stopSpeakingInternal() {
        neuralSynthesisJob?.cancel()
        neuralSynthesisJob = null
        neuralAudioPlayer.stopImmediately()
        runCatching { fallbackTts?.stop() }
    }

    private fun sanitizeForSpeech(raw: String): String {
        return raw
            .replace(Regex("```[\\s\\S]*?```"), " ")
            .replace(Regex("\\*+"), "")
            .replace(Regex("#+\\s*"), "")
            .replace("`", "")
            .replace(Regex("\\s{2,}"), " ")
            .trim()
    }

    private fun startTimerAndWaveformDriver() {
        if (timerJob?.isActive != true) {
            timerJob = scope.launch {
                while (isActive) {
                    delay(1000L)
                    val s = _state.value.status
                    if (s == VoiceSessionStatus.LISTENING ||
                        s == VoiceSessionStatus.PROCESSING ||
                        s == VoiceSessionStatus.AI_SPEAKING
                    ) {
                        _state.update { it.copy(elapsedSeconds = it.elapsedSeconds + 1L) }
                    }
                }
            }
        }
        if (pulseJob?.isActive != true) {
            pulseJob = scope.launch {
                var tick = 0.0
                while (isActive) {
                    delay(75L)
                    tick += 0.30
                    when (_state.value.status) {
                        VoiceSessionStatus.PROCESSING -> {
                            val thinkWave = 0.38f + 0.16f * sin(tick * 1.5).toFloat()
                            _state.update { it.copy(waveformEnergy = thinkWave.coerceIn(0.22f, 0.62f)) }
                        }
                        VoiceSessionStatus.LISTENING,
                        VoiceSessionStatus.AI_SPEAKING,
                        VoiceSessionStatus.PLAYING_RECORDING -> {
                            // Driven directly by real-time mic & neural AudioTrack PCM RMS!
                        }
                        else -> {
                            _state.update { it.copy(waveformEnergy = 0.14f) }
                        }
                    }
                }
            }
        }
    }

    private fun stopTimerAndWaveformDriver() {
        timerJob?.cancel()
        timerJob = null
        pulseJob?.cancel()
        pulseJob = null
    }

    fun release() {
        autoContinueConversation = false
        teardownActiveVoiceResources()
        runCatching { fallbackTts?.shutdown() }
    }
}
