package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.History
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.model.NovaVoiceLanguage
import com.example.data.model.NovaVoicePersona

private val VoiceAccentBlue = Color(0xFF1D63ED)
private val SheetCardBg = Color(0xFFF4F6FA)
private val SheetBorder = Color(0xFFE2E8F0)
private val SheetTitleInk = Color(0xFF0F172A)
private val SheetSubInk = Color(0xFF64748B)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun VoiceSettingsBottomSheetContent(
    selectedPersona: NovaVoicePersona,
    selectedLanguage: NovaVoiceLanguage,
    showCaptions: Boolean,
    savedRecordingsCount: Int,
    onSelectPersona: (NovaVoicePersona) -> Unit,
    onPreviewPersona: (NovaVoicePersona) -> Unit,
    onSelectLanguage: (NovaVoiceLanguage) -> Unit,
    onToggleCaptions: () -> Unit,
    onOpenSavedTranscripts: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 20.dp, vertical = 10.dp)
            .testTag("voice_settings_sheet")
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = stringResource(R.string.voice_settings_title),
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                color = SheetTitleInk
            )
            Text(
                text = stringResource(R.string.voice_settings_subtitle),
                style = MaterialTheme.typography.bodySmall,
                color = SheetSubInk
            )
        }

        // 1. Voice Persona Picker with Live Neural Voice Preview
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = stringResource(R.string.voice_persona_section),
                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                color = SheetTitleInk
            )

            LazyRow(
                contentPadding = PaddingValues(end = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                items(NovaVoicePersona.entries, key = { it.name }) { persona ->
                    val isSelected = persona == selectedPersona
                    Surface(
                        onClick = { onSelectPersona(persona) },
                        shape = RoundedCornerShape(18.dp),
                        color = if (isSelected) VoiceAccentBlue.copy(alpha = 0.09f) else SheetCardBg,
                        border = BorderStroke(
                            width = if (isSelected) 1.6.dp else 1.dp,
                            color = if (isSelected) VoiceAccentBlue else SheetBorder
                        ),
                        modifier = Modifier
                            .width(184.dp)
                            .testTag("voice_persona_${persona.name.lowercase()}")
                    ) {
                        Column(
                            verticalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.padding(14.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Rounded.GraphicEq,
                                        contentDescription = null,
                                        tint = if (isSelected) VoiceAccentBlue else SheetSubInk,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = persona.displayName,
                                        style = MaterialTheme.typography.titleSmall.copy(
                                            fontWeight = FontWeight.Bold
                                        ),
                                        color = SheetTitleInk
                                    )
                                }

                                IconButton(
                                    onClick = { onPreviewPersona(persona) },
                                    modifier = Modifier
                                        .size(36.dp)
                                        .testTag("preview_persona_${persona.name.lowercase()}")
                                ) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Rounded.VolumeUp,
                                        contentDescription = "Preview ${persona.displayName} voice",
                                        tint = VoiceAccentBlue,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(6.dp))

                            Text(
                                text = persona.description,
                                style = MaterialTheme.typography.labelSmall,
                                color = SheetSubInk,
                                minLines = 2
                            )
                        }
                    }
                }
            }
        }

        // 2. Multilingual Spoken Conversation Selector (Auto / English / Telugu / Hindi)
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = stringResource(R.string.voice_language_section),
                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                color = SheetTitleInk
            )

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                NovaVoiceLanguage.entries.forEach { lang ->
                    val selected = lang == selectedLanguage
                    FilterChip(
                        selected = selected,
                        onClick = { onSelectLanguage(lang) },
                        label = {
                            Text(
                                text = lang.displayName,
                                style = MaterialTheme.typography.labelMedium.copy(
                                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium
                                )
                            )
                        },
                        leadingIcon = if (selected) {
                            {
                                Icon(
                                    imageVector = Icons.Rounded.Check,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        } else {
                            null
                        },
                        shape = CircleShape,
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = VoiceAccentBlue,
                            selectedLabelColor = Color.White,
                            selectedLeadingIconColor = Color.White,
                            containerColor = SheetCardBg,
                            labelColor = SheetTitleInk
                        ),
                        border = FilterChipDefaults.filterChipBorder(
                            enabled = true,
                            selected = selected,
                            borderColor = SheetBorder,
                            selectedBorderColor = VoiceAccentBlue
                        ),
                        modifier = Modifier.testTag("voice_lang_${lang.name.lowercase()}")
                    )
                }
            }
        }

        // 3. Live Captions Switch
        Surface(
            onClick = onToggleCaptions,
            shape = RoundedCornerShape(16.dp),
            color = SheetCardBg,
            border = BorderStroke(1.dp, SheetBorder),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("voice_toggle_captions_row")
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.voice_captions_toggle),
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                        color = SheetTitleInk
                    )
                    Text(
                        text = stringResource(R.string.voice_captions_subtitle),
                        style = MaterialTheme.typography.labelSmall,
                        color = SheetSubInk
                    )
                }
                Switch(
                    checked = showCaptions,
                    onCheckedChange = { onToggleCaptions() },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.White,
                        checkedTrackColor = VoiceAccentBlue
                    )
                )
            }
        }

        // 4. Open Saved Voice Transcripts
        Surface(
            onClick = onOpenSavedTranscripts,
            shape = RoundedCornerShape(16.dp),
            color = SheetCardBg,
            border = BorderStroke(1.dp, SheetBorder),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("voice_open_saved_transcripts_row")
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.History,
                    contentDescription = null,
                    tint = VoiceAccentBlue,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = "${stringResource(R.string.voice_view_transcripts)} ($savedRecordingsCount)",
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                    color = SheetTitleInk
                )
            }
        }

        Spacer(modifier = Modifier.height(6.dp))
    }
}
