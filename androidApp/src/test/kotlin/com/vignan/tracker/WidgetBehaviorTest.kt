package com.vignan.tracker

import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WidgetBehaviorTest {
    @Test
    fun repeatedConcurrentTapsOnlyStartOneRefreshUntilItFinishes() {
        val gate = WidgetRefreshGate()
        val ready = CountDownLatch(1)
        val done = CountDownLatch(100)
        val accepted = AtomicInteger()
        val pool = Executors.newFixedThreadPool(8)
        try {
            repeat(100) {
                pool.execute {
                    ready.await()
                    if (gate.tryStart()) accepted.incrementAndGet()
                    done.countDown()
                }
            }
            ready.countDown()
            assertTrue(done.await(5, java.util.concurrent.TimeUnit.SECONDS))
            assertEquals(1, accepted.get())
            assertFalse(gate.tryStart())
            gate.finish()
            assertTrue(gate.tryStart())
        } finally {
            pool.shutdownNow()
        }
    }

    @Test
    fun shortAndNarrowWidgetsHideSecondaryDetails() {
        assertFalse(WidgetLayoutSize(180, 100, 1f).showToday)
        assertFalse(WidgetLayoutSize(180, 100, 1f).showDetails)
        assertTrue(WidgetLayoutSize(320, 180, 1f).showToday)
        assertTrue(WidgetLayoutSize(320, 180, 1f).showDetails)
        assertFalse(WidgetLayoutSize(250, 150, 1.5f).showToday)
        assertEquals(24f, WidgetLayoutSize(180, 100, 1f).percentageSize, 0f)
        assertEquals(44f, WidgetLayoutSize(500, 400, 1f).percentageSize, 0f)
    }

    @Test
    fun layoutSizeCanCoexistWithSharedWidgetSizeEnum() {
        assertTrue(WidgetSize::class.java.isEnum)
        assertEquals(2, WidgetSize.entries.size)
        assertTrue(WidgetLayoutSize(320, 180, 1f).showToday)
    }
}
