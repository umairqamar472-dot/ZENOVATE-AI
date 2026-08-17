package com.example.engine

import com.example.model.CaptionSegment
import com.example.model.ProcessingStage
import com.example.model.SampleVideoClip
import com.example.model.SilenceRegion
import com.example.model.SubtitleStyle
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import java.util.UUID

object NlveSimulationEngine {

    val sampleClips = listOf(
        SampleVideoClip(
            id = "podcast_1",
            title = "Tech Talk: Creator AI Workflow",
            category = "Talking Head",
            durationMs = 30000L,
            description = "Studio podcast session with hesitations, filler words, and natural speech pauses ideal for silence cuts and auto captions.",
            defaultSilences = listOf(
                SilenceRegion(startTimeMs = 3500L, endTimeMs = 6500L, avgDb = -48f),
                SilenceRegion(startTimeMs = 12000L, endTimeMs = 15000L, avgDb = -52f),
                SilenceRegion(startTimeMs = 21500L, endTimeMs = 24500L, avgDb = -46f)
            ),
            defaultCaptions = listOf(
                CaptionSegment(UUID.randomUUID().toString(), 500L, 3400L, "🔥 THIS CHANGED EVERYTHING!", SubtitleStyle.MR_BEAST_YELLOW),
                CaptionSegment(UUID.randomUUID().toString(), 6600L, 11800L, "AI Video Editing on your mobile camera roll 📱", SubtitleStyle.MR_BEAST_YELLOW),
                CaptionSegment(UUID.randomUUID().toString(), 15200L, 21200L, "Speak your commands like 'Cut silences' or 'Add Bitcoin graphic' 🚀", SubtitleStyle.MR_BEAST_YELLOW),
                CaptionSegment(UUID.randomUUID().toString(), 24800L, 29500L, "Instant 4K 60FPS social export ready in seconds! ✨", SubtitleStyle.MR_BEAST_YELLOW)
            ),
            baseWaveform = generateSyntheticWaveform(30, listOf(3 to 6, 12 to 15, 21 to 24))
        ),
        SampleVideoClip(
            id = "travel_vlog",
            title = "Tokyo Neon City Walkthrough",
            category = "Vlog & Aesthetic",
            durationMs = 25000L,
            description = "High energy urban walkthrough perfect for cyberpunk color grading, zoom cuts, camera shakes, and title typography.",
            defaultSilences = listOf(
                SilenceRegion(startTimeMs = 8000L, endTimeMs = 10500L, avgDb = -40f),
                SilenceRegion(startTimeMs = 18000L, endTimeMs = 20000L, avgDb = -42f)
            ),
            defaultCaptions = listOf(
                CaptionSegment(UUID.randomUUID().toString(), 500L, 7800L, "Exploring the glowing streets of Shibuya crossing tonight! 🏙️", SubtitleStyle.NEON_CYBERPUNK),
                CaptionSegment(UUID.randomUUID().toString(), 10800L, 17800L, "Look at all the holographic billboards and neon signs. 🔮", SubtitleStyle.NEON_CYBERPUNK),
                CaptionSegment(UUID.randomUUID().toString(), 20200L, 24500L, "Don't forget to like and subscribe for the full tour! ❤️", SubtitleStyle.NEON_CYBERPUNK)
            ),
            baseWaveform = generateSyntheticWaveform(25, listOf(8 to 10, 18 to 20))
        ),
        SampleVideoClip(
            id = "cooking_masterclass",
            title = "Culinary Masterclass: Perfect Sear",
            category = "Tutorial",
            durationMs = 28000L,
            description = "Close up culinary cooking demo suited for slow motion 0.5x, speed ramps, and step-by-step text banners.",
            defaultSilences = listOf(
                SilenceRegion(startTimeMs = 5000L, endTimeMs = 7500L, avgDb = -45f),
                SilenceRegion(startTimeMs = 16000L, endTimeMs = 19000L, avgDb = -48f)
            ),
            defaultCaptions = listOf(
                CaptionSegment(UUID.randomUUID().toString(), 500L, 4800L, "Step 1: Ensure the pan is scorching hot. 🔥", SubtitleStyle.TIKTOK_POP),
                CaptionSegment(UUID.randomUUID().toString(), 7800L, 15800L, "Lay the steak away from you to prevent splattering. 🥩", SubtitleStyle.TIKTOK_POP),
                CaptionSegment(UUID.randomUUID().toString(), 19200L, 27500L, "Sear for 90 seconds until a golden crust forms! 🍳", SubtitleStyle.TIKTOK_POP)
            ),
            baseWaveform = generateSyntheticWaveform(28, listOf(5 to 7, 16 to 19))
        )
    )

