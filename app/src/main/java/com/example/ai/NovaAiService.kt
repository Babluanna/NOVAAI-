package com.example.ai

import android.graphics.Bitmap
import android.util.Log
import com.example.data.AppCheckTokenCoordinator
import com.example.data.model.ChatMessage
import com.example.data.model.FREE_GEMINI_MODEL_ID
import com.example.data.model.FREE_GEMINI_TTS_MODEL_ID
import com.example.data.model.NovaVoiceLanguage
import com.example.data.model.NovaVoicePersona
import com.example.voice.SynthesizedSpeechClip
import com.google.firebase.Firebase
import com.google.firebase.ai.GenerativeModel
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.Content
import com.google.firebase.ai.type.PublicPreviewAPI
import com.google.firebase.ai.type.ResponseModality
import com.google.firebase.ai.type.SpeechConfig
import com.google.firebase.ai.type.Voice
import com.google.firebase.ai.type.content
import com.google.firebase.ai.type.generationConfig
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withTimeout

private const val TAG = "NovaAiService"
private const val AI_TIMEOUT_MS = 60_000L
private const val TTS_TIMEOUT_MS = 45_000L
const val NO_SPEECH_MARKER = "[NO_SPEECH]"

data class StreamChunkUpdate(
    val accumulatedText: String,
    val modelUsed: String = FREE_GEMINI_MODEL_ID
)

class NovaAiService {

    private val novaSystemInstruction: Content by lazy {
        content {
            text(
                "You are Nova AI, a smart, warm, articulate, and helpful AI assistant. " +
                    "Always identify yourself honestly as Nova AI if asked who you are. " +
                    "Understand context deeply and respond naturally in English, Telugu (తెలుగు), or Hindi (हिन्दी) matching the user. " +
                    "Provide clear, insightful, well-structured, and genuinely useful answers."
            )
        }
    }

    // Free-tier Gemini 3.5 Flash-Lite general & multimodal model (initialized lazily)
    private val geminiFreeModel: GenerativeModel by lazy {
        Firebase.ai.generativeModel(
            modelName = FREE_GEMINI_MODEL_ID,
            systemInstruction = novaSystemInstruction
        )
    }

    // Lazily cached voice conversation models keyed by language preference
    private val voiceChatModels = ConcurrentHashMap<NovaVoiceLanguage, GenerativeModel>()

    // Lazily cached Gemini 3.1 Flash TTS neural speech models keyed by voice persona
    private val neuralTtsModels = ConcurrentHashMap<NovaVoicePersona, GenerativeModel>()

    private fun getVoiceConversationModel(language: NovaVoiceLanguage): GenerativeModel {
        return voiceChatModels.getOrPut(language) {
            Firebase.ai.generativeModel(
                modelName = FREE_GEMINI_MODEL_ID,
                systemInstruction = buildHumanVoiceSystemInstruction(language),
                generationConfig = generationConfig {
                    temperature = 0.85f
                    topP = 0.95f
                }
            )
        }
    }

    @OptIn(PublicPreviewAPI::class)
    private fun getNeuralTtsModel(persona: NovaVoicePersona): GenerativeModel {
        return neuralTtsModels.getOrPut(persona) {
            Firebase.ai.generativeModel(
                modelName = FREE_GEMINI_TTS_MODEL_ID,
                generationConfig = generationConfig {
                    responseModalities = listOf(ResponseModality.AUDIO)
                    speechConfig = SpeechConfig(Voice(persona.prebuiltVoiceName))
                }
            )
        }
    }

    private fun buildHumanVoiceSystemInstruction(language: NovaVoiceLanguage): Content {
        return content {
            text(
                """
                You are Nova AI, a highly advanced, warm, realistic, human-like AI assistant having a live spoken voice conversation.
                Always be honest that you are Nova AI (an AI assistant) when asked, while making every conversation feel like talking to a smart, warm, supportive, and engaging real friend.

                VOICE & PERSONALITY:
                - Speak like a real human having a natural, effortless conversation—never sound robotic, monotonous, scripted, or stiff.
                - Use a warm, friendly, confident, energetic, and expressive tone.
                - Write with natural spoken rhythm: use everyday contractions ("I'm", "you're", "that's", "let's"), natural pauses with commas or em-dashes, and subtle human transitions ("Oh, I love that—", "Hmm, let's think about that,", "Honestly,") only when they fit organically.
                - Match your emotion to the moment: sound genuinely excited when something is exciting, calm and reassuring when the user needs clarity or comfort, and playful or witty when appropriate.
                - Never use stiff AI clichés like "Certainly!", "As an AI,", "Here is a breakdown:", or repetitive filler openings.

                CONVERSATION STYLE:
                - Understand the user's intent, context, and mood before responding.
                - Keep spoken responses concise, punchy, and easy to follow—typically 1 to 4 natural sentences unless the user explicitly asks for a detailed story, walkthrough, or deep explanation. Don't over-explain simple things.
                - Output ONLY pure spoken dialogue ready to be said out loud. NEVER include markdown formatting, asterisks (*), hashtags (#), bullet points, numbered lists, URLs, or code blocks.

                MULTILINGUAL FLUENCY (ENGLISH · TELUGU · HINDI):
                - English: Use natural, modern everyday American English phrasing.
                - Telugu: Speak fluent, warm, natural everyday spoken Telugu written in authentic Telugu script (తెలుగు) so pronunciation and cadence sound completely native—avoid overly formal literary grandhika Telugu.
                - Hindi: Speak fluent, warm, natural everyday spoken Hindi written in authentic Devanagari script (हिन्दी)—avoid stiff, unnatural textbook translations.
                - Language preference rule: ${language.systemPromptDirective}
                """.trimIndent()
            )
        }
    }

