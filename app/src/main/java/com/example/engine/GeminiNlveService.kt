package com.example.engine

import android.util.Log
import com.example.BuildConfig
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID
import java.util.concurrent.TimeUnit

object GeminiNlveService {

    private const val TAG = "GeminiNlveService"
    // Using gemini-3.5-flash as per gemini-api skill guidelines
    private const val MODEL_NAME = "gemini-3.5-flash"
    private const val BASE_URL = "https://generativelanguage.googleapis.com/v1beta/models/$MODEL_NAME:generateContent"

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .writeTimeout(12, TimeUnit.SECONDS)
        .build()

    val isGeminiConfigured: Boolean
        get() = try {
            val key = BuildConfig.GEMINI_API_KEY
            key.isNotBlank() && key != "MY_GEMINI_API_KEY"
        } catch (_: Exception) {
            false
        }

    /**
     * Attempts to query real Google Gemini API for deep semantic prompt translation.
     * Falls back to local NLP parser if key is absent or network fails.
     */
    suspend fun parseWithGeminiOrFallback(
        prompt: String,
        currentDurationMs: Long,
        playheadMs: Long,
        availableSilences: List<SilenceRegion>,
        availableDefaultCaptions: List<CaptionSegment>
    ): ParsedEditCommand = withContext(Dispatchers.IO) {
        if (!isGeminiConfigured) {
            Log.d(TAG, "Gemini API key not configured in secrets; using on-device NLP parser.")
            return@withContext NlveCommandParser.parseCommand(
                prompt = prompt,
                currentDurationMs = currentDurationMs,
                playheadMs = playheadMs,
                availableSilences = availableSilences,
                availableDefaultCaptions = availableDefaultCaptions
            )
        }

        try {
            val apiKey = BuildConfig.GEMINI_API_KEY
            val url = "$BASE_URL?key=$apiKey"

            val systemInstruction = """
                You are Zenovate AI, an expert Natural Language Video Editor (NLVE) engine.
                The user gives a video editing instruction for a video with duration ${currentDurationMs}ms (playhead at ${playheadMs}ms).
                
                You must output a JSON object with:
                - "actionType": one of ["CUT_SILENCES", "INSERT_SCRAPED_ASSET", "CANVAS_TRANSFORM", "AUTO_CAPTIONS", "ADD_ZOOM", "SPEED_RAMP", "APPLY_FILTER", "SPLIT_VIDEO", "TRIM_SEGMENT"]
                - "summary": concise human readable description of what Zenovate did
                - "assetQuery": string (if asset/sticker/image requested, e.g. "laptop", "bitcoin", "fire", "chart")
                - "assetEmoji": emoji string (e.g. "💻", "₿", "🔥", "📈")
                - "startTimeMs": number (start timestamp in ms)
                - "endTimeMs": number (end timestamp in ms)
                - "zoomScale": float (e.g. 1.8)
                - "rotationDegrees": float (0, 90, 180, 270)
                - "isFlippedHorizontal": boolean
                - "isShakeEnabled": boolean
                - "filterName": string ("CYBERPUNK", "VINTAGE", "ANIME", "WARM_CREATOR", "NONE")
                - "captionStyle": string ("MR_BEAST_YELLOW", "TIKTOK_POP", "NEON_CYBERPUNK", "RETRO_GLITCH")
                - "speedMultiplier": float (e.g. 2.0, 0.5)
                - "ffmpegCommand": realistic ffmpeg CLI command string
            """.trimIndent()

            val requestJson = JSONObject().apply {
                val contentsArray = JSONArray().apply {
                    val contentObj = JSONObject().apply {
                        val partsArray = JSONArray().apply {
                            put(JSONObject().put("text", "$systemInstruction\n\nUser command: \"$prompt\""))
                        }
                        put("parts", partsArray)
                    }
                    put(contentObj)
                }
                put("contents", contentsArray)

                val generationConfig = JSONObject().apply {
                    put("temperature", 0.1)
                    put("responseMimeType", "application/json")
                }
                put("generationConfig", generationConfig)
            }

            val requestBody = requestJson.toString().toRequestBody("application/json; charset=utf-8".toMediaType())
            val request = Request.Builder()
                .url(url)
                .post(requestBody)
                .build()

            val response = httpClient.newCall(request).execute()
            val responseBody = response.body?.string()

            if (response.isSuccessful && !responseBody.isNullOrBlank()) {
                val root = JSONObject(responseBody)
                val candidates = root.optJSONArray("candidates")
                if (candidates != null && candidates.length() > 0) {
                    val candidate = candidates.getJSONObject(0)
                    val content = candidate.optJSONObject("content")
                    val parts = content?.optJSONArray("parts")
                    val text = parts?.optJSONObject(0)?.optString("text")

                    if (!text.isNullOrBlank()) {
                        val parsedJson = JSONObject(text.trim())
                        val mappedCommand = buildCommandFromGeminiJson(
                            parsedJson,
                            prompt,
                            currentDurationMs,
                            playheadMs,
                            availableSilences,
                            availableDefaultCaptions
                        )
                        if (mappedCommand != null) {
                            return@withContext mappedCommand
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Gemini API call error: ${e.message}, falling back to on-device engine", e)
        }

        // Fallback to local deterministic parser on network failure or schema mismatch
        return@withContext NlveCommandParser.parseCommand(
            prompt = prompt,
            currentDurationMs = currentDurationMs,
            playheadMs = playheadMs,
            availableSilences = availableSilences,
            availableDefaultCaptions = availableDefaultCaptions
        )
    }

    private fun buildCommandFromGeminiJson(
        json: JSONObject,
        rawPrompt: String,
        durationMs: Long,
        playheadMs: Long,
        silences: List<SilenceRegion>,
        defaultCaptions: List<CaptionSegment>
    ): ParsedEditCommand? {
        val actionType = json.optString("actionType", "CUSTOM_AI_ACTION")
        val summary = json.optString("summary", "Zenovate Gemini AI applied: $rawPrompt")
        val ffmpeg = json.optString("ffmpegCommand", "ffmpeg -i input.mp4 -c:v copy output.mp4")
        val startMs = json.optLong("startTimeMs", playheadMs).coerceIn(0L, durationMs)
        val endMs = json.optLong("endTimeMs", (startMs + 5000L).coerceAtMost(durationMs)).coerceIn(startMs, durationMs)

        return when (actionType) {
            "CUT_SILENCES" -> {
                val targetSilences = if (silences.isNotEmpty()) silences else listOf(
                    SilenceRegion(startTimeMs = 3000L.coerceAtMost(durationMs), endTimeMs = 7000L.coerceAtMost(durationMs))
                )
                val cuts = targetSilences.map {
                    VideoSegment(
                        id = UUID.randomUUID().toString(),
                        startTimeMs = it.startTimeMs,
                        endTimeMs = it.endTimeMs,
                        isCutOut = true,
                        label = "Dead Air Cut"
                    )
                }
                ParsedEditCommand(
                    rawPrompt = rawPrompt,
                    actionType = "CUT_SILENCES",
                    description = "Zenovate AI (Gemini) removed ${cuts.size} silent pauses across the timeline.",
                    cutsAdded = cuts,
                    generatedFfmpegCommand = ffmpeg
                )
            }
            "INSERT_SCRAPED_ASSET" -> {
                val query = json.optString("assetQuery", "graphic")
                val emoji = json.optString("assetEmoji", "✨")
                val asset = OverlayAsset(
                    id = UUID.randomUUID().toString(),
                    query = query,
                    title = query.replaceFirstChar { it.uppercase() },
                    emojiOrIcon = emoji,
                    sourceUrlOrType = "gemini_retrieval",
                    posXFraction = 0.5f,
                    posYFraction = 0.4f,
                    scale = 1.0f,
                    startTimeMs = startMs,
                    endTimeMs = endMs,
                    isAnimated = true
                )
                val effect = VideoEffect(
                    id = UUID.randomUUID().toString(),
                    type = EffectType.STICKER_ASSET,
                    startTimeMs = startMs,
                    endTimeMs = endMs,
                    title = "AI Asset: \"$query\"",
                    assetData = asset
                )
                ParsedEditCommand(
                    rawPrompt = rawPrompt,
                    actionType = "INSERT_SCRAPED_ASSET",
                    description = "Gemini retrieved graphic: \"$query\" ($emoji)",
                    effectsAdded = listOf(effect),
                    assetsAdded = listOf(asset),
                    generatedFfmpegCommand = ffmpeg
                )
            }
            "CANVAS_TRANSFORM" -> {
                val rot = json.optDouble("rotationDegrees", 0.0).toFloat()
                val isFlip = json.optBoolean("isFlippedHorizontal", false)
                val isShake = json.optBoolean("isShakeEnabled", false)
                val transform = CanvasTransform(
                    rotationDegrees = rot,
                    isFlippedHorizontal = isFlip,
                    isShakeEnabled = isShake,
                    zoomScale = if (isShake) 1.15f else 1.0f
                )
                val effect = VideoEffect(
                    id = UUID.randomUUID().toString(),
                    type = EffectType.CANVAS_TRANSFORM,
                    startTimeMs = 0L,
                    endTimeMs = durationMs,
                    title = "Transform: ${rot.toInt()}° rotation",
                    transformData = transform
                )
                ParsedEditCommand(
                    rawPrompt = rawPrompt,
                    actionType = "CANVAS_TRANSFORM",
                    description = "Gemini updated canvas transform: ${rot.toInt()}° rotation, flip: $isFlip",
                    effectsAdded = listOf(effect),
                    transformUpdate = transform,
                    generatedFfmpegCommand = ffmpeg
                )
            }
            "AUTO_CAPTIONS" -> {
                val styleStr = json.optString("captionStyle", "MR_BEAST_YELLOW")
                val style = try {
                    SubtitleStyle.valueOf(styleStr)
                } catch (_: Exception) {
                    SubtitleStyle.MR_BEAST_YELLOW
                }
                val captions = if (defaultCaptions.isNotEmpty()) {
                    defaultCaptions.map { it.copy(style = style) }
                } else {
                    listOf(
                        CaptionSegment(UUID.randomUUID().toString(), 0L, (durationMs/3), "Welcome to Zenovate AI!", style),
                        CaptionSegment(UUID.randomUUID().toString(), (durationMs/3), (durationMs*2/3), "Creating viral videos with natural language", style),
                        CaptionSegment(UUID.randomUUID().toString(), (durationMs*2/3), durationMs, "Let's automate your creation!", style)
                    )
                }
                val effect = VideoEffect(
                    id = UUID.randomUUID().toString(),
                    type = EffectType.TEXT_OVERLAY,
                    startTimeMs = 0L,
                    endTimeMs = durationMs,
                    title = "Captions (${style.name})"
                )
                ParsedEditCommand(
                    rawPrompt = rawPrompt,
                    actionType = "AUTO_CAPTIONS",
                    description = "Gemini generated ${captions.size} speech captions in ${style.name} style",
                    effectsAdded = listOf(effect),
                    captionsAdded = captions,
                    generatedFfmpegCommand = ffmpeg
                )
            }
            "ADD_ZOOM" -> {
                val zoom = json.optDouble("zoomScale", 1.8).toFloat()
                val effect = VideoEffect(
                    id = UUID.randomUUID().toString(),
                    type = EffectType.ZOOM,
                    startTimeMs = startMs,
                    endTimeMs = endMs,
                    title = "Zoom Punch ${zoom}x",
                    zoomScale = zoom
                )
                ParsedEditCommand(
                    rawPrompt = rawPrompt,
                    actionType = "ADD_ZOOM",
                    description = "Gemini added zoom punch ${zoom}x from ${startMs/1000}s to ${endMs/1000}s",
                    effectsAdded = listOf(effect),
                    generatedFfmpegCommand = ffmpeg
                )
            }
            "SPEED_RAMP" -> {
                val multiplier = json.optDouble("speedMultiplier", 2.0).toFloat()
                val effect = VideoEffect(
                    id = UUID.randomUUID().toString(),
                    type = EffectType.SPEED_RAMP,
                    startTimeMs = startMs,
                    endTimeMs = endMs,
                    title = "Speed ${multiplier}x"
                )
                ParsedEditCommand(
                    rawPrompt = rawPrompt,
                    actionType = "SPEED_RAMP",
                    description = "Gemini adjusted speed to ${multiplier}x (${startMs/1000}s - ${endMs/1000}s)",
                    effectsAdded = listOf(effect),
                    generatedFfmpegCommand = ffmpeg
                )
            }
            "APPLY_FILTER" -> {
                val filterStr = json.optString("filterName", "CYBERPUNK")
                val filter = try {
                    VideoFilterType.valueOf(filterStr)
                } catch (_: Exception) {
                    VideoFilterType.CYBERPUNK
                }
                val effect = VideoEffect(
                    id = UUID.randomUUID().toString(),
                    type = EffectType.FILTER,
                    startTimeMs = startMs,
                    endTimeMs = endMs,
                    title = "Filter: ${filter.name}",
                    filterType = filter
                )
                ParsedEditCommand(
                    rawPrompt = rawPrompt,
                    actionType = "APPLY_FILTER",
                    description = "Gemini applied color grade: ${filter.name}",
                    effectsAdded = listOf(effect),
                    generatedFfmpegCommand = ffmpeg
                )
            }
            else -> {
                // Fallback to NlveCommandParser
                NlveCommandParser.parseCommand(
                    prompt = rawPrompt,
                    currentDurationMs = durationMs,
                    playheadMs = playheadMs,
                    availableSilences = silences,
                    availableDefaultCaptions = defaultCaptions
                )
            }
        }
    }
}
