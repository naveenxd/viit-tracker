package com.vignan.tracker

/**
 * Sliding-window failure limiter.
 *
 * Only failed credential checks count: a successful login/refresh clears the
 * window, and failures older than [windowMs] stop counting. The old version
 * counted every attempt (including successful ones) and never actually aged
 * anything out until the process died.
 */
internal class LoginAttemptLimiter(
    private val maxAttempts: Int = 10,
    private val windowMs: Long = 60 * 60 * 1000L
) {
    private val failures = ArrayDeque<Long>()

    /** Seconds until another attempt is allowed, or null when one is allowed now. */
    fun retryAfterSeconds(nowMs: Long): Int? {
        prune(nowMs)
        if (failures.size < maxAttempts) return null

        val retryAt = failures.first() + windowMs
        return ((retryAt - nowMs).coerceAtLeast(1L) / 1000L)
            .toInt()
            .coerceAtLeast(1)
    }

    fun recordFailure(nowMs: Long) {
        prune(nowMs)
        failures.addLast(nowMs)
    }

    fun recordSuccess() {
        failures.clear()
    }

    private fun prune(nowMs: Long) {
        while (failures.isNotEmpty() && nowMs - failures.first() >= windowMs) {
            failures.removeFirst()
        }
    }
}
