package com.vignan.tracker

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ApiBaseUrlTest {

    @Test
    fun acceptsPinnedHttpsWorkers() {
        assertEquals(
            "https://viit-attendance-api.nxd2k26.workers.dev",
            validateApiBaseUrl("https://viit-attendance-api.nxd2k26.workers.dev/")
        )
        assertEquals(
            "https://staging.nxd2k26.workers.dev",
            validateApiBaseUrl("https://staging.nxd2k26.workers.dev")
        )
    }

    @Test
    fun rejectsUnsafeOrLookalikeHosts() {
        assertNull(validateApiBaseUrl("http://viit-attendance-api.nxd2k26.workers.dev"))
        assertNull(validateApiBaseUrl("https://evil.workers.dev"))
        assertNull(validateApiBaseUrl("https://viit-attendance-api.nxd2k26.workers.dev.evil.com"))
        assertNull(validateApiBaseUrl("https://viit-attendance-api.nxd2k26.workers.dev:8443"))
        assertNull(validateApiBaseUrl(""))
        assertNull(validateApiBaseUrl(null))
    }
}
