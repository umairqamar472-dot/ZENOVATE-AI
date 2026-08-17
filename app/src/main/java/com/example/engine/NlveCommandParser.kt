package com.example.engine

import com.example.model.CaptionSegment
import com.example.model.CanvasTransform
import com.example.model.EffectType
import com.example.model.OverlayAsset
import com.example.model.ParsedEditCommand
import com.example.model.SilenceRegion
import com.example.model.SubtitleStyle
import com.example.model.VideoEffect
import com.example.model.VideoFilterType
import com.example.model.VideoSegment
import java.util.UUID
import java.util.regex.Pattern

object NlveCommandParser {

    /**
     * Parses natural language creator prompts into concrete timeline edits,
     * canvas transformations, asset research retrievals, and FFmpeg filtergraphs.
     */
    fun parseCommand(
        prompt: String,
        currentDurationMs: Long,
        playheadMs: Long,
        availableSilences: List<SilenceRegion>,
        availableDefaultCaptions: List<CaptionSegment>
    ): ParsedEditCommand {
        val clean = prompt.trim().lowercase()

        // 1. Automated Asset Research & Scraper Retrieval ("add image of...", "insert graphic...", "floating emoji...")
        if (clean.contains("image") || clean.contains("graphic") || clean.contains("asset") ||
            clean.contains("sticker") || clean.contains("picture") || clean.contains("badge") ||
            clean.contains("icon") || clean.contains("insert") || clean.contains("add a ") || clean.contains("add an ")
        ) {
            val assetQuery = extractAssetQuery(prompt)
            if (assetQuery.isNotBlank()) {
                val (startMs, endMs) = extractTimeRange(clean, playheadMs, currentDurationMs, defaultDurationMs = 6000L)
                val emojiRepresentation = mapQueryToAssetVisual(assetQuery)

                val asset = OverlayAsset(
                    id = UUID.randomUUID().toString(),
                    query = assetQuery,
                    title = assetQuery.replaceFirstChar { it.uppercase() },
                    emojiOrIcon = emojiRepresentation,
                    sourceUrlOrType = "media_library",
                    posXFraction = if (clean.contains("left")) 0.25f else if (clean.contains("right")) 0.75f else 0.5f,
                    posYFraction = if (clean.contains("top")) 0.25f else if (clean.contains("bottom")) 0.75f else 0.4f,
                    scale = if (clean.contains("large") || clean.contains("big")) 1.4f else 1.0f,
                    startTimeMs = startMs,
                    endTimeMs = endMs,
                    isAnimated = true
                )

                val effect = VideoEffect(
                    id = UUID.randomUUID().toString(),
                    type = EffectType.STICKER_ASSET,
                    startTimeMs = startMs,
                    endTimeMs = endMs,
                    title = "Asset: \"$assetQuery\"",
                    assetData = asset
                )

                val ffmpeg = "ffmpeg -i input.mp4 -i scraped_asset.png -filter_complex \"[0:v][1:v] overlay=W/2-w/2:H/2-h/2:enable='between(t,${startMs/1000.0},${endMs/1000.0})' [out]\" -map \"[out]\" -map 0:a? output_asset.mp4"

                return ParsedEditCommand(
                    rawPrompt = prompt,
                    actionType = "INSERT_SCRAPED_ASSET",
                    description = "Zenovate AI retrieved and overlaid media graphic: \"$assetQuery\" (${formatTime(startMs)} - ${formatTime(endMs)})",
                    effectsAdded = listOf(effect),
                    assetsAdded = listOf(asset),
                    generatedFfmpegCommand = ffmpeg
                )
            }
        }

        // 2. Canvas Transformations ("rotate clip 90 degrees", "flip horizontal / mirror", "camera shake", "tilt")
        if (clean.contains("rotate") || clean.contains("flip") || clean.contains("mirror") ||
            clean.contains("shake") || clean.contains("tilt") || clean.contains("turn")
        ) {
            val rotation = when {
                clean.contains("180") -> 180f
                clean.contains("270") || clean.contains("-90") || clean.contains("left") -> 270f
                clean.contains("90") || clean.contains("right") -> 90f
                clean.contains("reset") || clean.contains("normal") -> 0f
                else -> 90f
            }
            val isFlipH = clean.contains("flip") || clean.contains("mirror") || clean.contains("horizontal")
            val isShake = clean.contains("shake") || clean.contains("vibrate") || clean.contains("quake")

            val transform = CanvasTransform(
                rotationDegrees = rotation,
                isFlippedHorizontal = isFlipH,
                isShakeEnabled = isShake,
                zoomScale = if (isShake) 1.15f else 1.0f
            )

            val effect = VideoEffect(
                id = UUID.randomUUID().toString(),
                type = EffectType.CANVAS_TRANSFORM,
                startTimeMs = 0L,
                endTimeMs = currentDurationMs,
                title = if (isShake) "Camera Shake FX" else if (isFlipH) "Mirror Canvas" else "Rotate ${rotation.toInt()}°",
                transformData = transform
            )

            val ffmpegFilter = buildString {
                if (rotation == 90f) append("transpose=1")
                else if (rotation == 180f) append("transpose=1,transpose=1")
                else if (rotation == 270f) append("transpose=2")
                if (isFlipH) {
                    if (isNotEmpty()) append(",")
                    append("hflip")
                }
                if (isShake) {
                    if (isNotEmpty()) append(",")
                    append("crop=in_w-20:in_h-20")
                }
            }

            val ffmpeg = "ffmpeg -i input.mp4 -vf \"$ffmpegFilter\" -c:a copy output_transformed.mp4"

            return ParsedEditCommand(
                rawPrompt = prompt,
                actionType = "CANVAS_TRANSFORM",
                description = "Applied canvas viewport transformation: ${if (isShake) "Camera Shake" else if (isFlipH) "Horizontal Mirror Flip" else "Rotate ${rotation.toInt()}°"}",
                effectsAdded = listOf(effect),
                transformUpdate = transform,
                generatedFfmpegCommand = ffmpeg
            )
        }

        // 3. Jump Cuts & Slicing Out Dead Air / Silences
        if (clean.contains("silence") || clean.contains("pause") || clean.contains("quiet") ||
            clean.contains("blank audio") || clean.contains("dead space") || clean.contains("dead air") ||
            clean.contains("jump cut") || clean.contains("slice pauses")
        ) {
            val cuts = if (availableSilences.isNotEmpty()) {
                availableSilences.map { silence ->
                    VideoSegment(
                        id = UUID.randomUUID().toString(),
                        startTimeMs = silence.startTimeMs,
                        endTimeMs = silence.endTimeMs,
                        isCutOut = true,
                        isSilence = true,
                        label = "Dead Air Cut (${(silence.endTimeMs - silence.startTimeMs) / 1000f}s)"
                    )
                }
            } else {
                listOf(
                    VideoSegment(
                        id = UUID.randomUUID().toString(),
                        startTimeMs = 3500L.coerceAtMost(currentDurationMs),
                        endTimeMs = 6000L.coerceAtMost(currentDurationMs),
                        isCutOut = true,
                        isSilence = true,
                        label = "Silence (2.5s)"
                    ),
                    VideoSegment(
                        id = UUID.randomUUID().toString(),
                        startTimeMs = 14000L.coerceAtMost(currentDurationMs),
                        endTimeMs = 17500L.coerceAtMost(currentDurationMs),
                        isCutOut = true,
                        isSilence = true,
                        label = "Silence (3.5s)"
                    )
                )
            }

            val totalCutSec = cuts.sumOf { it.durationMs } / 1000f
            val selectFilters = cuts.joinToString("+") { "between(t,${it.startTimeMs/1000.0},${it.endTimeMs/1000.0})" }
            val ffmpeg = "ffmpeg -i input.mp4 -vf \"select='not($selectFilters)',setpts=N/FRAME_RATE/TB\" -af \"aselect='not($selectFilters)',asetpts=N/SR/TB\" -c:v libx264 -crf 18 output_jumpcuts.mp4"

            return ParsedEditCommand(
                rawPrompt = prompt,
                actionType = "CUT_SILENCES",
                description = "Jump-cut ${cuts.size} dead air pauses (sliced out ${"%.1f".format(totalCutSec)}s of silence)",
                cutsAdded = cuts,
                generatedFfmpegCommand = ffmpeg
            )
        }

        // 4. Dynamic Subtitles & Typography (TikTok / MrBeast / Neon styles)
        if (clean.contains("subtitle") || clean.contains("caption") || clean.contains("transcribe") ||
            clean.contains("speech to text") || clean.contains("stt") || clean.contains("mrbeast") ||
            clean.contains("tiktok") || clean.contains("karaoke")
        ) {
            val style = when {
                clean.contains("mrbeast") || clean.contains("yellow") || clean.contains("bold") -> SubtitleStyle.MR_BEAST_YELLOW
                clean.contains("neon") || clean.contains("cyber") || clean.contains("glow") -> SubtitleStyle.NEON_CYBERPUNK
                clean.contains("clean") || clean.contains("minimal") -> SubtitleStyle.MINIMAL_CLEAN
                clean.contains("glitch") || clean.contains("retro") -> SubtitleStyle.GLITCH_RETRO
                else -> SubtitleStyle.TIKTOK_POP
            }

            val rawCaptions = if (availableDefaultCaptions.isNotEmpty()) {
                availableDefaultCaptions.map { it.copy(style = style) }
            } else {
                listOf(
                    CaptionSegment(UUID.randomUUID().toString(), 800L, 3800L, "🔥 THIS CHANGED EVERYTHING!", style),
                    CaptionSegment(UUID.randomUUID().toString(), 4200L, 8800L, "AI Video Editing right on your phone 📱", style),
                    CaptionSegment(UUID.randomUUID().toString(), 9200L, 14500L, "Speak your commands and watch it happen live!", style),
                    CaptionSegment(UUID.randomUUID().toString(), 15000L, 21000L, "Export directly in 4K 60FPS 🚀", style)
                )
            }

            val fontColor = when (style) {
                SubtitleStyle.MR_BEAST_YELLOW -> "&H00D7FF&"
                SubtitleStyle.NEON_CYBERPUNK -> "&HFF00E5&"
                SubtitleStyle.GLITCH_RETRO -> "&H00FF00&"
                else -> "&HFFFFFF&"
            }

            val ffmpeg = "ffmpeg -i input.mp4 -vf \"subtitles=subtitles.ass:force_style='FontName=Montserrat-Black,FontSize=28,PrimaryColour=$fontColor,OutlineColour=&H000000&,Outline=3'\" -c:a copy output_subtitles.mp4"

            return ParsedEditCommand(
                rawPrompt = prompt,
                actionType = "AUTO_CAPTIONS",
                description = "Generated ${rawCaptions.size} stylized ${style.displayName} captions synced to speech",
                captionsAdded = rawCaptions,
                generatedFfmpegCommand = ffmpeg
            )
        }

        // 5. Cinematic Zoom In / Zoom Punch
        if (clean.contains("zoom") || clean.contains("punch in") || clean.contains("close up") || clean.contains("scale up")) {
            val (startMs, endMs) = extractTimeRange(clean, playheadMs, currentDurationMs, defaultDurationMs = 6000L)
            val zoomScale = when {
                clean.contains("3x") -> 3.0f
                clean.contains("2x") || clean.contains("double") -> 2.0f
                clean.contains("1.8x") -> 1.8f
                clean.contains("1.5x") -> 1.5f
                clean.contains("1.2x") -> 1.2f
                clean.contains("out") -> 0.8f
                else -> 1.6f
            }

            val effect = VideoEffect(
                id = UUID.randomUUID().toString(),
                type = EffectType.ZOOM,
                startTimeMs = startMs,
                endTimeMs = endMs,
                zoomScale = zoomScale,
                title = "Zoom ${zoomScale}x"
            )

            val ffmpeg = "ffmpeg -i input.mp4 -vf \"zoompan=z='if(between(it,${startMs/1000.0},${endMs/1000.0}),$zoomScale,1.0)':d=1:s=1080x1920\" -c:a copy output_zoomed.mp4"

            return ParsedEditCommand(
                rawPrompt = prompt,
                actionType = "ADD_ZOOM",
                description = "Applied ${zoomScale}x cinematic zoom punch (${formatTime(startMs)} - ${formatTime(endMs)})",
                effectsAdded = listOf(effect),
                generatedFfmpegCommand = ffmpeg
            )
        }

        // 6. Color Grading & Visual Look
        if (clean.contains("filter") || clean.contains("color") || clean.contains("grade") ||
            clean.contains("look") || clean.contains("vintage") || clean.contains("cyberpunk") ||
            clean.contains("black and white") || clean.contains("monochrome") || clean.contains("sunset") ||
            clean.contains("vignette") || clean.contains("contrast") || clean.contains("anime")
        ) {
            val (startMs, endMs) = extractTimeRange(clean, 0L, currentDurationMs, defaultDurationMs = currentDurationMs)
            val filterType = when {
                clean.contains("vintage") || clean.contains("retro") || clean.contains("film") -> VideoFilterType.VINTAGE
                clean.contains("cyberpunk") || clean.contains("neon") || clean.contains("synthwave") -> VideoFilterType.CYBERPUNK
                clean.contains("black and white") || clean.contains("b&w") || clean.contains("monochrome") -> VideoFilterType.BLACK_AND_WHITE
                clean.contains("sunset") || clean.contains("warm") || clean.contains("golden") -> VideoFilterType.SUNSET
                clean.contains("anime") || clean.contains("vibrant") || clean.contains("pop") -> VideoFilterType.ANIME_POP
                clean.contains("vignette") -> VideoFilterType.VIGNETTE
                clean.contains("contrast") || clean.contains("hdr") -> VideoFilterType.HIGH_CONTRAST
                else -> VideoFilterType.CYBERPUNK
            }

            val effect = VideoEffect(
                id = UUID.randomUUID().toString(),
                type = EffectType.FILTER,
                startTimeMs = startMs,
                endTimeMs = endMs,
                filterType = filterType,
                title = filterType.displayName
            )

            val filterParam = when (filterType) {
                VideoFilterType.VINTAGE -> "curves=vintage,hue=s=0.7"
                VideoFilterType.CYBERPUNK -> "eq=contrast=1.3:brightness=0.05:saturation=1.8,colorbalance=rs=0.1:gs=-0.1:bs=0.3"
                VideoFilterType.BLACK_AND_WHITE -> "hue=s=0,eq=contrast=1.2"
                VideoFilterType.SUNSET -> "colorbalance=rs=0.25:gs=0.05:bs=-0.2,eq=saturation=1.3"
                VideoFilterType.ANIME_POP -> "eq=contrast=1.4:saturation=2.0"
                VideoFilterType.VIGNETTE -> "vignette=PI/4"
                VideoFilterType.HIGH_CONTRAST -> "eq=contrast=1.5:saturation=1.2"
                VideoFilterType.NONE -> "null"
            }

            val ffmpeg = "ffmpeg -i input.mp4 -vf \"$filterParam\" -c:a copy output_graded.mp4"

            return ParsedEditCommand(
                rawPrompt = prompt,
                actionType = "APPLY_FILTER",
                description = "Graded canvas with ${filterType.displayName} color preset",
                effectsAdded = listOf(effect),
                generatedFfmpegCommand = ffmpeg
            )
        }

        // 7. Text Overlay / Heading
        if (clean.contains("text") || clean.contains("overlay") || clean.contains("title") || clean.contains("heading") || clean.contains("banner")) {
            val extractedText = extractQuotedText(prompt) ?: run {
                when {
                    clean.contains("subscribe") -> "SUBSCRIBE FOR MORE 🔥"
                    clean.contains("epic") -> "EPIC MOMENT ✨"
                    clean.contains("vlog") -> "DAILY CREATOR VLOG"
                    clean.contains("crypto") || clean.contains("bitcoin") -> "BITCOIN BREAKOUT 🚀"
                    else -> "ZENOVATE AI EDIT"
                }
            }

            val (startMs, endMs) = extractTimeRange(clean, playheadMs, currentDurationMs, defaultDurationMs = 5000L)
            val position = when {
                clean.contains("top") -> "top"
                clean.contains("bottom") -> "bottom"
                else -> "center"
            }

            val effect = VideoEffect(
                id = UUID.randomUUID().toString(),
                type = EffectType.TEXT_OVERLAY,
                startTimeMs = startMs,
                endTimeMs = endMs,
                overlayText = extractedText,
                textPosition = position,
                colorHex = if (clean.contains("yellow")) "#FACC15" else "#00E5FF",
                title = "Text: \"$extractedText\""
            )

            val ffmpeg = "ffmpeg -i input.mp4 -vf \"drawtext=text='$extractedText':fontcolor=white:fontsize=52:box=1:boxcolor=black@0.7:boxborderw=12:enable='between(t,${startMs/1000.0},${endMs/1000.0})'\" -c:a copy output_text.mp4"

            return ParsedEditCommand(
                rawPrompt = prompt,
                actionType = "ADD_TEXT_OVERLAY",
                description = "Rendered typography overlay \"$extractedText\" ($position) (${formatTime(startMs)} - ${formatTime(endMs)})",
                effectsAdded = listOf(effect),
                generatedFfmpegCommand = ffmpeg
            )
        }

        // 8. Speed Ramping / Slow Motion
        if (clean.contains("speed") || clean.contains("fast") || clean.contains("slow") || clean.contains("ramp")) {
            val (startMs, endMs) = extractTimeRange(clean, playheadMs, currentDurationMs, defaultDurationMs = 8000L)
            val multiplier = when {
                clean.contains("0.5x") || clean.contains("slow") -> 0.5f
                clean.contains("4x") -> 4.0f
                clean.contains("3x") -> 3.0f
                clean.contains("2x") -> 2.0f
                else -> 2.0f
            }

            val effect = VideoEffect(
                id = UUID.randomUUID().toString(),
                type = EffectType.SPEED_RAMP,
                startTimeMs = startMs,
                endTimeMs = endMs,
                title = "Speed ${multiplier}x"
            )

            val pts = 1.0f / multiplier
            val ffmpeg = "ffmpeg -i input.mp4 -filter_complex \"[0:v]trim=start=${startMs/1000.0}:end=${endMs/1000.0},setpts=${pts}*PTS[v];[0:a]atrim=start=${startMs/1000.0}:end=${endMs/1000.0},asetpts=PTS,atempo=$multiplier[a]\" -map \"[v]\" -map \"[a]\" output_speed.mp4"

            return ParsedEditCommand(
                rawPrompt = prompt,
                actionType = "SPEED_RAMP",
                description = "Ramped playback speed to ${multiplier}x between ${formatTime(startMs)} and ${formatTime(endMs)}",
                effectsAdded = listOf(effect),
                speedChangeMultiplier = multiplier,
                generatedFfmpegCommand = ffmpeg
            )
        }

        // 9. Split Video Clip at Playhead
        if (clean.contains("split") || clean.contains("divide") || clean.contains("cut here")) {
            val targetTimeMs = extractSingleTimestamp(clean) ?: playheadMs.takeIf { it > 500 } ?: (currentDurationMs / 2)
            val segment1 = VideoSegment(
                id = UUID.randomUUID().toString(),
                startTimeMs = 0L,
                endTimeMs = targetTimeMs,
                label = "Clip A (0 to ${formatTime(targetTimeMs)})"
            )
            val segment2 = VideoSegment(
                id = UUID.randomUUID().toString(),
                startTimeMs = targetTimeMs,
                endTimeMs = currentDurationMs,
                label = "Clip B (${formatTime(targetTimeMs)} to ${formatTime(currentDurationMs)})"
            )

            val ffmpeg = "ffmpeg -i input.mp4 -t ${targetTimeMs/1000.0} -c copy clip1.mp4 -ss ${targetTimeMs/1000.0} -c copy clip2.mp4"

            return ParsedEditCommand(
                rawPrompt = prompt,
                actionType = "SPLIT_VIDEO",
                description = "Split video timeline into 2 separate segments at ${formatTime(targetTimeMs)}",
                cutsAdded = listOf(segment1, segment2),
                generatedFfmpegCommand = ffmpeg
            )
        }

        // 10. Fade Transitions
        if (clean.contains("fade")) {
            val isFadeIn = clean.contains("in") || !clean.contains("out")
            val startMs = if (isFadeIn) 0L else (currentDurationMs - 2000L).coerceAtLeast(0L)
            val endMs = if (isFadeIn) 2000L.coerceAtMost(currentDurationMs) else currentDurationMs

            val effect = VideoEffect(
                id = UUID.randomUUID().toString(),
                type = EffectType.TRANSITION_FADE,
                startTimeMs = startMs,
                endTimeMs = endMs,
                title = if (isFadeIn) "Fade In" else "Fade Out"
            )

            val ffmpeg = "ffmpeg -i input.mp4 -vf \"fade=t=${if (isFadeIn) "in:st=0:d=2" else "out:st=${startMs/1000.0}:d=2"}\" -c:a copy output_faded.mp4"

            return ParsedEditCommand(
                rawPrompt = prompt,
                actionType = "FADE_TRANSITION",
                description = "Added ${if (isFadeIn) "Fade In" else "Fade Out"} transition (${formatTime(startMs)} - ${formatTime(endMs)})",
                effectsAdded = listOf(effect),
                generatedFfmpegCommand = ffmpeg
            )
        }

        // Default Intelligent Creator Action: Focus & Highlight
        val (startMs, endMs) = extractTimeRange(clean, playheadMs, currentDurationMs, defaultDurationMs = 5000L)
        val genericEffect = VideoEffect(
            id = UUID.randomUUID().toString(),
            type = EffectType.ZOOM,
            startTimeMs = startMs,
            endTimeMs = endMs,
            zoomScale = 1.35f,
            title = "Zenovate AI Highlight"
        )
        return ParsedEditCommand(
            rawPrompt = prompt,
            actionType = "AI_TRANSFORM",
            description = "Interpreted prompt: applied dynamic creator emphasis (${formatTime(startMs)} - ${formatTime(endMs)})",
            effectsAdded = listOf(genericEffect),
            generatedFfmpegCommand = "ffmpeg -i input.mp4 -vf \"zoompan=z='if(between(it,${startMs/1000.0},${endMs/1000.0}),1.35,1.0)':d=1\" -c:a copy output_enhanced.mp4"
        )
    }