    private fun generateSyntheticWaveform(seconds: Int, silenceIntervals: List<Pair<Int, Int>>): List<Float> {
        val points = mutableListOf<Float>()
        val totalBars = seconds * 4
        for (i in 0 until totalBars) {
            val sec = i / 4.0
            val isSilent = silenceIntervals.any { (start, end) -> sec >= start && sec <= end }
            if (isSilent) {
                points.add((0.05f + (Math.sin(i * 1.5).toFloat() * 0.03f)).coerceIn(0.02f, 0.1f))
            } else {
                val wave = (Math.sin(i * 0.8) * 0.35 + Math.cos(i * 1.7) * 0.25 + 0.55).toFloat()
                points.add(wave.coerceIn(0.2f, 0.95f))
            }
        }
        return points
    }

    /**
     * Simulates the Zenovate AI multi-agent processing pipeline with real-time feedback
     */
    fun simulateAiPipeline(prompt: String): Flow<ProcessingStage> = flow {
        val clean = prompt.lowercase()
        val isAssetRequest = clean.contains("image") || clean.contains("graphic") ||
                clean.contains("sticker") || clean.contains("picture") || clean.contains("asset") ||
                clean.contains("insert") || clean.contains("laptop") || clean.contains("bitcoin")

        if (isAssetRequest) {
            emit(
                ProcessingStage(
                    name = "Zenovate AI Media Retrieval",
                    detail = "Zenovate AI querying media libraries & vector graphic databases...",
                    progress = 0.20f,
                    isDone = false
                )
            )
            delay(400)
            emit(
                ProcessingStage(
                    name = "Asset Alpha Channel Extraction",
                    detail = "Synthesizing transparent PNG overlay & positioning anchor...",
                    progress = 0.55f,
                    isDone = false
                )
            )
            delay(350)
        } else {
            emit(
                ProcessingStage(
                    name = "Whisper STT Audio Engine",
                    detail = "Extracting audio track, analyzing voice envelope & silence threshold...",
                    progress = 0.25f,
                    isDone = false
                )
            )
            delay(350)

            emit(
                ProcessingStage(
                    name = "Gemini Multimodal Semantics",
                    detail = "Parsing natural language query '$prompt' & computing keyframe offsets...",
                    progress = 0.65f,
                    isDone = false
                )
            )
            delay(380)
        }

        emit(
            ProcessingStage(
                name = "FFmpeg Video Render Synthesis",
                detail = "Building filtergraph, compiling timeline tracks & keyframe cuts...",
                progress = 0.90f,
                isDone = false
            )
        )
        delay(320)

        emit(
            ProcessingStage(
                name = "Zenovate AI Edit Ready",
                detail = "Timeline updated! Ready for real-time mobile preview and export.",
                progress = 1.0f,
                isDone = true
            )
        )
    }

    /**
     * Simulates the FFmpeg export render progress
     */
    fun simulateExportRender(durationSec: Int): Flow<Float> = flow {
        val steps = 20
        for (i in 1..steps) {
            delay(100)
            emit(i.toFloat() / steps)
        }
    }
}
