package com.rabbithole.musicbbit.service

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import com.rabbithole.musicbbit.MainActivity
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
import org.robolectric.shadows.ShadowPendingIntent

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = HiltTestApplication::class)
class MainActivityIntentFactoryTest {

    private lateinit var context: Context
    private lateinit var factory: MainActivityIntentFactory

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        factory = MainActivityIntentFactory(context)
    }

    @Test
    fun `create returns non-null PendingIntent targeting MainActivity`() {
        val pendingIntent = factory.create()

        assertNotNull(pendingIntent)
        val savedIntent = shadowOf(pendingIntent).savedIntent
        assertEquals(MainActivity::class.java.name, savedIntent.component?.className)
    }

    @Test
    fun `create sets SINGLE_TOP flag so notification tap reuses existing task`() {
        val pendingIntent = factory.create()

        val savedIntent = shadowOf(pendingIntent).savedIntent
        assertTrue(
            "Intent should have FLAG_ACTIVITY_SINGLE_TOP",
            savedIntent.flags and Intent.FLAG_ACTIVITY_SINGLE_TOP != 0
        )
    }

    @Test
    fun `create uses FLAG_IMMUTABLE for security`() {
        val pendingIntent = factory.create()

        // Android 12+ rejects mutable PendingIntents — the shadow verifies the flag was set
        // at construction time so external callers cannot mutate the intent.
        assertTrue(
            "PendingIntent should be immutable",
            shadowOf(pendingIntent).isImmutable
        )
    }
}
