package com.example.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material.icons.outlined.Lightbulb
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material.icons.outlined.Videocam
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Menu
import androidx.compose.material.icons.rounded.NorthEast
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.R
import com.example.ai.PendingMediaAttachment
import com.example.data.model.ChatMessage
import com.example.data.model.Conversation
import com.example.data.model.VoiceRecording
import com.example.ui.components.ChatMessageItem
import com.example.ui.components.GlowingOrangeNovaOrb
import com.example.ui.components.NovaDrawerSheet
import com.example.ui.components.StreamingAssistantBlock
import com.example.ui.theme.NovaBlack
import com.example.ui.theme.NovaBorderDark
import com.example.ui.theme.NovaCharcoal
import com.example.ui.theme.NovaOrangeBright
import com.example.ui.theme.NovaOrangeCore
import com.example.ui.theme.NovaOrangeDeep
import com.example.ui.theme.NovaOrangeHighlight
import com.example.ui.theme.NovaSurfaceDark
import com.example.ui.theme.NovaSurfaceElevated
import com.example.ui.theme.NovaTextMutedDark
import com.example.ui.theme.NovaTextPrimaryDark
import com.example.ui.theme.NovaTextSecondaryDark
import com.example.ui.viewmodel.ChatComposerState
import com.example.ui.viewmodel.UiState
import com.example.voice.VoiceSessionStatus
import com.example.voice.VoiceUiState
import com.google.firebase.auth.FirebaseUser
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatModeScreen(
    user: FirebaseUser?,
    composerState: ChatComposerState,
    conversationsState: UiState<List<Conversation>>,
    activeConversationId: String?,
    messages: List<ChatMessage>,
    voiceRecordings: List<VoiceRecording>,
    voiceState: VoiceUiState,
    onDraftChange: (String) -> Unit,
    onDismissError: () -> Unit,
    onNewChat: () -> Unit,
    onSelectConversation: (Conversation) -> Unit,
    onDeleteConversation: (String) -> Unit,
    onAttachPhoto: (android.net.Uri) -> Unit,
    onAttachVideo: (android.net.Uri) -> Unit,
    onClearAttachment: () -> Unit,
    onSendPrompt: (String?) -> Unit,
    onStopGeneration: () -> Unit,
    onRegenerateMessage: (ChatMessage) -> Unit,
    onSpeakOrStopMessage: (ChatMessage) -> Unit,
    onOpenVoiceMode: () -> Unit,
    onStartInlineDictation: () -> Unit,
    onPlayVoiceRecording: (VoiceRecording) -> Unit,
    onDeleteVoiceRecording: (String) -> Unit,
    onSignOut: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    val scope = rememberCoroutineScope()
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val listState = rememberLazyListState()

    var showAttachSheet by remember { mutableStateOf(false) }

    val photoPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) onAttachPhoto(uri)
    }

    val videoPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) onAttachVideo(uri)
    }

    val inlineMicPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            onStartInlineDictation()
        }
    }

    LaunchedEffect(messages.size, composerState.isGenerating, composerState.streamingText.length) {
        val totalItems = messages.size + (if (composerState.isGenerating) 1 else 0)
        if (totalItems > 0) {
            listState.animateScrollToItem(totalItems - 1)
        }
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            NovaDrawerSheet(
                user = user,
                conversationsState = conversationsState,
                activeConversationId = activeConversationId,
                voiceRecordings = voiceRecordings,
                playingRecordingId = voiceState.playingRecordingId,
                onNewChat = {
                    onNewChat()
                    scope.launch { drawerState.close() }
                },
                onOpenVoiceMode = {
                    scope.launch { drawerState.close() }
                    onOpenVoiceMode()
                },
                onSelectConversation = { conv ->
                    onSelectConversation(conv)
                    scope.launch { drawerState.close() }
                },
                onDeleteConversation = onDeleteConversation,
                onPlayVoiceRecording = onPlayVoiceRecording,
                onDeleteVoiceRecording = onDeleteVoiceRecording,
                onSignOut = {
                    scope.launch { drawerState.close() }
                    onSignOut()
                }
            )
        },
        modifier = modifier
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .fillMaxSize()
                .background(NovaBlack)
                .statusBarsPadding()
                .navigationBarsPadding()
                .imePadding()
                .testTag("chat_mode_screen")
        ) {
            // Top Bar with Sidebar Menu, Centered Chat | Voice Switch, and New Chat Button
            ChatTopBar(
                onOpenDrawer = { scope.launch { drawerState.open() } },
                onOpenVoiceMode = {
                    focusManager.clearFocus()
                    onOpenVoiceMode()
                },
                onNewChat = onNewChat,
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 760.dp)
            )

            // Error Banner (if any)
            AnimatedVisibility(visible = composerState.errorBanner != null) {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = Color(0xFF3A161E),
                    border = BorderStroke(1.dp, Color(0xFF7F1D1D)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .widthIn(max = 720.dp)
                        .padding(horizontal = 16.dp, vertical = 6.dp)
                        .testTag("chat_error_banner")
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                    ) {
                        Text(
                            text = composerState.errorBanner.orEmpty(),
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFFFECACA),
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(
                            onClick = onDismissError,
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Close,
                                contentDescription = "Dismiss error",
                                tint = Color(0xFFFECACA),
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }

            // Center Content: Empty Greeting State OR Active Conversation Stream
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                if (messages.isEmpty() && !composerState.isGenerating) {
                    EmptyChatGreetingView(
                        onSelectPrompt = { promptText ->
                            onSendPrompt(promptText)
                        }
                    )
                } else {
                    LazyColumn(
                        state = listState,
                        contentPadding = PaddingValues(horizontal = 18.dp, vertical = 12.dp),
                        modifier = Modifier
                            .fillMaxSize()
                            .widthIn(max = 720.dp)
                            .testTag("chat_messages_list")
                    ) {
                        items(messages, key = { it.id }) { msg ->
                            ChatMessageItem(
                                message = msg,
                                isSpeaking = voiceState.speakingMessageId == msg.id,
                                onRegenerate = { onRegenerateMessage(msg) },
                                onToggleSpeak = { onSpeakOrStopMessage(msg) }
                            )
                        }

                        if (composerState.isGenerating) {
                            item(key = "streaming_turn") {
                                StreamingAssistantBlock(
                                    streamingText = composerState.streamingText
                                )
                            }
                        }
                    }
                }
            }

            // Bottom Rounded Composer Card
            ChatBottomInputCard(
                draftText = composerState.draftInput,
                pendingAttachment = composerState.pendingAttachment,
                isLoadingAttachment = composerState.isLoadingAttachment,
                isGenerating = composerState.isGenerating,
                isInlineListening = voiceState.status == VoiceSessionStatus.LISTENING,
                onDraftChange = onDraftChange,
                onOpenAttachSheet = { showAttachSheet = true },
                onClearAttachment = onClearAttachment,
                onDictateSpeech = {
                    val granted = ContextCompat.checkSelfPermission(
                        context,
                        Manifest.permission.RECORD_AUDIO
                    ) == PackageManager.PERMISSION_GRANTED
                    if (granted) {
                        onStartInlineDictation()
                    } else {
                        inlineMicPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                    }
                },
                onOpenVoiceMode = {
                    focusManager.clearFocus()
                    onOpenVoiceMode()
                },
                onSendOrStop = {
                    if (composerState.isGenerating) {
                        onStopGeneration()
                    } else {
                        focusManager.clearFocus()
                        onSendPrompt(null)
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 720.dp)
                    .padding(horizontal = 14.dp, vertical = 10.dp)
            )
        }
    }

    if (showAttachSheet) {
        ModalBottomSheet(
            onDismissRequest = { showAttachSheet = false },
            containerColor = NovaCharcoal,
            contentColor = NovaTextPrimaryDark
        ) {
            AttachmentCapabilitiesSheet(
                onPickPhoto = {
                    showAttachSheet = false
                    photoPicker.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                    )
                },
                onPickVideo = {
                    showAttachSheet = false
                    videoPicker.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly)
                    )
                },
                onDictateSpeech = {
                    showAttachSheet = false
                    val granted = ContextCompat.checkSelfPermission(
                        context,
                        Manifest.permission.RECORD_AUDIO
                    ) == PackageManager.PERMISSION_GRANTED
                    if (granted) {
                        onStartInlineDictation()
                    } else {
                        inlineMicPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                    }
                }
            )
        }
    }
}

