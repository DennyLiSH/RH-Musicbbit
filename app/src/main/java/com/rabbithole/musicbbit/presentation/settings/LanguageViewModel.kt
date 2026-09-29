package com.rabbithole.musicbbit.presentation.settings

import android.content.Context
import androidx.lifecycle.ViewModel
import com.rabbithole.musicbbit.LocaleHelper
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * Holds the app-language selection for the settings screen.
 *
 * The preference stays in SharedPreferences (not DataStore) on purpose:
 * [LocaleHelper.applyOnStartup] / [LocaleHelper.wrapContext] must read it
 * synchronously at attachBaseContext time. The ViewModel reads it once and
 * updates state after each write — the settings screen is the only writer.
 */
@HiltViewModel
class LanguageViewModel @Inject constructor(
    @ApplicationContext private val appContext: Context,
) : ViewModel() {

    data class LanguageUiState(
        val language: AppLanguage = AppLanguage.SYSTEM,
    )

    private val _uiState = MutableStateFlow(LanguageUiState(LocaleHelper.getCurrentLanguage(appContext)))
    val uiState: StateFlow<LanguageUiState> = _uiState.asStateFlow()

    fun setLanguage(language: AppLanguage) {
        LocaleHelper.setLanguage(appContext, language)
        _uiState.update { it.copy(language = language) }
    }
}