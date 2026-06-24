package com.rabbithole.musicbbit.service

import android.app.NotificationManager
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
class NotificationChannelFactoryTest {

    private lateinit var context: Context
    private lateinit var notificationManager: NotificationManager
    private lateinit var factory: NotificationChannelFactory

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        notificationManager = context.getSystemService(NotificationManager::class.java)
        factory = NotificationChannelFactory(context, NotificationResources(context))
    }

    @Test
    @Config(sdk = [25])
    fun `ensureChannel is no-op below API 26`() {
        factory.ensureChannel(
            channelId = "test_channel",
            nameRes = 0x7f000001,
            nameFallback = "Fallback Name",
            descRes = 0x7f000002,
            descFallback = "Fallback Desc",
            importance = NotificationManager.IMPORTANCE_LOW
        )

        assertTrue(
            "No channel should be created below API 26",
            shadowOf(notificationManager).notificationChannels.isEmpty()
        )
    }

    @Test
    @Config(sdk = [33])
    fun `ensureChannel creates channel with caller-specified id and importance`() {
        factory.ensureChannel(
            channelId = "test_channel",
            nameRes = 0x7f000001,
            nameFallback = "Fallback Name",
            descRes = 0x7f000002,
            descFallback = "Fallback Desc",
            importance = NotificationManager.IMPORTANCE_HIGH
        )

        val channel = shadowOf(notificationManager).notificationChannels
            .find { it.id == "test_channel" }
        assertNotNull("Channel should be created", channel)
        channel!!
        assertEquals("test_channel", channel.id)
        assertEquals(NotificationManager.IMPORTANCE_HIGH, channel.importance)
    }

    @Test
    @Config(sdk = [33])
    fun `ensureChannel uses fallback when resources cannot resolve`() {
        // Robolectric's default resource loader cannot resolve the merged R class for
        // our process, so any non-trivial resId triggers NotFoundException — which is
        // exactly the path we want to exercise here.
        factory.ensureChannel(
            channelId = "fallback_channel",
            nameRes = 0x7f000001,
            nameFallback = "Fallback Name",
            descRes = 0x7f000002,
            descFallback = "Fallback Desc",
            importance = NotificationManager.IMPORTANCE_LOW
        )

        val channel = shadowOf(notificationManager).notificationChannels
            .find { it.id == "fallback_channel" }
        assertNotNull("Channel should still be created via fallback", channel)
        assertEquals("Fallback Name", channel!!.name)
        assertEquals("Fallback Desc", channel.description)
    }
}
