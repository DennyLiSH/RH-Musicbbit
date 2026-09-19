package com.rabbithole.musicbbit.navigation

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import com.rabbithole.musicbbit.R
import com.rabbithole.musicbbit.HiltTestActivity
import com.rabbithole.musicbbit.domain.repository.PlaybackProgressRepository
import com.rabbithole.musicbbit.service.playback.FakeAudioFocusPort
import com.rabbithole.musicbbit.service.playback.FakeAudioStreamPort
import com.rabbithole.musicbbit.service.playback.FakePlayerPort
import com.rabbithole.musicbbit.service.playback.PlaybackCoordinator
import com.rabbithole.musicbbit.service.playback.UserPlaybackSession
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.mock
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import timber.log.Timber

/**
 * Cold-start smoke test for the root composition.
 *
 * AppNavigation's Scaffold composes MiniPlayer in the bottomBar slot — a
 * SubcomposeLayout sibling of the content slot. Regression guard: the
 * LocalPlaybackSession provider must wrap BOTH slots, otherwise MiniPlayer's
 * default parameter hits the fail-fast factory and crashes on first frame
 * (introduced by 91320c1, caught only on a real device in 26.9.1).
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltAndroidTest
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = HiltTestApplication::class)
class AppNavigationSmokeTest {

    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val composeTestRule = createAndroidComposeRule<HiltTestActivity>()

    // Separate dispatcher so the session's infinite loops never tie into the
    // compose test's Main test clock.
    private val sessionDispatcher = UnconfinedTestDispatcher()

    private lateinit var session: UserPlaybackSession

    @Before
    fun setUp() {
        Timber.uprootAll()
        Timber.plant(object : Timber.Tree() {
            override fun log(priority: Int, tag: String?, message: String, t: Throwable?) {}
        })

        val playerPort = FakePlayerPort()
        session = UserPlaybackSession(
            playerPort = playerPort,
            audioStreamPort = FakeAudioStreamPort(),
            playbackProgressRepository = mock<PlaybackProgressRepository>(),
            serviceStarter = mock(),
            audioFocusPort = FakeAudioFocusPort(),
            playbackCoordinator = PlaybackCoordinator(
                playerPort = playerPort,
                audioFocusPort = FakeAudioFocusPort(),
                mainDispatcher = sessionDispatcher,
            ),
            mainDispatcher = sessionDispatcher,
        )
    }

    @After
    fun tearDown() {
        session.close()
    }

    @Test
    fun `root composition renders bottom navigation without LocalPlaybackSession crash`() {
        composeTestRule.setContent {
            AppNavigation(playbackSession = session)
        }

        // The bottom tab and the start screen title share the "Alarms" label —
        // assert at least one renders; the crash guard is the composition itself.
        val labelNodes = composeTestRule
            .onAllNodesWithText(composeTestRule.activity.getString(R.string.tab_alarm))
            .fetchSemanticsNodes()
        org.junit.Assert.assertFalse(labelNodes.isEmpty())
    }
}
