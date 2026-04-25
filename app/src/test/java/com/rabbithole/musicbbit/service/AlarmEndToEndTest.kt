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
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.Shadows.shadowOf

/**
 * Service-layer end-to-end tests for the alarm trigger flow.
 *
 * Bypasses UI to test: AlarmReceiver -> MusicPlaybackService start.
 * Uses FakeAlarmDao to avoid MockK coEvery compilation issues under
 * Kotlin 2.2 + Robolectric.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class AlarmEndToEndTest {

    @Test
    fun `alarm trigger starts service and runs full flow`() {
        val context = RuntimeEnvironment.getApplication()
        val alarmId = 1L
        val intent = Intent(context, AlarmReceiver::class.java).apply {
            putExtra(AlarmScheduler.EXTRA_ALARM_ID, alarmId)
        }

        val alarmDao = FakeAlarmDao()
        alarmDao.add(
            createAlarmEntity(
                id = alarmId,
                repeatDaysBitmask = 0b1111111
            )
        )
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

    @Test
    fun `alarm trigger with disabled alarm only starts service`() {
        val context = RuntimeEnvironment.getApplication()
        val alarmId = 2L
        val intent = Intent(context, AlarmReceiver::class.java).apply {
            putExtra(AlarmScheduler.EXTRA_ALARM_ID, alarmId)
        }

        val alarmDao = FakeAlarmDao()
        alarmDao.add(
            createAlarmEntity(
                id = alarmId,
                isEnabled = false
            )
        )
        val alarmScheduler: AlarmScheduler = mockk(relaxed = true)

        val receiver = AlarmReceiver()
        injectMocks(receiver, alarmDao, alarmScheduler)
        receiver.onReceive(context, intent)

        Thread.sleep(300)

        val shadowApp = shadowOf(context)
        val startedService = shadowApp.peekNextStartedService()
        assertNotNull("Expected service to be started even for disabled alarm", startedService)

        assertEquals(0, alarmDao.updateCount)
    }

    @Test
    fun `one-time alarm trigger disables alarm and starts service`() {
        val context = RuntimeEnvironment.getApplication()
        val alarmId = 3L
        val intent = Intent(context, AlarmReceiver::class.java).apply {
            putExtra(AlarmScheduler.EXTRA_ALARM_ID, alarmId)
        }

        val alarmDao = FakeAlarmDao()
        alarmDao.add(
            createAlarmEntity(
                id = alarmId,
                repeatDaysBitmask = 0,
                isEnabled = true
            )
        )
        val alarmScheduler: AlarmScheduler = mockk(relaxed = true)

        val receiver = AlarmReceiver()
        injectMocks(receiver, alarmDao, alarmScheduler)
        receiver.onReceive(context, intent)

        Thread.sleep(300)

        val shadowApp = shadowOf(context)
        val startedService = shadowApp.peekNextStartedService()
        assertNotNull("Expected service to be started for one-time alarm", startedService)

        assertEquals(1, alarmDao.updateCount)
        val updated = alarmDao.getById(alarmId)
        assertNotNull(updated)
        assertEquals(false, updated!!.isEnabled)
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

    private class FakeAlarmDao : AlarmDao {
        private val data = mutableMapOf<Long, AlarmEntity>()
        private var nextId = 1L
        var updateCount = 0

        fun add(entity: AlarmEntity) {
            data[entity.id] = entity
        }

        override suspend fun getById(id: Long): AlarmEntity? = data[id]

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
