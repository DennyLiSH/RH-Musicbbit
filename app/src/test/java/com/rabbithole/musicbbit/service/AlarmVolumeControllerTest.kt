package com.rabbithole.musicbbit.service

import android.app.Application
import android.content.Context
import android.media.AudioManager
import androidx.test.core.app.ApplicationProvider
import com.rabbithole.musicbbit.domain.repository.AlarmRingSettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import dagger.hilt.android.testing.HiltTestApplication
import org.robolectric.annotation.Config

/**
 * Verifies [AlarmVolumeController] ramps the stream that matches the playback audio usage:
 * alarm stream when [useAlarmStream] is true (regression: previously always STREAM_MUSIC
 * while alarm playback outputs on STREAM_ALARM, making the ramp ineffective) and music
 * stream when false.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = HiltTestApplication::class)
@OptIn(ExperimentalCoroutinesApi::class)
class AlarmVolumeControllerTest {

    private val dispatcher = UnconfinedTestDispatcher()
    private lateinit var audioManager: AudioManager
    private lateinit var controller: AlarmVolumeController

    private class FakeRingSettingsRepository(var rampDurationSeconds: Int) : AlarmRingSettingsRepository {
        private val breathingEnabled = MutableStateFlow(true)
        private val breathingPeriodMs = MutableStateFlow(3000L)
        override fun isBreathingEnabled(): Flow<Boolean> = breathingEnabled
        override fun getBreathingPeriodMs(): Flow<Long> = breathingPeriodMs
        override suspend fun setBreathingEnabled(enabled: Boolean): Result<Unit> = Result.success(Unit)
        override suspend fun setBreathingPeriodMs(period: Long): Result<Unit> = Result.success(Unit)
        override fun getVolumeRampDurationSeconds(): Flow<Int> = MutableStateFlow(rampDurationSeconds)
        override suspend fun setVolumeRampDurationSeconds(seconds: Int): Result<Unit> = Result.success(Unit)
    }

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Application>()
        audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, 8, 0)
        audioManager.setStreamVolume(AudioManager.STREAM_ALARM, 3, 0)
    }

    private fun createController(rampDurationSeconds: Int): AlarmVolumeController =
        AlarmVolumeController(
            context = ApplicationProvider.getApplicationContext(),
            alarmRingSettingsRepository = FakeRingSettingsRepository(rampDurationSeconds),
        )

    @Test
    fun `ramp with useAlarmStream true targets STREAM_ALARM not STREAM_MUSIC`() = runTest(dispatcher) {
        controller = createController(rampDurationSeconds = 0)

        controller.startVolumeRamp(CoroutineScope(dispatcher), useAlarmStream = true)
        runCurrent()

        // duration=0: volume goes straight to the ALARM stream max; music stream untouched.
        val alarmMax = audioManager.getStreamMaxVolume(AudioManager.STREAM_ALARM)
        assertEquals(alarmMax, audioManager.getStreamVolume(AudioManager.STREAM_ALARM))
        assertEquals(8, audioManager.getStreamVolume(AudioManager.STREAM_MUSIC))
    }

    @Test
    fun `ramp with useAlarmStream false targets STREAM_MUSIC`() = runTest(dispatcher) {
        controller = createController(rampDurationSeconds = 0)

        controller.startVolumeRamp(CoroutineScope(dispatcher), useAlarmStream = false)
        runCurrent()

        assertEquals(
            audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC),
            audioManager.getStreamVolume(AudioManager.STREAM_MUSIC),
        )
        assertEquals(3, audioManager.getStreamVolume(AudioManager.STREAM_ALARM))
    }

    @Test
    fun `ramp starts low and steps up on the alarm stream`() = runTest(dispatcher) {
        controller = createController(rampDurationSeconds = 10)

        controller.startVolumeRamp(CoroutineScope(dispatcher), useAlarmStream = true)
        runCurrent()

        val alarmMax = audioManager.getStreamMaxVolume(AudioManager.STREAM_ALARM)
        val startVolume = (alarmMax * 0.3f).toInt().coerceAtLeast(1)
        assertEquals(startVolume, audioManager.getStreamVolume(AudioManager.STREAM_ALARM))

        advanceTimeBy(5_000L)
        runCurrent()
        val midRampVolume = audioManager.getStreamVolume(AudioManager.STREAM_ALARM)
        assertTrue(
            "volume should step up from $startVolume after half the ramp, was $midRampVolume",
            midRampVolume > startVolume,
        )

        controller.restoreVolume()
        assertEquals(3, audioManager.getStreamVolume(AudioManager.STREAM_ALARM))
    }

    @Test
    fun `restoreVolume restores original music stream volume after ramp`() = runTest(dispatcher) {
        controller = createController(rampDurationSeconds = 0)
        audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, 5, 0)

        controller.startVolumeRamp(CoroutineScope(dispatcher), useAlarmStream = false)
        runCurrent()
        assertEquals(
            audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC),
            audioManager.getStreamVolume(AudioManager.STREAM_MUSIC),
        )

        controller.restoreVolume()
        assertEquals(5, audioManager.getStreamVolume(AudioManager.STREAM_MUSIC))
    }

    @Test
    fun `user manual adjustment aborts ramp and is preserved by restore`() = runTest(dispatcher) {
        controller = createController(rampDurationSeconds = 10)

        controller.startVolumeRamp(CoroutineScope(dispatcher), useAlarmStream = true)
        runCurrent()

        // Simulate the user changing volume mid-ramp.
        audioManager.setStreamVolume(AudioManager.STREAM_ALARM, 7, 0)
        advanceTimeBy(1_000L)
        runCurrent()
        advanceTimeBy(1_000L)
        runCurrent()

        // Ramp aborted: user's 7 wins, further steps do not run.
        assertEquals(7, audioManager.getStreamVolume(AudioManager.STREAM_ALARM))

        controller.restoreVolume()
        assertEquals(7, audioManager.getStreamVolume(AudioManager.STREAM_ALARM))
    }
}
