package com.rabbithole.musicbbit.service.alarm

import android.content.Context
import androidx.work.WorkerParameters
import androidx.work.ListenableWorker
import com.rabbithole.musicbbit.domain.model.Alarm
import com.rabbithole.musicbbit.domain.repository.AlarmRepository
import com.rabbithole.musicbbit.service.AlarmScheduler
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.BeforeClass
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verifyBlocking
import org.mockito.kotlin.whenever
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
        alarmRepository = mock()
        alarmScheduler = mock()
        val context = mock<Context>()
        val workerParams = mock<WorkerParameters>()

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
        whenever(alarmRepository.getEnabledAlarms()).thenReturn(flowOf(emptyList()))

        // When
        val result = worker.doWork()

        // Then
        assertEquals(ListenableWorker.Result.success(), result)
        verifyBlocking(alarmScheduler, times(0)) { rescheduleAll(any<List<Alarm>>()) }
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
        whenever(alarmRepository.getEnabledAlarms()).thenReturn(flowOf(alarms))

        // When
        val result = worker.doWork()

        // Then
        assertEquals(ListenableWorker.Result.success(), result)
        verifyBlocking(alarmScheduler, times(1)) { rescheduleAll(alarms) }
    }

    @Test
    fun `exception during execution returns retry`() = runTest {
        // Given
        whenever(alarmRepository.getEnabledAlarms()).thenThrow(RuntimeException("DB error"))

        // When
        val result = worker.doWork()

        // Then
        assertEquals(ListenableWorker.Result.retry(), result)
        verifyBlocking(alarmScheduler, times(0)) { rescheduleAll(any<List<Alarm>>()) }
    }
}
