package com.rabbithole.musicbbit.presentation.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rabbithole.musicbbit.domain.model.ThemeMode
import com.rabbithole.musicbbit.domain.repository.ThemeRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

import com.rabbithole.musicbbit.R
import timber.log.Timber

@HiltViewModel
class ThemeViewModel @Inject constructor(
    private val themeRepository: ThemeRepository
) : ViewModel() {

    data class ThemeUiState(
        val themeMode: ThemeMode = ThemeMode.SYSTEM,
        val errorMessageResId: Int? = null
    )

    private val _uiState = MutableStateFlow(ThemeUiState())
    val uiState: StateFlow<ThemeUiState> = _uiState.asStateFlow()

    init {
        observeThemeMode()
    }

    private fun observeThemeMode() {
        themeRepository.getThemeMode()
            .onEach { mode ->
                _uiState.update { it.copy(themeMode = mode) }
            }
            .catch { e ->
                Timber.e(e, "Failed to load theme mode")
                _uiState.update { it.copy(errorMessageResId = R.string.error_load_failed) }
            }
            .launchIn(viewModelScope)
    }

    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch {
            themeRepository.setThemeMode(mode)
                .onFailure { e ->
                    Timber.w(e, "Failed to set theme mode")
                    _uiState.update { it.copy(errorMessageResId = R.string.theme_error_set_failed) }
                }
        }
    }
}
