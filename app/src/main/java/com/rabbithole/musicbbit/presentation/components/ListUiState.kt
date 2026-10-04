package com.rabbithole.musicbbit.presentation.components

/**
 * Shared state shape for list-like screens. One generic type replaces the per-screen
 * hand-written `Loading / Error / Success` sealed interfaces.
 *
 * Empty is NOT a distinct state: `Content(emptyList)` — screens render their own
 * EmptyState branch (StateView contract).
 */
sealed interface ListUiState<out T> {
    data object Loading : ListUiState<Nothing>
    data class Error(val messageResId: Int) : ListUiState<Nothing>
    data class Content<out T>(val data: T) : ListUiState<T>
}