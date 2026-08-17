package com.example.viewmodel

import android.app.Application
import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.local.NlveDatabase
import com.example.data.local.entity.CommandLogEntity
import com.example.data.local.entity.ProjectEntity
import com.example.data.repository.VideoEditorRepository
import com.example.engine.NlveCommandParser
import com.example.engine.NlveSimulationEngine
import com.example.model.CanvasTransform
import com.example.model.CaptionSegment
import com.example.model.OverlayAsset
import com.example.model.ProcessingStage
import com.example.model.SampleVideoClip
import com.example.model.VideoEffect
import com.example.model.VideoProject
import com.example.model.VideoSegment
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.UUID

data class TimelineSnapshot(
    val segments: List<VideoSegment>,
    val effects: List<VideoEffect>,
    val captions: List<CaptionSegment>,
    val assets: List<OverlayAsset>,
    val transform: CanvasTransform,
    val description: String
)

data class VideoEditorUiState(
    val currentProject: VideoProject,
    val selectedClip: SampleVideoClip,
    val customVideoUri: Uri? = null,
    val playheadMs: Long = 0L,
    val isPlaying: Boolean = false,
    val segments: List<VideoSegment> = emptyList(),
    val effects: List<VideoEffect> = emptyList(),
    val captions: List<CaptionSegment> = emptyList(),
    val assets: List<OverlayAsset> = emptyList(),
    val canvasTransform: CanvasTransform = CanvasTransform(),
    val isProcessingAi: Boolean = false,
    val processingStage: ProcessingStage? = null,
    val isRecordingMic: Boolean = false,
    val isHoldToSpeakActive: Boolean = false,
    val liveRecordedText: String = "",
    val micAudioLevels: List<Float> = emptyList(),
    val canUndo: Boolean = false,
    val canRedo: Boolean = false,
    val showExportDialog: Boolean = false,
    val exportProgress: Float = 0f,
    val isExporting: Boolean = false,
    val exportSuccessMessage: String? = null,
    val showCommandLogsSheet: Boolean = false,
    val showProjectPickerSheet: Boolean = false,
    val statusBanner: String? = null
)

class VideoEditorViewModel(application: Application) : AndroidViewModel(application) {

    private val repository: VideoEditorRepository
    private var playbackJob: Job? = null
    private var recordingJob: Job? = null

    private val undoStack = mutableListOf<TimelineSnapshot>()
    private val redoStack = mutableListOf<TimelineSnapshot>()

    private val initialClip = NlveSimulationEngine.sampleClips.first()
    private val initialProject = VideoProject(
        id = UUID.randomUUID().toString(),
        title = initialClip.title,
        videoSource = initialClip.id,
        totalDurationMs = initialClip.durationMs,
        resolution = "1080x1920",
        fps = 30
    )

    private val _uiState = MutableStateFlow(
        VideoEditorUiState(
            currentProject = initialProject,
            selectedClip = initialClip,
            segments = listOf(
                VideoSegment(
                    id = UUID.randomUUID().toString(),
                    startTimeMs = 0L,
                    endTimeMs = initialClip.durationMs,
                    label = "Original Video Clip"
                )
            )
        )
    )
    val uiState: StateFlow<VideoEditorUiState> = _uiState.asStateFlow()

    val projectLogs: StateFlow<List<CommandLogEntity>>

