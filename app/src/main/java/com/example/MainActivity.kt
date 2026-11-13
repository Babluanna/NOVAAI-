package com.example

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.credentials.CredentialManager
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.auth.authStateFlow
import com.example.auth.signOutUser
import com.example.data.configureAppCheck
import com.example.ui.components.NovaSplashIntroScreen
import com.example.ui.screens.ChatModeScreen
import com.example.ui.screens.VoiceModeScreen
import com.example.ui.screens.WelcomeAuthScreen
import com.example.ui.theme.NovaTheme
import com.example.ui.viewmodel.AppScreenMode
import com.example.ui.viewmodel.NovaViewModel
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.auth

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Mandatory App Check initialization BEFORE enableEdgeToEdge, setContent, or Firebase calls
        configureAppCheck(this, intent)
        enableEdgeToEdge()
        setContent {
            NovaAppRoot()
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        configureAppCheck(this, intent)
    }
}

@Composable
fun NovaAppRoot() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val credentialManager = remember(context) { CredentialManager.create(context) }

    var showStartupIntro by rememberSaveable { mutableStateOf(true) }

    val currentUser by Firebase.auth.authStateFlow()
        .collectAsStateWithLifecycle(initialValue = Firebase.auth.currentUser)

    AnimatedContent(
        targetState = showStartupIntro,
        transitionSpec = {
            (fadeIn(tween(420)) + scaleIn(initialScale = 0.97f, animationSpec = tween(420)))
                .togetherWith(fadeOut(tween(340)))
        },
        label = "startup_intro_transition"
    ) { isIntroActive ->
        if (isIntroActive) {
            NovaTheme(isVoiceLightMode = false) {
                NovaSplashIntroScreen(
                    onFinished = { showStartupIntro = false }
                )
            }
        } else {
            val user = currentUser
            if (user == null) {
                NovaTheme(isVoiceLightMode = false) {
                    WelcomeAuthScreen(
                        credentialManager = credentialManager,
                        onAuthenticated = { /* authStateFlow automatically transitions */ }
                    )
                }
            } else {
                NovaAuthenticatedShell(
                    user = user,
                    onSignOut = {
                        signOutUser(
                            credentialManager = credentialManager,
                            onSignOutComplete = {},
                            scope = scope
                        )
                    }
                )
            }
        }
    }
}

@Composable
private fun NovaAuthenticatedShell(
    user: FirebaseUser,
    onSignOut: () -> Unit
) {
    val context = LocalContext.current
    val viewModel: NovaViewModel = viewModel(
        key = "nova_vm_${user.uid}",
        factory = NovaViewModel.provideFactory(context = context, currentUserId = user.uid)
    )

    val composerState by viewModel.composerState.collectAsStateWithLifecycle()
    val conversationsState by viewModel.conversationsState.collectAsStateWithLifecycle()
    val activeConversationId by viewModel.activeConversationId.collectAsStateWithLifecycle()
    val messages by viewModel.messages.collectAsStateWithLifecycle()
    val voiceRecordings by viewModel.voiceRecordings.collectAsStateWithLifecycle()
    val voiceState by viewModel.voiceState.collectAsStateWithLifecycle()

    NovaTheme(isVoiceLightMode = composerState.screenMode == AppScreenMode.VOICE) {
        AnimatedContent(
            targetState = composerState.screenMode,
            transitionSpec = {
                fadeIn(animationSpec = tween(250))
                    .togetherWith(fadeOut(animationSpec = tween(200)))
            },
            label = "nova_mode_transition"
        ) { mode ->
            when (mode) {
                AppScreenMode.CHAT -> {
                    ChatModeScreen(
                        user = user,
                        composerState = composerState,
                        conversationsState = conversationsState,
                        activeConversationId = activeConversationId,
                        messages = messages,
                        voiceRecordings = voiceRecordings,
                        voiceState = voiceState,
                        onDraftChange = viewModel::updateDraftInput,
                        onDismissError = viewModel::dismissErrorBanner,
                        onNewChat = viewModel::startNewChat,
                        onSelectConversation = viewModel::selectConversation,
                        onDeleteConversation = viewModel::deleteConversation,
                        onAttachPhoto = viewModel::attachPhoto,
                        onAttachVideo = viewModel::attachVideo,
                        onClearAttachment = viewModel::clearAttachment,
                        onSendPrompt = viewModel::sendPrompt,
                        onStopGeneration = viewModel::stopGeneration,
                        onRegenerateMessage = viewModel::regenerateMessage,
                        onSpeakOrStopMessage = viewModel::speakOrStopMessage,
                        onOpenVoiceMode = viewModel::openVoiceMode,
                        onStartInlineDictation = viewModel::startInlineChatDictation,
                        onPlayVoiceRecording = viewModel::playOrStopVoiceRecording,
                        onDeleteVoiceRecording = viewModel::deleteVoiceRecording,
                        onSignOut = onSignOut
                    )
                }

                AppScreenMode.VOICE -> {
                    VoiceModeScreen(
                        voiceState = voiceState,
                        voiceRecordings = voiceRecordings,
                        onBackToChat = viewModel::returnToChatMode,
                        onCentralMicClick = viewModel::onCentralVoiceMicTapped,
                        onToggleMute = viewModel::toggleVoiceMute,
                        onSendVoiceTextPrompt = viewModel::sendTypedVoiceModePrompt,
                        onAttachVoicePhoto = viewModel::attachPhotoForVoiceMode,
                        onClearVoicePhoto = viewModel::clearVoicePhoto,
                        onSelectPersona = viewModel::selectVoicePersona,
                        onPreviewPersona = viewModel::previewVoicePersona,
                        onSelectLanguage = viewModel::selectVoiceLanguage,
                        onToggleCaptions = viewModel::toggleVoiceCaptions,
                        onInterruptAiSpeech = viewModel::interruptAiSpeech,
                        onPlayRecording = viewModel::playOrStopVoiceRecording,
                        onDeleteRecording = viewModel::deleteVoiceRecording
                    )
                }
            }
        }
    }
}
