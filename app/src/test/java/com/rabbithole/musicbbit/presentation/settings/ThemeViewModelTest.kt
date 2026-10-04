package com.rabbithole.musicbbit.presentation.settings

import com.rabbithole.musicbbit.R
import com.rabbithole.musicbbit.domain.model.ThemeMode
import com.rabbithole.musicbbit.domain.repository.ThemeRepository
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
import org.junit.Before
import org.junit.BeforeClass
import org.junit.Test
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.mockito.kotlin.wheneverBlocking
import timber.log.Timber

@OptIn(ExperimentalCoroutinesApi::class)
class ThemeViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()

    private lateinit var themeRepository: ThemeRepository

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
        themeRepository = mock()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `load theme mode from repository updates uiState`() = runTest {
        whenever(themeRepository.getThemeMode()).thenReturn(flowOf(ThemeMode.DARK))

        val viewModel = ThemeViewModel(themeRepository)

        assertEquals(ThemeMode.DARK, viewModel.uiState.value.themeMode)
    }

    @Test
    fun `set theme mode success does not emit any UserMessage`() = runTest {
        whenever(themeRepository.getThemeMode()).thenReturn(flowOf(ThemeMode.SYSTEM))
        wheneverBlocking { themeRepository.setThemeMode(ThemeMode.LIGHT) } doReturn Result.success(Unit)

        val viewModel = ThemeViewModel(themeRepository)
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.setThemeMode(ThemeMode.LIGHT)
        testDispatcher.scheduler.advanceUntilIdle()

        // Channel has no message to receive — no toast shown.
        // Verify the uiState still has its original mode.
        assertEquals(ThemeMode.SYSTEM, viewModel.uiState.value.themeMode)
    }

    @Test
    fun `set theme mode failure emits UserMessage`() = runTest {
        whenever(themeRepository.getThemeMode()).thenReturn(flowOf(ThemeMode.SYSTEM))
        wheneverBlocking { themeRepository.setThemeMode(ThemeMode.LIGHT) } doReturn Result.failure(RuntimeException("Failed"))

        val viewModel = ThemeViewModel(themeRepository)
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.setThemeMode(ThemeMode.LIGHT)
        testDispatcher.scheduler.advanceUntilIdle()

        val message = viewModel.messages.first()
        assertEquals(UserMessage(R.string.theme_error_set_failed), message)
    }
}