@Composable
private fun ChatTopBar(
    onOpenDrawer: () -> Unit,
    onOpenVoiceMode: () -> Unit,
    onNewChat: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
        modifier = modifier.padding(horizontal = 16.dp, vertical = 10.dp)
    ) {
        Surface(
            onClick = onOpenDrawer,
            shape = CircleShape,
            color = NovaCharcoal,
            border = BorderStroke(1.dp, NovaBorderDark),
            modifier = Modifier
                .size(48.dp)
                .testTag("sidebar_menu_button")
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.Rounded.Menu,
                    contentDescription = stringResource(R.string.cd_sidebar_menu),
                    tint = NovaTextPrimaryDark,
                    modifier = Modifier.size(21.dp)
                )
            }
        }

        // Centered Chat | Voice Segmented Capsule Switch
        Surface(
            shape = CircleShape,
            color = NovaCharcoal,
            border = BorderStroke(1.dp, NovaBorderDark)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(4.dp)
            ) {
                SegmentedTabPill(
                    label = stringResource(R.string.chat_tab),
                    selected = true,
                    onClick = { /* Already in Chat Mode */ },
                    testTag = "segmented_chat_tab"
                )
                SegmentedTabPill(
                    label = stringResource(R.string.voice_tab),
                    selected = false,
                    onClick = onOpenVoiceMode,
                    testTag = "segmented_voice_tab"
                )
            }
        }

        Surface(
            onClick = onNewChat,
            shape = CircleShape,
            color = NovaCharcoal,
            border = BorderStroke(1.dp, NovaBorderDark),
            modifier = Modifier
                .size(48.dp)
                .testTag("new_chat_button")
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.Outlined.EditNote,
                    contentDescription = stringResource(R.string.cd_new_chat),
                    tint = NovaTextPrimaryDark,
                    modifier = Modifier.size(22.dp)
                )
            }
        }
    }
}

