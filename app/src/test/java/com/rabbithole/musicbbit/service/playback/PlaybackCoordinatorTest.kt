package com.rabbithole.musicbbit.service.playback

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.mock

@OptIn(ExperimentalCoroutinesApi::class)
class PlaybackCoordinatorTest {

    private val dispatcher = UnconfinedTestDispatcher()
    private lateinit var playerPort: FakePlayerPort
    private lateinit var audioFocusPort: FakeAudioFocusPort
    private lateinit var coordinator: PlaybackCoordinator

    @Before
    fun setUp() {
        playerPort = FakePlayerPort()
        audioFocusPort = FakeAudioFocusPort()
        coordinator = PlaybackCoordinator(
            playerPort = playerPort,
            audioFocusPort = audioFocusPort,
            mainDispatcher = dispatcher,
        )
    }

    @Test
    fun `events route to active consumer`() = runTest(dispatcher) {
        val consumer = FakeConsumer()
        coordinator.activate(consumer)

        playerPort.emitEvent(PlayerEvent.PlaybackReady(durationMs = 200_000L))

        assertEquals(1, consumer.events.size)
        assertEquals(200_000L, (consumer.events[0] as PlayerEvent.PlaybackReady).durationMs)
    }

    @Test
    fun `focus callbacks route to active consumer`() = runTest(dispatcher) {
        val consumer = FakeConsumer()
        coordinator.activate(consumer)

        audioFocusPort.simulateFocusLoss()
        audioFocusPort.simulateFocusLossTransient()
        audioFocusPort.simulateFocusGain()

        assertTrue(consumer.focusLost)
        assertTrue(consumer.focusLostTransient)
        assertTrue(consumer.focusGained)
    }

    @Test
    fun `events are dropped when no consumer is active`() = runTest(dispatcher) {
        val consumer = FakeConsumer()
        coordinator.activate(consumer)
        coordinator.deactivate(consumer)

        playerPort.emitEvent(PlayerEvent.IsPlayingChanged(true))

        assertTrue(consumer.events.isEmpty())
    }

    @Test
    fun `deactivate from inactive consumer is no-op`() = runTest(dispatcher) {
        val active = FakeConsumer()
        val inactive = FakeConsumer()
        coordinator.activate(active)

        coordinator.deactivate(inactive)

        playerPort.emitEvent(PlayerEvent.IsPlayingChanged(true))
        assertTrue(active.events.isNotEmpty())
        assertTrue(inactive.events.isEmpty())
    }

    @Test
    fun `handoff switches event routing to new consumer`() = runTest(dispatcher) {
        val first = FakeConsumer()
        val second = FakeConsumer()
        coordinator.activate(first)
        coordinator.activate(second)

        playerPort.emitEvent(PlayerEvent.IsPlayingChanged(true))

        assertTrue(first.events.isEmpty())
        assertEquals(1, second.events.size)
    }

    @Test
    fun `focus callbacks route to new consumer after handoff`() = runTest(dispatcher) {
        val first = FakeConsumer()
        val second = FakeConsumer()
        coordinator.activate(first)
        coordinator.activate(second)

        audioFocusPort.simulateFocusLoss()

        assertFalse(first.focusLost)
        assertTrue(second.focusLost)
    }

    @Test
    fun `activate fires onDeactivated on previous consumer`() = runTest(dispatcher) {
        val first = FakeConsumer()
        val second = FakeConsumer()
        coordinator.activate(first)

        coordinator.activate(second)

        assertEquals(1, first.deactivatedCount)
        assertEquals(0, second.deactivatedCount)
    }

    @Test
    fun `activate does not fire onDeactivated when same consumer reactivates`() = runTest(dispatcher) {
        val consumer = FakeConsumer()
        coordinator.activate(consumer)

        coordinator.activate(consumer)

        assertEquals(0, consumer.deactivatedCount)
    }

    // -------- isOwnedByAnother ----------------------------------------------

