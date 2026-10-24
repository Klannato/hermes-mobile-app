package dev.nous.hermesmobile

/**
 * Pure WebSocket reconnect backoff schedule (no Android dependencies, so it is
 * unit-testable on the JVM).
 *
 * The first reconnect waits [initialDelayMs], and each subsequent attempt waits
 * `factor` times longer, capped at [maxDelayMs]. Call [reset] after a successful
 * connection so the next drop starts over from the initial delay.
 */
class ReconnectBackoff(
    private val initialDelayMs: Long = 3_000L,
    private val maxDelayMs: Long = 30_000L,
    private val factor: Double = 1.5,
) {
    private var current = initialDelayMs

    /** The delay to wait before the next attempt, then advance the schedule. */
    fun nextDelayMs(): Long {
        val delay = current
        current = (current * factor).toLong().coerceAtMost(maxDelayMs)
        return delay
    }

    /** Reset to the initial delay after a connection succeeds. */
    fun reset() {
        current = initialDelayMs
    }
}
