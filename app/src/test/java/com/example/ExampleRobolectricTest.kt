package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.engine.NlveCommandParser
import com.example.model.SubtitleStyle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

    @Test
    fun `read string from context`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("Zenovate AI", appName)
    }

    @Test
    fun `parse cut silences command`() {
        val result = NlveCommandParser.parseCommand(
            prompt = "Cut out the silences",
            currentDurationMs = 30000L,
            playheadMs = 0L,
            availableSilences = emptyList(),
            availableDefaultCaptions = emptyList()
        )
        assertEquals("CUT_SILENCES", result.actionType)
        assertTrue(result.cutsAdded.isNotEmpty())
    }

    @Test
    fun `parse zoom command`() {
        val result = NlveCommandParser.parseCommand(
            prompt = "Zoom in 2x at 5 seconds",
            currentDurationMs = 30000L,
            playheadMs = 0L,
            availableSilences = emptyList(),
            availableDefaultCaptions = emptyList()
        )
        assertEquals("ADD_ZOOM", result.actionType)
        assertTrue(result.effectsAdded.isNotEmpty())
        assertEquals(2.0f, result.effectsAdded.first().zoomScale)
    }

    @Test
    fun `parse asset research query`() {
        val result = NlveCommandParser.parseCommand(
            prompt = "Add an image of a laptop at 5 seconds",
            currentDurationMs = 30000L,
            playheadMs = 0L,
            availableSilences = emptyList(),
            availableDefaultCaptions = emptyList()
        )
        assertEquals("INSERT_SCRAPED_ASSET", result.actionType)
        assertTrue(result.assetsAdded.isNotEmpty())
        assertEquals("💻", result.assetsAdded.first().emojiOrIcon)
    }

    @Test
    fun `parse canvas transform command`() {
        val result = NlveCommandParser.parseCommand(
            prompt = "Rotate clip 90 degrees and flip horizontal",
            currentDurationMs = 30000L,
            playheadMs = 0L,
            availableSilences = emptyList(),
            availableDefaultCaptions = emptyList()
        )
        assertEquals("CANVAS_TRANSFORM", result.actionType)
        assertNotNull(result.transformUpdate)
        assertEquals(90f, result.transformUpdate?.rotationDegrees)
        assertTrue(result.transformUpdate?.isFlippedHorizontal == true)
    }

    @Test
    fun `parse mrbeast style captions`() {
        val result = NlveCommandParser.parseCommand(
            prompt = "Auto generate MrBeast yellow captions",
            currentDurationMs = 30000L,
            playheadMs = 0L,
            availableSilences = emptyList(),
            availableDefaultCaptions = emptyList()
        )
        assertEquals("AUTO_CAPTIONS", result.actionType)
        assertTrue(result.captionsAdded.isNotEmpty())
        assertEquals(SubtitleStyle.MR_BEAST_YELLOW, result.captionsAdded.first().style)
    }
}