    init {
        val db = NlveDatabase.getInstance(application)
        repository = VideoEditorRepository(db.nlveDao())

        projectLogs = repository.getLogsForProject(initialProject.id)
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5000),
                initialValue = emptyList()
            )

        viewModelScope.launch {
            repository.saveProject(
                ProjectEntity(
                    id = initialProject.id,
                    title = initialProject.title,
                    videoSource = initialProject.videoSource,
                    totalDurationMs = initialProject.totalDurationMs,
                    resolution = initialProject.resolution,
                    fps = initialProject.fps
                )
            )
        }
    }

    fun togglePlayPause() {
        if (_uiState.value.isPlaying) {
            pause()
        } else {
            play()
        }
    }

    fun play() {
        _uiState.update { it.copy(isPlaying = true) }
        playbackJob?.cancel()
        playbackJob = viewModelScope.launch {
            val tickInterval = 50L // 20 FPS tick rate
            while (isActive && _uiState.value.isPlaying) {
                delay(tickInterval)
                val current = _uiState.value.playheadMs
                val total = _uiState.value.currentProject.totalDurationMs

                val currentEffect = _uiState.value.effects.find {
                    it.type == com.example.model.EffectType.SPEED_RAMP && current in it.startTimeMs..it.endTimeMs
                }
                val speed = when {
                    currentEffect != null && currentEffect.title.contains("2x") -> 2.0f
                    currentEffect != null && currentEffect.title.contains("0.5x") -> 0.5f
                    else -> 1.0f
                }

                val nextTime = current + (tickInterval * speed).toLong()

                // Skip dead air / cut segments
                val cutSegment = _uiState.value.segments.find { it.isCutOut && nextTime in it.startTimeMs..it.endTimeMs }
                val finalTime = if (cutSegment != null) {
                    cutSegment.endTimeMs + 50L
                } else {
                    nextTime
                }

                if (finalTime >= total) {
                    _uiState.update { it.copy(playheadMs = 0L, isPlaying = false) }
                    break
                } else {
                    _uiState.update { it.copy(playheadMs = finalTime) }
                }
            }
        }
    }

    fun pause() {
        playbackJob?.cancel()
        _uiState.update { it.copy(isPlaying = false) }
    }

    fun seekTo(ms: Long) {
        val total = _uiState.value.currentProject.totalDurationMs
        val clamped = ms.coerceIn(0L, total)
        _uiState.update { it.copy(playheadMs = clamped) }
    }

    fun stepSeconds(seconds: Int) {
        val current = _uiState.value.playheadMs
        seekTo(current + (seconds * 1000L))
    }

    fun executeNaturalLanguageCommand(prompt: String) {
        if (prompt.isBlank() || _uiState.value.isProcessingAi) return

        saveUndoState("Action: $prompt")

        viewModelScope.launch {
            _uiState.update { it.copy(isProcessingAi = true, statusBanner = "Zenovate AI processing command...") }

            NlveSimulationEngine.simulateAiPipeline(prompt).collect { stage ->
                _uiState.update { it.copy(processingStage = stage) }
            }

            val state = _uiState.value
            val parsed = com.example.engine.GeminiNlveService.parseWithGeminiOrFallback(
                prompt = prompt,
                currentDurationMs = state.currentProject.totalDurationMs,
                playheadMs = state.playheadMs,
                availableSilences = state.selectedClip.defaultSilences,
                availableDefaultCaptions = state.selectedClip.defaultCaptions
            )

            var updatedSegments = state.segments.toMutableList()
            if (parsed.cutsAdded.isNotEmpty()) {
                if (parsed.actionType == "CUT_SILENCES") {
                    updatedSegments = rebuildSegmentsWithSilences(state.currentProject.totalDurationMs, parsed.cutsAdded)
                } else if (parsed.actionType == "SPLIT_VIDEO" || parsed.actionType == "TRIM_SEGMENT") {
                    updatedSegments.clear()
                    updatedSegments.addAll(parsed.cutsAdded)
                }
            }

            val updatedEffects = state.effects.toMutableList()
            if (parsed.effectsAdded.isNotEmpty()) {
                updatedEffects.addAll(parsed.effectsAdded)
            }

            val updatedCaptions = state.captions.toMutableList()
            if (parsed.captionsAdded.isNotEmpty()) {
                updatedCaptions.clear()
                updatedCaptions.addAll(parsed.captionsAdded)
            }

            val updatedAssets = state.assets.toMutableList()
            if (parsed.assetsAdded.isNotEmpty()) {
                updatedAssets.addAll(parsed.assetsAdded)
            }

            val updatedTransform = parsed.transformUpdate ?: state.canvasTransform

            repository.logCommand(
                projectId = state.currentProject.id,
                rawPrompt = prompt,
                parsedSummary = parsed.description,
                ffmpegCommand = parsed.generatedFfmpegCommand,
                isApplied = true
            )

            _uiState.update {
                it.copy(
                    segments = updatedSegments,
                    effects = updatedEffects,
                    captions = updatedCaptions,
                    assets = updatedAssets,
                    canvasTransform = updatedTransform,
                    isProcessingAi = false,
                    processingStage = null,
                    canUndo = undoStack.isNotEmpty(),
                    canRedo = redoStack.isNotEmpty(),
                    statusBanner = parsed.description
                )
            }
        }
    }

    private fun rebuildSegmentsWithSilences(
        totalDurationMs: Long,
        silenceCuts: List<VideoSegment>
    ): MutableList<VideoSegment> {
        val result = mutableListOf<VideoSegment>()
        var cursor = 0L
        val sortedCuts = silenceCuts.sortedBy { it.startTimeMs }

        for (silence in sortedCuts) {
            if (silence.startTimeMs > cursor) {
                result.add(
                    VideoSegment(
                        id = UUID.randomUUID().toString(),
                        startTimeMs = cursor,
                        endTimeMs = silence.startTimeMs,
                        isCutOut = false,
                        label = "Active Speech"
                    )
                )
            }
            result.add(silence)
            cursor = silence.endTimeMs
        }

        if (cursor < totalDurationMs) {
            result.add(
                VideoSegment(
                    id = UUID.randomUUID().toString(),
                    startTimeMs = cursor,
                    endTimeMs = totalDurationMs,
                    isCutOut = false,
                    label = "Active Speech"
                )
            )
        }
        return result
    }

    private fun saveUndoState(description: String) {
        val snapshot = TimelineSnapshot(
            segments = _uiState.value.segments,
            effects = _uiState.value.effects,
            captions = _uiState.value.captions,
            assets = _uiState.value.assets,
            transform = _uiState.value.canvasTransform,
            description = description
        )
        undoStack.add(snapshot)
        redoStack.clear()
        _uiState.update { it.copy(canUndo = true, canRedo = false) }
    }

    fun undo() {
        if (undoStack.isEmpty()) return
        val currentSnapshot = TimelineSnapshot(
            segments = _uiState.value.segments,
            effects = _uiState.value.effects,
            captions = _uiState.value.captions,
            assets = _uiState.value.assets,
            transform = _uiState.value.canvasTransform,
            description = "Current State"
        )
        redoStack.add(currentSnapshot)

        val previous = undoStack.removeAt(undoStack.lastIndex)
        _uiState.update {
            it.copy(
                segments = previous.segments,
                effects = previous.effects,
                captions = previous.captions,
                assets = previous.assets,
                canvasTransform = previous.transform,
                canUndo = undoStack.isNotEmpty(),
                canRedo = true,
                statusBanner = "Undid: ${previous.description}"
            )
        }
    }

    fun redo() {
        if (redoStack.isEmpty()) return
        val currentSnapshot = TimelineSnapshot(
            segments = _uiState.value.segments,
            effects = _uiState.value.effects,
            captions = _uiState.value.captions,
            assets = _uiState.value.assets,
            transform = _uiState.value.canvasTransform,
            description = "Undo State"
        )
        undoStack.add(currentSnapshot)

        val next = redoStack.removeAt(redoStack.lastIndex)
        _uiState.update {
            it.copy(
                segments = next.segments,
                effects = next.effects,
                captions = next.captions,
                assets = next.assets,
                canvasTransform = next.transform,
                canUndo = true,
                canRedo = redoStack.isNotEmpty(),
                statusBanner = "Redid: ${next.description}"
            )
        }
    }

    fun resetAllEdits() {
        saveUndoState("Reset all edits")
        val clip = _uiState.value.selectedClip
        _uiState.update {
            it.copy(
                segments = listOf(
                    VideoSegment(
                        id = UUID.randomUUID().toString(),
                        startTimeMs = 0L,
                        endTimeMs = clip.durationMs,
                        label = "Original Video Clip"
                    )
                ),
                effects = emptyList(),
                captions = emptyList(),
                assets = emptyList(),
                canvasTransform = CanvasTransform(),
                playheadMs = 0L,
                statusBanner = "All timeline edits and transforms reset"
            )
        }
    }

    fun onHoldToSpeakStart() {
        if (_uiState.value.isRecordingMic) return

        _uiState.update {
            it.copy(
                isRecordingMic = true,
                isHoldToSpeakActive = true,
                liveRecordedText = "",
                micAudioLevels = emptyList()
            )
        }

        recordingJob?.cancel()
        recordingJob = viewModelScope.launch {
            val sampleVoiceCommands = listOf(
                "Cut out the dead air silences and add MrBeast yellow captions",
                "Insert an image of a laptop and zoom in 1.8x at 5 seconds",
                "Apply cyberpunk neon filter and rotate clip 90 degrees",
                "Add floating fire emoji overlay at 3s and speed up 2x",
                "Trim the first 4 seconds and add text overlay 'BITCOIN TO THE MOON'"
            )
            val selectedVoicePrompt = sampleVoiceCommands.random()
            val words = selectedVoicePrompt.split(" ")
            val audioAmplitudes = mutableListOf<Float>()

            for (i in words.indices) {
                delay(260)
                if (!_uiState.value.isRecordingMic) break

                val subText = words.take(i + 1).joinToString(" ")
                val randomAmp = (0.35f + (Math.random().toFloat() * 0.65f))
                audioAmplitudes.add(randomAmp)
                if (audioAmplitudes.size > 20) audioAmplitudes.removeAt(0)

                _uiState.update {
                    it.copy(
                        liveRecordedText = subText,
                        micAudioLevels = audioAmplitudes.toList()
                    )
                }
            }
        }
    }

    fun onHoldToSpeakRelease() {
        recordingJob?.cancel()
        val recordedText = _uiState.value.liveRecordedText
        _uiState.update { it.copy(isRecordingMic = false, isHoldToSpeakActive = false) }

        if (recordedText.isNotBlank()) {
            executeNaturalLanguageCommand(recordedText)
        }
    }

    fun cancelAudioRecording() {
        recordingJob?.cancel()
        _uiState.update {
            it.copy(
                isRecordingMic = false,
                isHoldToSpeakActive = false,
                liveRecordedText = "",
                micAudioLevels = emptyList()
            )
        }
    }

    /**
     * Handles real user camera roll video selection from phone gallery
     */
    fun handlePickedVideoUri(uri: Uri, context: Context) {
        pause()
        var durationMs = 30000L
        var videoTitle = "Phone_Camera_Roll.mp4"

        try {
            val retriever = MediaMetadataRetriever()
            retriever.setDataSource(context, uri)
            val durationStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
            val titleStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE)
            if (durationStr != null) {
                durationMs = durationStr.toLongOrNull() ?: 30000L
            }
            if (!titleStr.isNullOrBlank()) {
                videoTitle = titleStr
            }
            retriever.release()
        } catch (_: Exception) {
            // fallback gracefully
        }

        val newProject = VideoProject(
            id = UUID.randomUUID().toString(),
            title = videoTitle,
            videoSource = uri.toString(),
            totalDurationMs = durationMs,
            resolution = "1080x1920",
            fps = 30,
            isCustomUserVideo = true
        )

        val customClip = SampleVideoClip(
            id = newProject.id,
            title = videoTitle,
            category = "Camera Roll",
            durationMs = durationMs,
            description = "Camera roll video loaded from device storage. Real-time NLP editing ready.",
            defaultSilences = listOf(
                com.example.model.SilenceRegion(startTimeMs = 3000L.coerceAtMost(durationMs), endTimeMs = 6000L.coerceAtMost(durationMs))
            ),
            defaultCaptions = emptyList(),
            baseWaveform = List(30) { (0.35f + Math.sin(it.toDouble()).toFloat() * 0.4f).coerceIn(0.1f, 0.95f) }
        )

        undoStack.clear()
        redoStack.clear()

        _uiState.update {
            it.copy(
                currentProject = newProject,
                selectedClip = customClip,
                customVideoUri = uri,
                playheadMs = 0L,
                segments = listOf(
                    VideoSegment(
                        id = UUID.randomUUID().toString(),
                        startTimeMs = 0L,
                        endTimeMs = durationMs,
                        label = videoTitle
                    )
                ),
                effects = emptyList(),
                captions = emptyList(),
                assets = emptyList(),
                canvasTransform = CanvasTransform(),
                canUndo = false,
                canRedo = false,
                showProjectPickerSheet = false,
                statusBanner = "Loaded Camera Roll video: $videoTitle (${durationMs/1000}s)"
            )
        }

        viewModelScope.launch {
            repository.saveProject(
                ProjectEntity(
                    id = newProject.id,
                    title = newProject.title,
                    videoSource = uri.toString(),
                    totalDurationMs = durationMs,
                    resolution = "1080x1920",
                    fps = 30
                )
            )
        }
    }

    fun switchClip(clip: SampleVideoClip) {
        pause()
        val newProject = VideoProject(
            id = UUID.randomUUID().toString(),
            title = clip.title,
            videoSource = clip.id,
            totalDurationMs = clip.durationMs,
            resolution = "1080x1920",
            fps = 30
        )
        undoStack.clear()
        redoStack.clear()

        _uiState.update {
            it.copy(
                currentProject = newProject,
                selectedClip = clip,
                customVideoUri = null,
                playheadMs = 0L,
                segments = listOf(
                    VideoSegment(
                        id = UUID.randomUUID().toString(),
                        startTimeMs = 0L,
                        endTimeMs = clip.durationMs,
                        label = clip.title
                    )
                ),
                effects = emptyList(),
                captions = emptyList(),
                assets = emptyList(),
                canvasTransform = CanvasTransform(),
                canUndo = false,
                canRedo = false,
                showProjectPickerSheet = false,
                statusBanner = "Loaded preset clip: ${clip.title}"
            )
        }
    }

    fun splitAtPlayhead() {
        val current = _uiState.value.playheadMs
        val total = _uiState.value.currentProject.totalDurationMs
        if (current <= 500L || current >= total - 500L) return

        executeNaturalLanguageCommand("split video at ${current / 1000}s")
    }

    fun removeEffect(effectId: String) {
        saveUndoState("Removed effect")
        _uiState.update {
            it.copy(
                effects = it.effects.filterNot { eff -> eff.id == effectId },
                statusBanner = "Removed effect"
            )
        }
    }

    fun removeAsset(assetId: String) {
        saveUndoState("Removed graphic asset")
        _uiState.update {
            it.copy(
                assets = it.assets.filterNot { a -> a.id == assetId },
                statusBanner = "Removed graphic asset"
            )
        }
    }

    fun toggleExportDialog(show: Boolean) {
        _uiState.update { it.copy(showExportDialog = show, exportProgress = 0f, isExporting = false, exportSuccessMessage = null) }
    }

    fun toggleCommandLogsSheet(show: Boolean) {
        _uiState.update { it.copy(showCommandLogsSheet = show) }
    }

    fun toggleProjectPickerSheet(show: Boolean) {
        _uiState.update { it.copy(showProjectPickerSheet = show) }
    }

    fun startExport(resolution: String, fps: Int) {
        viewModelScope.launch {
            _uiState.update { it.copy(isExporting = true, exportProgress = 0f, exportSuccessMessage = null) }
            val durationSec = (_uiState.value.currentProject.totalDurationMs / 1000).toInt().coerceAtLeast(10)
            NlveSimulationEngine.simulateExportRender(durationSec).collect { progress ->
                _uiState.update { it.copy(exportProgress = progress) }
            }
            _uiState.update {
                it.copy(
                    isExporting = false,
                    exportProgress = 1.0f,
                    exportSuccessMessage = "Zenovate AI render completed! Saved as ${it.currentProject.title.replace(" ", "_")}_social.mp4 ($resolution @ ${fps}fps)"
                )
            }
        }
    }

    fun clearStatusBanner() {
        _uiState.update { it.copy(statusBanner = null) }
    }
}
