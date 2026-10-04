package com.rabbithole.musicbbit.presentation.settings

import com.rabbithole.musicbbit.R
import com.rabbithole.musicbbit.domain.model.ScanDirectory
import com.rabbithole.musicbbit.domain.repository.MusicRepository
import com.rabbithole.musicbbit.domain.repository.ScanDirectoryRepository
import com.rabbithole.musicbbit.presentation.components.UserMessage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.BeforeClass
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.verifyBlocking
import org.mockito.kotlin.whenever
import org.mockito.kotlin.wheneverBlocking
import timber.log.Timber

@OptIn(ExperimentalCoroutinesApi::class)
class ScanDirectorySettingsViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()

    private lateinit var scanDirectoryRepository: ScanDirectoryRepository
    private lateinit var musicRepository: MusicRepository

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

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        scanDirectoryRepository = mock()
        musicRepository = mock()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `load directories emits Success with list`() = runTest {
        val directories = listOf(
            ScanDirectory(id = 1L, path = "/music", name = "Music", addedAt = 0L),
            ScanDirectory(id = 2L, path = "/download", name = "Downloads", addedAt = 0L)
        )
        whenever(scanDirectoryRepository.getAll()).thenReturn(flowOf(directories))

        val viewModel = ScanDirectorySettingsViewModel(
            scanDirectoryRepository,
            musicRepository
        )

        val state = viewModel.uiState.value as ScanDirectorySettingsUiState.Success
        assertEquals(2, state.directories.size)
        assertEquals("Music", state.directories[0].name)
    }

    @Test
    fun `add directory success clears pendingDirectory and emits no message`() = runTest {
        whenever(scanDirectoryRepository.getAll()).thenReturn(flowOf(emptyList()))
        wheneverBlocking { scanDirectoryRepository.add(any()) } doAnswer { Result.success(1L) }

        val viewModel = ScanDirectorySettingsViewModel(
            scanDirectoryRepository,
            musicRepository
        )

        val tempDir = System.getProperty("java.io.tmpdir")!!
        viewModel.onAction(ScanDirectorySettingsAction.OnScanDirectoryPreview(tempDir, "Temp"))

        viewModel.onAction(ScanDirectorySettingsAction.OnConfirmAddDirectory)
        testDispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.uiState.value as ScanDirectorySettingsUiState.Success
        assertNull(state.pendingDirectory)
    }

    @Test
    fun `add directory failure emits UserMessage with add failed`() = runTest {
        whenever(scanDirectoryRepository.getAll()).thenReturn(flowOf(emptyList()))
        wheneverBlocking { scanDirectoryRepository.add(any()) } doReturn Result.failure(RuntimeException("Failed"))

        val viewModel = ScanDirectorySettingsViewModel(
            scanDirectoryRepository,
            musicRepository
        )
        testDispatcher.scheduler.advanceUntilIdle()

        val tempDir = System.getProperty("java.io.tmpdir")!!
        viewModel.onAction(ScanDirectorySettingsAction.OnScanDirectoryPreview(tempDir, "Temp"))

        viewModel.onAction(ScanDirectorySettingsAction.OnConfirmAddDirectory)
        testDispatcher.scheduler.advanceUntilIdle()

        val message = viewModel.messages.first()
        assertEquals(UserMessage(R.string.settings_error_add_failed), message)
        val state = viewModel.uiState.value as ScanDirectorySettingsUiState.Success
        assertNull(state.pendingDirectory)
    }

    @Test
    fun `remove directory calls repository remove`() = runTest {
        whenever(scanDirectoryRepository.getAll()).thenReturn(flowOf(emptyList()))
        wheneverBlocking { scanDirectoryRepository.remove(any()) } doReturn Result.success(Unit)

        val viewModel = ScanDirectorySettingsViewModel(
            scanDirectoryRepository,
            musicRepository
        )

        viewModel.onAction(ScanDirectorySettingsAction.OnRemoveDirectory(1L))
        testDispatcher.scheduler.advanceUntilIdle()

        verifyBlocking(scanDirectoryRepository) { remove(1L) }
    }

    @Test
    fun `retry reloads directories after error`() = runTest {
        val errorFlow = kotlinx.coroutines.flow.flow<List<ScanDirectory>> { throw RuntimeException("DB error") }
        whenever(scanDirectoryRepository.getAll()).thenReturn(errorFlow)

        val viewModel = ScanDirectorySettingsViewModel(
            scanDirectoryRepository, musicRepository
        )
        testDispatcher.scheduler.advanceUntilIdle()

        assertTrue(viewModel.uiState.value is ScanDirectorySettingsUiState.Error)

        whenever(scanDirectoryRepository.getAll()).thenReturn(flowOf(
            listOf(ScanDirectory(id = 1L, path = "/music", name = "Music", addedAt = 0L))
        ))
        viewModel.retry()
        testDispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.uiState.value as ScanDirectorySettingsUiState.Success
        assertEquals(1, state.directories.size)
    }
}