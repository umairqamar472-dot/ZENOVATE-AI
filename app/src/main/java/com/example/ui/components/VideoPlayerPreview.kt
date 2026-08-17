package com.example.ui.components

import android.net.Uri
import android.widget.VideoView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AspectRatio
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.FastRewind
import androidx.compose.material.icons.filled.Flip
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.RotateRight
import androidx.compose.material.icons.filled.VolumeMute
import androidx.compose.material.icons.filled.Vibration
import androidx.compose.material.icons.filled.ZoomIn
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.engine.NlveCommandParser
import com.example.model.CanvasTransform
import com.example.model.CaptionSegment
import com.example.model.EffectType
import com.example.model.OverlayAsset
import com.example.model.SampleVideoClip
import com.example.model.SubtitleStyle
import com.example.model.VideoEffect
import com.example.model.VideoFilterType
import com.example.model.VideoProject
import com.example.ui.theme.CyanNeon
import com.example.ui.theme.EmeraldKeep
import com.example.ui.theme.RubyCut
import com.example.ui.theme.VioletNeon
import kotlin.math.roundToInt

@Composable
fun VideoPlayerPreview(
    project: VideoProject,
    clip: SampleVideoClip,
    customVideoUri: Uri?,
    playheadMs: Long,
    isPlaying: Boolean,
    effects: List<VideoEffect>,
    captions: List<CaptionSegment>,
    assets: List<OverlayAsset>,
    canvasTransform: CanvasTransform,
    onTogglePlay: () -> Unit,
    onStepBack: () -> Unit,
    onStepForward: () -> Unit,
    onSeekTo: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    var isPortraitRatio by remember { mutableStateOf(true) } // Default 9:16 mobile TikTok/Reels first

    // Check active effects at playhead
    val activeZoomEffect = effects.find {
        it.type == EffectType.ZOOM && playheadMs in it.startTimeMs..it.endTimeMs
    }
    val activeFilterEffect = effects.find {
        it.type == EffectType.FILTER && playheadMs in it.startTimeMs..it.endTimeMs
    }
    val activeTextEffect = effects.find {
        it.type == EffectType.TEXT_OVERLAY && playheadMs in it.startTimeMs..it.endTimeMs
    }
    val activeMuteEffect = effects.find {
        it.type == EffectType.AUDIO_MUTE && playheadMs in it.startTimeMs..it.endTimeMs
    }
    val activeFadeEffect = effects.find {
        it.type == EffectType.TRANSITION_FADE && playheadMs in it.startTimeMs..it.endTimeMs
    }

    // Active graphic asset at playhead
    val activeAssets = assets.filter { playheadMs in it.startTimeMs..it.endTimeMs }

    // Active caption at playhead
    val activeCaption = captions.find { playheadMs in it.startTimeMs..it.endTimeMs }

    // Dynamic zoom calculations
    val targetZoom = (activeZoomEffect?.zoomScale ?: 1.0f) * canvasTransform.zoomScale
    val animatedZoom = remember { Animatable(1.0f) }

    LaunchedEffect(targetZoom) {
        animatedZoom.animateTo(
            targetValue = targetZoom,
            animationSpec = tween(durationMillis = 350, easing = FastOutSlowInEasing)
        )
    }

    // Shake animation if enabled
    val infiniteTransition = rememberInfiniteTransition(label = "shake")
    val shakeOffset by infiniteTransition.animateFloat(
        initialValue = -6f,
        targetValue = 6f,
        animationSpec = infiniteRepeatable(
            animation = tween(80, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "shakeOffset"
    )

    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("zenovate_video_player_preview")
            .shadow(20.dp, RoundedCornerShape(18.dp), spotColor = VioletNeon.copy(alpha = 0.35f)),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF070A12)),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF1E293B))
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // Video Viewport Canvas with real-time transformations
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(if (isPortraitRatio) 9f / 16f else 16f / 9f)
                    .clip(RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp))
                    .background(Color.Black)
                    .clickable { onTogglePlay() },
                contentAlignment = Alignment.Center
            ) {
                // Video Transform Container (Rotations, Horizontal Flip, Shake, Scale)
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            rotationZ = canvasTransform.rotationDegrees
                            scaleX = (if (canvasTransform.isFlippedHorizontal) -1f else 1f) * animatedZoom.value
                            scaleY = (if (canvasTransform.isFlippedVertical) -1f else 1f) * animatedZoom.value
                            if (canvasTransform.isShakeEnabled) {
                                translationX = shakeOffset
                                translationY = -shakeOffset * 0.5f
                            }
                        }
                ) {
                    if (customVideoUri != null) {
                        // Real Camera Roll Video Player View
                        NativeVideoPlayer(
                            uri = customVideoUri,
                            isPlaying = isPlaying,
                            playheadMs = playheadMs,
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        // Procedural Simulated Video Engine Canvas
                        VideoCanvasRenderer(
                            clip = clip,
                            playheadMs = playheadMs,
                            isPlaying = isPlaying,
                            filterType = activeFilterEffect?.filterType ?: VideoFilterType.NONE,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }

                // Render Active Floating Scraped Graphic Assets / Stickers
                activeAssets.forEach { asset ->
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp),
                        contentAlignment = when {
                            asset.posYFraction < 0.35f && asset.posXFraction < 0.35f -> Alignment.TopStart
                            asset.posYFraction < 0.35f && asset.posXFraction > 0.65f -> Alignment.TopEnd
                            asset.posYFraction < 0.35f -> Alignment.TopCenter
                            asset.posYFraction > 0.65f && asset.posXFraction < 0.35f -> Alignment.BottomStart
                            asset.posYFraction > 0.65f && asset.posXFraction > 0.65f -> Alignment.BottomEnd
                            asset.posYFraction > 0.65f -> Alignment.BottomCenter
                            asset.posXFraction < 0.35f -> Alignment.CenterStart
                            asset.posXFraction > 0.65f -> Alignment.CenterEnd
                            else -> Alignment.Center
                        }
                    ) {
                        Surface(
                            color = Color(0xFF0F172A).copy(alpha = 0.90f),
                            shape = RoundedCornerShape(12.dp),
                            border = androidx.compose.foundation.BorderStroke(1.5.dp, CyanNeon),
                            modifier = Modifier
                                .scale(asset.scale)
                                .shadow(12.dp, RoundedCornerShape(12.dp))
                        ) {
                            Column(
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = asset.emojiOrIcon,
                                    fontSize = 36.sp,
                                    textAlign = TextAlign.Center
                                )
                                Text(
                                    text = asset.title,
                                    color = Color.White,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "✨ Zenovate Media",
                                    color = CyanNeon,
                                    fontSize = 8.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }
                }

                // Active Text Typography Overlay
                if (activeTextEffect != null && activeTextEffect.overlayText.isNotBlank()) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(20.dp),
                        contentAlignment = when (activeTextEffect.textPosition) {
                            "top" -> Alignment.TopCenter
                            "bottom" -> Alignment.BottomCenter
                            else -> Alignment.Center
                        }
                    ) {
                        Surface(
                            color = Color.Black.copy(alpha = 0.85f),
                            shape = RoundedCornerShape(10.dp),
                            border = androidx.compose.foundation.BorderStroke(2.dp, VioletNeon),
                            modifier = Modifier.padding(8.dp)
                        ) {
                            Text(
                                text = activeTextEffect.overlayText,
                                color = Color.White,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Black,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                                textAlign = TextAlign.Center,
                                letterSpacing = 1.sp
                            )
                        }
                    }
                }

                // Stylized Social Media Captions (MrBeast / TikTok / Neon / Glitch)
                if (activeCaption != null) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(bottom = 24.dp, start = 16.dp, end = 16.dp),
                        contentAlignment = Alignment.BottomCenter
                    ) {
                        when (activeCaption.style) {
                            SubtitleStyle.MR_BEAST_YELLOW -> {
                                Surface(
                                    color = Color(0xFFFACC15),
                                    shape = RoundedCornerShape(8.dp),
                                    border = androidx.compose.foundation.BorderStroke(2.dp, Color.Black)
                                ) {
                                    Text(
                                        text = activeCaption.text.uppercase(),
                                        color = Color.Black,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Black,
                                        fontFamily = FontFamily.SansSerif,
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                        textAlign = TextAlign.Center
                                    )
                                }
                            }
                            SubtitleStyle.NEON_CYBERPUNK -> {
                                Surface(
                                    color = Color.Black.copy(alpha = 0.85f),
                                    shape = RoundedCornerShape(8.dp),
                                    border = androidx.compose.foundation.BorderStroke(1.5.dp, CyanNeon)
                                ) {
                                    Text(
                                        text = activeCaption.text,
                                        color = CyanNeon,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                        textAlign = TextAlign.Center
                                    )
                                }
                            }
                            SubtitleStyle.GLITCH_RETRO -> {
                                Surface(
                                    color = Color(0xFF0F172A),
                                    shape = RoundedCornerShape(4.dp),
                                    border = androidx.compose.foundation.BorderStroke(1.dp, EmeraldKeep)
                                ) {
                                    Text(
                                        text = "> ${activeCaption.text}",
                                        color = EmeraldKeep,
                                        fontSize = 12.sp,
                                        fontFamily = FontFamily.Monospace,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                                    )
                                }
                            }
                            else -> {
                                Surface(
                                    color = Color.Black.copy(alpha = 0.80f),
                                    shape = RoundedCornerShape(20.dp),
                                    border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.4f))
                                ) {
                                    Text(
                                        text = activeCaption.text,
                                        color = Color.White,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                                        textAlign = TextAlign.Center
                                    )
                                }
                            }
                        }
                    }
                }

                // Fade In / Out Blackout Overlay
                if (activeFadeEffect != null) {
                    val fadeAlpha = if (activeFadeEffect.title.contains("In")) {
                        val progress = (playheadMs - activeFadeEffect.startTimeMs).toFloat() / (activeFadeEffect.endTimeMs - activeFadeEffect.startTimeMs).coerceAtLeast(1)
                        (1.0f - progress).coerceIn(0f, 1f)
                    } else {
                        val progress = (playheadMs - activeFadeEffect.startTimeMs).toFloat() / (activeFadeEffect.endTimeMs - activeFadeEffect.startTimeMs).coerceAtLeast(1)
                        progress.coerceIn(0f, 1f)
                    }
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = fadeAlpha))
                    )
                }

                // Active Transform & Filter Badges (Top HUD)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.TopStart)
                        .padding(8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (canvasTransform.rotationDegrees != 0f) {
                            ActivePill(icon = Icons.Default.RotateRight, text = "${canvasTransform.rotationDegrees.toInt()}°", color = VioletNeon)
                        }
                        if (canvasTransform.isFlippedHorizontal) {
                            ActivePill(icon = Icons.Default.Flip, text = "Mirrored", color = CyanNeon)
                        }
                        if (canvasTransform.isShakeEnabled) {
                            ActivePill(icon = Icons.Default.Vibration, text = "Shake FX", color = RubyCut)
                        }
                        if (activeZoomEffect != null) {
                            ActivePill(icon = Icons.Default.ZoomIn, text = "${activeZoomEffect.zoomScale}x Zoom", color = CyanNeon)
                        }
                        if (activeFilterEffect != null && activeFilterEffect.filterType != VideoFilterType.NONE) {
                            ActivePill(icon = Icons.Default.GraphicEq, text = activeFilterEffect.filterType.displayName, color = VioletNeon)
                        }
                        if (activeMuteEffect != null) {
                            ActivePill(icon = Icons.Default.VolumeMute, text = "Muted", color = RubyCut)
                        }
                    }

                    // Resolution Tag
                    Surface(
                        color = Color.Black.copy(alpha = 0.7f),
                        shape = RoundedCornerShape(4.dp)
                    ) {
                        Text(
                            text = if (isPortraitRatio) "9:16 Reel" else "16:9 Video",
                            color = Color.LightGray,
                            fontSize = 10.sp,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }

                // Play / Pause central pulse indicator when paused
                if (!isPlaying) {
                    Box(
                        modifier = Modifier
                            .size(54.dp)
                            .background(Color.Black.copy(alpha = 0.65f), CircleShape)
                            .border(1.5.dp, CyanNeon.copy(alpha = 0.9f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.PlayArrow,
                            contentDescription = "Play",
                            tint = CyanNeon,
                            modifier = Modifier.size(32.dp)
                        )
                    }
                }
            }

            // Transport Control HUD Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF111827))
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Playback and Step Controls
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    IconButton(
                        onClick = onStepBack,
                        modifier = Modifier.size(34.dp).testTag("step_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.FastRewind,
                            contentDescription = "Step Back 2s",
                            tint = Color.LightGray,
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    IconButton(
                        onClick = onTogglePlay,
                        modifier = Modifier
                            .size(38.dp)
                            .background(Brush.linearGradient(listOf(CyanNeon, VioletNeon)), CircleShape)
                            .testTag("play_pause_button")
                    ) {
                        Icon(
                            imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = if (isPlaying) "Pause" else "Play",
                            tint = Color.Black,
                            modifier = Modifier.size(22.dp)
                        )
                    }

                    IconButton(
                        onClick = onStepForward,
                        modifier = Modifier.size(34.dp).testTag("step_forward_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.FastForward,
                            contentDescription = "Step Forward 2s",
                            tint = Color.LightGray,
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(6.dp))

                    Text(
                        text = "${NlveCommandParser.formatTime(playheadMs)} / ${NlveCommandParser.formatTime(project.totalDurationMs)}",
                        color = Color.White,
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                // Ratio Toggle
                IconButton(
                    onClick = { isPortraitRatio = !isPortraitRatio },
                    modifier = Modifier.size(34.dp).testTag("aspect_ratio_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.AspectRatio,
                        contentDescription = "Toggle 9:16 / 16:9",
                        tint = if (isPortraitRatio) CyanNeon else Color.LightGray,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun ActivePill(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    text: String,
    color: Color
) {
    Surface(
        color = Color.Black.copy(alpha = 0.75f),
        shape = RoundedCornerShape(10.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, color.copy(alpha = 0.8f))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            Icon(imageVector = icon, contentDescription = null, tint = color, modifier = Modifier.size(10.dp))
            Text(text = text, color = Color.White, fontSize = 9.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun NativeVideoPlayer(
    uri: Uri,
    isPlaying: Boolean,
    playheadMs: Long,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var videoViewInstance by remember { mutableStateOf<VideoView?>(null) }

    DisposableEffect(uri) {
        onDispose {
            videoViewInstance?.stopPlayback()
        }
    }

    LaunchedEffect(isPlaying) {
        videoViewInstance?.let { view ->
            if (isPlaying && !view.isPlaying) {
                view.start()
            } else if (!isPlaying && view.isPlaying) {
                view.pause()
            }
        }
    }

    AndroidView(
        factory = { ctx ->
            VideoView(ctx).apply {
                setVideoURI(uri)
                setOnPreparedListener { mp ->
                    mp.isLooping = true
                    seekTo(playheadMs.toInt())
                    if (isPlaying) start()
                }
                videoViewInstance = this
            }
        },
        update = { view ->
            if (Math.abs(view.currentPosition - playheadMs) > 1000) {
                view.seekTo(playheadMs.toInt())
            }
        },
        modifier = modifier
    )
}

@Composable
private fun VideoCanvasRenderer(
    clip: SampleVideoClip,
    playheadMs: Long,
    isPlaying: Boolean,
    filterType: VideoFilterType,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "videoMotion")
    val pulseAnim by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(4000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "rotation"
    )

    Canvas(modifier = modifier) {
        val width = size.width
        val height = size.height
        val progress = (playheadMs.toFloat() / clip.durationMs).coerceIn(0f, 1f)

        val baseGradient = when (clip.id) {
            "podcast_1" -> listOf(Color(0xFF1E1B4B), Color(0xFF0F172A), Color(0xFF1E293B))
            "travel_vlog" -> listOf(Color(0xFF831843), Color(0xFF312E81), Color(0xFF0284C7))
            "cooking_masterclass" -> listOf(Color(0xFF78350F), Color(0xFF451A03), Color(0xFF1C1917))
            else -> listOf(Color(0xFF111827), Color(0xFF1F2937), Color(0xFF0F172A))
        }

        drawRect(
            brush = Brush.radialGradient(
                colors = baseGradient,
                center = Offset(width * (0.4f + progress * 0.2f), height * 0.5f),
                radius = width * 0.9f
            )
        )

        // Draw animated stylized subject
        when (clip.id) {
            "podcast_1" -> {
                val micCenterX = width * 0.5f
                val micCenterY = height * 0.52f
                val ringRadius = 38.dp.toPx() + (Math.sin(playheadMs / 150.0).toFloat() * 10.dp.toPx())

                drawCircle(
                    color = CyanNeon.copy(alpha = 0.15f),
                    radius = ringRadius + 20.dp.toPx(),
                    center = Offset(micCenterX, micCenterY)
                )
                drawCircle(
                    color = CyanNeon.copy(alpha = 0.35f),
                    radius = ringRadius,
                    center = Offset(micCenterX, micCenterY)
                )
                drawRoundRect(
                    color = Color(0xFFE2E8F0),
                    topLeft = Offset(micCenterX - 14.dp.toPx(), micCenterY - 26.dp.toPx()),
                    size = androidx.compose.ui.geometry.Size(28.dp.toPx(), 52.dp.toPx()),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(14.dp.toPx())
                )
            }
            "travel_vlog" -> {
                val trailPath = Path().apply {
                    moveTo(0f, height * 0.7f)
                    cubicTo(
                        width * 0.3f, height * (0.4f + Math.sin((pulseAnim + 0) * 0.05).toFloat() * 0.1f),
                        width * 0.7f, height * (0.8f + Math.cos((pulseAnim + 45) * 0.05).toFloat() * 0.1f),
                        width, height * 0.5f
                    )
                }
                drawPath(trailPath, color = Color(0xFFF43F5E), style = Stroke(width = 5.dp.toPx()))
                drawPath(trailPath, color = CyanNeon, style = Stroke(width = 2.dp.toPx()))
            }
            "cooking_masterclass" -> {
                val panCenterX = width * 0.5f
                val panCenterY = height * 0.55f
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(Color(0xFFF59E0B), Color(0xFFEF4444), Color.Transparent),
                        center = Offset(panCenterX, panCenterY),
                        radius = 75.dp.toPx()
                    ),
                    radius = 80.dp.toPx(),
                    center = Offset(panCenterX, panCenterY)
                )
            }
        }

        // Color Grading Filters
        when (filterType) {
            VideoFilterType.VINTAGE -> drawRect(color = Color(0x33D97706), size = size)
            VideoFilterType.CYBERPUNK -> {
                drawRect(
                    brush = Brush.linearGradient(
                        colors = listOf(Color(0x33EC4899), Color(0x3306B6D4)),
                        start = Offset.Zero,
                        end = Offset(width, height)
                    ),
                    size = size
                )
            }
            VideoFilterType.BLACK_AND_WHITE -> drawRect(color = Color(0x55808080), size = size)
            VideoFilterType.SUNSET -> {
                drawRect(
                    brush = Brush.verticalGradient(
                        colors = listOf(Color(0x44F97316), Color(0x337C2D12))
                    ),
                    size = size
                )
            }
            VideoFilterType.ANIME_POP -> {
                drawRect(
                    brush = Brush.radialGradient(
                        colors = listOf(Color(0x22F43F5E), Color(0x338B5CF6)),
                        center = Offset(width * 0.5f, height * 0.5f),
                        radius = width * 0.8f
                    ),
                    size = size
                )
            }
            VideoFilterType.VIGNETTE -> {
                drawRect(
                    brush = Brush.radialGradient(
                        colors = listOf(Color.Transparent, Color(0xCC000000)),
                        center = Offset(width * 0.5f, height * 0.5f),
                        radius = width * 0.6f
                    ),
                    size = size
                )
            }
            VideoFilterType.HIGH_CONTRAST -> {
                drawRect(
                    brush = Brush.radialGradient(
                        colors = listOf(Color(0x22FFFFFF), Color(0x44000000)),
                        radius = width * 0.7f
                    ),
                    size = size
                )
            }
            VideoFilterType.NONE -> { /* Raw */ }
        }
    }
}
