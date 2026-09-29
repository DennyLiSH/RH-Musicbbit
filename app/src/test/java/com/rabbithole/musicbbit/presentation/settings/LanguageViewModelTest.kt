package com.rabbithole.musicbbit.presentation.settings

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import dagger.hilt.android.testing.HiltTestApplication
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = HiltTestApplication::class)
class LanguageViewModelTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        clearPrefs()
    }

    @After
    fun tearDown() {
        clearPrefs()
    }

    private fun clearPrefs() {
        context.getSharedPreferences("locale_prefs", Context.MODE_PRIVATE)
            .edit().clear().commit()
    }

    @Test
    fun `initial state is SYSTEM when no preference stored`() {
        val viewModel = LanguageViewModel(context)

        assertEquals(AppLanguage.SYSTEM, viewModel.uiState.value.language)
    }

    @Test
    fun `initial state reads stored preference`() {
        context.getSharedPreferences("locale_prefs", Context.MODE_PRIVATE)
            .edit().putString("app_language", "zh-CN").commit()

        val viewModel = LanguageViewModel(context)

        assertEquals(AppLanguage.CHINESE, viewModel.uiState.value.language)
    }

    @Test
    fun `setLanguage persists tag and updates state`() {
        val viewModel = LanguageViewModel(context)

        viewModel.setLanguage(AppLanguage.JAPANESE)

        assertEquals(AppLanguage.JAPANESE, viewModel.uiState.value.language)
        val tag = context.getSharedPreferences("locale_prefs", Context.MODE_PRIVATE)
            .getString("app_language", "")
        assertEquals("ja", tag)
    }

    @Test
    fun `setLanguage SYSTEM persists empty tag and updates state`() {
        context.getSharedPreferences("locale_prefs", Context.MODE_PRIVATE)
            .edit().putString("app_language", "ja").commit()
        val viewModel = LanguageViewModel(context)

        viewModel.setLanguage(AppLanguage.SYSTEM)

        assertEquals(AppLanguage.SYSTEM, viewModel.uiState.value.language)
        val tag = context.getSharedPreferences("locale_prefs", Context.MODE_PRIVATE)
            .getString("app_language", null)
        assertEquals("", tag)
    }
}