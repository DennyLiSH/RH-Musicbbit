package com.rabbithole.musicbbit.service

import android.content.ActivityNotFoundException
import android.content.Context
import dagger.hilt.android.testing.HiltTestApplication
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.doThrow
import org.mockito.kotlin.mock
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = HiltTestApplication::class)
class PermissionHelpersOpenSettingsTest {

    private fun throwingContext(): Context = mock {
        on { startActivity(any<android.content.Intent>()) } doThrow ActivityNotFoundException()
    }

    @Test
    fun `exact alarm openSettings returns true on success`() {
        assertTrue(ExactAlarmPermissionHelper.openSettings(mock()))
    }

    @Test
    fun `exact alarm openSettings returns false when launch throws`() {
        assertFalse(ExactAlarmPermissionHelper.openSettings(throwingContext()))
    }

    @Test
    @Config(sdk = [33])
    fun `full screen intent openSettings is noop-true below API 34`() {
        assertTrue(FullScreenIntentPermissionHelper.openSettings(throwingContext()))
    }

    @Test
    @Config(sdk = [34])
    fun `full screen intent openSettings returns false when launch throws on API 34`() {
        assertFalse(FullScreenIntentPermissionHelper.openSettings(throwingContext()))
    }

    @Test
    @Config(sdk = [34])
    fun `full screen intent openSettings returns true on success on API 34`() {
        assertTrue(FullScreenIntentPermissionHelper.openSettings(mock()))
    }
}
