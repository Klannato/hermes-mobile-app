package dev.nous.hermesmobile

/**
 * Keep-awake policy: bounds on how long the phone may be held awake for one task.
 * Pure Kotlin so the cap and the release rule are unit-testable.
 */
object KeepAlivePolicy {

    /** Never keep the phone awake longer than this for a single task. */
    const val MAX_HOLD_MS: Long = 15 * 60 * 1000L

    /**
     * True once a task has run at least [maxMs] since it started — release the
     * wake-lock even if the client's "task done" signal never arrives.
     */
    fun shouldRelease(startedAtMs: Long, nowMs: Long, maxMs: Long = MAX_HOLD_MS): Boolean =
        nowMs - startedAtMs >= maxMs
}
