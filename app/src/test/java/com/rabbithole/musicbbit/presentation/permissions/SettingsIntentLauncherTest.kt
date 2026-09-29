package com.rabbithole.musicbbit.presentation.permissions

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.doThrow
import org.mockito.kotlin.mock
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

import dagger.hilt.android.testing.HiltTestApplication

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = HiltTestApplication::class)
class SettingsIntentLauncherTest {

    private val intent = Intent("com.rabbithole.musicbbit.TEST_SETTINGS_ACTION")

    private fun throwingContext(): Context = mock {
        on { startActivity(any<Intent>()) } doThrow ActivityNotFoundException()
    }

    @Test
    fun `returns true when startActivity succeeds`() {
        assertTrue(launchSettingsSafely(mock(), intent))
    }

    @Test
    fun `returns false when startActivity throws`() {
        assertFalse(launchSettingsSafely(throwingContext(), intent))
    }
}
