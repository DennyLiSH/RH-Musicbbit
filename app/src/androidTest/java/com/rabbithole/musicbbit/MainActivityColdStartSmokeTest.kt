package com.rabbithole.musicbbit

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onRoot
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Cold-start smoke: launches the real app process (MusicApplication onCreate +
 * real Hilt graph + real composition) and asserts the root renders. Guards
 * OEM/API-level startup crashes that Robolectric smoke tests cannot reach
 * (release gate via connectedDebugAndroidTest; see CLAUDE.md 冷启动冒烟守卫).
 */
@RunWith(AndroidJUnit4::class)
class MainActivityColdStartSmokeTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun coldStartRendersRootWithoutCrashing() {
        composeRule.onRoot().assertExists()
    }
}
