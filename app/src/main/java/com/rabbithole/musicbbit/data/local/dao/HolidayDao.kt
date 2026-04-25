package com.rabbithole.musicbbit.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.rabbithole.musicbbit.data.local.model.HolidayEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface HolidayDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(holidays: List<HolidayEntity>)

    @Query("SELECT * FROM holidays WHERE year = :year")
    fun getHolidaysForYear(year: Int): Flow<List<HolidayEntity>>

    @Query("SELECT * FROM holidays WHERE date = :date LIMIT 1")
    suspend fun getHolidayByDate(date: String): HolidayEntity?

    @Query("DELETE FROM holidays WHERE year = :year")
    suspend fun deleteByYear(year: Int)

    @Query("SELECT COUNT(*) FROM holidays WHERE year = :year")
    suspend fun countForYear(year: Int): Int
}
