package com.rabbithole.musicbbit.service.alarm

import com.rabbithole.musicbbit.domain.model.Alarm
import com.rabbithole.musicbbit.domain.repository.AlarmRepository
import com.rabbithole.musicbbit.service.AlarmScheduler
import io.mockk.coVerify
import io.mockk.verify
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.BeforeClass
import org.junit.Test
import timber.log.Timber
import java.time.DayOfWeek

/**
 * JVM unit tests for [AlarmStartupReconciler].
 *
 * Covers the three reconciliation scenarios:
 *   - One-shot alarm that already triggered but is still enabled -> disable it
 *   - Repeating alarm -> reschedule unconditionally (idempotent)
 *   - One-shot alarm not yet triggered -> reschedule (PendingIntent may be lost)
 *
 * All tests use [UnconfinedTestDispatcher] so the reconciler's internal scope
 * executes eagerly without needing virtual-time advances.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class AlarmStartupReconcilerTest {

    companion object {
        private const val FIXED_NOW_MS = 1_700_000_000_000L

        @JvmStatic
        @BeforeClass
        fun plantTimber() {
            Timber.uprootAll()
            Timber.plant(object : Timber.Tree() {
                override fun log(priority: Int, tag: String?, message: String, t: Throwable?) {}
            })
        }
    }

    private val testDispatcher = UnconfinedTestDispatcher()

    private lateinit var fakeRepository: FakeAlarmRepository
    private lateinit var alarmScheduler: AlarmScheduler
    private lateinit var reconciler: AlarmStartupReconciler

    @Before
    fun setUp() {
        fakeRepository = FakeAlarmRepository()
        alarmScheduler = mockk(relaxed = true)
        reconciler = AlarmStartupReconciler(
            alarmRepository = fakeRepository,
            alarmScheduler = alarmScheduler,
            ioDispatcher = testDispatcher,
        )
    }

    @Test
    fun `one-shot alarm with lastTriggeredAt is disabled`() = runTest(testDispatcher) {
        val alarm = Alarm(
            id = 1L,
            hour = 8,
            minute = 0,
            repeatDays = emptySet(),
            playlistId = 10L,
            isEnabled = true,
            label = "Test",
            autoStop = null,
            lastTriggeredAt = FIXED_NOW_MS - 1000L,
        )
        fakeRepository.addAlarm(alarm)

        reconciler.reconcileInternal()

        val updated = fakeRepository.getAlarmById(1L)
        assertNotNull(updated)
        assertFalse(updated!!.isEnabled)
        coVerify(exactly = 0) { alarmScheduler.rescheduleAll(any()) }
    }

    @Test
    fun `repeating alarm is rescheduled`() = runTest(testDispatcher) {
        val alarm = Alarm(
            id = 2L,
            hour = 9,
            minute = 0,
            repeatDays = DayOfWeek.entries.toSet(),
            playlistId = 10L,
            isEnabled = true,
            label = "Daily",
            autoStop = null,
            lastTriggeredAt = null,
        )
        fakeRepository.addAlarm(alarm)

        reconciler.reconcileInternal()

        coVerify { alarmScheduler.rescheduleAll(any<List<Alarm>>()) }
        val unchanged = fakeRepository.getAlarmById(2L)
        assertNotNull(unchanged)
        assertTrue(unchanged!!.isEnabled)
    }

    @Test
    fun `one-shot alarm not yet triggered is rescheduled`() = runTest(testDispatcher) {
        val alarm = Alarm(
            id = 3L,
            hour = 10,
            minute = 0,
            repeatDays = emptySet(),
            playlistId = 10L,
            isEnabled = true,
            label = "Future",
            autoStop = null,
            lastTriggeredAt = null,
        )
        fakeRepository.addAlarm(alarm)

        reconciler.reconcileInternal()

        // Alarm should remain enabled and be rescheduled via schedule()
        val unchanged = fakeRepository.getAlarmById(3L)
        assertNotNull(unchanged)
        assertTrue(unchanged!!.isEnabled)
        coVerify { alarmScheduler.schedule(alarm) }
        coVerify(exactly = 0) { alarmScheduler.rescheduleAll(any()) }
    }

    @Test
    fun `reconcile calls scheduleIntegrityCheck`() = runTest(testDispatcher) {
        // No alarms needed — just verify scheduleIntegrityCheck() is called after reconcileInternal
        reconciler.reconcile()

        // With UnconfinedTestDispatcher the launched coroutine executes eagerly
        verify { alarmScheduler.scheduleIntegrityCheck() }
    }

    // -------------------------------------------------------------------------
    // Fakes
    // -------------------------------------------------------------------------

    private class FakeAlarmRepository : AlarmRepository {
        private val rows = mutableMapOf<Long, Alarm>()

        fun addAlarm(alarm: Alarm) {
            rows[alarm.id] = alarm
        }

        override fun getAllAlarms(): Flow<List<Alarm>> =
            flowOf(rows.values.toList())

        override fun getEnabledAlarms(): Flow<List<Alarm>> =
            flowOf(rows.values.filter { it.isEnabled })

        override suspend fun getAlarmById(id: Long): Alarm? = rows[id]

        override suspend fun saveAlarm(alarm: Alarm): Result<Long> {
            rows[alarm.id] = alarm
            return Result.success(alarm.id)
        }

        override suspend fun updateAlarm(alarm: Alarm): Result<Unit> {
            rows[alarm.id] = alarm
            return Result.success(Unit)
        }

        override suspend fun deleteAlarm(alarm: Alarm): Result<Unit> {
            rows.remove(alarm.id)
            return Result.success(Unit)
        }

        override suspend fun enableAlarm(id: Long, enabled: Boolean): Result<Unit> {
            rows[id]?.let { rows[id] = it.copy(isEnabled = enabled) }
            return Result.success(Unit)
        }

        override suspend fun recordTriggered(alarmId: Long): Result<Unit> {
            // Not used in these tests
            return Result.success(Unit)
        }
    }

}
