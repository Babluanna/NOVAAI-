package com.example.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Subject
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.MicOff
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import androidx.core.content.ContextCompat
import com.example.R
import com.example.data.model.NovaVoiceLanguage
import com.example.data.model.NovaVoicePersona
import com.example.data.model.VoiceRecording
import com.example.ui.components.NovaSkyCloudOrb
import com.example.ui.components.VoiceSettingsBottomSheetContent
import com.example.voice.VoiceSessionStatus
import com.example.voice.VoiceUiState

private val VoiceCanvasWhite = Color(0xFFFFFFFF)
private val VoiceControlMist = Color(0xFFF2F4F8)
private val VoiceControlBorder = Color(0xFFE4E8F0)
private val VoiceDarkInk = Color(0xFF111318)
private val VoiceSubtleInk = Color(0xFF64748B)
private val VoiceSkyBlue = Color(0xFF1D63ED)
private val VoiceMutedBg = Color(0xFFFEE2E2)
private val VoiceMutedInk = Color(0xFFDC2626)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VoiceModeScreen(
    voiceState: VoiceUiState,
    voiceRecordings: List<VoiceRecording>,
    onBackToChat: () -> Unit,
    onCentralMicClick: () -> Unit,
    onToggleMute: () -> Unit,
    onSendVoiceTextPrompt: (String) -> Unit,
    onAttachVoicePhoto: (Uri) -> Unit,
    onClearVoicePhoto: () -> Unit,
    onSelectPersona: (NovaVoicePersona) -> Unit,
    onPreviewPersona: (NovaVoicePersona) -> Unit,
    onSelectLanguage: (NovaVoiceLanguage) -> Unit,
    onToggleCaptions: () -> Unit,
    onInterruptAiSpeech: () -> Unit,
    onPlayRecording: (VoiceRecording) -> Unit,
    onDeleteRecording: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    BackHandler(onBack = onBackToChat)

    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    val inputFocusRequester = remember { FocusRequester() }

    var showPermissionCard by remember { mutableStateOf(false) }
    var showSettingsSheet by remember { mutableStateOf(false) }
    var showTranscriptsSheet by remember { mutableStateOf(false) }
    var isTypingPrompt by remember { mutableStateOf(false) }
    var voiceDraftText by remember { mutableStateOf("") }

    val voicePhotoPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            onAttachVoicePhoto(uri)
        }
    }

    val audioPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        showPermissionCard = !isGranted
        if (isGranted) {
            onCentralMicClick()
        }
    }

    // Automatically request mic permission on entering Voice Mode if not yet granted
    LaunchedEffect(Unit) {
        val granted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
        if (!granted) {
            audioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    LaunchedEffect(isTypingPrompt) {
        if (isTypingPrompt) {
            runCatching { inputFocusRequester.requestFocus() }
        }
    }

    val handleOrbTap = {
        val hasMicPerm = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
        if (voiceState.status == VoiceSessionStatus.AI_SPEAKING ||
            voiceState.status == VoiceSessionStatus.PLAYING_RECORDING
        ) {
            onInterruptAiSpeech()
        } else if (hasMicPerm) {
            showPermissionCard = false
            onCentralMicClick()
        } else {
            audioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween,
        modifier = modifier
            .fillMaxSize()
            .background(VoiceCanvasWhite)
            .statusBarsPadding()
            .navigationBarsPadding()
            .imePadding()
            .padding(vertical = 12.dp)
            .testTag("voice_mode_screen")
    ) {
        // 1. Minimalist Top Bar matching Reference Video (Top-Left Transcripts/Menu, Top-Right Tune)
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 680.dp)
                .padding(horizontal = 20.dp)
        ) {
            Surface(
                onClick = { showTranscriptsSheet = true },
                shape = CircleShape,
                color = VoiceControlMist,
                modifier = Modifier
                    .size(48.dp)
                    .testTag("voice_history_button")
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.Subject,
                        contentDescription = stringResource(R.string.cd_voice_captions),
                        tint = VoiceDarkInk,
                        modifier = Modifier.size(21.dp)
                    )
                }
            }

            // Subtle language & persona pill only when non-default language is chosen
            if (voiceState.selectedLanguage != NovaVoiceLanguage.AUTO) {
                Surface(
                    onClick = { showSettingsSheet = true },
                    shape = CircleShape,
                    color = VoiceControlMist
                ) {
                    Text(
                        text = "${voiceState.selectedPersona.displayName} · ${voiceState.selectedLanguage.badgeLabel}",
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = VoiceSkyBlue,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                    )
                }
            } else {
                Spacer(modifier = Modifier.width(8.dp))
            }

            Surface(
                onClick = { showSettingsSheet = true },
                shape = CircleShape,
                color = VoiceControlMist,
                modifier = Modifier
                    .size(48.dp)
                    .testTag("voice_settings_button")
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Rounded.Tune,
                        contentDescription = stringResource(R.string.cd_voice_settings),
                        tint = VoiceDarkInk,
                        modifier = Modifier.size(21.dp)
                    )
                }
            }
        }

        // 2. Centerpiece: Living Sky & Cloud Fluid Orb + Subtle Floating Live Caption
        BoxWithConstraints(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
        ) {
            val adaptiveOrbSize = min(256.dp, min(maxWidth * 0.68f, maxHeight * 0.64f))
                .coerceAtLeast(156.dp)

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
                modifier = Modifier.fillMaxWidth()
            ) {
                NovaSkyCloudOrb(
                    status = voiceState.status,
                    isMuted = voiceState.isMuted,
                    energy = voiceState.waveformEnergy,
                    contentDesc = stringResource(R.string.cd_voice_orb),
                    orbSize = adaptiveOrbSize,
                    onClick = handleOrbTap
                )

                Spacer(modifier = Modifier.height(22.dp))

                // Permission Prompt Card if microphone access was denied
                AnimatedVisibility(
                    visible = showPermissionCard,
                    enter = fadeIn(),
                    exit = fadeOut()
                ) {
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = VoiceControlMist,
                        border = BorderStroke(1.dp, VoiceControlBorder),
                        modifier = Modifier
                            .widthIn(max = 400.dp)
                            .padding(bottom = 10.dp)
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.padding(horizontal = 18.dp, vertical = 14.dp)
                        ) {
                            Text(
                                text = stringResource(R.string.voice_permission_rationale),
                                style = MaterialTheme.typography.bodySmall,
                                color = VoiceDarkInk,
                                textAlign = TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            Button(
                                onClick = {
                                    audioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = VoiceSkyBlue,
                                    contentColor = Color.White
                                ),
                                shape = CircleShape,
                                modifier = Modifier.height(40.dp)
                            ) {
                                Text(text = stringResource(R.string.voice_grant_mic))
                            }
                        }
                    }
                }

                // Subtle Floating Caption / Status Cue below the Sky-Cloud Orb
                AnimatedVisibility(
                    visible = voiceState.showCaptions || voiceState.errorMessage != null,
                    enter = fadeIn(tween(220)),
                    exit = fadeOut(tween(180))
                ) {
                    val captionText = when {
                        voiceState.errorMessage != null -> voiceState.errorMessage
                        voiceState.status == VoiceSessionStatus.AI_SPEAKING &&
                            voiceState.liveAiTranscript.isNotBlank() -> voiceState.liveAiTranscript
                        voiceState.status == VoiceSessionStatus.PROCESSING &&
                            voiceState.liveAiTranscript.isNotBlank() -> voiceState.liveAiTranscript
                        voiceState.liveUserTranscript.isNotBlank() &&
                            (voiceState.status == VoiceSessionStatus.LISTENING ||
                                voiceState.status == VoiceSessionStatus.PROCESSING) ->
                            "“${voiceState.liveUserTranscript}”"
                        voiceState.isMuted -> stringResource(R.string.voice_status_muted)
                        voiceState.status == VoiceSessionStatus.LISTENING ->
                            stringResource(R.string.voice_status_listening)
                        voiceState.status == VoiceSessionStatus.PROCESSING ->
                            stringResource(R.string.voice_status_thinking)
                        voiceState.status == VoiceSessionStatus.AI_SPEAKING ->
                            stringResource(R.string.voice_status_speaking)
                        else -> voiceState.statusHint
                    }

                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .widthIn(max = 440.dp)
                            .padding(horizontal = 12.dp)
                            .testTag("voice_live_transcript_pill")
                    ) {
                        Text(
                            text = captionText,
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontWeight = FontWeight.Normal
                            ),
                            color = if (voiceState.errorMessage != null) {
                                VoiceMutedInk
                            } else if (voiceState.status == VoiceSessionStatus.AI_SPEAKING) {
                                VoiceDarkInk
                            } else {
                                VoiceSubtleInk
                            },
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis,
                            textAlign = TextAlign.Center
                        )

                        if (voiceState.status == VoiceSessionStatus.AI_SPEAKING ||
                            voiceState.status == VoiceSessionStatus.PLAYING_RECORDING
                        ) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Surface(
                                onClick = onInterruptAiSpeech,
                                shape = CircleShape,
                                color = VoiceControlMist,
                                modifier = Modifier.testTag("voice_interrupt_button")
                            ) {
                                Text(
                                    text = stringResource(R.string.voice_status_speaking),
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontWeight = FontWeight.Medium
                                    ),
                                    color = VoiceSubtleInk,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp)
                                )
                            }
                        }
                    }
                }
            }
        }

        // 3. Bottom Control Bar matching Reference Video (`+ Ask Nova AI` | Mute Mic | Dark `X` Close)
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 680.dp)
                .padding(horizontal = 20.dp, vertical = 4.dp)
        ) {
            // Optional Attached Photo Chip for Multimodal Voice Chat
            if (voiceState.pendingVoicePhoto != null) {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = VoiceControlMist,
                    border = BorderStroke(1.dp, VoiceControlBorder),
                    modifier = Modifier
                        .align(Alignment.Start)
                        .padding(bottom = 10.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(start = 8.dp, top = 6.dp, bottom = 6.dp, end = 6.dp)
                    ) {
                        voiceState.pendingVoicePhoto.previewBitmap?.let { bmp ->
                            Image(
                                bitmap = bmp.asImageBitmap(),
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .size(32.dp)
                                    .clip(RoundedCornerShape(8.dp))
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                        }
                        Text(
                            text = "Photo ready · Speak or ask Nova",
                            style = MaterialTheme.typography.labelMedium,
                            color = VoiceDarkInk
                        )
                        IconButton(
                            onClick = onClearVoicePhoto,
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Close,
                                contentDescription = stringResource(R.string.cd_remove_attachment),
                                tint = VoiceSubtleInk,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }

            AnimatedContent(
                targetState = isTypingPrompt,
                transitionSpec = {
                    fadeIn(tween(180)).togetherWith(fadeOut(tween(150)))
                },
                label = "voice_bottom_bar_mode"
            ) { expandedComposer ->
                if (expandedComposer) {
                    // Expanded Inline Voice Composer so users can type & hear Nova speak back
                    Surface(
                        shape = CircleShape,
                        color = VoiceControlMist,
                        border = BorderStroke(1.dp, VoiceControlBorder),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)
                        ) {
                            IconButton(
                                onClick = {
                                    voicePhotoPicker.launch(
                                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                    )
                                },
                                modifier = Modifier.size(44.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Add,
                                    contentDescription = stringResource(R.string.cd_voice_attach_photo),
                                    tint = VoiceDarkInk
                                )
                            }

                            Box(
                                contentAlignment = Alignment.CenterStart,
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(horizontal = 6.dp)
                            ) {
                                if (voiceDraftText.isEmpty()) {
                                    Text(
                                        text = stringResource(R.string.voice_ask_placeholder),
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = VoiceSubtleInk
                                    )
                                }
                                BasicTextField(
                                    value = voiceDraftText,
                                    onValueChange = { voiceDraftText = it },
                                    textStyle = MaterialTheme.typography.bodyMedium.copy(
                                        color = VoiceDarkInk
                                    ),
                                    cursorBrush = SolidColor(VoiceSkyBlue),
                                    singleLine = true,
                                    keyboardOptions = KeyboardOptions(
                                        capitalization = KeyboardCapitalization.Sentences,
                                        imeAction = ImeAction.Send
                                    ),
                                    keyboardActions = KeyboardActions(
                                        onSend = {
                                            if (voiceDraftText.isNotBlank() || voiceState.pendingVoicePhoto != null) {
                                                val toSend = voiceDraftText
                                                voiceDraftText = ""
                                                isTypingPrompt = false
                                                focusManager.clearFocus()
                                                onSendVoiceTextPrompt(toSend)
                                            }
                                        }
                                    ),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .focusRequester(inputFocusRequester)
                                        .testTag("voice_inline_text_input")
                                )
                            }

                            IconButton(
                                onClick = {
                                    voiceDraftText = ""
                                    isTypingPrompt = false
                                    focusManager.clearFocus()
                                },
                                modifier = Modifier.size(40.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Close,
                                    contentDescription = "Close text input",
                                    tint = VoiceSubtleInk,
                                    modifier = Modifier.size(18.dp)
                                )
                            }

                            val canSubmit = voiceDraftText.isNotBlank() || voiceState.pendingVoicePhoto != null
                            Surface(
                                onClick = {
                                    if (canSubmit) {
                                        val toSend = voiceDraftText
                                        voiceDraftText = ""
                                        isTypingPrompt = false
                                        focusManager.clearFocus()
                                        onSendVoiceTextPrompt(toSend)
                                    }
                                },
                                enabled = canSubmit,
                                shape = CircleShape,
                                color = if (canSubmit) VoiceSkyBlue else VoiceControlBorder,
                                modifier = Modifier
                                    .size(44.dp)
                                    .testTag("voice_inline_send_button")
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Rounded.ArrowUpward,
                                        contentDescription = stringResource(R.string.cd_send_message),
                                        tint = Color.White,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                        }
                    }
                } else {
                    // Canonical Video Control Row: `+ Ask Nova AI` Pill | Circular Mute Mic | Dark `X` Close
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        // Left: `+ Ask Nova AI` Pill
                        Surface(
                            shape = CircleShape,
                            color = VoiceControlMist,
                            modifier = Modifier
                                .weight(1f)
                                .height(54.dp)
                                .testTag("voice_ask_pill_button")
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxSize()
                            ) {
                                Box(
                                    contentAlignment = Alignment.Center,
                                    modifier = Modifier
                                        .size(54.dp)
                                        .clip(CircleShape)
                                        .clickable {
                                            voicePhotoPicker.launch(
                                                PickVisualMediaRequest(
                                                    ActivityResultContracts.PickVisualMedia.ImageOnly
                                                )
                                            )
                                        }
                                        .testTag("voice_attach_photo_button")
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Add,
                                        contentDescription = stringResource(R.string.cd_voice_attach_photo),
                                        tint = VoiceDarkInk,
                                        modifier = Modifier.size(22.dp)
                                    )
                                }

                                Box(
                                    contentAlignment = Alignment.CenterStart,
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(54.dp)
                                        .clickable { isTypingPrompt = true }
                                        .padding(end = 16.dp)
                                ) {
                                    Text(
                                        text = stringResource(R.string.voice_ask_nova_pill),
                                        style = MaterialTheme.typography.bodyLarge.copy(
                                            fontWeight = FontWeight.Medium
                                        ),
                                        color = VoiceSubtleInk,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }

                        // Middle-Right: Mute / Unmute Microphone Circle (with slash when muted, matching video)
                        val muteBg by animateColorAsState(
                            targetValue = if (voiceState.isMuted) VoiceMutedBg else VoiceControlMist,
                            label = "voice_mute_bg"
                        )
                        val muteTint by animateColorAsState(
                            targetValue = if (voiceState.isMuted) VoiceMutedInk else VoiceDarkInk,
                            label = "voice_mute_tint"
                        )

                        Surface(
                            onClick = {
                                val hasPerm = ContextCompat.checkSelfPermission(
                                    context,
                                    Manifest.permission.RECORD_AUDIO
                                ) == PackageManager.PERMISSION_GRANTED
                                if (hasPerm) {
                                    onToggleMute()
                                } else {
                                    audioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                }
                            },
                            shape = CircleShape,
                            color = muteBg,
                            modifier = Modifier
                                .size(54.dp)
                                .testTag("voice_mute_button")
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = if (voiceState.isMuted) {
                                        Icons.Outlined.MicOff
                                    } else {
                                        Icons.Outlined.Mic
                                    },
                                    contentDescription = stringResource(R.string.cd_voice_mute),
                                    tint = muteTint,
                                    modifier = Modifier.size(23.dp)
                                )
                            }
                        }

                        // Far-Right: Dark Circular Close (`X`) Button matching Reference Video
                        Surface(
                            onClick = onBackToChat,
                            shape = CircleShape,
                            color = VoiceDarkInk,
                            modifier = Modifier
                                .size(54.dp)
                                .testTag("voice_close_button")
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Rounded.Close,
                                    contentDescription = stringResource(R.string.cd_voice_close),
                                    tint = Color.White,
                                    modifier = Modifier.size(23.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (showSettingsSheet) {
        ModalBottomSheet(
            onDismissRequest = { showSettingsSheet = false },
            containerColor = VoiceCanvasWhite,
            contentColor = VoiceDarkInk
        ) {
            VoiceSettingsBottomSheetContent(
                selectedPersona = voiceState.selectedPersona,
                selectedLanguage = voiceState.selectedLanguage,
                showCaptions = voiceState.showCaptions,
                savedRecordingsCount = voiceRecordings.size,
                onSelectPersona = onSelectPersona,
                onPreviewPersona = onPreviewPersona,
                onSelectLanguage = onSelectLanguage,
                onToggleCaptions = onToggleCaptions,
                onOpenSavedTranscripts = {
                    showSettingsSheet = false
                    showTranscriptsSheet = true
                }
            )
        }
    }

    if (showTranscriptsSheet) {
        ModalBottomSheet(
            onDismissRequest = { showTranscriptsSheet = false },
            containerColor = VoiceCanvasWhite,
            contentColor = VoiceDarkInk
        ) {
            VoiceTranscriptsBottomSheet(
                recordings = voiceRecordings,
                playingRecordingId = voiceState.playingRecordingId,
                onPlayToggle = onPlayRecording,
                onDelete = onDeleteRecording
            )
        }
    }
}

@Composable
private fun VoiceTranscriptsBottomSheet(
    recordings: List<VoiceRecording>,
    playingRecordingId: String?,
    onPlayToggle: (VoiceRecording) -> Unit,
    onDelete: (String) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 20.dp, vertical = 12.dp)
    ) {
        Text(
            text = stringResource(R.string.voice_transcripts),
            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
            color = VoiceDarkInk,
            modifier = Modifier.padding(bottom = 12.dp)
        )

        if (recordings.isEmpty()) {
            Text(
                text = stringResource(R.string.no_voice_recordings_yet),
                style = MaterialTheme.typography.bodyMedium,
                color = VoiceSubtleInk,
                modifier = Modifier.padding(vertical = 24.dp)
            )
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(320.dp)
            ) {
                items(recordings, key = { it.id }) { rec ->
                    val isPlaying = playingRecordingId == rec.id
                    Surface(
                        shape = RoundedCornerShape(18.dp),
                        color = VoiceControlMist,
                        border = BorderStroke(1.dp, VoiceControlBorder),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(12.dp)
                        ) {
                            IconButton(
                                onClick = { onPlayToggle(rec) },
                                modifier = Modifier.size(48.dp)
                            ) {
                                Icon(
                                    imageVector = if (isPlaying) Icons.Rounded.Stop else Icons.Rounded.PlayArrow,
                                    contentDescription = stringResource(R.string.cd_play_recording),
                                    tint = VoiceSkyBlue
                                )
                            }
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = rec.title,
                                    style = MaterialTheme.typography.titleSmall.copy(
                                        fontWeight = FontWeight.SemiBold
                                    ),
                                    color = VoiceDarkInk,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                if (rec.userTranscript.isNotBlank()) {
                                    Text(
                                        text = "You: ${rec.userTranscript}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = VoiceSubtleInk,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                if (rec.aiTranscript.isNotBlank()) {
                                    Text(
                                        text = "Nova: ${rec.aiTranscript}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = VoiceDarkInk,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                            IconButton(
                                onClick = { onDelete(rec.id) },
                                modifier = Modifier.size(48.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.DeleteOutline,
                                    contentDescription = stringResource(R.string.cd_delete_item),
                                    tint = VoiceSubtleInk
                                )
                            }
                        }
                    }
                }
            }
        }
        Spacer(modifier = Modifier.height(12.dp))
    }
}
