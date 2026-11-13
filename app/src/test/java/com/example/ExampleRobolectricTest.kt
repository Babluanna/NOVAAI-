package com.example

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import com.example.data.model.NovaVoiceLanguage
import com.example.data.model.NovaVoicePersona
import com.example.ui.screens.VoiceModeScreen
import com.example.ui.theme.NovaTheme
import com.example.voice.NovaAudioRecorder
import com.example.voice.VoiceSessionStatus
import com.example.voice.VoiceUiState
import java.nio.ByteBuffer
import java.nio.ByteOrder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun `read string from context`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("Nova AI", appName)
    }

    @Test
    fun `wav encoder generates valid 16khz mono riff header`() {
        val pcmPayload = ByteArray(3200) { idx -> (idx % 64).toByte() }
        val wav = NovaAudioRecorder.encodePcm16MonoToWav(pcmPayload, sampleRate = 16_000)

        assertEquals(44 + pcmPayload.size, wav.size)
        assertEquals("RIFF", String(wav, 0, 4))
        assertEquals("WAVE", String(wav, 8, 4))

        val header = ByteBuffer.wrap(wav, 0, 44).order(ByteOrder.LITTLE_ENDIAN)
        assertEquals(16_000, header.getInt(24)) // Sample rate
        assertEquals(1.toShort(), header.getShort(22)) // Mono channel
        assertEquals(16.toShort(), header.getShort(34)) // 16-bit PCM
        assertEquals(pcmPayload.size, header.getInt(40)) // Data chunk size
    }

    @Test
    fun `voice mode renders sky orb and reference bottom controls`() {
        composeTestRule.mainClock.autoAdvance = false

        var muteToggled = false
        var closeClicked = false

        composeTestRule.setContent {
            NovaTheme(isVoiceLightMode = true) {
                VoiceModeScreen(
                    voiceState = VoiceUiState(
                        status = VoiceSessionStatus.LISTENING,
                        selectedPersona = NovaVoicePersona.NOVA_WARM,
                        selectedLanguage = NovaVoiceLanguage.AUTO,
                        waveformEnergy = 0.55f
                    ),
                    voiceRecordings = emptyList(),
                    onBackToChat = { closeClicked = true },
                    onCentralMicClick = {},
                    onToggleMute = { muteToggled = true },
                    onSendVoiceTextPrompt = {},
                    onAttachVoicePhoto = {},
                    onClearVoicePhoto = {},
                    onSelectPersona = {},
                    onPreviewPersona = {},
                    onSelectLanguage = {},
                    onToggleCaptions = {},
                    onInterruptAiSpeech = {},
                    onPlayRecording = {},
                    onDeleteRecording = {}
                )
            }
        }

        composeTestRule.mainClock.advanceTimeBy(400L)

        composeTestRule.onNodeWithTag("voice_mode_screen").assertIsDisplayed()
        composeTestRule.onNodeWithTag("voice_mic_button").assertIsDisplayed()
        composeTestRule.onNodeWithTag("voice_ask_pill_button").assertIsDisplayed()
        composeTestRule.onNodeWithTag("voice_mute_button").assertIsDisplayed()
        composeTestRule.onNodeWithTag("voice_close_button").assertIsDisplayed()
        composeTestRule.onNodeWithTag("voice_settings_button").assertIsDisplayed()

        composeTestRule.onNodeWithTag("voice_close_button").performClick()
        composeTestRule.mainClock.advanceTimeBy(100L)
        assertTrue(closeClicked)
        // Ensure muteToggled stays untouched unless mute is tapped with permission
        assertEquals(false, muteToggled)
    }
}
