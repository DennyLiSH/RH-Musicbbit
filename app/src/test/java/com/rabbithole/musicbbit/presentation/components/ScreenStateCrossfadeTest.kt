package com.rabbithole.musicbbit.presentation.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.rabbithole.musicbbit.R
import com.rabbithole.musicbbit.TestActivity
import dagger.hilt.android.testing.HiltTestApplication
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Smoke test for [ScreenStateCrossfade]: verifies Loading/Error/Content all render.
 * Pre-fix: per-screen `Crossfade + when` boilerplate duplicated 5× — this seam test
 * guarantees the unified renderer stays a single home.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = HiltTestApplication::class)
class ScreenStateCrossfadeTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<TestActivity>()

    @Test
    fun loadingErrorAndContentAllRender() {
        var retries = 0
        composeTestRule.setContent {
            MaterialTheme {
                ScreenStateCrossfade(
                    state = ListUiState.Error(R.string.error_load_failed),
                    onRetry = { retries++ },
                ) {}
                ScreenStateCrossfade(state = ListUiState.Loading) {}
                ScreenStateCrossfade(state = ListUiState.Content("marker-text")) { text ->
                    Text(text)
                }
            }
        }
        composeTestRule.waitForIdle()

        // Error branch: message + retry button exist and are clickable
        composeTestRule
            .onNodeWithText(composeTestRule.activity.getString(R.string.error_load_failed))
            .assertExists()
        composeTestRule
            .onNodeWithText(composeTestRule.activity.getString(R.string.retry))
            .assertExists()
            .performClick()
        assertEquals(1, retries)

        // Content branch renders the lambda content
        composeTestRule.onNodeWithText("marker-text").assertExists()
    }
}