package com.rabbithole.musicbbit.presentation.alarm.components

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.rabbithole.musicbbit.R
import com.rabbithole.musicbbit.TestActivity
import com.rabbithole.musicbbit.domain.model.AlarmRingMode
import dagger.hilt.android.testing.HiltTestApplication
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Robolectric Compose UI tests for [RingModeSelector].
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = HiltTestApplication::class)
class RingModeSelectorTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<TestActivity>()

    private fun str(id: Int): String = composeTestRule.activity.getString(id)

    @Test
    fun `both ring mode options are displayed`() {
        composeTestRule.setContent {
            RingModeSelector(
                selectedMode = AlarmRingMode.Normal,
                onModeChanged = {}
            )
        }

        composeTestRule.onNodeWithText(str(R.string.alarm_edit_ring_mode_normal))
            .assertIsDisplayed()
        composeTestRule.onNodeWithText(str(R.string.alarm_edit_ring_mode_full_screen))
            .assertIsDisplayed()
    }

    @Test
    fun `clicking full screen option invokes callback`() {
        var selected: AlarmRingMode? = null
        composeTestRule.setContent {
            RingModeSelector(
                selectedMode = AlarmRingMode.Normal,
                onModeChanged = { selected = it }
            )
        }

        composeTestRule.onNodeWithText(str(R.string.alarm_edit_ring_mode_full_screen))
            .performClick()
        composeTestRule.waitForIdle()

        assertEquals(AlarmRingMode.FullScreen, selected)
    }

    @Test
    fun `clicking normal option invokes callback`() {
        var selected: AlarmRingMode? = null
        composeTestRule.setContent {
            RingModeSelector(
                selectedMode = AlarmRingMode.FullScreen,
                onModeChanged = { selected = it }
            )
        }

        composeTestRule.onNodeWithText(str(R.string.alarm_edit_ring_mode_normal))
            .performClick()
        composeTestRule.waitForIdle()

        assertEquals(AlarmRingMode.Normal, selected)
    }
}
