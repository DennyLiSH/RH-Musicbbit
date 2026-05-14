package com.rabbithole.musicbbit.presentation.util

import org.junit.Assert.assertEquals
import org.junit.Test

class FormatUtilsTest {

    @Test
    fun `zero milliseconds returns 0 colon 00`() {
        assertEquals("0:00", formatDuration(0))
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
