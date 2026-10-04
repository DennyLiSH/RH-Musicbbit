package com.rabbithole.musicbbit.presentation.settings

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rabbithole.musicbbit.domain.model.ThemeMode

/** Single home for ThemeUiState → darkTheme mapping (was duplicated per Activity). */
@Composable
fun ThemeViewModel.appDarkTheme(): Boolean {
    val state by uiState.collectAsStateWithLifecycle()
    return when (state.themeMode) {
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
    }
}