package com.rabbithole.musicbbit.data.local.dao

import com.rabbithole.musicbbit.data.model.AlarmEntity
import kotlinx.coroutines.flow.first
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AlarmDaoTest : DatabaseTest() {

    private val dao by lazy { db.alarmDao() }

    @Test
    fun insert_returnsId() = dbTest {
        val alarm = AlarmEntity(
            hour = 7,
            minute = 30,
            repeatDaysBitmask = 0b0111110,
            playlistId = 1L,
            isEnabled = true,
            label = "Morning Alarm",
            autoStop = null,
            lastTriggeredAt = null
        )

        val id = dao.insert(alarm)

        assertTrue(id > 0)
    }

    @Test
    fun getById_returnsEntity_whenExists() = dbTest {
        val alarm = AlarmEntity(
            hour = 7,
            minute = 30,
            repeatDaysBitmask = 0b0111110,
            playlistId = 1L,
            isEnabled = true,
            label = "Morning Alarm",
            autoStop = null,
            lastTriggeredAt = null
        )
        val id = dao.insert(alarm)

        val result = dao.getById(id)

        assertNotNull(result)
        assertEquals(id, result?.id)
        assertEquals(7, result?.hour)
        assertEquals(30, result?.minute)
        assertEquals("Morning Alarm", result?.label)
    }

    @Test
    fun getById_returnsNull_whenNotExists() = dbTest {
        val result = dao.getById(999L)

        assertNull(result)
    }

    @Test
    fun getAll_emitsAlarms() = dbTest {
        val alarm1 = AlarmEntity(
            hour = 7,
            minute = 0,
            repeatDaysBitmask = 0,
            playlistId = 1L,
            isEnabled = true,
            label = null,
            autoStop = null,
            lastTriggeredAt = null
        )
        val alarm2 = AlarmEntity(
            hour = 8,
            minute = 0,
            repeatDaysBitmask = 0,
            playlistId = 2L,
            isEnabled = false,
            label = null,
            autoStop = null,
            lastTriggeredAt = null
        )
        dao.insert(alarm1)
        dao.insert(alarm2)

        val result = dao.getAll().first()

        assertEquals(2, result.size)
        assertTrue(result.any { it.hour == 7 })
        assertTrue(result.any { it.hour == 8 })
    }

    @Test
    fun getEnabledAlarms_filtersDisabled() = dbTest {
        val enabledAlarm = AlarmEntity(
            hour = 7,
            minute = 0,
            repeatDaysBitmask = 0,
            playlistId = 1L,
            isEnabled = true,
            label = null,
            autoStop = null,
            lastTriggeredAt = null
        )
        val disabledAlarm = AlarmEntity(
            hour = 8,
            minute = 0,
            repeatDaysBitmask = 0,
            playlistId = 2L,
            isEnabled = false,
            label = null,
            autoStop = null,
            lastTriggeredAt = null
        )
        dao.insert(enabledAlarm)
        dao.insert(disabledAlarm)

        val result = dao.getEnabledAlarms().first()

        assertEquals(1, result.size)
        assertTrue(result[0].isEnabled)
    }

    @Test
    fun update_modifiesEntity() = dbTest {
        val alarm = AlarmEntity(
            hour = 7,
            minute = 0,
            repeatDaysBitmask = 0,
            playlistId = 1L,
            isEnabled = true,
            label = "Old Label",
            autoStop = null,
            lastTriggeredAt = null
        )
        val id = dao.insert(alarm)
        val inserted = dao.getById(id)!!
        val updated = inserted.copy(label = "New Label", hour = 8)

        dao.update(updated)
        val result = dao.getById(id)

        assertEquals("New Label", result?.label)
        assertEquals(8, result?.hour)
    }

    @Test
    fun delete_removesEntity() = dbTest {
        val alarm = AlarmEntity(
            hour = 7,
            minute = 0,
            repeatDaysBitmask = 0,
            playlistId = 1L,
            isEnabled = true,
            label = null,
            autoStop = null,
            lastTriggeredAt = null
        )
        val id = dao.insert(alarm)
        val inserted = dao.getById(id)!!

        dao.delete(inserted)
        val result = dao.getById(id)

        assertNull(result)
    }
}
