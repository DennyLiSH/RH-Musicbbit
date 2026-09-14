package com.rabbithole.musicbbit.presentation.alarm.components

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.core.app.ApplicationProvider
import com.rabbithole.musicbbit.TestActivity
import dagger.hilt.android.testing.HiltTestApplication
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = HiltTestApplication::class, qualifiers = "en")
class AlarmEditComponentsTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<TestActivity>()

    @Test
    fun `save button bar shows saving text while saving`() {
        composeTestRule.setContent {
            SaveButtonBar(isSaving = true, onSave = {})
        }
        composeTestRule.onNodeWithText(
            ApplicationProvider.getApplicationContext<android.content.Context>()
                .getString(com.rabbithole.musicbbit.R.string.alarm_edit_saving)
        ).assertIsDisplayed()
    }

    @Test
    fun `autostop dropdown shows truthful label for non-preset value`() {
        composeTestRule.setContent {
            AutoStopDropdown(
                selectedAutoStop = com.rabbithole.musicbbit.domain.model.AutoStop.ByMinutes(20),
                onSelectionChange = {}
            )
        }
        composeTestRule.onNodeWithText(
            ApplicationProvider.getApplicationContext<android.content.Context>()
                .getString(com.rabbithole.musicbbit.R.string.alarm_edit_auto_stop_minutes_format, 20)
        ).assertExists()
    }

    @Test
    fun `discard dialog confirm button uses explicit discard verb`() {
        composeTestRule.setContent {
            DiscardDialog(onDismiss = {}, onConfirm = {})
        }
        composeTestRule.onNodeWithText(
            ApplicationProvider.getApplicationContext<android.content.Context>()
                .getString(com.rabbithole.musicbbit.R.string.action_discard)
        ).assertIsDisplayed()
    }
}
