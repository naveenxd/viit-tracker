package com.vignan.tracker

import kotlin.test.Test
import kotlin.test.assertEquals

class ResponseCacheTest {

    @Test
    fun reportCacheKeepsTwelveRecentRangesAndRefreshesTheirOrder() {
        val keys = (1..20).map { "report_v1_$it" }
        assertEquals((9..20).map { "report_v1_$it" }, recentReportKeys(keys))
        assertEquals((10..20).map { "report_v1_$it" } + "report_v1_9",
            recentReportKeys(recentReportKeys(keys), "report_v1_9"))
        assertEquals((11..21).map { "report_v1_$it" } + "report_v1_9",
            recentReportKeys((11..21).map { "report_v1_$it" }, "report_v1_9"))
    }

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
