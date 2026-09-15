package com.rabbithole.musicbbit.service

import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import com.rabbithole.musicbbit.service.alarm.AlarmRecovery
import dagger.hilt.android.testing.BindValue
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.mock
import org.mockito.kotlin.wheneverBlocking
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Smoke test for the receiver shell only: BOOT_COMPLETED → EntryPointAccessors
 * resolves AlarmRecovery from the (test) SingletonComponent and calls
 * rescheduleEnabledAlarms. Repair logic itself is covered by
 * AlarmStartupReconcilerTest / AlarmIntegrityWorkerTest.
 */
@HiltAndroidTest
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = HiltTestApplication::class)
class BootReceiverTest {

    @get:Rule
    val hiltRule = HiltAndroidRule(this)

    @BindValue
    @JvmField
    val alarmRecovery: AlarmRecovery = mock()

    private val called = CountDownLatch(1)

    @Before
    fun setUp() {
        hiltRule.inject()
        wheneverBlocking { alarmRecovery.rescheduleEnabledAlarms() } doAnswer {
            called.countDown()
            Result.success(0)
        }
    }

    @Test
    fun `boot completed triggers alarm recovery`() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val intent = Intent(Intent.ACTION_BOOT_COMPLETED)

        BootReceiver().onReceive(context, intent)

        val reached = called.await(5, TimeUnit.SECONDS)
        assert(reached) { "rescheduleEnabledAlarms was not invoked within 5s" }
    }

    @Test
    fun `non boot action is ignored`() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val intent = Intent(Intent.ACTION_POWER_CONNECTED)

        BootReceiver().onReceive(context, intent)

        // grace window：给潜在的异步误调用留出时间窗口，超时后仍无调用才判定忽略生效
        val unexpectedlyCalled = called.await(200, TimeUnit.MILLISECONDS)
        assert(!unexpectedlyCalled) { "non-boot action must not trigger alarm recovery" }
    }
}
