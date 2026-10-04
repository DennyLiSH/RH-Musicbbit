package com.rabbithole.musicbbit.presentation.components

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow

/**
 * One-shot user-facing message (operation failure feedback).
 *
 * Channel-based (not SharedFlow): emissions survive a collector gap (rotation) and are
 * consumed exactly once — no replay, no duplicates. Contract mirrors AppToast: message
 * must be static localized text from strings.xml.
 */
data class UserMessage(@StringRes val messageResId: Int)

fun userMessageChannel(): Pair<Channel<UserMessage>, Flow<UserMessage>> {
    val channel = Channel<UserMessage>(Channel.BUFFERED)
    return channel to channel.receiveAsFlow()
}

/**
 * Shared collector: renders every [UserMessage] as a toast. Screens whose ViewModel
 * exposes `messages` mount this once at the screen root. Renders through [AppToast]
 * (the project's single toast seam — same PII contract as its KDoc).
 */
@Composable
fun CollectUserMessages(messages: Flow<UserMessage>) {
    val context = LocalContext.current
    val toast = rememberAppToast()  // 既有 seam（AppToast.kt），含 Activity-context 约束 KDoc
    LaunchedEffect(messages) {
        messages.collect { message ->
            toast.showShort(context.getString(message.messageResId))
        }
    }
}