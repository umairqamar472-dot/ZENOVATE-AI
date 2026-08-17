package com.example

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import com.example.engine.NlveSimulationEngine
import com.example.model.CanvasTransform
import com.example.model.VideoProject
import com.example.ui.components.VideoPlayerPreview
import com.example.ui.theme.MyApplicationTheme
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = RobolectricDeviceQualifiers.Pixel8, sdk = [36])
class GreetingScreenshotTest {

    @get:Rule val composeTestRule = createComposeRule()

    @Test
    fun greeting_screenshot() {
        val clip = NlveSimulationEngine.sampleClips.first()
        val project = VideoProject(
            id = "test_proj",
            title = clip.title,
            videoSource = clip.id,
            totalDurationMs = clip.durationMs
        )
        composeTestRule.setContent {
            MyApplicationTheme {
                VideoPlayerPreview(
                    project = project,
                    clip = clip,
                    customVideoUri = null,
                    playheadMs = 4000L,
                    isPlaying = false,
                    effects = emptyList(),
                    captions = clip.defaultCaptions,
                    assets = emptyList(),
                    canvasTransform = CanvasTransform(),
                    onTogglePlay = {},
                    onStepBack = {},
                    onStepForward = {},
                    onSeekTo = {}
                )
            }
        }

        composeTestRule.onRoot().captureRoboImage(filePath = "src/test/screenshots/greeting.png")
    }
}
