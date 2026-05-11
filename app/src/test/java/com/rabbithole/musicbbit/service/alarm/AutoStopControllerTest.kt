package com.rabbithole.musicbbit.service.alarm

import com.rabbithole.musicbbit.domain.model.AutoStop
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AutoStopControllerTest {

    @Test
    fun `ByMinutes timer fires after delay`() = runTest {
        val controller = AutoStopController(this.backgroundScope)
        var triggered = false

        controller.start(AutoStop.ByMinutes(1)) { triggered = true }
        assertFalse(triggered)

        advanceTimeBy(60_000L)
        runCurrent()
        assertTrue(triggered)
    }

    @Test
    fun `ByMinutes timer can be cancelled`() = runTest {
        val controller = AutoStopController(this.backgroundScope)
        var triggered = false

        controller.start(AutoStop.ByMinutes(1)) { triggered = true }
        controller.cancel()
        advanceTimeBy(60_000L)
        runCurrent()

        assertFalse(triggered)
    }

    @Test
    fun `extend cancels prior timer and starts fresh`() = runTest {
        val controller = AutoStopController(this.backgroundScope)
        var triggered = false

        controller.start(AutoStop.ByMinutes(10)) { triggered = true }
        advanceTimeBy(5 * 60_000L)
        controller.extend(20) { triggered = true }

        // Original 10-minute deadline should NOT trigger
        advanceTimeBy(6 * 60_000L)
        runCurrent()
        assertFalse(triggered)

        // Fresh 20-minute deadline should fire
        advanceTimeBy(15 * 60_000L)
        runCurrent()
        assertTrue(triggered)
    }

    @Test
    fun `extend is no-op when no timer in flight`() = runTest {
        val controller = AutoStopController(this.backgroundScope)
        var triggered = false

        controller.extend(5) { triggered = true }
        advanceTimeBy(10 * 60_000L)
        runCurrent()

        assertFalse(triggered)
    }

    @Test
    fun `BySongCount stops after N songs`() = runTest {
        val controller = AutoStopController(this.backgroundScope)

        controller.start(AutoStop.BySongCount(3)) {}

        assertFalse(controller.onSongCompleted())
        assertFalse(controller.onSongCompleted())
        assertTrue(controller.onSongCompleted())
    }

    @Test
    fun `onSongCompleted returns false when no counter active`() = runTest {
        val controller = AutoStopController(this.backgroundScope)

        assertFalse(controller.onSongCompleted())
    }

    @Test
    fun `onQueueEnded returns true when counter active`() = runTest {
        val controller = AutoStopController(this.backgroundScope)

        controller.start(AutoStop.BySongCount(5)) {}
        assertTrue(controller.onQueueEnded())
    }

    @Test
    fun `onQueueEnded returns false when no counter active`() = runTest {
        val controller = AutoStopController(this.backgroundScope)

        assertFalse(controller.onQueueEnded())
    }

    @Test
    fun `extendToEnd flag can be toggled`() = runTest {
        val controller = AutoStopController(this.backgroundScope)

        assertFalse(controller.isExtendToEnd())
        controller.setExtendToEnd(true)
        assertTrue(controller.isExtendToEnd())
        controller.setExtendToEnd(false)
        assertFalse(controller.isExtendToEnd())
    }

    @Test
    fun `reset clears all state`() = runTest {
        val controller = AutoStopController(this.backgroundScope)
        var triggered = false

        controller.start(AutoStop.ByMinutes(1)) { triggered = true }
        controller.setExtendToEnd(true)
        controller.reset()

        assertFalse(controller.isExtendToEnd())
        advanceTimeBy(60_000L)
        runCurrent()
        assertFalse(triggered)
    }
}
