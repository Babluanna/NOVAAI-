package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Chat
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.model.Conversation
import com.example.data.model.VoiceRecording
import com.example.ui.theme.NovaBlack
import com.example.ui.theme.NovaBorderDark
import com.example.ui.theme.NovaCharcoal
import com.example.ui.theme.NovaNeonViolet
import com.example.ui.theme.NovaOrangeBright
import com.example.ui.theme.NovaOrangeCore
import com.example.ui.theme.NovaSurfaceDark
import com.example.ui.theme.NovaSurfaceElevated
import com.example.ui.theme.NovaTextMutedDark
import com.example.ui.theme.NovaTextPrimaryDark
import com.example.ui.theme.NovaTextSecondaryDark
import com.example.ui.viewmodel.UiState
import com.google.firebase.auth.FirebaseUser

@Composable
fun NovaDrawerSheet(
    user: FirebaseUser?,
    conversationsState: UiState<List<Conversation>>,
    activeConversationId: String?,
    voiceRecordings: List<VoiceRecording>,
    playingRecordingId: String?,
    onNewChat: () -> Unit,
    onOpenVoiceMode: () -> Unit,
    onSelectConversation: (Conversation) -> Unit,
    onDeleteConversation: (String) -> Unit,
    onPlayVoiceRecording: (VoiceRecording) -> Unit,
    onDeleteVoiceRecording: (String) -> Unit,
    onSignOut: () -> Unit,
    modifier: Modifier = Modifier
) {
    ModalDrawerSheet(
        drawerContainerColor = NovaCharcoal,
        drawerContentColor = NovaTextPrimaryDark,
        modifier = modifier
            .widthIn(max = 336.dp)
            .fillMaxHeight()
            .testTag("nova_drawer_sheet")
    ) {
        Column(
            modifier = Modifier
                .fillMaxHeight()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            // Brand Header
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp)
            ) {
                GlowingOrangeNovaOrb(size = 38.dp)
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.app_name),
                        style = MaterialTheme.typography.titleLarge,
                        color = NovaTextPrimaryDark
                    )
                    Text(
                        text = stringResource(R.string.brand_tagline),
                        style = MaterialTheme.typography.labelSmall,
                        color = NovaTextSecondaryDark
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Primary Actions: New Chat & Voice Mode
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Button(
                    onClick = onNewChat,
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = NovaOrangeCore,
                        contentColor = Color.White
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .testTag("drawer_new_chat_button")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Add,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = stringResource(R.string.new_chat),
                        style = MaterialTheme.typography.labelLarge
                    )
                }

                Surface(
                    onClick = onOpenVoiceMode,
                    shape = RoundedCornerShape(16.dp),
                    color = NovaSurfaceElevated,
                    border = BorderStroke(1.dp, NovaBorderDark),
                    modifier = Modifier
                        .height(48.dp)
                        .testTag("drawer_open_voice_button")
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 14.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.GraphicEq,
                            contentDescription = stringResource(R.string.cd_open_voice_mode),
                            tint = Color(0xFFD8B4FE),
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = stringResource(R.string.voice_tab),
                            style = MaterialTheme.typography.labelLarge,
                            color = NovaTextPrimaryDark
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Free-Tier Gemini Engine Badge
            Text(
                text = stringResource(R.string.model_badge_title),
                style = MaterialTheme.typography.labelMedium,
                color = NovaTextMutedDark,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = NovaSurfaceElevated,
                border = BorderStroke(1.dp, NovaOrangeCore.copy(alpha = 0.6f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Bolt,
                        contentDescription = null,
                        tint = NovaOrangeBright,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.model_free_tier_name),
                            style = MaterialTheme.typography.labelLarge,
                            color = NovaTextPrimaryDark
                        )
                        Text(
                            text = stringResource(R.string.model_free_tier_subtitle),
                            style = MaterialTheme.typography.labelSmall,
                            color = NovaTextSecondaryDark
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
            HorizontalDivider(color = NovaBorderDark)
            Spacer(modifier = Modifier.height(12.dp))

            // Scrollable Chat History + Saved Voice Transcripts
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                item {
                    Text(
                        text = stringResource(R.string.chat_history),
                        style = MaterialTheme.typography.labelMedium,
                        color = NovaTextMutedDark,
                        modifier = Modifier.padding(vertical = 4.dp)
                    )
                }

                when (conversationsState) {
                    is UiState.Loading -> {
                        item {
                            Text(
                                text = "Loading history…",
                                style = MaterialTheme.typography.bodySmall,
                                color = NovaTextSecondaryDark,
                                modifier = Modifier.padding(vertical = 8.dp)
                            )
                        }
                    }
                    is UiState.Error -> {
                        item {
                            Text(
                                text = conversationsState.message,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error,
                                modifier = Modifier.padding(vertical = 8.dp)
                            )
                        }
                    }
                    is UiState.Success -> {
                        val list = conversationsState.data
                        if (list.isEmpty()) {
                            item {
                                Text(
                                    text = stringResource(R.string.no_conversations_yet),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = NovaTextMutedDark,
                                    modifier = Modifier.padding(vertical = 6.dp)
                                )
                            }
                        } else {
                            items(list, key = { it.id }) { conv ->
                                val isSelected = conv.id == activeConversationId
                                ConversationDrawerRow(
                                    conversation = conv,
                                    isSelected = isSelected,
                                    onClick = { onSelectConversation(conv) },
                                    onDelete = { onDeleteConversation(conv.id) }
                                )
                            }
                        }
                    }
                }

                item {
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = stringResource(R.string.voice_transcripts),
                        style = MaterialTheme.typography.labelMedium,
                        color = NovaTextMutedDark,
                        modifier = Modifier.padding(vertical = 4.dp)
                    )
                }

                if (voiceRecordings.isEmpty()) {
                    item {
                        Text(
                            text = stringResource(R.string.no_voice_recordings_yet),
                            style = MaterialTheme.typography.bodySmall,
                            color = NovaTextMutedDark,
                            modifier = Modifier.padding(vertical = 6.dp)
                        )
                    }
                } else {
                    items(voiceRecordings, key = { it.id }) { rec ->
                        VoiceRecordingDrawerRow(
                            recording = rec,
                            isPlaying = playingRecordingId == rec.id,
                            onPlayToggle = { onPlayVoiceRecording(rec) },
                            onDelete = { onDeleteVoiceRecording(rec.id) }
                        )
                    }
                }
            }

            HorizontalDivider(color = NovaBorderDark)
            Spacer(modifier = Modifier.height(10.dp))

            // Signed-in User Footer + Sign Out
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(
                            Brush.linearGradient(
                                colors = listOf(NovaOrangeCore, NovaNeonViolet)
                            )
                        )
                ) {
                    val initial = (user?.displayName?.firstOrNull()
                        ?: user?.email?.firstOrNull()
                        ?: 'N').uppercaseChar()
                    Text(
                        text = initial.toString(),
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                        color = Color.White
                    )
                }
                Spacer(modifier = Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = user?.displayName?.takeIf { it.isNotBlank() } ?: "Nova Member",
                        style = MaterialTheme.typography.labelLarge,
                        color = NovaTextPrimaryDark,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = user?.email.orEmpty(),
                        style = MaterialTheme.typography.labelSmall,
                        color = NovaTextSecondaryDark,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                IconButton(
                    onClick = onSignOut,
                    modifier = Modifier
                        .size(48.dp)
                        .testTag("drawer_sign_out_button")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.Logout,
                        contentDescription = stringResource(R.string.sign_out),
                        tint = NovaTextSecondaryDark
                    )
                }
            }
        }
    }
}

