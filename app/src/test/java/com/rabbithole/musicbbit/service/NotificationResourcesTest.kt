package com.rabbithole.musicbbit.service

import android.content.Context
import android.content.res.Resources
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class NotificationResourcesTest {

    private lateinit var mockContext: Context
    private lateinit var resources: NotificationResources

    @Before
    fun setUp() {
        mockContext = mockk()
        resources = NotificationResources(mockContext)
    }

    @Test
    fun `getString returns real value when resource resolves`() {
        every { mockContext.getString(42) } returns "real value"

        assertEquals("real value", resources.getString(42, "fallback"))
    }

    @Test
    fun `getString returns fallback when NotFoundException is thrown`() {
        every { mockContext.getString(42) } throws Resources.NotFoundException("missing")

        assertEquals("fallback", resources.getString(42, "fallback"))
    }

    @Test
    fun `getString fallback does not depend on locale`() {
        every { mockContext.getString(42) } throws Resources.NotFoundException("missing")

        assertEquals("English fallback", resources.getString(42, "English fallback"))
    }
}
