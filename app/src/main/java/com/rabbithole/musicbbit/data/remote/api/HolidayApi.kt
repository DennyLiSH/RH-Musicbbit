package com.rabbithole.musicbbit.data.remote.api

import com.rabbithole.musicbbit.data.remote.dto.HolidayResponseDto
import retrofit2.http.GET
import retrofit2.http.Path

/**
 * Retrofit interface for timor.tech Chinese holiday API.
 */
interface HolidayApi {

    /**
     * Fetch holiday data for a given year.
     *
     * @param year The year to fetch (e.g., 2026)
     * @return Holiday response containing all holidays and adjusted workdays
     */
    @GET("api/holiday/year/{year}")
    suspend fun getHolidaysForYear(@Path("year") year: Int): String
}
