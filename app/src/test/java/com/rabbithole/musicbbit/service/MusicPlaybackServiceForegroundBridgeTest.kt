package com.rabbithole.musicbbit.service

import android.app.Application
import dagger.hilt.android.testing.HiltTestApplication
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import timber.log.Timber

/**
 * Robolectric tests for [MusicPlaybackServiceForegroundBridge].
 *
 * Covers the duplicate-attach warning path. Bridge is a pure Kotlin @Singleton
 * but its `attach` parameter is an Android Service, so Robolectric is required
 * to construct MusicPlaybackService instances.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = HiltTestApplication::class, sdk = [33])
class MusicPlaybackServiceForegroundBridgeTest {

    private val bridge = MusicPlaybackServiceForegroundBridge()
    private val capturedLogs = mutableListOf<Pair<Int, String?>>()

    @Before
    fun setUp() {
        capturedLogs.clear()
        Timber.plant(object : Timber.Tree() {
            override fun log(priority: Int, tag: String?, message: String, t: Throwable?) {
                capturedLogs.add(priority to message)
            }
        })
    }

    @After
    fun tearDown() {
        Timber.uprootAll()
    }

    @Test
    fun `attach first service logs info`() {
        val service = buildService()

        bridge.attach(service)

        val infoLogs = capturedLogs.filter { it.first == android.util.Log.INFO }
        assertEquals("Expected one info log on first attach", 1, infoLogs.size)
    }

    @Test
    fun `attach same instance twice does not log warning`() {
        val service = buildService()

        bridge.attach(service)
        bridge.attach(service)

        val warnLogs = capturedLogs.filter { it.first == android.util.Log.WARN }
        assertEquals(
            "Re-attaching the same instance must not log warning",
            0,
            warnLogs.size,
        )
    }

    @Test
    fun `attach different instance overwrites and logs warning`() {
        val first = buildService()
        val second = buildService()

        bridge.attach(first)
        bridge.attach(second)

        val warnLogs = capturedLogs.filter { it.first == android.util.Log.WARN }
        assertEquals("Expected one warning log on overwrite", 1, warnLogs.size)
        val message = warnLogs.single().second
        assertEquals(
            "Warning message should mention both instances",
            true,
            message!!.contains(first.toString()) && message.contains(second.toString()),
        )

        val attachedField = bridge.javaClass.getDeclaredField("service").apply {
            isAccessible = true
        }.get(bridge)
        assertSame("Bridge should hold the most recent service", second, attachedField)
    }

    private fun buildService(): MusicPlaybackService =
        Robolectric.buildService(MusicPlaybackService::class.java).get()
}
