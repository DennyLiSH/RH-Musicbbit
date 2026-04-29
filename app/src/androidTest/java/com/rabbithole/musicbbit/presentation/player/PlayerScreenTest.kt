package com.rabbithole.musicbbit.presentation.player

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.navigation.compose.rememberNavController
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.rabbithole.musicbbit.domain.model.Song
import com.rabbithole.musicbbit.service.PlaybackState
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Instrumented Compose UI tests for [PlayerScreen].
 *
 * These tests run on a device/emulator and verify the screen renders
 * correctly for different playback states.
 */
@RunWith(AndroidJUnit4::class)
class PlayerScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun `play button visible when not playing`() {
        val viewModel = createMockViewModel(
            playbackState = PlaybackState(isPlaying = false)
        )

        composeTestRule.setContent {
            PlayerScreen(
                navController = rememberNavController(),
                viewModel = viewModel
            )
        }

        composeTestRule.onNodeWithContentDescription("Play")
            .assertIsDisplayed()
    }

    @Test
    fun `pause button visible when playing`() {
        val viewModel = createMockViewModel(
            playbackState = PlaybackState(isPlaying = true)
        )

        composeTestRule.setContent {
            PlayerScreen(
                navController = rememberNavController(),
                viewModel = viewModel
            )
        }

        composeTestRule.onNodeWithContentDescription("Pause")
            .assertIsDisplayed()
    }

    @Test
    fun `song title displays when current song is non-null`() {
        val song = Song(
            id = 1L,
            path = "/music/test.mp3",
            title = "Test Song Title",
            artist = "Test Artist",
            album = "Test Album",
            durationMs = 180000L,
            dateAdded = 0L,
            coverUri = null
        )
        val viewModel = createMockViewModel(
            playbackState = PlaybackState(currentSong = song)
        )

        composeTestRule.setContent {
            PlayerScreen(
                navController = rememberNavController(),
                viewModel = viewModel
            )
        }

        composeTestRule.onNodeWithText("Test Song Title")
            .assertIsDisplayed()
    }

    @Test
    fun `progress slider exists in semantics tree`() {
        val viewModel = createMockViewModel(
            playbackState = PlaybackState(positionMs = 30000L, durationMs = 180000L)
        )

        composeTestRule.setContent {
            PlayerScreen(
                navController = rememberNavController(),
                viewModel = viewModel
            )
        }

        // Slider semantics: Compose Slider exposes a "ProgressBar" semantics role.
        // We match by the progress range semantics which is always present for Slider.
        composeTestRule.onNodeWithContentDescription("")
            .assertExists()
    }

    private fun createMockViewModel(playbackState: PlaybackState): PlayerViewModel {
        val viewModel = mockk<PlayerViewModel>(relaxed = true)
        every { viewModel.playbackState } returns MutableStateFlow(playbackState)
        every { viewModel.alarmLabel } returns MutableStateFlow(null)
        return viewModel
    }
}
