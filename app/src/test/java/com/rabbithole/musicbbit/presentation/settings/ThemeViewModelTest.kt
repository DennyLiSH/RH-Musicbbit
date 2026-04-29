package com.rabbithole.musicbbit.presentation.settings

import com.rabbithole.musicbbit.R
import com.rabbithole.musicbbit.domain.model.ThemeMode
import com.rabbithole.musicbbit.domain.repository.ThemeRepository
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ThemeViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()

    private lateinit var themeRepository: ThemeRepository

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        themeRepository = mockk()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `load theme mode from repository updates uiState`() = runTest {
        every { themeRepository.getThemeMode() } returns flowOf(ThemeMode.DARK)

        val viewModel = ThemeViewModel(themeRepository)

        assertEquals(ThemeMode.DARK, viewModel.uiState.value.themeMode)
    }

    @Test
    fun `set theme mode success does not set error`() = runTest {
        every { themeRepository.getThemeMode() } returns flowOf(ThemeMode.SYSTEM)
        coEvery { themeRepository.setThemeMode(ThemeMode.LIGHT) } returns Result.success(Unit)

        val viewModel = ThemeViewModel(themeRepository)

        viewModel.setThemeMode(ThemeMode.LIGHT)
        testDispatcher.scheduler.advanceUntilIdle()

        assertNull(viewModel.uiState.value.errorMessageResId)
    }

    @Test
    fun `set theme mode failure sets error`() = runTest {
        every { themeRepository.getThemeMode() } returns flowOf(ThemeMode.SYSTEM)
        coEvery { themeRepository.setThemeMode(ThemeMode.LIGHT) } coAnswers { Result.failure(RuntimeException("Failed")) }

        val viewModel = ThemeViewModel(themeRepository)

        viewModel.setThemeMode(ThemeMode.LIGHT)
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(R.string.theme_error_set_failed, viewModel.uiState.value.errorMessageResId)
    }
}
