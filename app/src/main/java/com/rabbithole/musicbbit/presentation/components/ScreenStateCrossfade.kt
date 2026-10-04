package com.rabbithole.musicbbit.presentation.components

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.res.stringResource
import com.rabbithole.musicbbit.ui.theme.MotionTokens

/**
 * Renders [ListUiState] with the unified crossfade: Loading spinner, Error with retry,
 * and [content] for the Content branch. Replaces the per-screen
 * `Crossfade + when(state)` boilerplate.
 */
@Composable
fun <T> ScreenStateCrossfade(
    state: ListUiState<T>,
    modifier: Modifier = Modifier,
    errorIcon: Painter? = null,
    onRetry: (() -> Unit)? = null,
    content: @Composable (T) -> Unit,
) {
    Crossfade(
        targetState = state,
        animationSpec = tween(
            durationMillis = MotionTokens.DurationLong,
            easing = MotionTokens.EasingEmphasized
        ),
        modifier = modifier.fillMaxSize(),
        label = "ScreenStateCrossfade",
    ) { value ->
        when (value) {
            is ListUiState.Loading -> LoadingState()
            is ListUiState.Error -> ErrorContent(
                message = stringResource(value.messageResId),
                icon = errorIcon,
                onRetry = onRetry,
            )
            is ListUiState.Content -> content(value.data)
        }
    }
}