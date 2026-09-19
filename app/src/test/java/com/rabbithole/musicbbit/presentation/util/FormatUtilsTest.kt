package com.rabbithole.musicbbit.presentation.util

import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Locale

class FormatUtilsTest {

    @Test
    fun `zero milliseconds returns 0 colon 00`() {
        assertEquals("0:00", formatDuration(0))
    }

    @Test
    fun `clock time formats with leading zeros`() {
        assertEquals("07:05", formatClockTime(7, 5))
    }

    @Test
    fun `clock time uses latin digits regardless of default locale`() {
        val original = Locale.getDefault()
        Locale.setDefault(Locale.forLanguageTag("ar"))
        try {
            assertEquals("07:05", formatClockTime(7, 5))
        } finally {
            Locale.setDefault(original)
        }
    }

    @Test
    fun `30 seconds returns 0 colon 30`() {
        assertEquals("0:30", formatDuration(30_000))
    }

    @Test
    fun `3 minutes 5 seconds returns 3 colon 05`() {
        assertEquals("3:05", formatDuration(185_000))
    }

    @Test
    fun `60 minutes 59 seconds returns 60 colon 59`() {
        assertEquals("60:59", formatDuration(3_659_000))
    }

    @Test
    fun `exactly 60 minutes returns 60 colon 00`() {
        assertEquals("60:00", formatDuration(3_600_000))
    }

    @Test
    fun `negative value clamped to 0 colon 00`() {
        assertEquals("0:00", formatDuration(-1))
    }
}
