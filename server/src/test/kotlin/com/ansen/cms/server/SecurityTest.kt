package com.ansen.cms.server

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class SecurityTest {
    @Test
    fun `production mode requires configured access rules`() {
        assertFailsWith<IllegalArgumentException> {
            CmsSecurityConfig.fromEnvironment(mapOf("CMS_ENVIRONMENT" to "production"))
        }
    }

    @Test
    fun `production mode accepts scoped roles`() {
        val (mode, security) = CmsSecurityConfig.fromEnvironment(
            mapOf("CMS_ENVIRONMENT" to "production", "CMS_ACCESS_TOKENS" to "value|user-1|editor|site-a,site-b"),
        )
        assertEquals("production", mode)
        check(security is CmsSecurity)
    }
}
