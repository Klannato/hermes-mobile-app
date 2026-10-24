package dev.nous.hermesmobile

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class KeepAlivePolicyTest {

    @Test
    fun capIsFifteenMinutes() {
        assertEquals(15 * 60 * 1000L, KeepAlivePolicy.MAX_HOLD_MS)
    }

    @Test
    fun doesNotReleaseBeforeCap() {
        val max = KeepAlivePolicy.MAX_HOLD_MS
        assertFalse(KeepAlivePolicy.shouldRelease(startedAtMs = 0L, nowMs = max - 1))
    }

    @Test
    fun releasesAtCap() {
        val max = KeepAlivePolicy.MAX_HOLD_MS
        assertTrue(KeepAlivePolicy.shouldRelease(startedAtMs = 0L, nowMs = max))
    }

    @Test
    fun releasesAfterCap() {
        val max = KeepAlivePolicy.MAX_HOLD_MS
        assertTrue(KeepAlivePolicy.shouldRelease(startedAtMs = 1000L, nowMs = 1000L + max + 5000L))
    }

    @Test
    fun usesNonZeroStartTime() {
        val max = KeepAlivePolicy.MAX_HOLD_MS
        assertFalse(KeepAlivePolicy.shouldRelease(startedAtMs = 5000L, nowMs = 5000L + max - 1))
    }
}
