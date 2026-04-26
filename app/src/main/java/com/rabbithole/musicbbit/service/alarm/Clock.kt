package com.rabbithole.musicbbit.service.alarm

import javax.inject.Inject
import javax.inject.Singleton

/**
 * Abstraction over the wall clock so alarm-fire orchestration can be JVM-unit-tested.
 *
 * Production adapter: [SystemClock] — delegates to [System.currentTimeMillis].
 * Test adapter: a fake holding a mutable epoch milliseconds counter.
 */
interface Clock {
    fun nowMs(): Long
}

@Singleton
class SystemClock @Inject constructor() : Clock {
    override fun nowMs(): Long = System.currentTimeMillis()
}
