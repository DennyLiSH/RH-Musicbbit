package com.rabbithole.musicbbit.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * API response from timor.tech holiday endpoint.
 *
 * Example: https://timor.tech/api/holiday/year/2026
 *
 * Response format:
 * ```json
 * {
 *   "code": 0,
 *   "holiday": {
 *     "01-01": {
 *       "holiday": true,
 *       "name": "元旦",
 *       "wage": 3,
 *       "date": "2026-01-01",
 *       "rest": 1
 *     }
 *   }
 * }
 * ```
 */
@Serializable
data class HolidayResponseDto(
    val code: Int,
    val holiday: Map<String, HolidayEntryDto> = emptyMap()
)

@Serializable
data class HolidayEntryDto(
    val holiday: Boolean,
    val name: String,
    val wage: Int = 1,
    val date: String,
    @SerialName("rest")
    val restDays: Int = 1
)
