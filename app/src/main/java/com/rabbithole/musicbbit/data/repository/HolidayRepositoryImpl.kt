package com.rabbithole.musicbbit.data.repository

import android.content.Context
import com.rabbithole.musicbbit.data.local.dao.HolidayDao
import com.rabbithole.musicbbit.data.local.model.HolidayEntity
import com.rabbithole.musicbbit.data.remote.api.HolidayApi
import com.rabbithole.musicbbit.data.remote.dto.HolidayResponseDto
import com.rabbithole.musicbbit.di.IoDispatcher
import com.rabbithole.musicbbit.domain.model.Holiday
import com.rabbithole.musicbbit.domain.repository.HolidayRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import timber.log.Timber
import java.time.DayOfWeek
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class HolidayRepositoryImpl @Inject constructor(
    private val holidayDao: HolidayDao,
    private val holidayApi: HolidayApi,
    private val json: Json,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
    @ApplicationContext private val context: Context
) : HolidayRepository {

    private var fallbackCache: Map<String, HolidayEntity>? = null

    override fun getHolidaysForYear(year: Int): Flow<List<Holiday>> {
        return holidayDao.getHolidaysForYear(year)
            .map { entities -> entities.map { it.toDomain() } }
            .flowOn(ioDispatcher)
    }

    override suspend fun refreshHolidays(year: Int): Result<Unit> = withContext(ioDispatcher) {
        try {
            Timber.i("Refreshing holidays for year $year")
            val jsonString = holidayApi.getHolidaysForYear(year)
            val response = json.decodeFromString<HolidayResponseDto>(jsonString)

            if (response.code != 0) {
                Timber.w("Holiday API returned non-zero code: ${response.code}")
                return@withContext Result.failure(Exception("API error code: ${response.code}"))
            }

            val entities = response.holiday.map { (_, entry) ->
                HolidayEntity(
                    date = entry.date,
                    year = year,
                    name = entry.name,
                    isHoliday = entry.holiday,
                    fetchedAt = System.currentTimeMillis()
                )
            }

            holidayDao.deleteByYear(year)
            holidayDao.insertAll(entities)

            Timber.i("Saved ${entities.size} holiday records for year $year")
            Result.success(Unit)
        } catch (e: Exception) {
            Timber.e(e, "Failed to refresh holidays for year $year")
            Result.failure(e)
        }
    }

    override suspend fun isWorkday(date: String): Boolean = withContext(ioDispatcher) {
        val localDate = try {
            LocalDate.parse(date)
        } catch (e: Exception) {
            Timber.w("Invalid date format: $date")
            return@withContext true // Default to workday on parse error
        }

        val dayOfWeek = localDate.dayOfWeek
        val isWeekend = dayOfWeek == DayOfWeek.SATURDAY || dayOfWeek == DayOfWeek.SUNDAY

        // Check cached holiday data
        val holiday = holidayDao.getHolidayByDate(date)

        when {
            holiday != null -> {
                // If we have cached data, use it
                // isHoliday=true means day off, isHoliday=false means adjusted workday
                val result = !holiday.isHoliday
                Timber.d("Date $date from cache: isWorkday=$result (isHoliday=${holiday.isHoliday})")
                result
            }
            else -> {
                // No cached data: try fallback assets
                val fallback = loadFallbackHolidays()[date]
                if (fallback != null) {
                    val result = !fallback.isHoliday
                    Timber.d("Date $date from fallback: isWorkday=$result (isHoliday=${fallback.isHoliday})")
                    return@withContext result
                }

                // No fallback either: use weekend/weekday logic
                if (isWeekend) {
                    Timber.d("Date $date is weekend, no cache or fallback: isWorkday=false")
                    false
                } else {
                    Timber.d("Date $date is weekday, no cache or fallback: isWorkday=true")
                    true
                }
            }
        }
    }

    /**
     * Load built-in holiday fallback data from assets.
     * Result is cached to avoid re-reading the file on every call.
     */
    private fun loadFallbackHolidays(): Map<String, HolidayEntity> {
        fallbackCache?.let { return it }

        return try {
            context.assets.open("holidays_fallback.json").use { inputStream ->
                val jsonString = inputStream.bufferedReader().use { it.readText() }
                val response = json.decodeFromString<HolidayResponseDto>(jsonString)

                val entities = response.holiday.map { (_, entry) ->
                    val year = entry.date.substringBefore("-").toInt()
                    entry.date to HolidayEntity(
                        date = entry.date,
                        year = year,
                        name = entry.name,
                        isHoliday = entry.holiday,
                        fetchedAt = 0L // Built-in data has no fetch timestamp
                    )
                }.toMap()

                Timber.i("Loaded ${entities.size} fallback holiday records from assets")
                fallbackCache = entities
                entities
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to load fallback holiday data from assets")
            emptyMap()
        }
    }

    private fun HolidayEntity.toDomain(): Holiday = Holiday(
        date = date,
        year = year,
        name = name,
        isHoliday = isHoliday
    )
}
