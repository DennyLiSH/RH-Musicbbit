package com.rabbithole.musicbbit.data.local

import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.verifyBlocking
import org.mockito.kotlin.verifyNoMoreInteractions
import org.mockito.kotlin.wheneverBlocking
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class MediaStoreObserverTest {

    @Test
    fun `burst of changes triggers single debounced refresh`() = runTest {
        val libraryRefresher: LibraryRefresher = mock()
        wheneverBlocking { libraryRefresher.refreshAll() } doReturn Result.success(
            com.rabbithole.musicbbit.data.local.sync.SyncResult(0, 0, 0)
        )
        val dispatcher = StandardTestDispatcher(testScheduler)
        val observer = MediaStoreObserver(
            context = ApplicationProvider.getApplicationContext(),
            libraryRefresher = libraryRefresher,
            ioDispatcher = dispatcher
        )

        // 先让 init 中的 collector 协程启动并订阅 SharedFlow（replay=0 时未订阅的
        // tryEmit 会丢失——这是本测试与实现之间的时序契约）
        advanceUntilIdle()

        repeat(5) { observer.onChange(false, Uri.EMPTY) }

        advanceTimeBy(1_000)
        verifyNoMoreInteractions(libraryRefresher) // 防抖窗口内不触发
        advanceUntilIdle()
        verifyBlocking(libraryRefresher) { refreshAll() } // 窗口后触发
        verifyNoMoreInteractions(libraryRefresher) // 锁定"恰好一次"
    }

    @Test
    fun `refresh failure is logged and does not crash observer`() = runTest {
        val libraryRefresher: LibraryRefresher = mock()
        wheneverBlocking { libraryRefresher.refreshAll() } doReturn Result.failure(RuntimeException("io"))
        val observer = MediaStoreObserver(
            context = ApplicationProvider.getApplicationContext(),
            libraryRefresher = libraryRefresher,
            ioDispatcher = StandardTestDispatcher(testScheduler)
        )
        advanceUntilIdle()
        observer.onChange(false, Uri.EMPTY)
        advanceUntilIdle()
        verifyBlocking(libraryRefresher) { refreshAll() } // 失败被 .onFailure 消化，不抛出
    }
}
