package com.example.data.model

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp

const val FREE_GEMINI_MODEL_ID = "gemini-3.5-flash-lite"
const val FREE_GEMINI_TTS_MODEL_ID = "gemini-3.1-flash-tts-preview"

enum class NovaVoicePersona(
    val displayName: String,
    val description: String,
    val prebuiltVoiceName: String,
    val directorCharacterNote: String
) {
    NOVA_WARM(
        displayName = "Nova",
        description = "Warm, friendly, expressive & confident",
        prebuiltVoiceName = "Aoede",
        directorCharacterNote = "Warm, upbeat, intelligent young woman with a smooth, natural, friendly and expressive conversational voice."
    ),
    SOLSTICE(
        displayName = "Solstice",
        description = "Clear, calm, poised & articulate",
        prebuiltVoiceName = "Kore",
        directorCharacterNote = "Clear, calm, composed, articulate voice with reassuring warmth and crisp pronunciation."
    ),
    SPARK(
        displayName = "Spark",
        description = "Energetic, playful, witty & bright",
        prebuiltVoiceName = "Puck",
        directorCharacterNote = "Energetic, cheerful, lively, charismatic conversational voice with playful warmth and natural enthusiasm."
    ),
    HORIZON(
        displayName = "Horizon",
        description = "Smooth, grounded, resonant & steady",
        prebuiltVoiceName = "Fenrir",
        directorCharacterNote = "Smooth, warm, confident, grounded baritone voice with calm presence and natural rhythm."
    ),
    BREEZE(
        displayName = "Breeze",
        description = "Relaxed, thoughtful & easygoing",
        prebuiltVoiceName = "Charon",
        directorCharacterNote = "Relaxed, easygoing, thoughtful, friendly human voice with gentle conversational flow."
    )
}

enum class NovaVoiceLanguage(
    val displayName: String,
    val badgeLabel: String,
    val bcp47Code: String?,
    val systemPromptDirective: String
) {
    AUTO(
        displayName = "Auto (English · తెలుగు · हिन्दी)",
        badgeLabel = "Auto",
        bcp47Code = null,
        systemPromptDirective = "Automatically detect the language the user is speaking (English, Telugu, or Hindi, or natural code-switching like Tinglish/Hinglish) and respond fluently and naturally in that exact same language."
    ),
    ENGLISH(
        displayName = "English (US)",
        badgeLabel = "EN",
        bcp47Code = "en-US",
        systemPromptDirective = "Respond in natural, warm, modern everyday American English unless the user explicitly asks you to switch languages."
    ),
    TELUGU(
        displayName = "తెలుగు (Telugu)",
        badgeLabel = "తెలుగు",
        bcp47Code = "te-IN",
        systemPromptDirective = "Respond in fluent, warm, natural everyday spoken Telugu written in authentic Telugu script (తెలుగు), using natural conversational phrasing rather than stiff textbook Telugu."
    ),
    HINDI(
        displayName = "हिन्दी (Hindi)",
        badgeLabel = "हिन्दी",
        bcp47Code = "hi-IN",
        systemPromptDirective = "Respond in fluent, warm, natural everyday spoken Hindi written in Devanagari script (हिन्दी), using natural conversational phrasing rather than overly formal textbook Hindi."
    )
}

@IgnoreExtraProperties
data class Conversation(
    @DocumentId val id: String = "",
    val userId: String = "",
    val title: String = "New Chat",
    val mode: String = "chat",
    val previewText: String = "",
    val messageCount: Long = 0L,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    fun toCreatePayload(): Map<String, Any> {
        return mapOf<String, Any?>(
            "userId" to userId,
            "title" to title.take(200).ifBlank { "New Chat" },
            "mode" to mode,
            "previewText" to previewText.take(500),
            "messageCount" to messageCount,
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp()
        ).filterValues { it != null }.mapValues { it.value!! }
    }
}

@IgnoreExtraProperties
data class ChatMessage(
    @DocumentId val id: String = "",
    val userId: String = "",
    val conversationId: String = "",
    val role: String = "user",
    val text: String = "",
    val thoughtSummary: String? = null,
    val messageType: String = "text",
    val modelUsed: String = FREE_GEMINI_MODEL_ID,
    val mediaLocalPath: String? = null,
    val mediaThumbBase64: String? = null,
    val audioDurationSec: Long? = null,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    fun toCreatePayload(): Map<String, Any> {
        return mapOf<String, Any?>(
            "userId" to userId,
            "conversationId" to conversationId,
            "role" to role,
            "text" to text.take(50000),
            "thoughtSummary" to thoughtSummary?.take(30000)?.takeIf { it.isNotBlank() },
            "messageType" to messageType,
            "modelUsed" to modelUsed.take(100).ifBlank { FREE_GEMINI_MODEL_ID },
            "mediaLocalPath" to mediaLocalPath?.take(1000)?.takeIf { it.isNotBlank() },
            "mediaThumbBase64" to mediaThumbBase64?.take(200000)?.takeIf { it.isNotBlank() },
            "audioDurationSec" to audioDurationSec,
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp()
        ).filterValues { it != null }.mapValues { it.value!! }
    }
}

@IgnoreExtraProperties
data class VoiceRecording(
    @DocumentId val id: String = "",
    val userId: String = "",
    val conversationId: String = "",
    val title: String = "Voice Session",
    val userTranscript: String = "",
    val aiTranscript: String = "",
    val durationSeconds: Long = 0L,
    val audioFilePath: String? = null,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    fun toCreatePayload(): Map<String, Any> {
        return mapOf<String, Any?>(
            "userId" to userId,
            "conversationId" to conversationId,
            "title" to title.take(200).ifBlank { "Voice Session" },
            "userTranscript" to userTranscript.take(30000),
            "aiTranscript" to aiTranscript.take(30000),
            "durationSeconds" to durationSeconds,
            "audioFilePath" to audioFilePath?.take(1000)?.takeIf { it.isNotBlank() },
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp()
        ).filterValues { it != null }.mapValues { it.value!! }
    }
}