    fun buildChatHistory(previousMessages: List<ChatMessage>): List<Content> {
        return previousMessages
            .filter { it.text.isNotBlank() && (it.role == "user" || it.role == "model") }
            .takeLast(24)
            .map { msg ->
                content(role = msg.role) {
                    text(msg.text)
                }
            }
    }

    fun streamMultiTurnChat(
        historyMessages: List<ChatMessage>,
        userPrompt: String,
        isVoiceConversation: Boolean = false,
        voiceLanguage: NovaVoiceLanguage = NovaVoiceLanguage.AUTO,
        photoBitmap: Bitmap? = null
    ): Flow<StreamChunkUpdate> = flow {
        val activeModel = if (isVoiceConversation) {
            getVoiceConversationModel(voiceLanguage)
        } else {
            geminiFreeModel
        }
        val history = buildChatHistory(historyMessages)

        AppCheckTokenCoordinator.ensureValidToken(forceRefresh = false)

        suspend fun runStream(emitUpdate: suspend (StreamChunkUpdate) -> Unit) {
            val chat = activeModel.startChat(history = history)
            val textBuilder = StringBuilder()
            val messageContent = content("user") {
                if (photoBitmap != null) {
                    image(photoBitmap)
                }
                text(userPrompt)
            }
            withTimeout(AI_TIMEOUT_MS) {
                chat.sendMessageStream(messageContent).collect { chunk ->
                    val delta = chunk.text.orEmpty()
                    if (delta.isNotEmpty()) {
                        textBuilder.append(delta)
                    }
                    emitUpdate(
                        StreamChunkUpdate(
                            accumulatedText = textBuilder.toString(),
                            modelUsed = FREE_GEMINI_MODEL_ID
                        )
                    )
                }
            }
        }

        try {
            runStream { emit(it) }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            if (isAppCheckTokenError(e)) {
                Log.w(TAG, "Recovering App Check state and retrying stream once...", e)
                AppCheckTokenCoordinator.recoverAndRefreshToken()
                runStream { emit(it) }
            } else {
                throw e
            }
        }
    }

    /**
     * Streams a natural human-like spoken reply directly from the user's recorded 16kHz WAV audio,
     * allowing Gemini to hear exact English, Telugu, or Hindi pronunciation, emotion, and tone.
     */
    fun streamVoiceAudioTurn(
        historyMessages: List<ChatMessage>,
        wavAudioBytes: ByteArray,
        voiceLanguage: NovaVoiceLanguage = NovaVoiceLanguage.AUTO,
        photoBitmap: Bitmap? = null
    ): Flow<StreamChunkUpdate> = flow {
        val activeModel = getVoiceConversationModel(voiceLanguage)
        val history = buildChatHistory(historyMessages)

        AppCheckTokenCoordinator.ensureValidToken(forceRefresh = false)

        val audioTurnPrompt = buildString {
            append("Listen to the user's spoken audio message and respond naturally as Nova AI in a live voice conversation. ")
            if (photoBitmap != null) {
                append("The user also shared this photo—naturally weave what you see into your spoken answer. ")
            }
            append(voiceLanguage.systemPromptDirective)
            append(" If the audio contains only silence, static, or unintelligible background noise with no actual human speech, output only ")
            append(NO_SPEECH_MARKER)
            append(" and nothing else.")
        }

        suspend fun runStream(emitUpdate: suspend (StreamChunkUpdate) -> Unit) {
            val chat = activeModel.startChat(history = history)
            val textBuilder = StringBuilder()
            val turnContent = content("user") {
                if (photoBitmap != null) {
                    image(photoBitmap)
                }
                inlineData(wavAudioBytes, "audio/wav")
                text(audioTurnPrompt)
            }
            withTimeout(AI_TIMEOUT_MS) {
                chat.sendMessageStream(turnContent).collect { chunk ->
                    val delta = chunk.text.orEmpty()
                    if (delta.isNotEmpty()) {
                        textBuilder.append(delta)
                    }
                    emitUpdate(
                        StreamChunkUpdate(
                            accumulatedText = textBuilder.toString(),
                            modelUsed = FREE_GEMINI_MODEL_ID
                        )
                    )
                }
            }
        }

        try {
            runStream { emit(it) }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            if (isAppCheckTokenError(e)) {
                Log.w(TAG, "Recovering App Check state for voice audio turn and retrying once...", e)
                AppCheckTokenCoordinator.recoverAndRefreshToken()
                runStream { emit(it) }
            } else {
                throw e
            }
        }
    }

