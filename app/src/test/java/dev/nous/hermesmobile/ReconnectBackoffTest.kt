package dev.nous.hermesmobile

import org.junit.Assert.assertEquals
import org.junit.Test

class ReconnectBackoffTest {

    @Test
    fun startsAtInitialDelay() {
        val backoff = ReconnectBackoff(initialDelayMs = 3_000L, maxDelayMs = 30_000L, factor = 1.5)
        assertEquals(3_000L, backoff.nextDelayMs())
    }

    @Test
    fun growsByFactorEachAttempt() {
        val backoff = ReconnectBackoff(initialDelayMs = 3_000L, maxDelayMs = 30_000L, factor = 1.5)
        assertEquals(3_000L, backoff.nextDelayMs())
        assertEquals(4_500L, backoff.nextDelayMs())
        assertEquals(6_750L, backoff.nextDelayMs())
    }

    @Test
    fun capsAtMaxDelay() {
        val backoff = ReconnectBackoff(initialDelayMs = 3_000L, maxDelayMs = 30_000L, factor = 1.5)
        var last = 0L
        repeat(20) { last = backoff.nextDelayMs() }
        assertEquals(30_000L, last)
    }

    @Test
    fun resetReturnsToInitialDelay() {
        val backoff = ReconnectBackoff(initialDelayMs = 3_000L, maxDelayMs = 30_000L, factor = 1.5)
        repeat(5) { backoff.nextDelayMs() }
        backoff.reset()
        assertEquals(3_000L, backoff.nextDelayMs())
    }
}
