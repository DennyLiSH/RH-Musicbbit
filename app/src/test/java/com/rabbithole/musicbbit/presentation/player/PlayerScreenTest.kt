package com.rabbithole.musicbbit.presentation.player

import android.content.Context
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.navigation.compose.rememberNavController
import androidx.test.core.app.ApplicationProvider
import androidx.work.Configuration
import androidx.work.WorkManager
import com.rabbithole.musicbbit.R
import com.rabbithole.musicbbit.TestActivity
import com.rabbithole.musicbbit.domain.model.Song
import com.rabbithole.musicbbit.service.PlaybackState
import dagger.hilt.android.testing.HiltTestApplication
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Wiring-level Compose UI tests for [PlayerScreen].
 *
 * Runs under Robolectric (JVM) to avoid Android 14+ JVMTI agent restrictions
 * that block mockk-android's inline mocking on instrumented environment.
 * Mirrors SettingsScreenTest migration pattern (createAndroidComposeRule +
 * main source-set TestActivity so LocalActivity.current resolves).
 *
 * WorkManager is initialized in @Before so the HiltTestApplication-induced
 * AlarmStartupReconciler coroutine (which calls scheduleIntegrityCheck) does
 * not throw "WorkManager is not initialized" — preventing test pollution
 * that would otherwise surface as UncaughtExceptionsBeforeTest in downstream
 * tests.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = HiltTestApplication::class)
class PlayerScreenTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<TestActivity>()

    @Before
    fun initWorkManagerAndDrainPendingReconcile() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        try {
            WorkManager.initialize(context, Configuration.Builder().build())
        } catch (e: IllegalStateException) {
            // Already initialized by a previous test class — safe to ignore.
        }
        // Give any background reconcile coroutine started by HiltTestApplication.onCreate
        // a chance to complete before the next test method begins, so its
        // scheduleIntegrityCheck does not cross the test boundary as an
        // UncaughtExceptionsBeforeTest.
        Thread.sleep(50)
    }

    @Test
    fun playButtonVisibleWhenNotPlaying() {
        val viewModel = createMockViewModel(
            playbackState = PlaybackState(isPlaying = false)
        )

        composeTestRule.setContent {
            PlayerScreen(
                navController = rememberNavController(),
                viewModel = viewModel
            )
        }

        val playLabel = composeTestRule.activity.getString(R.string.player_play)
        composeTestRule.onNodeWithContentDescription(playLabel)
            .assertExists()
    }

    @Test
    fun pauseButtonVisibleWhenPlaying() {
        val viewModel = createMockViewModel(
            playbackState = PlaybackState(isPlaying = true)
        )

        composeTestRule.setContent {
            PlayerScreen(
                navController = rememberNavController(),
                viewModel = viewModel
            )
        }

        val pauseLabel = composeTestRule.activity.getString(R.string.player_pause)
        composeTestRule.onNodeWithContentDescription(pauseLabel)
            .assertExists()
    }

    @Test
    fun songTitleDisplaysWhenCurrentSongNonNull() {
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
    fun progressSliderExistsInSemanticsTree() {
        val viewModel = createMockViewModel(
            playbackState = PlaybackState(positionMs = 30000L, durationMs = 180000L)
        )

        composeTestRule.setContent {
            PlayerScreen(
                navController = rememberNavController(),
                viewModel = viewModel
            )
        }

        // Slider exposes ProgressBarRangeInfo semantics; the original instrumented test matched
        // empty-string contentDescription which is unstable under Robolectric. Match by the
        // property key being defined — robust against minor float precision differences in
        // the current value (30000/180000 = 0.16666667).
        composeTestRule.onNode(
            SemanticsMatcher.keyIsDefined(SemanticsProperties.ProgressBarRangeInfo)
        ).assertExists()
    }

    private fun createMockViewModel(playbackState: PlaybackState): PlayerViewModel {
        val viewModel = mock<PlayerViewModel>()
        whenever(viewModel.playbackState).thenReturn(MutableStateFlow(playbackState))
        return viewModel
    }
}