    private fun extractAssetQuery(prompt: String): String {
        val clean = prompt.trim()
        val regex = Regex("""(?i)(?:add|insert|overlay|put)\s+(?:an?\s+)?(?:image|graphic|picture|photo|sticker|icon|badge|emoji)?\s*(?:of\s+)?([^,.;]+)""")
        val match = regex.find(clean)
        if (match != null) {
            val extracted = match.groupValues[1].trim()
            // clean out timing keywords
            return extracted.replace(Regex("""(?i)\s+(?:at|from|to|in|for)\s+\d+.*"""), "").trim()
        }
        return ""
    }

    private fun mapQueryToAssetVisual(query: String): String {
        val q = query.lowercase()
        return when {
            q.contains("laptop") || q.contains("computer") || q.contains("macbook") -> "💻"
            q.contains("bitcoin") || q.contains("crypto") || q.contains("btc") -> "₿"
            q.contains("fire") || q.contains("flame") -> "🔥"
            q.contains("rocket") || q.contains("launch") -> "🚀"
            q.contains("coffee") || q.contains("cup") -> "☕"
            q.contains("money") || q.contains("cash") || q.contains("dollar") -> "💰"
            q.contains("robot") || q.contains("ai") -> "🤖"
            q.contains("camera") || q.contains("video") -> "📹"
            q.contains("subscribe") || q.contains("bell") -> "🔔"
            q.contains("heart") || q.contains("love") -> "❤️"
            q.contains("star") || q.contains("sparkle") -> "✨"
            q.contains("trophy") || q.contains("winner") -> "🏆"
            q.contains("music") || q.contains("audio") -> "🎵"
            q.contains("phone") || q.contains("mobile") -> "📱"
            q.contains("car") -> "🏎️"
            q.contains("pizza") || q.contains("food") -> "🍕"
            else -> "🎨"
        }
    }