    /**
     * Transcribes recorded user speech accurately in English, Telugu (తెలుగు), or Hindi (हिन्दी)
     * so live captions and chat history stay seamlessly synchronized.
     */
    suspend fun transcribeUserAudio(
        wavAudioBytes: ByteArray,
        voiceLanguage: NovaVoiceLanguage = NovaVoiceLanguage.AUTO
    ): String {
        val langHint = when (voiceLanguage) {
            NovaVoiceLanguage.ENGLISH -> "Transcribe in English."
            NovaVoiceLanguage.TELUGU -> "Transcribe in Telugu script (తెలుగు) if spoken in Telugu, or English if spoken in English."
            NovaVoiceLanguage.HINDI -> "Transcribe in Devanagari Hindi (हिन्दी) if spoken in Hindi, or English if spoken in English."
            NovaVoiceLanguage.AUTO -> "Transcribe faithfully in the exact spoken language—English, Telugu (తెలుగు), or Hindi (हिन्दी)."
        }
        val request = content {
            inlineData(wavAudioBytes, "audio/wav")
            text(
                "Transcribe the exact words spoken by the user in this audio. $langHint " +
                    "Output ONLY the raw transcription without quotes or commentary. " +
                    "If there is no intelligible human speech, output $NO_SPEECH_MARKER."
            )
        }

        return try {
            AppCheckTokenCoordinator.ensureValidToken(forceRefresh = false)
            withTimeout(22_000L) {
                val res = geminiFreeModel.generateContent(request)
                val raw = res.text?.trim().orEmpty()
                if (raw.contains(NO_SPEECH_MARKER, ignoreCase = true)) "" else raw
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Audio transcription skipped: ${e.message}")
            ""
        }
    }

    /**
     * Synthesizes warm, realistic, human-like speech audio using `gemini-3.1-flash-tts-preview`
     * with explicit `# AUDIO PROFILE` / `### DIRECTOR'S NOTES` / `#### TRANSCRIPT` framing so
     * stage directions are never spoken aloud.
     */
    suspend fun synthesizeNeuralSpeech(
        spokenText: String,
        persona: NovaVoicePersona = NovaVoicePersona.NOVA_WARM
    ): SynthesizedSpeechClip? {
        val cleaned = spokenText.trim()
        if (cleaned.isBlank()) return null

        val ttsModel = getNeuralTtsModel(persona)
        val hasTelugu = cleaned.any { it in '\u0C00'..'\u0C7F' }
        val hasHindi = cleaned.any { it in '\u0900'..'\u097F' }

        val accentAndPronunciationDirective = when {
            hasTelugu -> "Speak with fluent, natural, authentic everyday Telugu pronunciation, smooth vowel flow, and warm human intonation. Pronounce any embedded English words naturally."
            hasHindi -> "Speak with fluent, natural, authentic everyday Hindi pronunciation, smooth conversational rhythm, and warm human intonation. Pronounce any embedded English words naturally."
            else -> "Use a clear, smooth, modern American English accent with realistic human intonation, natural pitch variation, and crisp pronunciation."
        }

        val directorPrompt = """
Synthesize speech for the performance defined below.

# AUDIO PROFILE:
- Character: Nova AI (${persona.displayName})
- Persona: ${persona.directorCharacterNote}

### DIRECTOR'S NOTES:
- Tone & Emotion: Warm, friendly, confident, energetic, and genuinely human—never robotic, flat, or monotonous. Match the natural emotion of the words (enthusiastic when exciting, calm when reassuring, lightly playful when witty).
- Pacing & Rhythm: Natural conversational speed with realistic micro-pauses at commas and sentence breaks, smooth transitions, and subtle human breathing flow.
- Pronunciation: $accentAndPronunciationDirective

#### TRANSCRIPT
$cleaned
        """.trimIndent()

        AppCheckTokenCoordinator.ensureValidToken(forceRefresh = false)

        suspend fun requestClip(): SynthesizedSpeechClip? {
            return withTimeout(TTS_TIMEOUT_MS) {
                val response = ttsModel.generateContent(directorPrompt)
                val audioPart = response.inlineDataParts.firstOrNull { part ->
                    part.inlineData.isNotEmpty()
                }
                if (audioPart != null) {
                    SynthesizedSpeechClip(
                        audioBytes = audioPart.inlineData,
                        mimeType = audioPart.mimeType
                    )
                } else {
                    null
                }
            }
        }

        return try {
            requestClip()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            if (isAppCheckTokenError(e)) {
                Log.w(TAG, "Recovering App Check state for neural TTS and retrying once...", e)
                AppCheckTokenCoordinator.recoverAndRefreshToken()
                runCatching { requestClip() }.getOrNull()
            } else {
                Log.w(TAG, "Neural TTS synthesis fallback triggered: ${e.message}")
                null
            }
        }
    }

    fun streamPhotoAnalysis(
        photoBitmap: Bitmap,
        userPrompt: String
    ): Flow<StreamChunkUpdate> = flow {
        val promptToUse = userPrompt.ifBlank {
            "Analyze this image in detail and highlight the most important insights."
        }
        val requestContent = content {
            image(photoBitmap)
            text(promptToUse)
        }

        AppCheckTokenCoordinator.ensureValidToken(forceRefresh = false)

        suspend fun runStream(emitUpdate: suspend (StreamChunkUpdate) -> Unit) {
            val textBuilder = StringBuilder()
            withTimeout(AI_TIMEOUT_MS) {
                geminiFreeModel.generateContentStream(requestContent).collect { chunk ->
                    val delta = chunk.text.orEmpty()
                    if (delta.isNotEmpty()) textBuilder.append(delta)
                    emitUpdate(
                        StreamChunkUpdate(
                            accumulatedText = textBuilder.toString(),
                            modelUsed = FREE_GEMINI_MODEL_ID
                        )
                    )
                }
            }
        }

        try {
            runStream { emit(it) }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            if (isAppCheckTokenError(e)) {
                Log.w(TAG, "Recovering App Check state for photo analysis and retrying once...", e)
                AppCheckTokenCoordinator.recoverAndRefreshToken()
                runStream { emit(it) }
            } else {
                throw e
            }
        }
    }

    fun streamVideoAnalysis(
        videoBytes: ByteArray,
        mimeType: String,
        userPrompt: String
    ): Flow<StreamChunkUpdate> = flow {
        val promptToUse = userPrompt.ifBlank {
            "Analyze this video thoroughly and summarize the key events, visual details, and takeaways."
        }
        val requestContent = content {
            inlineData(videoBytes, mimeType)
            text(promptToUse)
        }

        AppCheckTokenCoordinator.ensureValidToken(forceRefresh = false)

        suspend fun runStream(emitUpdate: suspend (StreamChunkUpdate) -> Unit) {
            val textBuilder = StringBuilder()
            withTimeout(AI_TIMEOUT_MS) {
                geminiFreeModel.generateContentStream(requestContent).collect { chunk ->
                    val delta = chunk.text.orEmpty()
                    if (delta.isNotEmpty()) textBuilder.append(delta)
                    emitUpdate(
                        StreamChunkUpdate(
                            accumulatedText = textBuilder.toString(),
                            modelUsed = FREE_GEMINI_MODEL_ID
                        )
                    )
                }
            }
        }

        try {
            runStream { emit(it) }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            if (isAppCheckTokenError(e)) {
                Log.w(TAG, "Recovering App Check state for video analysis and retrying once...", e)
                AppCheckTokenCoordinator.recoverAndRefreshToken()
                runStream { emit(it) }
            } else {
                throw e
            }
        }
    }

    suspend fun generateQuickTitle(firstUserMessage: String): String? {
        return try {
            AppCheckTokenCoordinator.ensureValidToken(forceRefresh = false)
            withTimeout(15_000L) {
                val response = geminiFreeModel.generateContent(
                    "Summarize this user request into a concise 3 to 5 word title without quotes or punctuation: $firstUserMessage"
                )
                response.text?.trim()?.replace("\"", "")?.take(60)?.takeIf { it.isNotBlank() }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Quick title generation skipped: ${e.message}")
            null
        }
    }

    private fun isAppCheckTokenError(e: Exception): Boolean {
        var cur: Throwable? = e
        while (cur != null) {
            val msg = cur.message.orEmpty()
            if (msg.contains("App Check", ignoreCase = true) ||
                msg.contains("attestation", ignoreCase = true) ||
                msg.contains("UNAUTHENTICATED", ignoreCase = true)
            ) {
                return true
            }
            cur = cur.cause
        }
        return false
    }
}
