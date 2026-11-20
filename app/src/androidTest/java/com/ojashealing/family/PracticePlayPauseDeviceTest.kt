package com.ojashealing.family

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.DpRect
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.ojashealing.family.ui.content.BreathingOrb
import com.ojashealing.family.ui.content.Controls
import com.ojashealing.family.ui.content.SessionColors
import com.ojashealing.family.ui.content.SessionUi
import com.ojashealing.family.ui.theme.LocalReducedMotion
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Pressing play or pause in a practice changes only the button and the light, never where things sit. */
@RunWith(AndroidJUnit4::class)
class PracticePlayPauseDeviceTest {
    @get:Rule val compose = createComposeRule()
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val colors = SessionColors(top = Color(0xFF0B3D91), accent = Color(0xFF7CC4FF), soft = Color(0x2E7CC4FF))
    private val play get() = context.getString(R.string.media_play)
    private val pause get() = context.getString(R.string.media_pause)
    private val rewind get() = context.getString(R.string.session_rewind)
    private val forward get() = context.getString(R.string.session_forward)

    private var playing by mutableStateOf(false)

    /** The practice screen's centre and bottom: the breathing light, the controls, and the hint under them. */
    private fun showSession(reducedMotion: Boolean = false) {
        // The light and the halo loop forever, so the test drives the clock itself
        compose.mainClock.autoAdvance = false
        compose.setContent {
            CompositionLocalProvider(LocalReducedMotion provides reducedMotion) {
                MaterialTheme {
                    Column {
                        BreathingOrb(playing, colors, Modifier.size(260.dp))
                        Controls(
                            SessionUi(playing = playing, positionMs = 30_000, durationMs = 600_000, reachedMs = 60_000),
                            colors,
                            onToggle = { playing = !playing },
                            onSeek = {},
                        )
                        Text("hint under the controls", Modifier.testTag("hint"))
                    }
                }
            }
        }
        settle()
    }

    private fun bounds(): List<DpRect> = listOf(
        compose.onNodeWithTag("practice_lotus").getUnclippedBoundsInRoot(),
        compose.onNodeWithContentDescription(rewind).getUnclippedBoundsInRoot(),
        compose.onNodeWithContentDescription(forward).getUnclippedBoundsInRoot(),
        compose.onNodeWithTag("hint").getUnclippedBoundsInRoot(),
    )

    private fun settle() {
        compose.mainClock.advanceTimeBy(1_500)
        compose.waitForIdle()
    }

    @Test fun playingDoesNotMoveAnything() {
        showSession()
        val paused = bounds()
        compose.onNodeWithContentDescription(play).performClick()
        settle()
        compose.onNodeWithContentDescription(pause).assertExists()
        assertEquals(paused, bounds())
    }

    @Test fun pausingDoesNotMoveAnything() {
        playing = true
        showSession()
        val playingBounds = bounds()
        compose.onNodeWithContentDescription(pause).performClick()
        settle()
        compose.onNodeWithContentDescription(play).assertExists()
        assertEquals(playingBounds, bounds())
    }

    @Test fun theButtonStaysPutAcrossManyTaps() {
        showSession()
        val first = compose.onNodeWithContentDescription(play).getUnclippedBoundsInRoot()
        val before = bounds()
        repeat(6) { i ->
            compose.onNodeWithContentDescription(if (i % 2 == 0) play else pause).performClick()
            compose.mainClock.advanceTimeBy(120)
            assertEquals(before, bounds())
        }
        settle()
        assertEquals(first, compose.onNodeWithContentDescription(play).getUnclippedBoundsInRoot())
    }

    @Test fun reducedMotionAlsoStaysStill() {
        showSession(reducedMotion = true)
        val paused = bounds()
        compose.onNodeWithContentDescription(play).performClick()
        settle()
        assertEquals(paused, bounds())
        compose.onNodeWithContentDescription(pause).performClick()
        settle()
        assertEquals(paused, bounds())
    }

    @Test fun breathWordsAreReadOnlyWhilePlaying() {
        showSession()
        val breatheIn = context.getString(R.string.session_breathe_in)
        val breatheOut = context.getString(R.string.session_breathe_out)
        compose.onNodeWithText(breatheIn).assertDoesNotExist()
        compose.onNodeWithText(breatheOut).assertDoesNotExist()
        compose.onNodeWithContentDescription(play).performClick()
        settle()
        val shown = runCatching { compose.onNodeWithText(breatheIn).assertExists() }.isSuccess ||
            runCatching { compose.onNodeWithText(breatheOut).assertExists() }.isSuccess
        assertEquals(true, shown)
    }
}
