package com.vignan.tracker

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class LoginAttemptLimiterTest {

    @Test
    fun onlyFailuresCountAndTheWindowSlides() {
        val limiter = LoginAttemptLimiter(maxAttempts = 2, windowMs = 1_000L)

        limiter.recordFailure(0L)
        limiter.recordFailure(100L)
        assertNotNull(limiter.retryAfterSeconds(200L))

        // The first failure ages out, then the second.
        assertNull(limiter.retryAfterSeconds(1_000L))
        assertNull(limiter.retryAfterSeconds(1_100L))
    }

    @Test
    fun successClearsTheFailureWindow() {
        val limiter = LoginAttemptLimiter(maxAttempts = 2, windowMs = 1_000L)

        limiter.recordFailure(0L)
        limiter.recordFailure(100L)
        assertNotNull(limiter.retryAfterSeconds(200L))

        limiter.recordSuccess()
        assertNull(limiter.retryAfterSeconds(200L))
    }

    @Test
    fun retryAfterIsAtLeastOneSecond() {
        val limiter = LoginAttemptLimiter(maxAttempts = 1, windowMs = 5_000L)
        limiter.recordFailure(0L)

        assertEquals(5, limiter.retryAfterSeconds(0L))
        assertEquals(1, limiter.retryAfterSeconds(4_999L))
    }
}
