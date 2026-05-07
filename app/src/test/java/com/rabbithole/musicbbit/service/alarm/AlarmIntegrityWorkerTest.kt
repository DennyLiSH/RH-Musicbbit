package com.rabbithole.musicbbit.service.alarm

import android.content.Context
import androidx.work.WorkerParameters
import androidx.work.ListenableWorker
import com.rabbithole.musicbbit.domain.model.Alarm
import com.rabbithole.musicbbit.domain.repository.AlarmRepository
import com.rabbithole.musicbbit.service.AlarmScheduler
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.BeforeClass
import org.junit.Test
import timber.log.Timber
import java.time.DayOfWeek

/**
 * JVM unit tests for [AlarmIntegrityWorker].
 *
 * Covers:
 *   - No enabled alarms -> returns success without calling rescheduleAll
 *   - Has enabled alarms -> calls rescheduleAll and returns success
 *   - Exception during execution -> returns retry
 */
@OptIn(ExperimentalCoroutinesApi::class)
class AlarmIntegrityWorkerTest {

    companion object {
        @JvmStatic
        @BeforeClass
        fun plantTimber() {
            Timber.uprootAll()
            Timber.plant(object : Timber.Tree() {
                override fun log(priority: Int, tag: String?, message: String, t: Throwable?) {}
            })
        }
    }

    private lateinit var alarmRepository: AlarmRepository
    private lateinit var alarmScheduler: AlarmScheduler
    private lateinit var worker: AlarmIntegrityWorker

    @Before
    fun setUp() {
        alarmRepository = mockk(relaxed = true)
        alarmScheduler = mockk(relaxed = true)
        val context = mockk<Context>(relaxed = true)
        val workerParams = mockk<WorkerParameters>(relaxed = true)

        worker = AlarmIntegrityWorker(
            appContext = context,
            workerParams = workerParams,
            alarmRepository = alarmRepository,
            alarmScheduler = alarmScheduler,
        )
    }

    @Test
    fun `no enabled alarms returns success without rescheduling`() = runTest {
        // Given
        coEvery { alarmRepository.getEnabledAlarms() } returns flowOf(emptyList())

        // When
        val result = worker.doWork()

        // Then
        assertEquals(ListenableWorker.Result.success(), result)
        coVerify(exactly = 0) { alarmScheduler.rescheduleAll(any<List<Alarm>>()) }
    }

    @Test
    fun `enabled alarms triggers rescheduleAll and returns success`() = runTest {
        // Given
        val alarms = listOf(
            Alarm(
                id = 1L,
                hour = 8,
                minute = 0,
                repeatDays = DayOfWeek.entries.toSet(),
                playlistId = 10L,
                isEnabled = true,
                label = "Daily",
                autoStop = null,
                lastTriggeredAt = null,
            )
        )
        coEvery { alarmRepository.getEnabledAlarms() } returns flowOf(alarms)

        // When
        val result = worker.doWork()

        // Then
        assertEquals(ListenableWorker.Result.success(), result)
        coVerify(exactly = 1) { alarmScheduler.rescheduleAll(alarms) }
    }

    @Test
    fun `exception during execution returns retry`() = runTest {
        // Given
        coEvery { alarmRepository.getEnabledAlarms() } throws RuntimeException("DB error")

        // When
        val result = worker.doWork()

        // Then
        assertEquals(ListenableWorker.Result.retry(), result)
        coVerify(exactly = 0) { alarmScheduler.rescheduleAll(any<List<Alarm>>()) }
    }
}