@Composable
private fun SegmentedTabPill(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    testTag: String
) {
    val bgColor by animateColorAsState(
        targetValue = if (selected) Color.White else Color.Transparent,
        label = "seg_bg"
    )
    val textColor by animateColorAsState(
        targetValue = if (selected) NovaBlack else NovaTextSecondaryDark,
        label = "seg_txt"
    )

    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .height(40.dp)
            .clip(CircleShape)
            .background(bgColor)
            .clickable(onClick = onClick)
            .padding(horizontal = 22.dp)
            .testTag(testTag)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge.copy(
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
            ),
            color = textColor
        )
    }
}

@Composable
private fun EmptyChatGreetingView(
    onSelectPrompt: (String) -> Unit
) {
    val promptIdeas = stringResource(R.string.prompt_brainstorm_ideas)
    val promptEarn = stringResource(R.string.prompt_ways_to_earn)
    val promptApp = stringResource(R.string.prompt_create_app)

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier
            .fillMaxWidth()
            .widthIn(max = 460.dp)
            .padding(horizontal = 24.dp)
    ) {
        GlowingOrangeNovaOrb(
            size = 118.dp,
            isPulsing = false,
            modifier = Modifier.testTag("chat_nova_orb")
        )

        Spacer(modifier = Modifier.height(26.dp))

        Text(
            text = stringResource(R.string.chat_greeting),
            style = MaterialTheme.typography.headlineMedium.copy(
                fontSize = 23.sp,
                lineHeight = 31.sp,
                fontWeight = FontWeight.SemiBold
            ),
            color = NovaTextPrimaryDark,
            textAlign = TextAlign.Center,
            modifier = Modifier.testTag("chat_greeting_text")
        )

        Spacer(modifier = Modifier.height(34.dp))

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            SuggestedPromptPill(
                icon = Icons.Outlined.Lightbulb,
                label = promptIdeas,
                onClick = { onSelectPrompt(promptIdeas) },
                testTag = "prompt_card_ideas"
            )
            SuggestedPromptPill(
                icon = Icons.Outlined.ChatBubbleOutline,
                label = promptEarn,
                onClick = { onSelectPrompt(promptEarn) },
                testTag = "prompt_card_earn"
            )
            SuggestedPromptPill(
                icon = Icons.Rounded.Code,
                label = promptApp,
                onClick = { onSelectPrompt(promptApp) },
                testTag = "prompt_card_app"
            )
        }
    }
}

