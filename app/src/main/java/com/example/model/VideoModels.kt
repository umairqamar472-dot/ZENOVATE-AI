package com.example.model

enum class VideoFilterType(val displayName: String) {
    NONE("Original"),
    VINTAGE("Vintage Film"),
    CYBERPUNK("Cyberpunk Neon"),
    BLACK_AND_WHITE("Monochrome B&W"),
    SUNSET("Warm Sunset"),
    VIGNETTE("Dramatic Vignette"),
    HIGH_CONTRAST("High Dynamic"),
    ANIME_POP("Anime Pop Vibrant")
}

enum class EffectType {
    ZOOM,
    FILTER,
    TEXT_OVERLAY,
    AUDIO_MUTE,
    SPEED_RAMP,
    TRANSITION_FADE,
    STICKER_ASSET,
    CANVAS_TRANSFORM
}

enum class SubtitleStyle(val displayName: String, val badge: String) {
    MR_BEAST_YELLOW("MrBeast Bold", "⚡ BEAST"),
    TIKTOK_POP("TikTok Pop", "📱 TIKTOK"),
    NEON_CYBERPUNK("Neon Cyberpunk", "🔮 NEON"),
    MINIMAL_CLEAN("Minimal Sans", "✨ CLEAN"),
    GLITCH_RETRO("Glitch Retro", "👾 GLITCH")
}

data class OverlayAsset(
    val id: String,
    val query: String,
    val title: String,
    val emojiOrIcon: String,
    val sourceUrlOrType: String,
    val posXFraction: Float = 0.5f, // 0.0 to 1.0 (center = 0.5)
    val posYFraction: Float = 0.4f, // 0.0 to 1.0
    val scale: Float = 1.0f,
    val startTimeMs: Long,
    val endTimeMs: Long,
    val isAnimated: Boolean = true
)

data class CanvasTransform(
    val rotationDegrees: Float = 0f,
    val isFlippedHorizontal: Boolean = false,
    val isFlippedVertical: Boolean = false,
    val zoomScale: Float = 1.0f,
    val panX: Float = 0f,
    val panY: Float = 0f,
    val isShakeEnabled: Boolean = false
)

data class VideoSegment(
    val id: String,
    val startTimeMs: Long,
    val endTimeMs: Long,
    val isCutOut: Boolean = false,
    val isSilence: Boolean = false,
    val speedMultiplier: Float = 1.0f,
    val label: String = ""
) {
    val durationMs: Long get() = (endTimeMs - startTimeMs).coerceAtLeast(0)
}

data class VideoEffect(
    val id: String,
    val type: EffectType,
    val startTimeMs: Long,
    val endTimeMs: Long,
    val zoomScale: Float = 1.0f,
    val filterType: VideoFilterType = VideoFilterType.NONE,
    val overlayText: String = "",
    val textPosition: String = "center", // "top", "center", "bottom"
    val colorHex: String = "#00E5FF",
    val title: String = "",
    val assetData: OverlayAsset? = null,
    val transformData: CanvasTransform? = null
)

data class CaptionSegment(
    val id: String,
    val startTimeMs: Long,
    val endTimeMs: Long,
    val text: String,
    val style: SubtitleStyle = SubtitleStyle.MR_BEAST_YELLOW
)

data class SilenceRegion(
    val startTimeMs: Long,
    val endTimeMs: Long,
    val avgDb: Float = -45f
)

data class VideoProject(
    val id: String,
    val title: String,
    val videoSource: String, // preset ID or local Uri string
    val totalDurationMs: Long,
    val resolution: String = "1080x1920",
    val fps: Int = 30,
    val isCustomUserVideo: Boolean = false,
    val createdAt: Long = System.currentTimeMillis()
)

data class SampleVideoClip(
    val id: String,
    val title: String,
    val category: String,
    val durationMs: Long,
    val description: String,
    val defaultSilences: List<SilenceRegion>,
    val defaultCaptions: List<CaptionSegment>,
    val baseWaveform: List<Float>
)

data class ParsedEditCommand(
    val rawPrompt: String,
    val actionType: String,
    val description: String,
    val cutsAdded: List<VideoSegment> = emptyList(),
    val effectsAdded: List<VideoEffect> = emptyList(),
    val captionsAdded: List<CaptionSegment> = emptyList(),
    val assetsAdded: List<OverlayAsset> = emptyList(),
    val transformUpdate: CanvasTransform? = null,
    val speedChangeMultiplier: Float? = null,
    val generatedFfmpegCommand: String = ""
)

data class ProcessingStage(
    val name: String,
    val detail: String,
    val progress: Float,
    val isDone: Boolean
)
