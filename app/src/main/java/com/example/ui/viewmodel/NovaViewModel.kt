package com.example.ui.viewmodel

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.R
import com.example.ai.AttachmentKind
import com.example.ai.MediaAttachmentHelper
import com.example.ai.NO_SPEECH_MARKER
import com.example.ai.NovaAiService
import com.example.ai.PendingMediaAttachment
import com.example.data.model.ChatMessage
import com.example.data.model.Conversation
import com.example.data.model.FREE_GEMINI_MODEL_ID
import com.example.data.model.NovaVoiceLanguage
import com.example.data.model.NovaVoicePersona
import com.example.data.model.VoiceRecording
import com.example.data.repository.NovaRepository
import com.example.voice.NovaVoiceController
import com.example.voice.VoiceSessionStatus
import com.example.voice.VoiceUiState
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

private const val TAG = "NovaViewModel"

sealed interface UiState<out T> {
    data object Loading : UiState<Nothing>
    data class Success<T>(val data: T) : UiState<T>
    data class Error(val message: String) : UiState<Nothing>
}

enum class AppScreenMode {
    CHAT,
    VOICE
}

data class ChatComposerState(
    val screenMode: AppScreenMode = AppScreenMode.CHAT,
    val draftInput: String = "",
    val pendingAttachment: PendingMediaAttachment? = null,
    val isLoadingAttachment: Boolean = false,
    val isGenerating: Boolean = false,
    val streamingText: String = "",
    val errorBanner: String? = null
)

