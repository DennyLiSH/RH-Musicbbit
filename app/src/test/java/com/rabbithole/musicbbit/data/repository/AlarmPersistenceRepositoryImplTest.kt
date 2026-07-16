package com.rabbithole.musicbbit.data.repository

import com.rabbithole.musicbbit.data.local.dao.AlarmDao
import com.rabbithole.musicbbit.data.model.AlarmEntity
import com.rabbithole.musicbbit.domain.model.Alarm
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argThat
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.verifyBlocking
import org.mockito.kotlin.wheneverBlocking
import java.time.DayOfWeek

/**
 * Unit tests for [AlarmPersistenceRepositoryImpl] covering save + recordTriggered
 * (one-time vs repeating) + enableAlarm (non-existent id) branches.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class AlarmPersistenceRepositoryImplTest {

    private val alarmDao: AlarmDao = mock()
    private val testDispatcher = UnconfinedTestDispatcher()
    private lateinit var repository: AlarmPersistenceRepositoryImpl

    @Before
    fun setUp() {
        repository = AlarmPersistenceRepositoryImpl(alarmDao, testDispatcher)
    }

    private fun alarmEntity(
        id: Long = 1L,
        repeatDaysBitmask: Int = 0,
        isEnabled: Boolean = true,
    ) = AlarmEntity(
        id = id,
        hour = 7,
        minute = 30,
        repeatDaysBitmask = repeatDaysBitmask,
        excludeHolidays = false,
        playlistId = 10L,
        isEnabled = isEnabled,
        label = null,
        autoStop = null,
        lastTriggeredAt = null,
        resumePlayback = true,
        ringMode = "NORMAL"
    )

    private fun alarmDomain(id: Long = 1L, repeatDays: Set<DayOfWeek> = emptySet()) = Alarm(
        id = id,
        hour = 7,
        minute = 30,
        repeatDays = repeatDays,
        excludeHolidays = false,
        playlistId = 10L,
        isEnabled = true,
        label = null,
        autoStop = null,
        lastTriggeredAt = null,
        resumePlayback = true,
        ringMode = com.rabbithole.musicbbit.domain.model.AlarmRingMode.Normal,
    )

    @Test
    fun `save inserts entity and returns generated id`() = runTest(testDispatcher) {
        val alarm = alarmDomain(id = 0)
        wheneverBlocking { alarmDao.insert(any()) } doReturn 42L

        val id = repository.save(alarm)

        assertEquals(42L, id)
        verifyBlocking(alarmDao) { insert(argThat { hour == 7 && minute == 30 && playlistId == 10L }) }
    }

    @Test
    fun `recordTriggered for one-time alarm disables it`() = runTest(testDispatcher) {
        // repeatDaysBitmask=0 → one-time alarm
        wheneverBlocking { alarmDao.getById(7L) } doReturn alarmEntity(id = 7L, repeatDaysBitmask = 0, isEnabled = true)

        repository.recordTriggered(7L)

        verifyBlocking(alarmDao) {
            update(argThat { id == 7L && !isEnabled && lastTriggeredAt != null })
        }
    }

    @Test
    fun `recordTriggered for repeating alarm keeps enabled flag`() = runTest(testDispatcher) {
        // repeatDaysBitmask=0x1F → Mon-Fri
        wheneverBlocking { alarmDao.getById(8L) } doReturn alarmEntity(id = 8L, repeatDaysBitmask = 0x1F, isEnabled = true)

        repository.recordTriggered(8L)

        verifyBlocking(alarmDao) {
            update(argThat { id == 8L && isEnabled && lastTriggeredAt != null })
        }
    }

    @Test
    fun `enableAlarm on non-existent id is a silent no-op`() = runTest(testDispatcher) {
        wheneverBlocking { alarmDao.getById(99L) } doReturn null

        repository.enableAlarm(99L, true)

        verifyBlocking(alarmDao, org.mockito.Mockito.never()) { update(any()) }
    }
}
