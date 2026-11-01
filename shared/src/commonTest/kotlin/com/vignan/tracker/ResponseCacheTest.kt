package com.vignan.tracker

import kotlin.test.Test
import kotlin.test.assertEquals

class ResponseCacheTest {

    @Test
    fun reportCacheKeysDistinguishRangesAndFlags() {
        val month = ReportQuery("01/11/2026", null, excludeOthers = false)
        val monthExcluding = ReportQuery("01/11/2026", null, excludeOthers = true)
        val range = ReportQuery("01/11/2026", "30/11/2026", excludeOthers = false)

        assertEquals("01-11-2026__0", month.cacheKey)
        assertEquals("01-11-2026__1", monthExcluding.cacheKey)
        assertEquals("01-11-2026_30-11-2026_0", range.cacheKey)
    }
}