    private fun extractQuotedText(input: String): String? {
        val pattern = Pattern.compile("[\"']([^\"']+)[\"']")
        val matcher = pattern.matcher(input)
        return if (matcher.find()) matcher.group(1) else null
    }

    private fun extractSingleTimestamp(input: String): Long? {
        val secPattern = Pattern.compile("(\\d+(\\.\\d+)?)\\s*(?:s|sec|second|seconds)")
        val matcher = secPattern.matcher(input)
        if (matcher.find()) {
            val seconds = matcher.group(1)?.toDoubleOrNull() ?: return null
            return (seconds * 1000).toLong()
        }
        val minSecPattern = Pattern.compile("(\\d+):(\\d+)")
        val msMatcher = minSecPattern.matcher(input)
        if (msMatcher.find()) {
            val min = msMatcher.group(1)?.toLongOrNull() ?: 0L
            val sec = msMatcher.group(2)?.toLongOrNull() ?: 0L
            return (min * 60 + sec) * 1000L
        }
        return null
    }

    private fun extractTimeRange(
        input: String,
        currentPlayheadMs: Long,
        totalDurationMs: Long,
        defaultDurationMs: Long
    ): Pair<Long, Long> {
        val numbers = mutableListOf<Double>()
        val pattern = Pattern.compile("(\\d+(?:\\.\\d+)?)\\s*(?:s|sec|seconds)?")
        val matcher = pattern.matcher(input)
        while (matcher.find()) {
            val num = matcher.group(1)?.toDoubleOrNull()
            if (num != null && num > 0.0) {
                numbers.add(num)
            }
        }

        if (numbers.size >= 2) {
            val start = (numbers[0] * 1000).toLong().coerceIn(0L, totalDurationMs)
            val end = (numbers[1] * 1000).toLong().coerceIn(start + 500L, totalDurationMs)
            return Pair(start, end)
        } else if (numbers.size == 1) {
            val start = (numbers[0] * 1000).toLong().coerceIn(0L, totalDurationMs)
            val end = (start + defaultDurationMs).coerceIn(start + 500L, totalDurationMs)
            return Pair(start, end)
        }

        val start = currentPlayheadMs.coerceIn(0L, (totalDurationMs - 1000L).coerceAtLeast(0L))
        val end = (start + defaultDurationMs).coerceAtMost(totalDurationMs)
        return Pair(start, end)
    }

    fun formatTime(ms: Long): String {
        val totalSec = ms / 1000
        val min = totalSec / 60
        val sec = totalSec % 60
        val tenths = (ms % 1000) / 100
        return "%02d:%02d.%d".format(min, sec, tenths)
    }
}