class NovaViewModel(
    private val repository: NovaRepository,
    private val aiService: NovaAiService,
    private val appContext: Context,
    private val currentUserId: String
) : ViewModel() {

    val voiceController = NovaVoiceController(
        context = appContext,
        scope = viewModelScope,
        aiService = aiService
    )

    private val _composerState = MutableStateFlow(ChatComposerState())
    val composerState: StateFlow<ChatComposerState> = _composerState.asStateFlow()

    private val _activeConversationId = MutableStateFlow<String?>(null)
    val activeConversationId: StateFlow<String?> = _activeConversationId.asStateFlow()

    val voiceState: StateFlow<VoiceUiState> = voiceController.state

    val conversationsState: StateFlow<UiState<List<Conversation>>> =
        repository.observeConversations(currentUserId)
            .map<List<Conversation>, UiState<List<Conversation>>> { list ->
                UiState.Success(list)
            }
            .catch { e ->
                emit(UiState.Error(e.localizedMessage ?: "Unable to load chat history."))
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000L),
                initialValue = UiState.Loading
            )

    @OptIn(ExperimentalCoroutinesApi::class)
    val messages: StateFlow<List<ChatMessage>> =
        _activeConversationId
            .flatMapLatest { convId ->
                if (convId.isNullOrBlank()) {
                    flowOf(emptyList())
                } else {
                    repository.observeMessages(currentUserId, convId)
                        .catch { e ->
                            Log.e(TAG, "Messages stream error", e)
                            emit(emptyList())
                        }
                }
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000L),
                initialValue = emptyList()
            )

    val voiceRecordings: StateFlow<List<VoiceRecording>> =
        repository.observeVoiceRecordings(currentUserId)
            .catch { e ->
                Log.e(TAG, "Voice recordings stream error", e)
                emit(emptyList())
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000L),
                initialValue = emptyList()
            )

    private var generationJob: Job? = null
    private var voiceAiJob: Job? = null

    init {
        voiceController.onDictationResult = { recognizedText ->
            if (recognizedText.isNotBlank()) {
                _composerState.update { current ->
                    val merged = if (current.draftInput.isBlank()) {
                        recognizedText
                    } else {
                        "${current.draftInput.trimEnd()} $recognizedText"
                    }
                    current.copy(draftInput = merged)
                }
            }
        }

        voiceController.onUserVoiceAudioCaptured = { wavBytes, durationMs ->
            handleCapturedVoiceAudioTurn(wavBytes, durationMs)
        }

        voiceController.onUserVoiceTurnRecognized = { spokenUserText ->
            handleSpokenVoiceTurn(spokenUserText)
        }
    }

    fun updateDraftInput(text: String) {
        _composerState.update { it.copy(draftInput = text) }
    }

    fun dismissErrorBanner() {
        _composerState.update { it.copy(errorBanner = null) }
    }

    fun startNewChat() {
        stopGeneration()
        voiceController.stopSpeaking()
        _activeConversationId.value = null
        _composerState.update {
            it.copy(
                draftInput = "",
                pendingAttachment = null,
                errorBanner = null,
                streamingText = ""
            )
        }
    }

    fun selectConversation(conversation: Conversation) {
        stopGeneration()
        voiceController.stopSpeaking()
        _activeConversationId.value = conversation.id
        _composerState.update {
            it.copy(
                screenMode = AppScreenMode.CHAT,
                errorBanner = null
            )
        }
    }

    fun deleteConversation(conversationId: String) {
        viewModelScope.launch {
            try {
                if (_activeConversationId.value == conversationId) {
                    _activeConversationId.value = null
                }
                repository.deleteConversation(conversationId)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _composerState.update {
                    it.copy(errorBanner = e.localizedMessage ?: "Could not delete conversation.")
                }
            }
        }
    }

    fun attachPhoto(uri: Uri) {
        viewModelScope.launch {
            _composerState.update { it.copy(isLoadingAttachment = true, errorBanner = null) }
            MediaAttachmentHelper.loadPhotoAttachment(appContext, uri).fold(
                onSuccess = { attachment ->
                    _composerState.update {
                        it.copy(pendingAttachment = attachment, isLoadingAttachment = false)
                    }
                },
                onFailure = { err ->
                    _composerState.update {
                        it.copy(
                            isLoadingAttachment = false,
                            errorBanner = err.localizedMessage ?: "Failed to attach photo."
                        )
                    }
                }
            )
        }
    }

    fun attachVideo(uri: Uri) {
        viewModelScope.launch {
            _composerState.update { it.copy(isLoadingAttachment = true, errorBanner = null) }
            MediaAttachmentHelper.loadVideoAttachment(appContext, uri).fold(
                onSuccess = { attachment ->
                    _composerState.update {
                        it.copy(pendingAttachment = attachment, isLoadingAttachment = false)
                    }
                },
                onFailure = { err ->
                    _composerState.update {
                        it.copy(
                            isLoadingAttachment = false,
                            errorBanner = err.localizedMessage ?: "Failed to attach video."
                        )
                    }
                }
            )
        }
    }

    fun clearAttachment() {
        _composerState.update { it.copy(pendingAttachment = null) }
    }

    fun attachPhotoForVoiceMode(uri: Uri) {
        viewModelScope.launch {
            MediaAttachmentHelper.loadPhotoAttachment(appContext, uri).fold(
                onSuccess = { attachment ->
                    voiceController.setPendingVoicePhoto(attachment)
                },
                onFailure = { err ->
                    voiceController.onVoiceAiError(
                        err.localizedMessage ?: "Could not attach photo."
                    )
                }
            )
        }
    }

    fun clearVoicePhoto() {
        voiceController.setPendingVoicePhoto(null)
    }

    fun sendPrompt(overridePrompt: String? = null) {
        val snapshot = _composerState.value
        if (snapshot.isGenerating) return

        val rawPrompt = (overridePrompt ?: snapshot.draftInput).trim()
        val attachment = snapshot.pendingAttachment
        if (rawPrompt.isBlank() && attachment == null) return

        voiceController.stopSpeaking()
        _composerState.update {
            it.copy(
                draftInput = if (overridePrompt == null) "" else it.draftInput,
                pendingAttachment = null,
                errorBanner = null,
                isGenerating = true,
                streamingText = ""
            )
        }

        val priorHistory = messages.value
        generationJob = viewModelScope.launch {
            var currentConvId = _activeConversationId.value
            val isBrandNewConv = currentConvId.isNullOrBlank()
            val effectivePrompt = rawPrompt.ifBlank {
                when (attachment?.kind) {
                    AttachmentKind.PHOTO -> "Analyze this image in detail."
                    AttachmentKind.VIDEO -> "Analyze and summarize this video."
                    null -> ""
                }
            }

            try {
                if (currentConvId.isNullOrBlank()) {
                    val fallbackTitle = effectivePrompt.take(44).ifBlank { "Visual Analysis" }
                    currentConvId = repository.createConversation(
                        title = fallbackTitle,
                        mode = "chat",
                        previewText = effectivePrompt
                    )
                    _activeConversationId.value = currentConvId
                }

                // Matches firestore.rules enum: ['text', 'image_gen', 'image_analysis', 'video_analysis', 'voice']
                val userMessageType = when (attachment?.kind) {
                    AttachmentKind.PHOTO -> "image_analysis"
                    AttachmentKind.VIDEO -> "video_analysis"
                    null -> "text"
                }

                repository.addMessage(
                    conversationId = currentConvId,
                    role = "user",
                    text = effectivePrompt,
                    messageType = userMessageType,
                    modelUsed = FREE_GEMINI_MODEL_ID,
                    mediaLocalPath = attachment?.localPreviewPath,
                    mediaThumbBase64 = attachment?.thumbBase64
                )

                val streamFlow = when {
                    attachment?.kind == AttachmentKind.PHOTO && attachment.previewBitmap != null -> {
                        aiService.streamPhotoAnalysis(attachment.previewBitmap, effectivePrompt)
                    }
                    attachment?.kind == AttachmentKind.VIDEO && attachment.videoBytes != null -> {
                        aiService.streamVideoAnalysis(
                            videoBytes = attachment.videoBytes,
                            mimeType = attachment.mimeType,
                            userPrompt = effectivePrompt
                        )
                    }
                    else -> {
                        aiService.streamMultiTurnChat(
                            historyMessages = priorHistory,
                            userPrompt = effectivePrompt
                        )
                    }
                }

                var finalText = ""
                streamFlow.collect { chunk ->
                    finalText = chunk.accumulatedText
                    _composerState.update { it.copy(streamingText = finalText) }
                }

                if (finalText.isNotBlank()) {
                    repository.addMessage(
                        conversationId = currentConvId,
                        role = "model",
                        text = finalText,
                        messageType = "text",
                        modelUsed = FREE_GEMINI_MODEL_ID
                    )
                    repository.updateConversationMetadata(
                        conversationId = currentConvId,
                        previewText = finalText.take(180),
                        messageCount = (if (isBrandNewConv) 2 else priorHistory.size + 2).toLong()
                    )
                    if (isBrandNewConv) {
                        val savedConvId = currentConvId
                        launch {
                            val smartTitle = aiService.generateQuickTitle(effectivePrompt)
                            if (!smartTitle.isNullOrBlank()) {
                                runCatching {
                                    repository.updateConversationMetadata(
                                        conversationId = savedConvId,
                                        title = smartTitle
                                    )
                                }
                            }
                        }
                    }
                } else {
                    _composerState.update {
                        it.copy(errorBanner = "Nova returned an empty response. Please try again.")
                    }
                }
            } catch (e: TimeoutCancellationException) {
                Log.e(TAG, "AI generation timed out", e)
                _composerState.update {
                    it.copy(errorBanner = "Request timed out after 60 seconds. Please try again.")
                }
            } catch (e: CancellationException) {
                val partial = _composerState.value.streamingText
                if (!currentConvId.isNullOrBlank() && partial.isNotBlank()) {
                    runCatching {
                        repository.addMessage(
                            conversationId = currentConvId,
                            role = "model",
                            text = "$partial\n\n_[Stopped]_",
                            messageType = "text",
                            modelUsed = FREE_GEMINI_MODEL_ID
                        )
                    }
                }
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "AI generation failed", e)
                _composerState.update {
                    it.copy(
                        errorBanner = e.localizedMessage ?: "Nova AI encountered an error. Please try again."
                    )
                }
            } finally {
                _composerState.update {
                    it.copy(isGenerating = false, streamingText = "")
                }
            }
        }
    }

    fun stopGeneration() {
        generationJob?.cancel()
        generationJob = null
        _composerState.update {
            it.copy(isGenerating = false, streamingText = "")
        }
    }

    fun regenerateMessage(targetModelMessage: ChatMessage) {
        if (_composerState.value.isGenerating) return
        val currentList = messages.value
        val idx = currentList.indexOfFirst { it.id == targetModelMessage.id }
        if (idx <= 0) return

        val precedingUserMsg = currentList.subList(0, idx).lastOrNull { it.role == "user" } ?: return
        val historyBeforeTurn = currentList.subList(
            0,
            currentList.indexOfFirst { it.id == precedingUserMsg.id }.coerceAtLeast(0)
        )
        val convId = targetModelMessage.conversationId.ifBlank { _activeConversationId.value ?: return }

        voiceController.stopSpeaking()
        _composerState.update {
            it.copy(isGenerating = true, streamingText = "", errorBanner = null)
        }

        generationJob = viewModelScope.launch {
            try {
                repository.deleteMessage(convId, targetModelMessage.id)
                var finalText = ""
                aiService.streamMultiTurnChat(
                    historyMessages = historyBeforeTurn,
                    userPrompt = precedingUserMsg.text
                ).collect { chunk ->
                    finalText = chunk.accumulatedText
                    _composerState.update { it.copy(streamingText = finalText) }
                }
                if (finalText.isNotBlank()) {
                    repository.addMessage(
                        conversationId = convId,
                        role = "model",
                        text = finalText,
                        messageType = "text",
                        modelUsed = FREE_GEMINI_MODEL_ID
                    )
                }
            } catch (e: TimeoutCancellationException) {
                _composerState.update {
                    it.copy(errorBanner = "Regeneration timed out after 60 seconds.")
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _composerState.update {
                    it.copy(errorBanner = e.localizedMessage ?: "Failed to regenerate response.")
                }
            } finally {
                _composerState.update {
                    it.copy(isGenerating = false, streamingText = "")
                }
            }
        }
    }

    fun speakOrStopMessage(message: ChatMessage) {
        if (voiceState.value.speakingMessageId == message.id) {
            voiceController.stopSpeaking()
        } else {
            voiceController.speakText(text = message.text, messageId = message.id)
        }
    }

    // --- Seamless Chat <-> Voice Mode Transitions & Controls ---

    fun openVoiceMode() {
        voiceController.stopSpeaking()
        _composerState.update { it.copy(screenMode = AppScreenMode.VOICE, errorBanner = null) }
        if (voiceController.hasRecordAudioPermission()) {
            voiceController.startVoiceConversation()
        }
    }

    fun returnToChatMode() {
        voiceAiJob?.cancel()
        voiceController.stopAndFinishSession()
        _composerState.update { it.copy(screenMode = AppScreenMode.CHAT) }
    }

    fun onCentralVoiceMicTapped() {
        when (voiceState.value.status) {
            VoiceSessionStatus.IDLE,
            VoiceSessionStatus.PAUSED,
            VoiceSessionStatus.ERROR -> {
                voiceController.resumeVoiceSession()
            }
            VoiceSessionStatus.PLAYING_RECORDING,
            VoiceSessionStatus.AI_SPEAKING -> {
                interruptAiSpeech()
            }
            VoiceSessionStatus.LISTENING -> {
                // Tapping orb while speaking completes the current utterance immediately
                voiceController.finishCurrentListeningTurnEarly()
            }
            VoiceSessionStatus.PROCESSING -> {
                voiceAiJob?.cancel()
                voiceController.startVoiceConversation()
            }
        }
    }

    fun toggleVoiceMute() {
        if (voiceState.value.status == VoiceSessionStatus.AI_SPEAKING) {
            voiceController.stopSpeaking()
        }
        voiceController.toggleMute()
    }

    fun selectVoicePersona(persona: NovaVoicePersona) {
        voiceController.selectVoicePersona(persona)
    }

    fun previewVoicePersona(persona: NovaVoicePersona) {
        voiceController.previewSelectedVoicePersona(persona)
    }

    fun selectVoiceLanguage(language: NovaVoiceLanguage) {
        voiceController.selectVoiceLanguage(language)
    }

    fun toggleVoiceCaptions() {
        voiceController.toggleCaptions()
    }

    fun sendTypedVoiceModePrompt(promptText: String) {
        val trimmed = promptText.trim()
        val photo = voiceState.value.pendingVoicePhoto
        if (trimmed.isBlank() && photo == null) return

        val effectivePrompt = trimmed.ifBlank { "Tell me naturally about what you see in this photo." }
        voiceController.setPendingVoicePhoto(null)
        voiceController.prepareForTypedOrPhotoVoiceTurn(effectivePrompt)

        voiceAiJob?.cancel()
        voiceAiJob = viewModelScope.launch {
            try {
                var finalReply = ""
                aiService.streamMultiTurnChat(
                    historyMessages = messages.value,
                    userPrompt = effectivePrompt,
                    isVoiceConversation = true,
                    voiceLanguage = voiceState.value.selectedLanguage,
                    photoBitmap = photo?.previewBitmap
                ).collect { chunk ->
                    finalReply = chunk.accumulatedText
                    voiceController.updateLiveAiTranscript(finalReply)
                }

                if (finalReply.isNotBlank()) {
                    persistVoiceExchange(
                        userText = effectivePrompt,
                        aiText = finalReply,
                        durationSec = 1L,
                        photoAttachment = photo
                    )
                    val shouldResumeMic = !voiceState.value.isMuted && voiceController.hasRecordAudioPermission()
                    voiceController.speakVoiceReply(
                        replyText = finalReply,
                        resumeListeningWhenFinished = shouldResumeMic
                    )
                } else {
                    voiceController.onVoiceAiError("Nova returned an empty voice response.")
                }
            } catch (e: TimeoutCancellationException) {
                voiceController.onVoiceAiError("Response timed out. Tap orb to try again.")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Typed voice turn failed", e)
                voiceController.onVoiceAiError(
                    e.localizedMessage ?: "Voice response failed. Tap orb to retry."
                )
            }
        }
    }

    fun interruptAiSpeech() {
        voiceAiJob?.cancel()
        voiceController.stopSpeaking()
        if (voiceController.hasRecordAudioPermission() && !voiceState.value.isMuted) {
            voiceController.startVoiceConversation()
        }
    }

    fun startInlineChatDictation() {
        voiceController.startInlineDictation()
    }

    fun playOrStopVoiceRecording(recording: VoiceRecording) {
        if (voiceState.value.playingRecordingId == recording.id) {
            voiceController.stopSpeaking()
        } else {
            val script = recording.aiTranscript.ifBlank { recording.userTranscript }
            voiceController.speakText(text = script, recordingId = recording.id)
        }
    }

    fun deleteVoiceRecording(recordingId: String) {
        viewModelScope.launch {
            runCatching { repository.deleteVoiceRecording(recordingId) }
        }
    }

    private fun handleCapturedVoiceAudioTurn(wavBytes: ByteArray, durationMs: Long) {
        val attachedPhoto = voiceState.value.pendingVoicePhoto
        if (attachedPhoto != null) {
            voiceController.setPendingVoicePhoto(null)
        }
        val activeLanguage = voiceState.value.selectedLanguage

        voiceAiJob?.cancel()
        voiceAiJob = viewModelScope.launch {
            try {
                // Transcribe user's spoken audio in parallel so captions & chat history stay synchronized
                val transcriptDeferred = async {
                    aiService.transcribeUserAudio(wavBytes, activeLanguage)
                        .also { text ->
                            if (text.isNotBlank()) {
                                voiceController.updateLiveUserTranscript(text)
                            }
                        }
                }

                var finalReply = ""
                aiService.streamVoiceAudioTurn(
                    historyMessages = messages.value,
                    wavAudioBytes = wavBytes,
                    voiceLanguage = activeLanguage,
                    photoBitmap = attachedPhoto?.previewBitmap
                ).collect { chunk ->
                    finalReply = chunk.accumulatedText
                    if (!finalReply.contains(NO_SPEECH_MARKER, ignoreCase = true)) {
                        voiceController.updateLiveAiTranscript(finalReply)
                    }
                }

                val cleanReply = finalReply.trim()
                if (cleanReply.isBlank() || cleanReply.contains(NO_SPEECH_MARKER, ignoreCase = true)) {
                    transcriptDeferred.cancel()
                    voiceController.resumeListeningAfterSilentAudio()
                    return@launch
                }

                val spokenTranscript = runCatching { transcriptDeferred.await() }
                    .getOrDefault("")
                    .ifBlank { "Spoken message" }

                val durationSec = (durationMs / 1000L).coerceAtLeast(1L)
                persistVoiceExchange(
                    userText = spokenTranscript,
                    aiText = cleanReply,
                    durationSec = durationSec,
                    photoAttachment = attachedPhoto
                )
                voiceController.speakVoiceReply(
                    replyText = cleanReply,
                    resumeListeningWhenFinished = !voiceState.value.isMuted
                )
            } catch (e: TimeoutCancellationException) {
                Log.e(TAG, "Voice audio turn timed out", e)
                voiceController.onVoiceAiError("Voice response timed out. Tap orb to try again.")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Voice audio turn failed", e)
                voiceController.onVoiceAiError(
                    e.localizedMessage ?: "Voice response failed. Tap orb to retry."
                )
            }
        }
    }

    private fun handleSpokenVoiceTurn(userText: String) {
        val attachedPhoto = voiceState.value.pendingVoicePhoto
        if (attachedPhoto != null) {
            voiceController.setPendingVoicePhoto(null)
        }

        voiceAiJob?.cancel()
        voiceAiJob = viewModelScope.launch {
            try {
                var finalReply = ""
                aiService.streamMultiTurnChat(
                    historyMessages = messages.value,
                    userPrompt = userText,
                    isVoiceConversation = true,
                    voiceLanguage = voiceState.value.selectedLanguage,
                    photoBitmap = attachedPhoto?.previewBitmap
                ).collect { chunk ->
                    finalReply = chunk.accumulatedText
                    voiceController.updateLiveAiTranscript(finalReply)
                }

                if (finalReply.isNotBlank()) {
                    val duration = voiceState.value.elapsedSeconds.coerceAtLeast(1L)
                    persistVoiceExchange(
                        userText = userText,
                        aiText = finalReply,
                        durationSec = duration,
                        photoAttachment = attachedPhoto
                    )
                    voiceController.speakVoiceReply(
                        replyText = finalReply,
                        resumeListeningWhenFinished = !voiceState.value.isMuted
                    )
                } else {
                    voiceController.onVoiceAiError("Nova returned an empty voice response.")
                }
            } catch (e: TimeoutCancellationException) {
                voiceController.onVoiceAiError("Voice response timed out. Tap orb to retry.")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Voice AI reply failed", e)
                voiceController.onVoiceAiError(
                    e.localizedMessage ?: "Voice response failed. Tap orb to retry."
                )
            }
        }
    }

    private suspend fun persistVoiceExchange(
        userText: String,
        aiText: String,
        durationSec: Long,
        photoAttachment: PendingMediaAttachment? = null
    ) {
        try {
            var convId = _activeConversationId.value
            val preview = (aiText.ifBlank { userText }).take(160)
            if (convId.isNullOrBlank()) {
                val baseTitle = userText.take(42).ifBlank { "Voice Conversation" }
                convId = repository.createConversation(
                    title = baseTitle,
                    mode = "voice",
                    previewText = preview
                )
                _activeConversationId.value = convId
            }

            if (userText.isNotBlank()) {
                repository.addMessage(
                    conversationId = convId,
                    role = "user",
                    text = userText,
                    messageType = if (photoAttachment != null) "image_analysis" else "voice",
                    modelUsed = FREE_GEMINI_MODEL_ID,
                    mediaLocalPath = photoAttachment?.localPreviewPath,
                    mediaThumbBase64 = photoAttachment?.thumbBase64,
                    audioDurationSec = durationSec
                )
            }
            if (aiText.isNotBlank()) {
                repository.addMessage(
                    conversationId = convId,
                    role = "model",
                    text = aiText,
                    messageType = "voice",
                    modelUsed = FREE_GEMINI_MODEL_ID,
                    audioDurationSec = durationSec
                )
            }
            repository.updateConversationMetadata(
                conversationId = convId,
                previewText = preview
            )
            repository.saveVoiceRecording(
                conversationId = convId,
                title = userText.take(48).ifBlank { "Nova Voice Session" },
                userTranscript = userText,
                aiTranscript = aiText,
                durationSeconds = durationSec
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Failed persisting voice exchange", e)
        }
    }

    override fun onCleared() {
        super.onCleared()
        voiceController.release()
    }

    companion object {
        fun provideFactory(
            context: Context,
            currentUserId: String
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                val appCtx = context.applicationContext
                val db = FirebaseFirestore.getInstance(
                    appCtx.getString(R.string.firestore_database_id)
                )
                val repo = NovaRepository(db = db)
                val ai = NovaAiService()
                return NovaViewModel(
                    repository = repo,
                    aiService = ai,
                    appContext = appCtx,
                    currentUserId = currentUserId
                ) as T
            }
        }
    }
}
