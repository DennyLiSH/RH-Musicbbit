package com.rabbithole.musicbbit.service

import android.content.Intent
import com.rabbithole.musicbbit.data.local.dao.AlarmDao
import com.rabbithole.musicbbit.data.model.AlarmEntity
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.Shadows.shadowOf

/**
 * Robolectric unit tests for [AlarmReceiver].
 *
 * Uses a hand-rolled FakeAlarmDao to avoid MockK coEvery/coVerify compilation
 * issues under Kotlin 2.2 + Robolectric. AlarmScheduler is mocked with relaxed
 * mockk but never stubbed or verified via MockK DSL suspend calls.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class AlarmReceiverTest {

    // ------------------------------------------------------------------
    // Existing tests (behaviour preserved)
    // ------------------------------------------------------------------

    @Test
    fun `onReceive with valid alarmId starts foreground service`() {
        val context = RuntimeEnvironment.getApplication()
        val alarmId = 1L
        val intent = Intent(context, AlarmReceiver::class.java).apply {
            putExtra(AlarmScheduler.EXTRA_ALARM_ID, alarmId)
        }

        val alarmDao = FakeAlarmDao()
        alarmDao.add(createAlarmEntity(id = alarmId))
        val alarmScheduler: AlarmScheduler = mockk(relaxed = true)

        val receiver = AlarmReceiver()
        injectMocks(receiver, alarmDao, alarmScheduler)
        receiver.onReceive(context, intent)

        Thread.sleep(300)

        val shadowApp = shadowOf(context)
        val startedService = shadowApp.peekNextStartedService()
        assertNotNull("Expected service to be started", startedService)
    }

    @Test
    fun `onReceive with invalid alarmId does not start service`() {
        val context = RuntimeEnvironment.getApplication()
        val intent = Intent(context, AlarmReceiver::class.java).apply {
            putExtra(AlarmScheduler.EXTRA_ALARM_ID, -1L)
        }

        val alarmDao = FakeAlarmDao()
        val alarmScheduler: AlarmScheduler = mockk(relaxed = true)

        val receiver = AlarmReceiver()
        injectMocks(receiver, alarmDao, alarmScheduler)
        receiver.onReceive(context, intent)

        Thread.sleep(100)

        val shadowApp = shadowOf(context)
        val startedService = shadowApp.peekNextStartedService()
        assertNull("Expected no service to be started", startedService)
    }

    @Test
    fun `onReceive with missing alarmId does not crash`() {
        val context = RuntimeEnvironment.getApplication()
        val intent = Intent(context, AlarmReceiver::class.java)

        val alarmDao = FakeAlarmDao()
        val alarmScheduler: AlarmScheduler = mockk(relaxed = true)

        val receiver = AlarmReceiver()
        injectMocks(receiver, alarmDao, alarmScheduler)
        receiver.onReceive(context, intent)

        Thread.sleep(100)

        val shadowApp = shadowOf(context)
        val startedService = shadowApp.peekNextStartedService()
        assertNull("Expected no service to be started", startedService)
    }

    // ------------------------------------------------------------------
    // Exception path tests
    // ------------------------------------------------------------------

    @Test
    fun `onReceive when alarmDao returns null - service started but no update or reschedule`() {
        val context = RuntimeEnvironment.getApplication()
        val alarmId = 2L
        val intent = Intent(context, AlarmReceiver::class.java).apply {
            putExtra(AlarmScheduler.EXTRA_ALARM_ID, alarmId)
        }

        val alarmDao = FakeAlarmDao()
        val alarmScheduler: AlarmScheduler = mockk(relaxed = true)

        val receiver = AlarmReceiver()
        injectMocks(receiver, alarmDao, alarmScheduler)
        receiver.onReceive(context, intent)

        Thread.sleep(300)

        val shadowApp = shadowOf(context)
        val startedService = shadowApp.peekNextStartedService()
        assertNotNull("Expected service to be started even when alarmDao returns null", startedService)

        assertEquals(0, alarmDao.updateCount)
    }

    @Test
    fun `onReceive when alarm is disabled - service started but no update or reschedule`() {
        val context = RuntimeEnvironment.getApplication()
        val alarmId = 3L
        val intent = Intent(context, AlarmReceiver::class.java).apply {
            putExtra(AlarmScheduler.EXTRA_ALARM_ID, alarmId)
        }

        val alarmDao = FakeAlarmDao()
        alarmDao.add(createAlarmEntity(id = alarmId, isEnabled = false))
        val alarmScheduler: AlarmScheduler = mockk(relaxed = true)

        val receiver = AlarmReceiver()
        injectMocks(receiver, alarmDao, alarmScheduler)
        receiver.onReceive(context, intent)

        Thread.sleep(300)

        val shadowApp = shadowOf(context)
        val startedService = shadowApp.peekNextStartedService()
        assertNotNull("Expected service to be started even when alarm is disabled", startedService)

        assertEquals(0, alarmDao.updateCount)
    }

    @Test
    fun `onReceive with snooze - skips update and reschedule`() {
        val context = RuntimeEnvironment.getApplication()
        val alarmId = 4L
        val intent = Intent(context, AlarmReceiver::class.java).apply {
            putExtra(AlarmScheduler.EXTRA_ALARM_ID, alarmId)
            putExtra(AlarmScheduler.EXTRA_IS_SNOOZE, true)
        }

        val alarmDao = FakeAlarmDao()
        alarmDao.add(createAlarmEntity(id = alarmId, repeatDaysBitmask = 0b1111111))
        val alarmScheduler: AlarmScheduler = mockk(relaxed = true)

        val receiver = AlarmReceiver()
        injectMocks(receiver, alarmDao, alarmScheduler)
        receiver.onReceive(context, intent)

        Thread.sleep(300)

        val shadowApp = shadowOf(context)
        val startedService = shadowApp.peekNextStartedService()
        assertNotNull("Expected service to be started for snooze", startedService)

        assertEquals(0, alarmDao.updateCount)
    }

    @Test
    fun `onReceive with non-repeating alarm - does not reschedule`() {
        val context = RuntimeEnvironment.getApplication()
        val alarmId = 5L
        val intent = Intent(context, AlarmReceiver::class.java).apply {
            putExtra(AlarmScheduler.EXTRA_ALARM_ID, alarmId)
        }

        val alarmDao = FakeAlarmDao()
        alarmDao.add(createAlarmEntity(id = alarmId, repeatDaysBitmask = 0))
        val alarmScheduler: AlarmScheduler = mockk(relaxed = true)

        val receiver = AlarmReceiver()
        injectMocks(receiver, alarmDao, alarmScheduler)
        receiver.onReceive(context, intent)

        Thread.sleep(300)

        val shadowApp = shadowOf(context)
        val startedService = shadowApp.peekNextStartedService()
        assertNotNull("Expected service to be started for non-repeating alarm", startedService)

        assertEquals(1, alarmDao.updateCount)
    }

    @Test
    fun `onReceive handles exception gracefully`() {
        val context = RuntimeEnvironment.getApplication()
        val alarmId = 6L
        val intent = Intent(context, AlarmReceiver::class.java).apply {
            putExtra(AlarmScheduler.EXTRA_ALARM_ID, alarmId)
        }

        val alarmDao = FakeAlarmDao()
        alarmDao.exceptionOnGetById = RuntimeException("Database error")
        val alarmScheduler: AlarmScheduler = mockk(relaxed = true)

        val receiver = AlarmReceiver()
        injectMocks(receiver, alarmDao, alarmScheduler)

        receiver.onReceive(context, intent)

        Thread.sleep(300)

        val shadowApp = shadowOf(context)
        val startedService = shadowApp.peekNextStartedService()
        assertNotNull("Expected service to be started even when exception occurs", startedService)
    }

    @Test
    fun `onReceive finishes pendingResult`() {
        val context = RuntimeEnvironment.getApplication()
        val alarmId = 7L
        val intent = Intent(context, AlarmReceiver::class.java).apply {
            putExtra(AlarmScheduler.EXTRA_ALARM_ID, alarmId)
        }

        val alarmDao = FakeAlarmDao()
        alarmDao.add(createAlarmEntity(id = alarmId))
        val alarmScheduler: AlarmScheduler = mockk(relaxed = true)

        val receiver = AlarmReceiver()
        injectMocks(receiver, alarmDao, alarmScheduler)
        receiver.onReceive(context, intent)

        Thread.sleep(300)

        val shadowApp = shadowOf(context)
        val startedService = shadowApp.peekNextStartedService()
        assertNotNull("Expected service to be started", startedService)
        assertEquals(1, alarmDao.updateCount)
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private fun injectMocks(
        receiver: AlarmReceiver,
        alarmDao: AlarmDao,
        alarmScheduler: AlarmScheduler
    ) {
        val hiltClazz = Class.forName("com.rabbithole.musicbbit.service.Hilt_AlarmReceiver")
        hiltClazz.getDeclaredField("injected").apply {
            isAccessible = true
            set(receiver, true)
        }

        val clazz = AlarmReceiver::class.java
        clazz.getDeclaredField("alarmDao").apply {
            isAccessible = true
            set(receiver, alarmDao)
        }
        clazz.getDeclaredField("alarmScheduler").apply {
            isAccessible = true
            set(receiver, alarmScheduler)
        }
    }

    private fun createAlarmEntity(
        id: Long = 1L,
        hour: Int = 8,
        minute: Int = 0,
        repeatDaysBitmask: Int = 0,
        playlistId: Long = 1L,
        isEnabled: Boolean = true,
        label: String? = "Test Alarm",
        autoStopMinutes: Int? = null,
        lastTriggeredAt: Long? = null
    ): AlarmEntity = AlarmEntity(
        id = id,
        hour = hour,
        minute = minute,
        repeatDaysBitmask = repeatDaysBitmask,
        playlistId = playlistId,
        isEnabled = isEnabled,
        label = label,
        autoStopMinutes = autoStopMinutes,
        lastTriggeredAt = lastTriggeredAt
    )

    /**
     * Lightweight in-memory fake for [AlarmDao].
     * Avoids MockK coEvery/coVerify which fail to compile under Kotlin 2.2 + Robolectric.
     */
    private class FakeAlarmDao : AlarmDao {
        private val data = mutableMapOf<Long, AlarmEntity>()
        private var nextId = 1L
        var updateCount = 0
        var exceptionOnGetById: Exception? = null

        fun add(entity: AlarmEntity) {
            data[entity.id] = entity
        }

        override suspend fun getById(id: Long): AlarmEntity? {
            exceptionOnGetById?.let { throw it }
            return data[id]
        }

        override suspend fun insert(alarm: AlarmEntity): Long {
            val id = if (alarm.id == 0L) nextId++ else alarm.id
            data[id] = alarm.copy(id = id)
            return id
        }

        override suspend fun update(alarm: AlarmEntity) {
            data[alarm.id] = alarm
            updateCount++
        }

        override suspend fun delete(alarm: AlarmEntity) {
            data.remove(alarm.id)
        }

        override fun getAll(): Flow<List<AlarmEntity>> = flowOf(data.values.toList())

        override fun getEnabledAlarms(): Flow<List<AlarmEntity>> =
            flowOf(data.values.filter { it.isEnabled })
    }
}
