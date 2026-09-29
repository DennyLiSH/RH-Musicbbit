package com.rabbithole.musicbbit.presentation.alarm

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.core.app.ApplicationProvider
import com.rabbithole.musicbbit.R
import com.rabbithole.musicbbit.TestActivity
import com.rabbithole.musicbbit.domain.model.Alarm
import dagger.hilt.android.testing.HiltTestApplication
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = HiltTestApplication::class, qualifiers = "en")
class AlarmListItemSubtitleTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<TestActivity>()

    private fun alarm(ignoreQuietMode: Boolean) = Alarm(
        id = 1L,
        hour = 7,
        minute = 30,
        repeatDays = emptySet(),
        playlistId = 5L,
        isEnabled = true,
        label = null,
        autoStop = null,
        lastTriggeredAt = null,
        ignoreQuietMode = ignoreQuietMode
    )

    private fun suffixText(): String =
        ApplicationProvider.getApplicationContext<android.content.Context>()
            .getString(R.string.alarm_list_muted_in_dnd)

    @Test
    fun `subtitle appends muted-in-dnd suffix when ignoreQuietMode is false`() {
        composeTestRule.setContent {
            AlarmListItemSubtitle(
                alarmItem = AlarmItem(alarm = alarm(ignoreQuietMode = false), playlistName = "P1")
            )
        }
        composeTestRule.onNodeWithText(suffixText(), substring = true).assertExists()
    }

    @Test
    fun `subtitle omits suffix when ignoreQuietMode is true`() {
        composeTestRule.setContent {
            AlarmListItemSubtitle(
                alarmItem = AlarmItem(alarm = alarm(ignoreQuietMode = true), playlistName = "P1")
            )
        }
        composeTestRule.onNodeWithText(suffixText(), substring = true).assertDoesNotExist()
    }
}
