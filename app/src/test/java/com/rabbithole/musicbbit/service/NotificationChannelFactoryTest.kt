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
import dagger.hilt.android.testing.HiltTestApplication
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = HiltTestApplication::class)
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

    @Test
    @Config(sdk = [33])
    fun `ensureChannel creates bypass channel with canBypassDnd true`() {
        factory.ensureChannel(
            channelId = "bypass_channel",
            nameRes = 0x7f000001,
            nameFallback = "Fallback Name",
            descRes = 0x7f000002,
            descFallback = "Fallback Desc",
            importance = NotificationManager.IMPORTANCE_HIGH,
            bypassDnd = true
        )

        val channel = notificationManager.getNotificationChannel("bypass_channel")
        assertNotNull("Bypass channel should be created", channel)
        assertTrue("Channel should bypass DND", channel!!.canBypassDnd())
    }

    @Test
    @Config(sdk = [33])
    fun `ensureChannel never deletes an existing channel`() {
        factory.ensureChannel(
            channelId = "stable_channel",
            nameRes = 0x7f000001,
            nameFallback = "Fallback Name",
            descRes = 0x7f000002,
            descFallback = "Fallback Desc",
            importance = NotificationManager.IMPORTANCE_HIGH,
            bypassDnd = true
        )
        val first = notificationManager.getNotificationChannel("stable_channel")

        factory.ensureChannel(
            channelId = "stable_channel",
            nameRes = 0x7f000001,
            nameFallback = "Fallback Name",
            descRes = 0x7f000002,
            descFallback = "Fallback Desc",
            importance = NotificationManager.IMPORTANCE_HIGH,
            bypassDnd = true
        )

        val second = notificationManager.getNotificationChannel("stable_channel")
        // createNotificationChannel is upsert semantics: a pure state check cannot
        // distinguish no-op from delete+recreate — assert on instance identity instead.
        org.junit.Assert.assertSame(
            "Re-calling ensureChannel must not delete/recreate the channel",
            first,
            second
        )
    }

    @Test
    @Config(sdk = [33])
    fun `bypassDnd cannot be flipped for an existing channel id`() {
        // Documents the platform constraint the two-channel design relies on: a deleted
        // channel re-created with the same id resurrects with its previous settings.
        factory.ensureChannel(
            channelId = "flip_channel",
            nameRes = 0x7f000001,
            nameFallback = "Fallback Name",
            descRes = 0x7f000002,
            descFallback = "Fallback Desc",
            importance = NotificationManager.IMPORTANCE_HIGH,
            bypassDnd = false
        )
        notificationManager.deleteNotificationChannel("flip_channel")

        factory.ensureChannel(
            channelId = "flip_channel",
            nameRes = 0x7f000001,
            nameFallback = "Fallback Name",
            descRes = 0x7f000002,
            descFallback = "Fallback Desc",
            importance = NotificationManager.IMPORTANCE_HIGH,
            bypassDnd = true
        )

        val channel = notificationManager.getNotificationChannel("flip_channel")
        assertNotNull("Channel should be resurrected", channel)
        assertEquals(
            "Resurrected channel keeps its original bypassDnd — use a different id instead",
            false,
            channel!!.canBypassDnd()
        )
    }
}
