package com.rabbithole.musicbbit.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import com.rabbithole.musicbbit.data.local.dao.HolidayDao
import com.rabbithole.musicbbit.data.local.model.HolidayEntity
import com.rabbithole.musicbbit.data.remote.api.HolidayApi
import com.rabbithole.musicbbit.data.remote.dto.HolidayEntryDto
import com.rabbithole.musicbbit.data.remote.dto.HolidayResponseDto
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class HolidayRepositoryImplTest {

    private val holidayDao: HolidayDao = mockk()
    private val holidayApi: HolidayApi = mockk()
    private val json: Json = Json { ignoreUnknownKeys = true }
    private val context: Context = mockk(relaxed = true)
    private val dataStore: DataStore<Preferences> = mockk(relaxed = true)
    private val testDispatcher = UnconfinedTestDispatcher()
    private lateinit var repository: HolidayRepositoryImpl

    @Before
    fun setup() {
        every { dataStore.data } returns emptyFlow()
        repository = HolidayRepositoryImpl(holidayDao, holidayApi, json, testDispatcher, context, dataStore)
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private fun holidayEntity(
        date: String = "2026-01-01",
        year: Int = 2026,
        name: String = "New Year",
        isHoliday: Boolean = true,
        fetchedAt: Long = 1000L
    ) = HolidayEntity(date = date, year = year, name = name, isHoliday = isHoliday, fetchedAt = fetchedAt)

    // ------------------------------------------------------------------
    // Tests
    // ------------------------------------------------------------------

    @Test
    fun `isWorkday returns false for holiday from cache`() = runTest(testDispatcher) {
        // 2026-01-01 is a Thursday (weekday)
        coEvery { holidayDao.getHolidayByDate("2026-01-01") } returns holidayEntity(
            date = "2026-01-01", isHoliday = true, name = "元旦"
        )

        val result = repository.isWorkday("2026-01-01")

        assertFalse(result)
    }

    @Test
    fun `isWorkday returns true for adjusted workday from cache`() = runTest(testDispatcher) {
        // Adjusted workday: isHoliday=false means it's a workday even on weekend
        coEvery { holidayDao.getHolidayByDate("2026-01-04") } returns holidayEntity(
            date = "2026-01-04", isHoliday = false, name = "调休上班"
        )

        val result = repository.isWorkday("2026-01-04")

        assertTrue(result)
    }

    @Test
    fun `isWorkday returns false for weekend with no data`() = runTest(testDispatcher) {
        // 2026-01-03 is a Saturday, no cache, no fallback (relaxed context -> empty assets)
        coEvery { holidayDao.getHolidayByDate("2026-01-03") } returns null

        val result = repository.isWorkday("2026-01-03")

        assertFalse(result)
    }

    @Test
    fun `isWorkday returns true for weekday with no data`() = runTest(testDispatcher) {
        // 2026-01-05 is a Monday, no cache, no fallback
        coEvery { holidayDao.getHolidayByDate("2026-01-05") } returns null

        val result = repository.isWorkday("2026-01-05")

        assertTrue(result)
    }

    @Test
    fun `isWorkday defaults to true for invalid date`() = runTest(testDispatcher) {
        val result = repository.isWorkday("not-a-date")

        assertTrue(result)
    }

    @Test
    fun `refreshHolidays success clears and inserts data`() = runTest(testDispatcher) {
        val responseJson = """{"code":0,"holiday":{"01-01":{"holiday":true,"name":"元旦","date":"2026-01-01","wage":3,"rest":1}}}"""
        coEvery { holidayApi.getHolidaysForYear(2026) } returns responseJson
        coEvery { holidayDao.deleteByYear(2026) } returns Unit
        coEvery { holidayDao.insertAll(any()) } returns Unit

        val result = repository.refreshHolidays(2026)

        assertTrue(result.isSuccess)
        coVerify { holidayDao.deleteByYear(2026) }
        coVerify { holidayDao.insertAll(match { it.size == 1 && it[0].date == "2026-01-01" && it[0].isHoliday }) }
    }
}
