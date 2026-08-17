package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.ProcessingStage
import com.example.ui.theme.CyanNeon
import com.example.ui.theme.RubyCut
import com.example.ui.theme.SurfaceDark
import com.example.ui.theme.SurfaceElevatedDark
import com.example.ui.theme.VioletNeon

@Composable
fun CommandPromptBar(
    isProcessing: Boolean,
    processingStage: ProcessingStage?,
    isRecordingMic: Boolean,
    isHoldToSpeakActive: Boolean,
    liveRecordedText: String,
    micLevels: List<Float>,
    onSubmitCommand: (String) -> Unit,
    onHoldToSpeakStart: () -> Unit,
    onHoldToSpeakRelease: () -> Unit,
    onCancelRecording: () -> Unit,
    modifier: Modifier = Modifier
) {
    var textInput by remember { mutableStateOf("") }

    val presetChips = listOf(
        "✂️ Cut out silences",
        "💻 Add image of laptop",
        "₿ Insert Bitcoin graphic",
        "🔥 MrBeast yellow captions",
        "🔍 Zoom punch 1.8x at 5s",
        "🔄 Rotate clip 90°",
        "🪞 Flip horizontal",
        "🎨 Cyberpunk neon look",
        "⚡ Speed 2x from 10s to 20s",
        "📱 TikTok pop subtitles",
        "✨ Camera shake effect",
        "🌊 Fade out at end"
    )

    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("command_prompt_bar_card"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = SurfaceDark),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF1E293B))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp)
        ) {
            // Live AI Processing Progress View
            AnimatedVisibility(
                visible = isProcessing && processingStage != null,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                if (processingStage != null) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 10.dp)
                            .background(Color(0xFF0C1322), RoundedCornerShape(8.dp))
                            .border(1.dp, CyanNeon.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                            .padding(10.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp,
                                color = CyanNeon
                            )
                            Text(
                                text = processingStage.name,
                                color = CyanNeon,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = processingStage.detail,
                            color = Color.LightGray,
                            fontSize = 11.sp
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        LinearProgressIndicator(
                            progress = { processingStage.progress },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(4.dp)
                                .clip(RoundedCornerShape(2.dp)),
                            color = CyanNeon,
                            trackColor = Color(0xFF1E293B)
                        )
                    }
                }
            }

            // Live Audio Recording Modal Bar
            AnimatedVisibility(
                visible = isRecordingMic,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp)
                        .background(
                            Brush.horizontalGradient(
                                listOf(Color(0xFF3B0764), Color(0xFF1E1B4B))
                            ),
                            RoundedCornerShape(10.dp)
                        )
                        .border(1.dp, VioletNeon.copy(alpha = 0.8f), RoundedCornerShape(10.dp))
                        .padding(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            PulsingMicIcon()
                            Text(
                                text = "HOLD TO SPEAK ACTIVE (RELEASE TO COMMIT)",
                                color = VioletNeon,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Black,
                                letterSpacing = 1.sp
                            )
                        }

                        IconButton(
                            onClick = onCancelRecording,
                            modifier = Modifier.size(28.dp).testTag("cancel_recording_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Cancel recording",
                                tint = Color.LightGray,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    // Audio level waveform pulse
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(20.dp)
                            .padding(horizontal = 4.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val displayLevels = if (micLevels.isNotEmpty()) micLevels else List(16) { 0.3f }
                        displayLevels.forEach { level ->
                            Box(
                                modifier = Modifier
                                    .width(4.dp)
                                    .padding(horizontal = 1.dp)
                                    .height((18.dp * level).coerceIn(4.dp, 18.dp))
                                    .background(CyanNeon, RoundedCornerShape(2.dp))
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    Text(
                        text = if (liveRecordedText.isNotBlank()) "\"$liveRecordedText\"" else "Listening to your natural speech...",
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            // Quick Prompt Chips
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                presetChips.forEach { chipText ->
                    Surface(
                        color = SurfaceElevatedDark,
                        shape = RoundedCornerShape(20.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF334155)),
                        modifier = Modifier
                            .clip(RoundedCornerShape(20.dp))
                            .testTag("prompt_chip_${chipText.take(6).filter { it.isLetterOrDigit() }}")
                    ) {
                        Text(
                            text = chipText,
                            color = Color(0xFFE2E8F0),
                            fontSize = 10.5.sp,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier
                                .clickable {
                                    val commandWithoutEmoji = chipText.substringAfter(" ").trim()
                                    onSubmitCommand(commandWithoutEmoji)
                                }
                                .padding(horizontal = 9.dp, vertical = 5.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Main Text Input & Microphone Launcher
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                OutlinedTextField(
                    value = textInput,
                    onValueChange = { textInput = it },
                    placeholder = {
                        Text(
                            text = "Command AI: 'Cut silences', 'Add laptop image'...",
                            color = Color(0xFF64748B),
                            fontSize = 11.5.sp
                        )
                    },
                    modifier = Modifier
                        .weight(1f)
                        .testTag("command_text_input"),
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = CyanNeon,
                        unfocusedBorderColor = Color(0xFF334155),
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedContainerColor = SurfaceElevatedDark,
                        unfocusedContainerColor = SurfaceElevatedDark
                    ),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(onSend = {
                        if (textInput.isNotBlank()) {
                            onSubmitCommand(textInput)
                            textInput = ""
                        }
                    }),
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.AutoAwesome,
                            contentDescription = null,
                            tint = CyanNeon,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                )

                // Giant Hold-to-Speak Microphone Pill
                Box(
                    modifier = Modifier
                        .size(46.dp)
                        .clip(CircleShape)
                        .background(
                            if (isRecordingMic) Brush.linearGradient(listOf(RubyCut, VioletNeon))
                            else Brush.linearGradient(listOf(Color(0xFF4338CA), Color(0xFF6D28D9)))
                        )
                        .border(1.5.dp, if (isRecordingMic) RubyCut else VioletNeon, CircleShape)
                        .pointerInput(Unit) {
                            detectTapGestures(
                                onPress = {
                                    onHoldToSpeakStart()
                                    tryAwaitRelease()
                                    onHoldToSpeakRelease()
                                }
                            )
                        }
                        .testTag("mic_record_button"),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Mic,
                        contentDescription = "Hold to Speak",
                        tint = Color.White,
                        modifier = Modifier.size(22.dp)
                    )
                }

                // Send Button
                IconButton(
                    onClick = {
                        if (textInput.isNotBlank()) {
                            onSubmitCommand(textInput)
                            textInput = ""
                        }
                    },
                    enabled = textInput.isNotBlank() && !isProcessing,
                    modifier = Modifier
                        .size(46.dp)
                        .background(
                            if (textInput.isNotBlank()) CyanNeon else Color(0xFF1E293B),
                            CircleShape
                        )
                        .testTag("send_command_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Send,
                        contentDescription = "Execute Command",
                        tint = if (textInput.isNotBlank()) Color.Black else Color.DarkGray,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun PulsingMicIcon() {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val scale by infiniteTransition.animateFloat(
        initialValue = 0.9f,
        targetValue = 1.3f,
        animationSpec = infiniteRepeatable(
            animation = tween(600, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "scale"
    )

    Box(
        modifier = Modifier
            .size(18.dp)
            .scale(scale)
            .background(VioletNeon, CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = Icons.Default.Mic,
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier.size(11.dp)
        )
    }
}