@Composable
private fun SuggestedPromptPill(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    testTag: String
) {
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = NovaCharcoal,
        border = BorderStroke(1.dp, NovaBorderDark),
        modifier = Modifier
            .height(48.dp)
            .testTag(testTag)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 20.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = NovaTextSecondaryDark,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                color = Color(0xFFD6D6E0)
            )
        }
    }
}

@Composable
private fun ChatBottomInputCard(
    draftText: String,
    pendingAttachment: PendingMediaAttachment?,
    isLoadingAttachment: Boolean,
    isGenerating: Boolean,
    isInlineListening: Boolean,
    onDraftChange: (String) -> Unit,
    onOpenAttachSheet: () -> Unit,
    onClearAttachment: () -> Unit,
    onDictateSpeech: () -> Unit,
    onOpenVoiceMode: () -> Unit,
    onSendOrStop: () -> Unit,
    modifier: Modifier = Modifier
) {
    val canSend = isGenerating || draftText.isNotBlank() || pendingAttachment != null

    Surface(
        shape = RoundedCornerShape(26.dp),
        color = NovaCharcoal,
        border = BorderStroke(1.dp, NovaBorderDark),
        modifier = modifier
    ) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
            if (pendingAttachment != null || isLoadingAttachment) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(bottom = 10.dp)
                ) {
                    if (isLoadingAttachment) {
                        CircularProgressIndicator(
                            color = NovaOrangeBright,
                            strokeWidth = 2.dp,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    if (pendingAttachment != null) {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = NovaSurfaceElevated,
                            border = BorderStroke(1.dp, NovaBorderDark)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(start = 8.dp, top = 4.dp, bottom = 4.dp, end = 4.dp)
                            ) {
                                if (pendingAttachment.previewBitmap != null) {
                                    Image(
                                        bitmap = pendingAttachment.previewBitmap.asImageBitmap(),
                                        contentDescription = null,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier
                                            .size(28.dp)
                                            .clip(RoundedCornerShape(6.dp))
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                }
                                Text(
                                    text = pendingAttachment.displayName,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = NovaTextPrimaryDark
                                )
                                IconButton(
                                    onClick = onClearAttachment,
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Close,
                                        contentDescription = stringResource(R.string.cd_remove_attachment),
                                        tint = NovaTextSecondaryDark,
                                        modifier = Modifier.size(14.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 2.dp, vertical = 4.dp)
            ) {
                if (draftText.isEmpty()) {
                    val placeholder = if (isInlineListening) {
                        stringResource(R.string.input_placeholder_listening)
                    } else {
                        stringResource(R.string.input_placeholder_chat)
                    }
                    Text(
                        text = placeholder,
                        style = MaterialTheme.typography.bodyLarge,
                        color = if (isInlineListening) NovaOrangeBright else NovaTextMutedDark
                    )
                }

                BasicTextField(
                    value = draftText,
                    onValueChange = onDraftChange,
                    textStyle = MaterialTheme.typography.bodyLarge.copy(
                        color = NovaTextPrimaryDark
                    ),
                    cursorBrush = SolidColor(NovaOrangeBright),
                    maxLines = 5,
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.Sentences,
                        imeAction = ImeAction.Send
                    ),
                    keyboardActions = KeyboardActions(
                        onSend = {
                            if (canSend && !isGenerating) onSendOrStop()
                        }
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("chat_message_input")
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Surface(
                    onClick = onOpenAttachSheet,
                    shape = CircleShape,
                    color = NovaSurfaceElevated,
                    border = BorderStroke(1.dp, NovaBorderDark),
                    modifier = Modifier
                        .size(48.dp)
                        .testTag("attach_button")
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Rounded.Add,
                            contentDescription = stringResource(R.string.cd_attach_media),
                            tint = NovaTextPrimaryDark,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Inline Dictation Mic Button
                    Surface(
                        onClick = onDictateSpeech,
                        shape = CircleShape,
                        color = NovaSurfaceElevated,
                        border = BorderStroke(
                            1.dp,
                            if (isInlineListening) NovaOrangeCore else NovaBorderDark
                        ),
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("inline_dictate_button")
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Outlined.Mic,
                                contentDescription = stringResource(R.string.cd_inline_dictate),
                                tint = if (isInlineListening) NovaOrangeBright else NovaTextSecondaryDark,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }

                    if (canSend) {
                        // Glowing Send / Stop Action Button when typing or generating
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .size(48.dp)
                                .shadow(
                                    elevation = 12.dp,
                                    shape = CircleShape,
                                    ambientColor = NovaOrangeCore,
                                    spotColor = NovaOrangeCore
                                )
                                .clip(CircleShape)
                                .background(
                                    brush = Brush.linearGradient(
                                        colors = listOf(
                                            NovaOrangeHighlight,
                                            NovaOrangeBright,
                                            NovaOrangeCore,
                                            NovaOrangeDeep
                                        )
                                    )
                                )
                                .border(
                                    width = 1.dp,
                                    color = NovaOrangeHighlight.copy(alpha = 0.55f),
                                    shape = CircleShape
                                )
                                .clickable(onClick = onSendOrStop)
                                .testTag("send_message_button")
                        ) {
                            Icon(
                                imageVector = if (isGenerating) Icons.Rounded.Stop else Icons.Rounded.NorthEast,
                                contentDescription = if (isGenerating) {
                                    stringResource(R.string.cd_stop_generation)
                                } else {
                                    stringResource(R.string.cd_send_message)
                                },
                                tint = NovaBlack,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    } else {
                        // Circular Sky-Blue Live Voice Conversation Waveform Button (matching Video 00:00)
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .size(48.dp)
                                .shadow(
                                    elevation = 10.dp,
                                    shape = CircleShape,
                                    ambientColor = Color(0xFF2563EB),
                                    spotColor = Color(0xFF2563EB)
                                )
                                .clip(CircleShape)
                                .background(
                                    brush = Brush.linearGradient(
                                        colors = listOf(
                                            Color(0xFF3B82F6),
                                            Color(0xFF1D63ED)
                                        )
                                    )
                                )
                                .clickable(onClick = onOpenVoiceMode)
                                .testTag("open_voice_mode_button")
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.GraphicEq,
                                contentDescription = stringResource(R.string.cd_open_voice_mode),
                                tint = Color.White,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AttachmentCapabilitiesSheet(
    onPickPhoto: () -> Unit,
    onPickVideo: () -> Unit,
    onDictateSpeech: () -> Unit
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 20.dp, vertical = 12.dp)
    ) {
        Text(
            text = stringResource(R.string.attach_sheet_title),
            style = MaterialTheme.typography.titleMedium,
            color = NovaTextPrimaryDark,
            modifier = Modifier.padding(bottom = 4.dp)
        )

        AttachmentOptionRow(
            icon = Icons.Outlined.PhotoLibrary,
            title = stringResource(R.string.attach_photo_title),
            subtitle = stringResource(R.string.attach_photo_subtitle),
            onClick = onPickPhoto,
            testTag = "sheet_pick_photo"
        )

        AttachmentOptionRow(
            icon = Icons.Outlined.Videocam,
            title = stringResource(R.string.attach_video_title),
            subtitle = stringResource(R.string.attach_video_subtitle),
            onClick = onPickVideo,
            testTag = "sheet_pick_video"
        )

        AttachmentOptionRow(
            icon = Icons.Outlined.Mic,
            title = stringResource(R.string.attach_dictate_title),
            subtitle = stringResource(R.string.attach_dictate_subtitle),
            onClick = onDictateSpeech,
            testTag = "sheet_dictate_speech"
        )

        Spacer(modifier = Modifier.height(12.dp))
    }
}

@Composable
private fun AttachmentOptionRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    testTag: String
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        color = NovaSurfaceDark,
        border = BorderStroke(1.dp, NovaBorderDark),
        modifier = Modifier
            .fillMaxWidth()
            .testTag(testTag)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = NovaOrangeBright,
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    color = NovaTextPrimaryDark
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = NovaTextSecondaryDark
                )
            }
        }
    }
}
