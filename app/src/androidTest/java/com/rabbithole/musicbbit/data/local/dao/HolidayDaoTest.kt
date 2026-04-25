package com.rabbithole.musicbbit.data.local.dao

import com.rabbithole.musicbbit.data.local.model.HolidayEntity
import kotlinx.coroutines.flow.first
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HolidayDaoTest : DatabaseTest() {

    private val dao by lazy { db.holidayDao() }

    @Test
    fun insertAll_and_getHolidaysForYear() = dbTest {
        val holidays = listOf(
            HolidayEntity(
                date = "2026-01-01",
                year = 2026,
                name = "New Year's Day",
                isHoliday = true,
                fetchedAt = 1_700_000_000_000L
            ),
            HolidayEntity(
                date = "2026-02-17",
                year = 2026,
                name = "Spring Festival",
                isHoliday = true,
                fetchedAt = 1_700_000_000_000L
            )
        )

        dao.insertAll(holidays)
        val result = dao.getHolidaysForYear(2026).first()

        assertEquals(2, result.size)
    }

    @Test
    fun getHolidayByDate_returnsEntity_whenExists() = dbTest {
        val holiday = HolidayEntity(
            date = "2026-01-01",
            year = 2026,
            name = "New Year's Day",
            isHoliday = true,
            fetchedAt = 1_700_000_000_000L
        )
        dao.insertAll(listOf(holiday))

        val result = dao.getHolidayByDate("2026-01-01")

        assertNotNull(result)
        assertEquals("New Year's Day", result?.name)
        assertEquals(2026, result?.year)
        assertTrue(result?.isHoliday == true)
    }

    @Test
    fun getHolidayByDate_returnsNull_whenNotExists() = dbTest {
        val result = dao.getHolidayByDate("2026-12-31")

        assertNull(result)
    }

    @Test
    fun deleteByYear_removesHolidays() = dbTest {
        val holidays2026 = listOf(
            HolidayEntity(
                date = "2026-01-01",
                year = 2026,
                name = "New Year's Day",
                isHoliday = true,
                fetchedAt = 1_700_000_000_000L
            )
        )
        val holidays2027 = listOf(
            HolidayEntity(
                date = "2027-01-01",
                year = 2027,
                name = "New Year's Day",
                isHoliday = true,
                fetchedAt = 1_700_000_000_000L
            )
        )
        dao.insertAll(holidays2026)
        dao.insertAll(holidays2027)

        dao.deleteByYear(2026)
        val result2026 = dao.getHolidaysForYear(2026).first()
        val result2027 = dao.getHolidaysForYear(2027).first()

        assertTrue(result2026.isEmpty())
        assertEquals(1, result2027.size)
    }

    @Test
    fun countForYear_returnsCorrectCount() = dbTest {
        val holidays = listOf(
            HolidayEntity(
                date = "2026-01-01",
                year = 2026,
                name = "New Year's Day",
                isHoliday = true,
                fetchedAt = 1_700_000_000_000L
            ),
            HolidayEntity(
                date = "2026-02-17",
                year = 2026,
                name = "Spring Festival",
                isHoliday = true,
                fetchedAt = 1_700_000_000_000L
            ),
            HolidayEntity(
                date = "2026-02-18",
                year = 2026,
                name = "Spring Festival Holiday",
                isHoliday = true,
                fetchedAt = 1_700_000_000_000L
            )
        )
        dao.insertAll(holidays)

        val count = dao.countForYear(2026)

        assertEquals(3, count)
    }
}
