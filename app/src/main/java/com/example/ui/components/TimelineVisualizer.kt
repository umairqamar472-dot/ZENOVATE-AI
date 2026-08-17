package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CallSplit
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Redo
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Subtitles
import androidx.compose.material.icons.filled.Undo
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.ZoomIn
import androidx.compose.material.icons.filled.ZoomOut
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.engine.NlveCommandParser
import com.example.model.CaptionSegment
import com.example.model.EffectType
import com.example.model.OverlayAsset
import com.example.model.SampleVideoClip
import com.example.model.VideoEffect
import com.example.model.VideoSegment
import com.example.ui.theme.AudioTrackColor
import com.example.ui.theme.CaptionTrackColor
import com.example.ui.theme.CyanNeon
import com.example.ui.theme.EffectTrackColor
import com.example.ui.theme.EmeraldKeep
import com.example.ui.theme.RubyCut
import com.example.ui.theme.SilenceCutColor
import com.example.ui.theme.SurfaceDark
import com.example.ui.theme.SurfaceElevatedDark
import com.example.ui.theme.VideoTrackColor
import com.example.ui.theme.VioletNeon

@Composable
fun TimelineVisualizer(
    totalDurationMs: Long,
    playheadMs: Long,
    segments: List<VideoSegment>,
    effects: List<VideoEffect>,
    captions: List<CaptionSegment>,
    assets: List<OverlayAsset>,
    clip: SampleVideoClip,
    canUndo: Boolean,
    canRedo: Boolean,
    onSeekTo: (Long) -> Unit,
    onSplitAtPlayhead: () -> Unit,
    onUndo: () -> Unit,
    onRedo: () -> Unit,
    onResetAll: () -> Unit,
    onRemoveEffect: (String) -> Unit,
    onRemoveAsset: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var zoomFactor by remember { mutableFloatStateOf(1.0f) }
    val scrollState = rememberScrollState()

    val totalSeconds = (totalDurationMs / 1000f).coerceAtLeast(10f)
    val basePixelsPerSecond = 24.dp * zoomFactor
    val timelineWidthDp = (totalSeconds * basePixelsPerSecond.value).dp.coerceAtLeast(320.dp)

    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("zenovate_timeline_visualizer"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = SurfaceDark),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF1E293B))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp)
        ) {
            // Timeline Header Toolbar
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = "MULTI-LAYER TIMELINE",
                        color = Color.White,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 1.sp
                    )

                    Surface(
                        color = SurfaceElevatedDark,
                        shape = RoundedCornerShape(4.dp)
                    ) {
                        Text(
                            text = "${segments.size} clips • ${effects.size} FX • ${assets.size} assets",
                            color = CyanNeon,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                        )
                    }
                }

                // Action Buttons
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    IconButton(
                        onClick = onSplitAtPlayhead,
                        modifier = Modifier.size(30.dp).testTag("split_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.CallSplit,
                            contentDescription = "Split Clip at Playhead",
                            tint = CyanNeon,
                            modifier = Modifier.size(16.dp)
                        )
                    }

                    IconButton(
                        onClick = onUndo,
                        enabled = canUndo,
                        modifier = Modifier.size(30.dp).testTag("undo_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Undo,
                            contentDescription = "Undo",
                            tint = if (canUndo) Color.White else Color.DarkGray,
                            modifier = Modifier.size(16.dp)
                        )
                    }

                    IconButton(
                        onClick = onRedo,
                        enabled = canRedo,
                        modifier = Modifier.size(30.dp).testTag("redo_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Redo,
                            contentDescription = "Redo",
                            tint = if (canRedo) Color.White else Color.DarkGray,
                            modifier = Modifier.size(16.dp)
                        )
                    }

                    IconButton(
                        onClick = onResetAll,
                        modifier = Modifier.size(30.dp).testTag("reset_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.RestartAlt,
                            contentDescription = "Reset Timeline",
                            tint = RubyCut,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Multi-Layer Scrollable Tracks Viewport
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(230.dp)
                    .background(Color(0xFF070A12), RoundedCornerShape(10.dp))
                    .border(1.dp, Color(0xFF1E293B), RoundedCornerShape(10.dp))
                    .horizontalScroll(scrollState)
            ) {
                // Interactive Pointer Surface
                Box(
                    modifier = Modifier
                        .width(timelineWidthDp)
                        .fillMaxHeight()
                        .pointerInput(totalDurationMs, timelineWidthDp) {
                            detectTapGestures { offset ->
                                val fraction = (offset.x / size.width).coerceIn(0f, 1f)
                                onSeekTo((fraction * totalDurationMs).toLong())
                            }
                        }
                        .pointerInput(totalDurationMs, timelineWidthDp) {
                            detectDragGestures { change, _ ->
                                change.consume()
                                val fraction = (change.position.x / size.width).coerceIn(0f, 1f)
                                onSeekTo((fraction * totalDurationMs).toLong())
                            }
                        }
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .fillMaxHeight()
                            .padding(vertical = 4.dp),
                        verticalArrangement = Arrangement.spacedBy(3.dp)
                    ) {
                        // 1. Time Ruler / Markers
                        TimeRuler(
                            totalDurationMs = totalDurationMs,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(16.dp)
                        )

                        // 2. Video Cuts & Jump Cuts Track
                        TrackRow(
                            label = "VIDEO",
                            icon = Icons.Default.Videocam,
                            trackColor = VideoTrackColor,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(40.dp)
                        ) {
                            segments.forEach { segment ->
                                val startFraction = (segment.startTimeMs.toFloat() / totalDurationMs).coerceIn(0f, 1f)
                                val endFraction = (segment.endTimeMs.toFloat() / totalDurationMs).coerceIn(0f, 1f)
                                val segmentWidth = ((endFraction - startFraction) * timelineWidthDp.value).dp

                                Box(
                                    modifier = Modifier
                                        .offset(x = (startFraction * timelineWidthDp.value).dp)
                                        .width(segmentWidth)
                                        .height(36.dp)
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(
                                            if (segment.isCutOut) SilenceCutColor.copy(alpha = 0.85f)
                                            else VideoTrackColor.copy(alpha = 0.9f)
                                        )
                                        .border(
                                            1.dp,
                                            if (segment.isCutOut) RubyCut else CyanNeon.copy(alpha = 0.6f),
                                            RoundedCornerShape(4.dp)
                                        )
                                        .padding(horizontal = 4.dp, vertical = 2.dp),
                                    contentAlignment = Alignment.CenterStart
                                ) {
                                    Text(
                                        text = if (segment.isCutOut) "✂️ DEAD AIR CUT (${segment.durationMs/1000f}s)" else segment.label.ifBlank { "Video Clip" },
                                        color = if (segment.isCutOut) Color(0xFFFCA5A5) else Color.White,
                                        fontSize = 8.sp,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1
                                    )
                                }
                            }
                        }

                        // 3. Audio Waveform Track
                        TrackRow(
                            label = "AUDIO",
                            icon = Icons.Default.GraphicEq,
                            trackColor = AudioTrackColor,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(36.dp)
                        ) {
                            AudioWaveformVisualizer(
                                waveform = clip.baseWaveform,
                                silences = clip.defaultSilences,
                                totalDurationMs = totalDurationMs,
                                modifier = Modifier.fillMaxSize()
                            )
                        }

                        // 4. Scraped Graphics / Media Assets Track
                        if (assets.isNotEmpty()) {
                            TrackRow(
                                label = "ASSETS",
                                icon = Icons.Default.Image,
                                trackColor = EmeraldKeep,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(34.dp)
                            ) {
                                assets.forEach { asset ->
                                    val startFraction = (asset.startTimeMs.toFloat() / totalDurationMs).coerceIn(0f, 1f)
                                    val endFraction = (asset.endTimeMs.toFloat() / totalDurationMs).coerceIn(0f, 1f)
                                    val assetWidth = ((endFraction - startFraction) * timelineWidthDp.value).dp.coerceAtLeast(30.dp)

                                    Row(
                                        modifier = Modifier
                                            .offset(x = (startFraction * timelineWidthDp.value).dp)
                                            .width(assetWidth)
                                            .height(28.dp)
                                            .clip(RoundedCornerShape(4.dp))
                                            .background(EmeraldKeep.copy(alpha = 0.9f))
                                            .border(1.dp, Color.White, RoundedCornerShape(4.dp))
                                            .padding(horizontal = 4.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(
                                            text = "${asset.emojiOrIcon} ${asset.title}",
                                            color = Color.Black,
                                            fontSize = 8.sp,
                                            fontWeight = FontWeight.ExtraBold,
                                            maxLines = 1,
                                            modifier = Modifier.weight(1f, fill = false)
                                        )
                                        Icon(
                                            imageVector = Icons.Default.Close,
                                            contentDescription = "Remove asset",
                                            tint = Color.Black,
                                            modifier = Modifier
                                                .size(12.dp)
                                                .clickable { onRemoveAsset(asset.id) }
                                        )
                                    }
                                }
                            }
                        }

                        // 5. Effects & Transforms Track (Zooms, Filters, Rotations)
                        TrackRow(
                            label = "FX",
                            icon = Icons.Default.ZoomIn,
                            trackColor = EffectTrackColor,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(32.dp)
                        ) {
                            effects.forEach { effect ->
                                val startFraction = (effect.startTimeMs.toFloat() / totalDurationMs).coerceIn(0f, 1f)
                                val endFraction = (effect.endTimeMs.toFloat() / totalDurationMs).coerceIn(0f, 1f)
                                val effectWidth = ((endFraction - startFraction) * timelineWidthDp.value).dp.coerceAtLeast(24.dp)

                                val effectBg = when (effect.type) {
                                    EffectType.ZOOM -> CyanNeon.copy(alpha = 0.85f)
                                    EffectType.FILTER -> VioletNeon.copy(alpha = 0.85f)
                                    EffectType.TEXT_OVERLAY -> Color(0xFFF59E0B)
                                    EffectType.SPEED_RAMP -> EmeraldKeep
                                    EffectType.AUDIO_MUTE -> RubyCut
                                    EffectType.TRANSITION_FADE -> Color(0xFFEA580C)
                                    EffectType.CANVAS_TRANSFORM -> VioletNeon
                                    EffectType.STICKER_ASSET -> EmeraldKeep
                                }

                                Row(
                                    modifier = Modifier
                                        .offset(x = (startFraction * timelineWidthDp.value).dp)
                                        .width(effectWidth)
                                        .height(26.dp)
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(effectBg)
                                        .border(1.dp, Color.White.copy(alpha = 0.6f), RoundedCornerShape(4.dp))
                                        .padding(horizontal = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = effect.title.ifBlank { effect.type.name },
                                        color = Color.Black,
                                        fontSize = 8.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        maxLines = 1,
                                        modifier = Modifier.weight(1f, fill = false)
                                    )
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = "Remove effect",
                                        tint = Color.Black,
                                        modifier = Modifier
                                            .size(12.dp)
                                            .clickable { onRemoveEffect(effect.id) }
                                    )
                                }
                            }
                        }

                        // 6. Subtitles & Social Captions Track
                        if (captions.isNotEmpty()) {
                            TrackRow(
                                label = "CC",
                                icon = Icons.Default.Subtitles,
                                trackColor = CaptionTrackColor,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(30.dp)
                            ) {
                                captions.forEach { caption ->
                                    val startFraction = (caption.startTimeMs.toFloat() / totalDurationMs).coerceIn(0f, 1f)
                                    val endFraction = (caption.endTimeMs.toFloat() / totalDurationMs).coerceIn(0f, 1f)
                                    val capWidth = ((endFraction - startFraction) * timelineWidthDp.value).dp.coerceAtLeast(22.dp)

                                    Box(
                                        modifier = Modifier
                                            .offset(x = (startFraction * timelineWidthDp.value).dp)
                                            .width(capWidth)
                                            .height(24.dp)
                                            .clip(RoundedCornerShape(4.dp))
                                            .background(Color(0xFFB45309).copy(alpha = 0.9f))
                                            .border(1.dp, Color(0xFFFDE047), RoundedCornerShape(4.dp))
                                            .padding(horizontal = 4.dp),
                                        contentAlignment = Alignment.CenterStart
                                    ) {
                                        Text(
                                            text = "💬 ${caption.text}",
                                            color = Color.White,
                                            fontSize = 7.5.sp,
                                            fontWeight = FontWeight.Medium,
                                            maxLines = 1
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // Playhead Needle Indicator
                    val playheadFraction = (playheadMs.toFloat() / totalDurationMs).coerceIn(0f, 1f)
                    val playheadOffsetDp = (playheadFraction * timelineWidthDp.value).dp

                    Box(
                        modifier = Modifier
                            .offset(x = playheadOffsetDp - 6.dp)
                            .width(12.dp)
                            .fillMaxHeight(),
                        contentAlignment = Alignment.TopCenter
                    ) {
                        Box(
                            modifier = Modifier
                                .size(12.dp)
                                .background(RubyCut, CircleShape)
                                .border(1.5.dp, Color.White, CircleShape)
                        )
                        Box(
                            modifier = Modifier
                                .width(2.dp)
                                .fillMaxHeight()
                                .background(RubyCut)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            // Timeline Scale Slider
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.ZoomOut,
                    contentDescription = "Zoom Out Timeline",
                    tint = Color.Gray,
                    modifier = Modifier.size(14.dp)
                )

                Slider(
                    value = zoomFactor,
                    onValueChange = { zoomFactor = it },
                    valueRange = 0.5f..2.5f,
                    colors = SliderDefaults.colors(
                        thumbColor = CyanNeon,
                        activeTrackColor = CyanNeon,
                        inactiveTrackColor = SurfaceElevatedDark
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .height(18.dp)
                )

                Icon(
                    imageVector = Icons.Default.ZoomIn,
                    contentDescription = "Zoom In Timeline",
                    tint = Color.Gray,
                    modifier = Modifier.size(14.dp)
                )

                Text(
                    text = "${(zoomFactor * 100).toInt()}%",
                    color = Color.LightGray,
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace
                )
            }
        }
    }
}

@Composable
private fun TrackRow(
    label: String,
    icon: ImageVector,
    trackColor: Color,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    Box(
        modifier = modifier
            .background(Color(0xFF0F172A), RoundedCornerShape(4.dp))
            .border(0.5.dp, Color(0xFF1E293B), RoundedCornerShape(4.dp))
    ) {
        content()

        Surface(
            color = trackColor.copy(alpha = 0.85f),
            shape = RoundedCornerShape(topStart = 4.dp, bottomEnd = 4.dp),
            modifier = Modifier.align(Alignment.TopStart)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Icon(imageVector = icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(7.dp))
                Text(text = label, color = Color.White, fontSize = 6.5.sp, fontWeight = FontWeight.Black)
            }
        }
    }
}

@Composable
private fun TimeRuler(
    totalDurationMs: Long,
    modifier: Modifier = Modifier
) {
    val seconds = (totalDurationMs / 1000).toInt()

    Row(
        modifier = modifier.background(Color(0xFF0B1120)),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        for (sec in 0..seconds step 5) {
            Text(
                text = "${sec}s",
                color = Color(0xFF64748B),
                fontSize = 8.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun AudioWaveformVisualizer(
    waveform: List<Float>,
    silences: List<com.example.model.SilenceRegion>,
    totalDurationMs: Long,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.padding(horizontal = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        waveform.forEachIndexed { index, amplitude ->
            val fraction = index.toFloat() / waveform.size
            val timeMs = (fraction * totalDurationMs).toLong()
            val isSilence = silences.any { timeMs in it.startTimeMs..it.endTimeMs }

            Box(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 0.5.dp)
                    .height((26.dp * amplitude).coerceIn(3.dp, 26.dp))
                    .clip(RoundedCornerShape(1.dp))
                    .background(
                        if (isSilence) RubyCut.copy(alpha = 0.7f)
                        else AudioTrackColor.copy(alpha = 0.8f)
                    )
            )
        }
    }
}
