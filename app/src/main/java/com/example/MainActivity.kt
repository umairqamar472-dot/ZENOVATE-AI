package com.example

import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ui.components.CommandHistorySheet
import com.example.ui.components.CommandPromptBar
import com.example.ui.components.ExportDialog
import com.example.ui.components.ProjectPickerModal
import com.example.ui.components.TimelineVisualizer
import com.example.ui.components.VideoPlayerPreview
import com.example.ui.theme.CyanNeon
import com.example.ui.theme.EmeraldKeep
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.theme.SurfaceElevatedDark
import com.example.ui.theme.VioletNeon
import com.example.viewmodel.VideoEditorViewModel

class MainActivity : ComponentActivity() {
    private val viewModel: VideoEditorViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                VideoEditorScreen(viewModel = viewModel)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VideoEditorScreen(viewModel: VideoEditorViewModel) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val projectLogs by viewModel.projectLogs.collectAsStateWithLifecycle()

    // Activity Result Launcher for selecting real video from camera roll / device storage
    val videoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) {
            viewModel.handlePickedVideoUri(uri, context)
        }
    }

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .testTag("zenovate_main_screen"),
        containerColor = Color(0xFF070A12),
        topBar = {
            TopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        androidx.compose.foundation.Image(
                            painter = androidx.compose.ui.res.painterResource(id = R.drawable.zenovate_ai_logo),
                            contentDescription = "Zenovate AI Logo",
                            modifier = Modifier
                                .size(34.dp)
                                .clip(RoundedCornerShape(8.dp))
                        )

                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "ZENO",
                                    color = Color.White,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Black,
                                    letterSpacing = 1.sp
                                )
                                Text(
                                    text = "VATE",
                                    color = VioletNeon,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Black,
                                    letterSpacing = 1.sp
                                )
                            }
                            Text(
                                text = "AI AUTOMATION • NLVE",
                                color = CyanNeon,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 0.8.sp
                            )
                        }
                    }
                },
                actions = {
                    // Camera Roll / Preset Library Switcher Button
                    Surface(
                        color = SurfaceElevatedDark,
                        shape = RoundedCornerShape(20.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF334155)),
                        modifier = Modifier
                            .clip(RoundedCornerShape(20.dp))
                            .clickable { viewModel.toggleProjectPickerSheet(true) }
                            .testTag("top_bar_project_button")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                imageVector = if (uiState.customVideoUri != null) Icons.Default.PhotoLibrary else Icons.Default.VideoLibrary,
                                contentDescription = "Projects",
                                tint = CyanNeon,
                                modifier = Modifier.size(13.dp)
                            )
                            Text(
                                text = uiState.selectedClip.title.take(12) + if (uiState.selectedClip.title.length > 12) "..." else "",
                                color = Color.White,
                                fontSize = 10.5.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }

                    // SQLite Command Logs Button
                    IconButton(
                        onClick = { viewModel.toggleCommandLogsSheet(true) },
                        modifier = Modifier.size(34.dp).testTag("top_bar_logs_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.History,
                            contentDescription = "Command History",
                            tint = Color.LightGray,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    // Export Video Button
                    Surface(
                        color = CyanNeon,
                        shape = RoundedCornerShape(20.dp),
                        modifier = Modifier
                            .clip(RoundedCornerShape(20.dp))
                            .clickable { viewModel.toggleExportDialog(true) }
                            .testTag("top_bar_export_button")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 11.dp, vertical = 5.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Download,
                                contentDescription = "Export",
                                tint = Color.Black,
                                modifier = Modifier.size(13.dp)
                            )
                            Text(
                                text = "Export",
                                color = Color.Black,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(6.dp))
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xFF070A12))
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 10.dp, vertical = 6.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Status Notification Banner
            AnimatedVisibility(
                visible = uiState.statusBanner != null,
                enter = fadeIn() + slideInVertically(),
                exit = fadeOut() + slideOutVertically()
            ) {
                if (uiState.statusBanner != null) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF062A1F)),
                        border = androidx.compose.foundation.BorderStroke(1.dp, EmeraldKeep.copy(alpha = 0.6f))
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                modifier = Modifier.weight(1f),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.AutoAwesome,
                                    contentDescription = null,
                                    tint = EmeraldKeep,
                                    modifier = Modifier.size(15.dp)
                                )
                                Text(
                                    text = uiState.statusBanner ?: "",
                                    color = Color(0xFFA7F3D0),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                            IconButton(
                                onClick = { viewModel.clearStatusBanner() },
                                modifier = Modifier.size(22.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Dismiss",
                                    tint = Color.Gray,
                                    modifier = Modifier.size(13.dp)
                                )
                            }
                        }
                    }
                }
            }

            // 1. High-Fidelity Video Player Preview Canvas
            VideoPlayerPreview(
                project = uiState.currentProject,
                clip = uiState.selectedClip,
                customVideoUri = uiState.customVideoUri,
                playheadMs = uiState.playheadMs,
                isPlaying = uiState.isPlaying,
                effects = uiState.effects,
                captions = uiState.captions,
                assets = uiState.assets,
                canvasTransform = uiState.canvasTransform,
                onTogglePlay = { viewModel.togglePlayPause() },
                onStepBack = { viewModel.stepSeconds(-2) },
                onStepForward = { viewModel.stepSeconds(2) },
                onSeekTo = { viewModel.seekTo(it) }
            )

            // 2. Interactive Multi-Track Timeline Visualizer
            TimelineVisualizer(
                totalDurationMs = uiState.currentProject.totalDurationMs,
                playheadMs = uiState.playheadMs,
                segments = uiState.segments,
                effects = uiState.effects,
                captions = uiState.captions,
                assets = uiState.assets,
                clip = uiState.selectedClip,
                canUndo = uiState.canUndo,
                canRedo = uiState.canRedo,
                onSeekTo = { viewModel.seekTo(it) },
                onSplitAtPlayhead = { viewModel.splitAtPlayhead() },
                onUndo = { viewModel.undo() },
                onRedo = { viewModel.redo() },
                onResetAll = { viewModel.resetAllEdits() },
                onRemoveEffect = { viewModel.removeEffect(it) },
                onRemoveAsset = { viewModel.removeAsset(it) }
            )

            // 3. Audio & Text Command Prompt Box with "Hold to Speak"
            CommandPromptBar(
                isProcessing = uiState.isProcessingAi,
                processingStage = uiState.processingStage,
                isRecordingMic = uiState.isRecordingMic,
                isHoldToSpeakActive = uiState.isHoldToSpeakActive,
                liveRecordedText = uiState.liveRecordedText,
                micLevels = uiState.micAudioLevels,
                onSubmitCommand = { viewModel.executeNaturalLanguageCommand(it) },
                onHoldToSpeakStart = { viewModel.onHoldToSpeakStart() },
                onHoldToSpeakRelease = { viewModel.onHoldToSpeakRelease() },
                onCancelRecording = { viewModel.cancelAudioRecording() }
            )

            Spacer(modifier = Modifier.height(12.dp))
        }
    }

    // Modal Bottom Sheets & Dialogs
    if (uiState.showCommandLogsSheet) {
        CommandHistorySheet(
            logs = projectLogs,
            onDismiss = { viewModel.toggleCommandLogsSheet(false) },
            onReplayCommand = {
                viewModel.toggleCommandLogsSheet(false)
                viewModel.executeNaturalLanguageCommand(it)
            }
        )
    }

    if (uiState.showProjectPickerSheet) {
        ProjectPickerModal(
            currentClipId = uiState.selectedClip.id,
            onDismiss = { viewModel.toggleProjectPickerSheet(false) },
            onSelectClip = { viewModel.switchClip(it) },
            onPickCameraRollVideo = {
                viewModel.toggleProjectPickerSheet(false)
                videoPickerLauncher.launch(
                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly)
                )
            }
        )
    }

    if (uiState.showExportDialog) {
        ExportDialog(
            project = uiState.currentProject,
            isExporting = uiState.isExporting,
            progress = uiState.exportProgress,
            successMessage = uiState.exportSuccessMessage,
            onDismiss = { viewModel.toggleExportDialog(false) },
            onStartExport = { res, fps ->
                viewModel.startExport(res, fps)
            }
        )
    }
}
