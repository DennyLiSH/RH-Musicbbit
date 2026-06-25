package com.rabbithole.musicbbit.service

import android.content.Context
import android.content.res.Resources
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class NotificationResourcesTest {

    private lateinit var mockContext: Context
    private lateinit var resources: NotificationResources

    @Before
    fun setUp() {
        mockContext = mock()
        resources = NotificationResources(mockContext)
    }

    @Test
    fun `getString returns real value when resource resolves`() {
        whenever(mockContext.getString(42)).thenReturn("real value")

        assertEquals("real value", resources.getString(42, "fallback"))
    }

    @Test
    fun `getString returns fallback when NotFoundException is thrown`() {
        whenever(mockContext.getString(42)).thenThrow(Resources.NotFoundException("missing"))

        assertEquals("fallback", resources.getString(42, "fallback"))
    }

    @Test
    fun `getString fallback does not depend on locale`() {
        whenever(mockContext.getString(42)).thenThrow(Resources.NotFoundException("missing"))

        assertEquals("English fallback", resources.getString(42, "English fallback"))
    }
}