@Composable
private fun ConversationDrawerRow(
    conversation: Conversation,
    isSelected: Boolean,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = if (isSelected) NovaSurfaceElevated else NovaBlack.copy(alpha = 0.45f),
        border = if (isSelected) BorderStroke(1.dp, NovaOrangeCore.copy(alpha = 0.5f)) else null,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .testTag("conversation_item_${conversation.id}")
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(start = 12.dp, top = 6.dp, bottom = 6.dp, end = 4.dp)
        ) {
            Icon(
                imageVector = if (conversation.mode == "voice") {
                    Icons.Outlined.Mic
                } else {
                    Icons.AutoMirrored.Outlined.Chat
                },
                contentDescription = null,
                tint = if (isSelected) NovaOrangeBright else NovaTextSecondaryDark,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = conversation.title,
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                    color = NovaTextPrimaryDark,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (conversation.previewText.isNotBlank()) {
                    Text(
                        text = conversation.previewText,
                        style = MaterialTheme.typography.labelSmall,
                        color = NovaTextMutedDark,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            IconButton(
                onClick = onDelete,
                modifier = Modifier.size(48.dp)
            ) {
                Icon(
                    imageVector = Icons.Outlined.DeleteOutline,
                    contentDescription = stringResource(R.string.cd_delete_item),
                    tint = NovaTextMutedDark,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

@Composable
private fun VoiceRecordingDrawerRow(
    recording: VoiceRecording,
    isPlaying: Boolean,
    onPlayToggle: () -> Unit,
    onDelete: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = NovaSurfaceDark,
        border = BorderStroke(1.dp, NovaBorderDark),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("voice_recording_item_${recording.id}")
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(start = 10.dp, top = 6.dp, bottom = 6.dp, end = 4.dp)
        ) {
            IconButton(
                onClick = onPlayToggle,
                modifier = Modifier.size(48.dp)
            ) {
                Icon(
                    imageVector = if (isPlaying) Icons.Rounded.Stop else Icons.Rounded.PlayArrow,
                    contentDescription = stringResource(R.string.cd_play_recording),
                    tint = Color(0xFFD8B4FE)
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = recording.title,
                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                    color = NovaTextPrimaryDark,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "${formatShortDuration(recording.durationSeconds)} · ${recording.aiTranscript.ifBlank { recording.userTranscript }}",
                    style = MaterialTheme.typography.labelSmall,
                    color = NovaTextSecondaryDark,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            IconButton(
                onClick = onDelete,
                modifier = Modifier.size(48.dp)
            ) {
                Icon(
                    imageVector = Icons.Outlined.DeleteOutline,
                    contentDescription = stringResource(R.string.cd_delete_item),
                    tint = NovaTextMutedDark,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

private fun formatShortDuration(sec: Long): String {
    val m = sec / 60
    val s = sec % 60
    return "%d:%02d".format(m, s)
}
