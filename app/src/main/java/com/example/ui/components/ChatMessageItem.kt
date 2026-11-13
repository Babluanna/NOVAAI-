package com.example.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.VolumeUp
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.Videocam
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.StopCircle
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.ai.MediaAttachmentHelper
import com.example.data.model.ChatMessage
import com.example.ui.theme.NovaBorderDark
import com.example.ui.theme.NovaOrangeBright
import com.example.ui.theme.NovaSurfaceDark
import com.example.ui.theme.NovaSurfaceElevated
import com.example.ui.theme.NovaTextMutedDark
import com.example.ui.theme.NovaTextPrimaryDark
import com.example.ui.theme.NovaTextSecondaryDark
import kotlinx.coroutines.delay

@Composable
fun ChatMessageItem(
    message: ChatMessage,
    isSpeaking: Boolean,
    onRegenerate: () -> Unit,
    onToggleSpeak: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (message.role == "user") {
        UserMessageBubble(message = message, modifier = modifier)
    } else {
        NovaAssistantMessageBlock(
            message = message,
            isSpeaking = isSpeaking,
            onRegenerate = onRegenerate,
            onToggleSpeak = onToggleSpeak,
            modifier = modifier
        )
    }
}

@Composable
private fun UserMessageBubble(
    message: ChatMessage,
    modifier: Modifier = Modifier
) {
    val decodedBitmap = remember(message.mediaLocalPath, message.mediaThumbBase64) {
        MediaAttachmentHelper.decodeDisplayBitmap(message.mediaLocalPath, message.mediaThumbBase64)
    }

    Row(
        horizontalArrangement = Arrangement.End,
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
            .testTag("user_message_${message.id}")
    ) {
        Surface(
            shape = RoundedCornerShape(
                topStart = 22.dp,
                topEnd = 22.dp,
                bottomStart = 22.dp,
                bottomEnd = 6.dp
            ),
            color = NovaSurfaceElevated,
            border = BorderStroke(1.dp, NovaBorderDark),
            modifier = Modifier.widthIn(max = 320.dp)
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                if (decodedBitmap != null) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                    ) {
                        Image(
                            bitmap = decodedBitmap.asImageBitmap(),
                            contentDescription = "Attached media",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(168.dp)
                        )
                        if (message.messageType == "video_analysis") {
                            Surface(
                                shape = CircleShape,
                                color = Color.Black.copy(alpha = 0.68f),
                                modifier = Modifier
                                    .align(Alignment.BottomStart)
                                    .padding(8.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.Videocam,
                                        contentDescription = null,
                                        tint = Color.White,
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = "Video",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = Color.White
                                    )
                                }
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                }

                if (message.messageType == "voice") {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(bottom = 4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Mic,
                            contentDescription = null,
                            tint = Color(0xFFD8B4FE),
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "Voice",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color(0xFFD8B4FE)
                        )
                    }
                }

                SelectionContainer {
                    Text(
                        text = message.text,
                        style = MaterialTheme.typography.bodyLarge,
                        color = NovaTextPrimaryDark
                    )
                }
            }
        }
    }
}

@Composable
private fun NovaAssistantMessageBlock(
    message: ChatMessage,
    isSpeaking: Boolean,
    onRegenerate: () -> Unit,
    onToggleSpeak: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var copiedRecently by remember { mutableStateOf(false) }

    LaunchedEffect(copiedRecently) {
        if (copiedRecently) {
            delay(1800L)
            copiedRecently = false
        }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
            .testTag("model_message_${message.id}")
    ) {
        // Assistant Header Row
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(bottom = 8.dp)
        ) {
            GlowingOrangeNovaOrb(size = 24.dp)
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = stringResource(R.string.app_name),
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                color = NovaTextPrimaryDark
            )
            if (message.messageType == "voice") {
                Spacer(modifier = Modifier.width(8.dp))
                Surface(
                    shape = CircleShape,
                    color = NovaSurfaceDark,
                    border = BorderStroke(1.dp, NovaBorderDark)
                ) {
                    Text(
                        text = stringResource(R.string.voice_tab),
                        style = MaterialTheme.typography.labelSmall,
                        color = NovaTextSecondaryDark,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                    )
                }
            }
        }

        if (message.text.isNotBlank()) {
            SelectionContainer {
                Text(
                    text = message.text,
                    style = MaterialTheme.typography.bodyLarge,
                    color = NovaTextPrimaryDark,
                    modifier = Modifier.padding(horizontal = 2.dp)
                )
            }
        }

        // Action Row: Copy, Regenerate, Speak / Stop TTS
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(top = 4.dp)
        ) {
            IconButton(
                onClick = {
                    copyToClipboard(context, message.text)
                    copiedRecently = true
                },
                modifier = Modifier
                    .size(48.dp)
                    .testTag("copy_btn_${message.id}")
            ) {
                Icon(
                    imageVector = if (copiedRecently) Icons.Outlined.Check else Icons.Outlined.ContentCopy,
                    contentDescription = stringResource(R.string.copy_response),
                    tint = if (copiedRecently) NovaOrangeBright else NovaTextMutedDark,
                    modifier = Modifier.size(18.dp)
                )
            }

            IconButton(
                onClick = onRegenerate,
                modifier = Modifier
                    .size(48.dp)
                    .testTag("regenerate_btn_${message.id}")
            ) {
                Icon(
                    imageVector = Icons.Rounded.Refresh,
                    contentDescription = stringResource(R.string.regenerate_response),
                    tint = NovaTextMutedDark,
                    modifier = Modifier.size(19.dp)
                )
            }

            if (message.text.isNotBlank()) {
                IconButton(
                    onClick = onToggleSpeak,
                    modifier = Modifier
                        .size(48.dp)
                        .testTag("speak_btn_${message.id}")
                ) {
                    Icon(
                        imageVector = if (isSpeaking) {
                            Icons.Rounded.StopCircle
                        } else {
                            Icons.AutoMirrored.Outlined.VolumeUp
                        },
                        contentDescription = if (isSpeaking) {
                            stringResource(R.string.stop_speaking)
                        } else {
                            stringResource(R.string.speak_response)
                        },
                        tint = if (isSpeaking) NovaOrangeBright else NovaTextMutedDark,
                        modifier = Modifier.size(19.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun StreamingAssistantBlock(
    streamingText: String,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 10.dp)
            .testTag("streaming_assistant_block")
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(bottom = 8.dp)
        ) {
            GlowingOrangeNovaOrb(size = 24.dp, isPulsing = true)
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = stringResource(R.string.app_name),
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                color = NovaTextPrimaryDark
            )
        }

        val display = if (streamingText.isBlank()) "Thinking…" else "$streamingText ▍"
        Text(
            text = display,
            style = MaterialTheme.typography.bodyLarge,
            color = if (streamingText.isBlank()) NovaTextSecondaryDark else NovaTextPrimaryDark
        )
    }
}

private fun copyToClipboard(context: Context, text: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
    clipboard?.setPrimaryClip(ClipData.newPlainText("Nova AI Response", text))
}
