package com.rabbithole.musicbbit.presentation.components

import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class UserMessageTest {
    @Test
    fun `messages are buffered and consumed exactly once`() = runTest {
        val (channel, flow) = userMessageChannel()
        channel.trySend(UserMessage(1))
        channel.trySend(UserMessage(2))
        channel.close()
        assertEquals(listOf(UserMessage(1), UserMessage(2)), flow.take(2).toList())
    }
}