    @Test
    fun `isOwnedByAnother is false when no consumer is active`() = runTest(dispatcher) {
        val consumer = FakeConsumer()
        assertFalse(coordinator.isOwnedByAnother(consumer))
    }

    @Test
    fun `isOwnedByAnother is false for the active consumer itself`() = runTest(dispatcher) {
        val consumer = FakeConsumer()
        coordinator.activate(consumer)
        assertFalse(coordinator.isOwnedByAnother(consumer))
    }

    @Test
    fun `isOwnedByAnother is true for a non-active consumer`() = runTest(dispatcher) {
        val active = FakeConsumer()
        val other = FakeConsumer()
        coordinator.activate(active)
        assertTrue(coordinator.isOwnedByAnother(other))
    }

    @Test
    fun `isOwnedByAnother returns to false after deactivate`() = runTest(dispatcher) {
        val active = FakeConsumer()
        val other = FakeConsumer()
        coordinator.activate(active)
        coordinator.deactivate(active)
        assertFalse(coordinator.isOwnedByAnother(other))
    }

    // -------- activeConsumer StateFlow ---------------------------------------

    @Test
    fun `activeConsumer is observable and reflects activate deactivate`() = runTest(dispatcher) {
        val consumer = FakeConsumer()
        assertNull(coordinator.activeConsumer.value)

        coordinator.activate(consumer)
        assertEquals(consumer, coordinator.activeConsumer.value)

        coordinator.deactivate(consumer)
        assertNull(coordinator.activeConsumer.value)
    }

    @Test
    fun `commandsBlocked on UserPlaybackSession flips with coordinator ownership`() = runTest(dispatcher) {
        // Two sessions share the same coordinator. The non-owning one must surface
        // commandsBlocked = true while the other owns the player.
        val playerPort = FakePlayerPort()
        val audioFocusPort = FakeAudioFocusPort()
        val progressRepo = com.rabbithole.musicbbit.service.alarm.FakeProgressRepository()
        val serviceStarter: ServiceStarter = mock()
        val sharedCoordinator = PlaybackCoordinator(
            playerPort = playerPort,
            audioFocusPort = audioFocusPort,
            mainDispatcher = dispatcher,
        )
        val user = UserPlaybackSession(
            playerPort = playerPort,
            audioStreamPort = FakeAudioStreamPort(),
            playbackProgressRepository = progressRepo,
            serviceStarter = serviceStarter,
            audioFocusPort = audioFocusPort,
            playbackCoordinator = sharedCoordinator,
            mainDispatcher = dispatcher,
        )
        val alarm = AlarmPlaybackSession(
            playerPort = playerPort,
            audioStreamPort = FakeAudioStreamPort(),
            playbackProgressRepository = progressRepo,
            audioFocusPort = audioFocusPort,
            serviceStarter = serviceStarter,
            playbackCoordinator = sharedCoordinator,
            mainDispatcher = dispatcher,
        )

        // Initially neither owns — neither session is blocked.
        assertFalse(user.commandsBlocked.value)

        sharedCoordinator.activate(alarm)
        assertTrue(user.commandsBlocked.value)

        sharedCoordinator.deactivate(alarm)
        assertFalse(user.commandsBlocked.value)

        user.close()
        alarm.close()
        sharedCoordinator.close()
    }

    private class FakeConsumer : PlaybackCoordinator.PlaybackConsumer {
        val events = mutableListOf<PlayerEvent>()
        var focusLost = false
        var focusLostTransient = false
        var focusGained = false
        var deactivatedCount = 0
            private set

        override fun onPlayerEvent(event: PlayerEvent) {
            events.add(event)
        }

        override fun onFocusLoss() {
            focusLost = true
        }

        override fun onFocusLossTransient() {
            focusLostTransient = true
        }

        override fun onFocusGain() {
            focusGained = true
        }

        override fun onDeactivated() {
            deactivatedCount++
        }
    }
}
