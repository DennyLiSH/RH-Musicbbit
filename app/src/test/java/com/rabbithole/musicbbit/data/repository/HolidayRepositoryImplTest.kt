package com.rabbithole.musicbbit.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import com.rabbithole.musicbbit.data.local.dao.HolidayDao
import com.rabbithole.musicbbit.data.local.model.HolidayEntity
import com.rabbithole.musicbbit.data.remote.api.HolidayApi
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argThat
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.doThrow
import org.mockito.kotlin.mock
import org.mockito.kotlin.verifyBlocking
import org.mockito.kotlin.whenever
import org.mockito.kotlin.wheneverBlocking

@OptIn(ExperimentalCoroutinesApi::class)
class HolidayRepositoryImplTest {

    private val holidayDao: HolidayDao = mock()
    private val holidayApi: HolidayApi = mock()
    private val json: Json = Json { ignoreUnknownKeys = true }
    private val context: Context = mock()
    private val dataStore: DataStore<Preferences> = mock()
    private val testDispatcher = UnconfinedTestDispatcher()
    private lateinit var repository: HolidayRepositoryImpl

    @Before
    fun setup() {
        whenever(dataStore.data).thenReturn(emptyFlow())
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
        wheneverBlocking { holidayDao.getHolidayByDate("2026-01-01") } doReturn holidayEntity(
            date = "2026-01-01", isHoliday = true, name = "元旦"
        )

        val result = repository.isWorkday("2026-01-01")

        assertFalse(result)
    }

    @Test
    fun `isWorkday returns true for adjusted workday from cache`() = runTest(testDispatcher) {
        // Adjusted workday: isHoliday=false means it's a workday even on weekend
        wheneverBlocking { holidayDao.getHolidayByDate("2026-01-04") } doReturn holidayEntity(
            date = "2026-01-04", isHoliday = false, name = "调休上班"
        )

        val result = repository.isWorkday("2026-01-04")

        assertTrue(result)
    }

    @Test
    fun `isWorkday returns false for weekend with no data`() = runTest(testDispatcher) {
        // 2026-01-03 is a Saturday, no cache, no fallback (relaxed context -> empty assets)
        wheneverBlocking { holidayDao.getHolidayByDate("2026-01-03") } doReturn null

        val result = repository.isWorkday("2026-01-03")

        assertFalse(result)
    }

    @Test
    fun `isWorkday returns true for weekday with no data`() = runTest(testDispatcher) {
        // 2026-01-05 is a Monday, no cache, no fallback
        wheneverBlocking { holidayDao.getHolidayByDate("2026-01-05") } doReturn null

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
        wheneverBlocking { holidayApi.getHolidaysForYear(2026) } doReturn responseJson
        wheneverBlocking { holidayDao.deleteByYear(2026) } doReturn Unit
        wheneverBlocking { holidayDao.insertAll(any()) } doReturn Unit

        val result = repository.refreshHolidays(2026)

        assertTrue(result.isSuccess)
        verifyBlocking(holidayDao) { deleteByYear(2026) }
        verifyBlocking(holidayDao) {
            insertAll(argThat { size == 1 && this[0].date == "2026-01-01" && this[0].isHoliday })
        }
    }

    @Test
    fun `refreshHolidays with non-zero api code returns failure and skips dao`() = runTest(testDispatcher) {
        wheneverBlocking { holidayApi.getHolidaysForYear(2026) } doReturn """{"code":1,"holiday":{}}"""

        val result = repository.refreshHolidays(2026)

        assertTrue(result.isFailure)
        verifyBlocking(holidayDao, org.mockito.Mockito.never()) { deleteByYear(any()) }
        verifyBlocking(holidayDao, org.mockito.Mockito.never()) { insertAll(any()) }
    }

    @Test
    fun `refreshHolidays network exception returns failure`() = runTest(testDispatcher) {
        wheneverBlocking { holidayApi.getHolidaysForYear(2026) } doThrow java.io.IOException("network")

        val result = repository.refreshHolidays(2026)

        assertTrue(result.isFailure)
    }

    @Test
    fun `maybeRefreshHolidays skips api when already called this month`() = runTest(testDispatcher) {
        val prefs = androidx.datastore.preferences.core.mutablePreferencesOf(
            com.rabbithole.musicbbit.data.local.datastore.SettingsKeys.LAST_HOLIDAY_API_CALL_MONTH to
                java.time.YearMonth.now().toString()
        )
        whenever(dataStore.data).thenReturn(kotlinx.coroutines.flow.MutableStateFlow(prefs))

        val result = repository.maybeRefreshHolidays(2026)

        assertTrue(result.isSuccess)
        verifyBlocking(holidayApi, org.mockito.Mockito.never()) { getHolidaysForYear(any()) }
    }

    @Test
    fun `maybeRefreshHolidays surfaces api failure as Result failure`() = runTest(testDispatcher) {
        // Empty prefs → no previous month record → proceeds to refresh
        whenever(dataStore.data).thenReturn(
            kotlinx.coroutines.flow.MutableStateFlow(
                androidx.datastore.preferences.core.mutablePreferencesOf()
            )
        )
        wheneverBlocking { holidayApi.getHolidaysForYear(2026) } doThrow java.io.IOException("network")

        val result = repository.maybeRefreshHolidays(2026)

        assertTrue(result.isFailure)
        verifyBlocking(holidayApi) { getHolidaysForYear(2026) }
    }
}
