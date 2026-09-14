package com.rabbithole.musicbbit.presentation.alarm

import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Locale

class AlarmListScreenFormatTimeTest {

    @Test
    fun `formats time with leading zeros`() {
        assertEquals("07:05", formatTime(7, 5))
    }

    @Test
    fun `uses latin digits regardless of default locale`() {
        val original = Locale.getDefault()
        Locale.setDefault(Locale.forLanguageTag("ar"))
        try {
            assertEquals("07:05", formatTime(7, 5))
        } finally {
            Locale.setDefault(original)
        }
    }
}
