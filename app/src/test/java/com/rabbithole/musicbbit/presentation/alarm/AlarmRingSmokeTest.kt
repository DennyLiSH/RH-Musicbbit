package com.rabbithole.musicbbit.presentation.alarm

import android.content.Intent
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.rabbithole.musicbbit.R
import com.rabbithole.musicbbit.TestActivity
import com.rabbithole.musicbbit.ui.theme.音乐兔Theme
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Cold-start smoke guards for the alarm ring entry point.
 *
 * Mirrors AppNavigationSmokeTest: composition-time defects (theme token,
 * resource, layout breakage) are invisible to the compiler and to unit tests
 * that pass arguments directly — only a real composition exposes them. The
 * composition test renders the real screen tree with a mocked ViewModel; the
 * entry-contract test launches the real @AndroidEntryPoint activity without an
 * alarmId and asserts the documented finish() path.
 */
@HiltAndroidTest
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = HiltTestApplication::class)
class AlarmRingSmokeTest {

    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val composeTestRule = createAndroidComposeRule<TestActivity>()

    @Test
    fun `ring screen composition renders controls without crashing`() {
        val viewModel = mock<AlarmRingViewModel>().also {
            whenever(it.uiState).thenReturn(MutableStateFlow(AlarmRingUiState()))
            whenever(it.messages).thenReturn(kotlinx.coroutines.flow.emptyFlow())
        }

        composeTestRule.setContent {
            音乐兔Theme(darkTheme = false) {
                AlarmRingScreen(
                    alarmId = 1L,
                    viewModel = viewModel,
                    onStop = {}
                )
            }
        }

        composeTestRule
            .onNodeWithText(composeTestRule.activity.getString(R.string.alarm_ring_stop))
            .assertExists()
    }

    @Test
    fun `activity launched without alarmId finishes instead of composing`() {
        val intent = Intent(composeTestRule.activity, AlarmRingActivity::class.java)

        val activity = Robolectric.buildActivity(AlarmRingActivity::class.java, intent)
            .setup()
            .get()

        assertTrue(activity.isFinishing)
    }
}
