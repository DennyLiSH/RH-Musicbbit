package com.rabbithole.musicbbit.service.alarm

import com.rabbithole.musicbbit.data.local.dao.AlarmDao
import com.rabbithole.musicbbit.data.model.AlarmEntity
import com.rabbithole.musicbbit.service.AlarmScheduler
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * JVM unit tests for [AlarmStartupReconciler].
 *
 * Covers the three reconciliation scenarios:
 *   - One-shot alarm that already triggered but is still enabled -> disable it
 *   - Repeating alarm -> reschedule unconditionally (idempotent)
 *   - One-shot alarm not yet triggered -> leave untouched
 *
 * All tests use [UnconfinedTestDispatcher] so the reconciler's internal scope
 * executes eagerly without needing virtual-time advances.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class AlarmStartupReconcilerTest {

    private val testDispatcher = UnconfinedTestDispatcher()

    private lateinit var fakeDao: FakeAlarmDao
    private lateinit var fakeScheduler: FakeAlarmScheduler
    private lateinit var reconciler: AlarmStartupReconciler

    @Before
    fun setUp() {
        fakeDao = FakeAlarmDao()
        fakeScheduler = FakeAlarmScheduler()
        reconciler = AlarmStartupReconciler(
            alarmDao = fakeDao,
            alarmScheduler = fakeScheduler,
            ioDispatcher = testDispatcher,
        )
    }

    @Test
    fun `one-shot alarm with lastTriggeredAt is disabled`() = runTest(testDispatcher) {
        val alarm = AlarmEntity(
            id = 1L,
            hour = 8,
            minute = 0,
            repeatDaysBitmask = 0,
            playlistId = 10L,
            isEnabled = true,
            label = "Test",
            autoStopMinutes = 10,
            lastTriggeredAt = FIXED_NOW_MS - 1000L,
        )
        fakeDao.upsert(alarm)

        reconciler.reconcileInternal()

        val updated = fakeDao.getById(1L)
        assertNotNull(updated)
        assertFalse(updated!!.isEnabled)
        assertEquals(0, fakeScheduler.scheduledIds.size)
    }

    @Test
    fun `repeating alarm is rescheduled`() = runTest(testDispatcher) {
        val alarm = AlarmEntity(
            id = 2L,
            hour = 9,
            minute = 0,
            repeatDaysBitmask = 0b1111111,
            playlistId = 10L,
            isEnabled = true,
            label = "Daily",
            autoStopMinutes = 10,
            lastTriggeredAt = null,
        )
        fakeDao.upsert(alarm)

        reconciler.reconcileInternal()

        assertEquals(listOf(2L), fakeScheduler.scheduledIds)
        val unchanged = fakeDao.getById(2L)
        assertNotNull(unchanged)
        assertTrue(unchanged!!.isEnabled)
    }

    @Test
    fun `one-shot alarm not yet triggered is untouched`() = runTest(testDispatcher) {
        val alarm = AlarmEntity(
            id = 3L,
            hour = 10,
            minute = 0,
            repeatDaysBitmask = 0,
            playlistId = 10L,
            isEnabled = true,
            label = "Future",
            autoStopMinutes = 10,
            lastTriggeredAt = null,
        )
        fakeDao.upsert(alarm)

        reconciler.reconcileInternal()

        val unchanged = fakeDao.getById(3L)
        assertNotNull(unchanged)
        assertTrue(unchanged!!.isEnabled)
        assertEquals(0, fakeScheduler.scheduledIds.size)
    }

    // -------------------------------------------------------------------------
    // Fakes
    // -------------------------------------------------------------------------

    private class FakeAlarmDao : AlarmDao {
        private val rows = mutableMapOf<Long, AlarmEntity>()

        fun upsert(alarm: AlarmEntity) {
            rows[alarm.id] = alarm
        }

        override suspend fun insert(alarm: AlarmEntity): Long {
            rows[alarm.id] = alarm
            return alarm.id
        }

        override suspend fun update(alarm: AlarmEntity) {
            rows[alarm.id] = alarm
        }

        override suspend fun delete(alarm: AlarmEntity) {
            rows.remove(alarm.id)
        }

        override fun getAll(): Flow<List<AlarmEntity>> =
            flowOf(rows.values.toList())

        override fun getEnabledAlarms(): Flow<List<AlarmEntity>> =
            flowOf(rows.values.filter { it.isEnabled })

        override suspend fun getById(id: Long): AlarmEntity? = rows[id]
    }

    private class FakeAlarmScheduler : AlarmScheduler {
        val scheduledIds = mutableListOf<Long>()
        val cancelledIds = mutableListOf<Long>()

        override suspend fun schedule(alarm: AlarmEntity) {
            scheduledIds += alarm.id
        }

        override fun cancel(alarmId: Long) {
            cancelledIds += alarmId
        }

        override suspend fun rescheduleAll(enabledAlarms: List<AlarmEntity>) {
            enabledAlarms.forEach { cancel(it.id) }
            enabledAlarms.forEach { schedule(it) }
        }

        override fun canScheduleExactAlarms(): Boolean = true
    }

    companion object {
        private const val FIXED_NOW_MS = 1_700_000_000_000L
    }
}
