package com.rabbithole.musicbbit.presentation.components

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.rabbithole.musicbbit.TestActivity
import com.rabbithole.musicbbit.presentation.permissions.launchSettingsSafely
import dagger.hilt.android.testing.HiltTestApplication
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.doThrow
import org.mockito.kotlin.mock
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowToast

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = HiltTestApplication::class)
class SettingsLauncherTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<TestActivity>()

    @Test
    fun `rememberSettingsLauncher launches resolvable intent without failure toast`() {
        composeTestRule.setContent {
            val launchSettings = rememberSettingsLauncher()
            Button(
                onClick = { launchSettings(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)) }
            ) { Text("go") }
        }

        composeTestRule.onNodeWithText("go").performClick()

        assertNull(
            "resolvable intent must not show the failure toast",
            ShadowToast.getTextOfLatestToast(),
        )
    }

    @Test
    fun `launchSettingsSafely returns false for throwing context - contract pin for the toast branch`() {
        val throwingContext: Context = mock {
            on { startActivity(any<Intent>()) } doThrow ActivityNotFoundException()
        }
        assertFalse(
            "helper's toast branch depends on this returning false",
            launchSettingsSafely(throwingContext, Intent("com.rabbithole.musicbbit.nonexistent.ACTION")),
        )
    }
}
