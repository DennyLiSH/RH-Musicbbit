package com.rabbithole.musicbbit.data.mapper

import com.rabbithole.musicbbit.data.model.AlarmEntity
import com.rabbithole.musicbbit.domain.model.Alarm
import com.rabbithole.musicbbit.domain.model.AutoStop
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.DayOfWeek

class AlarmMapperTest {

    @Test
    fun `toDomain maps all fields correctly`() {
        val entity = AlarmEntity(
            id = 1L,
            hour = 8,
            minute = 15,
            repeatDaysBitmask = 0b0010101, // Mon, Wed, Fri
            excludeHolidays = true,
            playlistId = 10L,
            isEnabled = true,
            label = "Work Alarm",
            autoStop = "MINUTES:20",
            lastTriggeredAt = 1_700_000_000_000L
        )

        val domain = with(AlarmMapper) { entity.toDomain() }

        assertEquals(1L, domain.id)
        assertEquals(8, domain.hour)
        assertEquals(15, domain.minute)
        assertEquals(setOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY, DayOfWeek.FRIDAY), domain.repeatDays)
        assertEquals(true, domain.excludeHolidays)
        assertEquals(10L, domain.playlistId)
        assertEquals(true, domain.isEnabled)
        assertEquals("Work Alarm", domain.label)
        assertEquals(AutoStop.ByMinutes(20), domain.autoStop)
        assertEquals(1_700_000_000_000L, domain.lastTriggeredAt)
    }

    @Test
    fun `toDomain maps null autoStop and label correctly`() {
        val entity = AlarmEntity(
            id = 2L,
            hour = 7,
            minute = 0,
            repeatDaysBitmask = 0,
            excludeHolidays = false,
            playlistId = 5L,
            isEnabled = false,
            label = null,
            autoStop = null,
            lastTriggeredAt = null
        )

        val domain = with(AlarmMapper) { entity.toDomain() }

        assertNull(domain.label)
        assertNull(domain.autoStop)
        assertNull(domain.lastTriggeredAt)
        assertEquals(emptySet<DayOfWeek>(), domain.repeatDays)
    }

    @Test
    fun `toEntity maps all fields correctly`() {
        val domain = Alarm(
            id = 1L,
            hour = 8,
            minute = 15,
            repeatDays = setOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY, DayOfWeek.FRIDAY),
            excludeHolidays = true,
            playlistId = 10L,
            isEnabled = true,
            label = "Work Alarm",
            autoStop = AutoStop.BySongCount(3),
            lastTriggeredAt = 1_700_000_000_000L
        )

        val entity = with(AlarmMapper) { domain.toEntity() }

        assertEquals(1L, entity.id)
        assertEquals(8, entity.hour)
        assertEquals(15, entity.minute)
        assertEquals(0b0010101, entity.repeatDaysBitmask)
        assertEquals(true, entity.excludeHolidays)
        assertEquals(10L, entity.playlistId)
        assertEquals(true, entity.isEnabled)
        assertEquals("Work Alarm", entity.label)
        assertEquals("SONGS:3", entity.autoStop)
        assertEquals(1_700_000_000_000L, entity.lastTriggeredAt)
    }

    @Test
    fun `roundtrip conversion preserves data`() {
        val original = Alarm(
            id = 42L,
            hour = 23,
            minute = 59,
            repeatDays = setOf(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY),
            excludeHolidays = false,
            playlistId = 99L,
            isEnabled = true,
            label = "Weekend",
            autoStop = AutoStop.ByMinutes(5),
            lastTriggeredAt = null
        )

        val entity = with(AlarmMapper) { original.toEntity() }
        val roundtrip = with(AlarmMapper) { entity.toDomain() }

        assertEquals(original, roundtrip)
    }
}
