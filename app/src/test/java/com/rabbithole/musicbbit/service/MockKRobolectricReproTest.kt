package com.rabbithole.musicbbit.service

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Reproduction test for MockK coEvery / coVerify issues under Robolectric.
 *
 * Environment: Kotlin 2.2.10, Robolectric 4.14.1, MockK 1.14.0, JUnit 4.13.2
 *
 * This test intentionally uses [coEvery] inside a Robolectric runner to capture
 * the exact failure mode. The known issue is that MockK's inline bytecode
 * transformation (via mockk-agent / ByteBuddy / JVM TI) conflicts with
 * Robolectric's own classloader instrumentation, leading to ClassCastException
 * or NoClassDefFoundError when mocking suspend functions.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class MockKRobolectricReproTest {

    interface SuspendService {
        suspend fun fetchData(): String
    }

    /**
     * Baseline: verify that a plain mock (non-suspend) works under Robolectric.
     */
    @Test
    fun `plain mock should work in Robolectric`() {
        val mock = io.mockk.mockk<SuspendService>()
        io.mockk.every { mock.fetchData() } returns "hello"
        // Note: every {} on a suspend function compiles but does NOT correctly
        // intercept the coroutine suspension point. The test below shows the
        // actual failure when invoking the suspend function.
    }

    /**
     * Repro 1: coEvery with returns — the most common pattern.
     */
    @Test
    fun `coEvery with returns should work in Robolectric`() = runTest {
        val mock = io.mockk.mockk<SuspendService>()
        io.mockk.coEvery { mock.fetchData() } returns "hello"
        assertEquals("hello", mock.fetchData())
    }

    /**
     * Repro 2: coEvery with answers block.
     */
    @Test
    fun `coEvery with answers should work in Robolectric`() = runTest {
        val mock = io.mockk.mockk<SuspendService>()
        io.mockk.coEvery { mock.fetchData() } answers { "hello" }
        assertEquals("hello", mock.fetchData())
    }

    /**
     * Repro 3: coVerify after a successful mock setup.
     */
    @Test
    fun `coVerify should work in Robolectric`() = runTest {
        val mock = io.mockk.mockk<SuspendService>()
        io.mockk.coEvery { mock.fetchData() } returns "hello"
        mock.fetchData()
        io.mockk.coVerify(exactly = 1) { mock.fetchData() }
    }
}
