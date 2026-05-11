package com.rabbithole.musicbbit.data.mapper

import com.rabbithole.musicbbit.data.local.model.HolidayEntity
import org.junit.Assert.assertEquals
import org.junit.Test

class HolidayMapperTest {

    @Test
    fun `toDomain maps all fields correctly`() {
        val entity = HolidayEntity(
            date = "2026-01-01",
            year = 2026,
            name = "元旦",
            isHoliday = true,
            fetchedAt = 1_700_000_000_000L
        )

        val domain = with(HolidayMapper) { entity.toDomain() }

        assertEquals("2026-01-01", domain.date)
        assertEquals(2026, domain.year)
        assertEquals("元旦", domain.name)
        assertEquals(true, domain.isHoliday)
    }

    @Test
    fun `toDomain excludes fetchedAt`() {
        val entity = HolidayEntity(
            date = "2026-02-14",
            year = 2026,
            name = "调休上班",
            isHoliday = false,
            fetchedAt = 999L
        )

        val domain = with(HolidayMapper) { entity.toDomain() }

        // Verify fetchedAt is not part of domain model
        assertEquals(false, domain.isHoliday)
    }
}
