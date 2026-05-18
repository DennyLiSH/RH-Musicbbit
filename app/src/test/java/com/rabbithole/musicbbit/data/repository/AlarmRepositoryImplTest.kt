package com.rabbithole.musicbbit.data.repository

import app.cash.turbine.test
import com.rabbithole.musicbbit.data.local.dao.AlarmDao
import com.rabbithole.musicbbit.data.model.AlarmEntity
import com.rabbithole.musicbbit.domain.model.Alarm
import com.rabbithole.musicbbit.domain.model.AutoStop
import com.rabbithole.musicbbit.service.AlarmScheduler
import com.rabbithole.musicbbit.service.alarm.AlarmSchedulerCoordinator
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.DayOfWeek

@OptIn(ExperimentalCoroutinesApi::class)
class AlarmRepositoryImplTest {

    private val alarmDao: AlarmDao = mockk()
    private val alarmScheduler: AlarmScheduler = mockk(relaxed = true)
    private val testDispatcher = UnconfinedTestDispatcher()
    private lateinit var repository: AlarmRepositoryImpl

    @Before
    fun setup() {
        val persistence = AlarmPersistenceRepositoryImpl(alarmDao, testDispatcher)
        val coordinator = AlarmSchedulerCoordinator(alarmScheduler)
        repository = AlarmRepositoryImpl(persistence, coordinator, testDispatcher)
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private fun alarmEntity(
        id: Long = 1L,
        hour: Int = 7,
        minute: Int = 30,
        repeatDaysBitmask: Int = 0,
        excludeHolidays: Boolean = false,
        playlistId: Long = 10L,
        isEnabled: Boolean = true,
        label: String? = "Test Alarm",
        autoStop: String? = null,
        lastTriggeredAt: Long? = null
    ) = AlarmEntity(
        id = id,
        hour = hour,
        minute = minute,
        repeatDaysBitmask = repeatDaysBitmask,
        excludeHolidays = excludeHolidays,
        playlistId = playlistId,
        isEnabled = isEnabled,
        label = label,
        autoStop = autoStop,
        lastTriggeredAt = lastTriggeredAt
    )

    private fun alarmDomain(
        id: Long = 1L,
        hour: Int = 7,
        minute: Int = 30,
        repeatDays: Set<DayOfWeek> = emptySet(),
        excludeHolidays: Boolean = false,
        playlistId: Long = 10L,
        isEnabled: Boolean = true,
        label: String? = "Test Alarm",
        autoStop: AutoStop? = null,
        lastTriggeredAt: Long? = null
    ) = Alarm(
        id = id,
        hour = hour,
        minute = minute,
        repeatDays = repeatDays,
        excludeHolidays = excludeHolidays,
        playlistId = playlistId,
        isEnabled = isEnabled,
        label = label,
        autoStop = autoStop,
        lastTriggeredAt = lastTriggeredAt
    )

    // ------------------------------------------------------------------
    // Tests
    // ------------------------------------------------------------------

    @Test
    fun `saveAlarm inserts entity and schedules alarm`() = runTest(testDispatcher) {
        val alarm = alarmDomain(id = 0, hour = 8, minute = 0, playlistId = 5L)
        coEvery { alarmDao.insert(any()) } returns 42L

        val result = repository.saveAlarm(alarm)

        assertTrue(result.isSuccess)
        assertEquals(42L, result.getOrNull())
        coVerify { alarmDao.insert(match { it.hour == 8 && it.minute == 0 && it.playlistId == 5L }) }
        coVerify { alarmScheduler.schedule(match { it.id == 42L && it.hour == 8 }) }
    }

    @Test
    fun `updateAlarm updates entity and reschedules`() = runTest(testDispatcher) {
        val alarm = alarmDomain(id = 5L, hour = 9, minute = 15)
        coEvery { alarmDao.update(any()) } returns Unit

        val result = repository.updateAlarm(alarm)

        assertTrue(result.isSuccess)
        coVerify { alarmDao.update(match { it.id == 5L && it.hour == 9 && it.minute == 15 }) }
        coVerify { alarmScheduler.schedule(match { it.id == 5L }) }
    }

    @Test
    fun `deleteAlarm cancels and deletes`() = runTest(testDispatcher) {
        val alarm = alarmDomain(id = 3L)
        coEvery { alarmDao.delete(any()) } returns Unit

        val result = repository.deleteAlarm(alarm)

        assertTrue(result.isSuccess)
        verify { alarmScheduler.cancel(3L) }
        coVerify { alarmDao.delete(match { it.id == 3L }) }
    }

    @Test
    fun `enableAlarm updates and reschedules`() = runTest(testDispatcher) {
        val entity = alarmEntity(id = 7L, isEnabled = false)
        coEvery { alarmDao.getById(7L) } returns entity
        coEvery { alarmDao.update(any()) } returns Unit

        val result = repository.enableAlarm(7L, true)

        assertTrue(result.isSuccess)
        coVerify { alarmDao.update(match { it.id == 7L && it.isEnabled }) }
        coVerify { alarmScheduler.schedule(match { it.id == 7L && it.isEnabled }) }
    }

    @Test
    fun `enableAlarm no-op for non-existent alarm`() = runTest(testDispatcher) {
        coEvery { alarmDao.getById(99L) } returns null

        val result = repository.enableAlarm(99L, true)

        assertTrue(result.isSuccess)
        coVerify(exactly = 0) { alarmScheduler.schedule(any()) }
        coVerify(exactly = 0) { alarmDao.update(any()) }
    }

    @Test
    fun `getAllAlarms maps entities to domain`() = runTest(testDispatcher) {
        val entities = listOf(
            alarmEntity(id = 1L, hour = 7, minute = 0, repeatDaysBitmask = 0x1F, playlistId = 10L),
            alarmEntity(id = 2L, hour = 9, minute = 30, repeatDaysBitmask = 0, playlistId = 20L)
        )
        every { alarmDao.getAll() } returns flowOf(entities)

        repository.getAllAlarms().test {
            val alarms = awaitItem()
            assertEquals(2, alarms.size)
            assertEquals(7, alarms[0].hour)
            assertEquals(0, alarms[0].minute)
            assertEquals(5, alarms[0].repeatDays.size) // Mon-Fri
            assertEquals(10L, alarms[0].playlistId)
            assertEquals(9, alarms[1].hour)
            assertEquals(emptySet<DayOfWeek>(), alarms[1].repeatDays)
            awaitComplete()
        }
    }
}
