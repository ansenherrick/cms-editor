package com.ansen.cms.server

import kotlin.test.Test
import kotlin.test.assertFailsWith

class DeploymentConfigTest {
    @Test
    fun `production rejects a public bind without an explicit tunnel`() {
        assertFailsWith<IllegalArgumentException> {
            validateBindHost(mode = "production", host = "0.0.0.0", behindTunnel = false)
        }
    }

    @Test
    fun `production permits a container-only bind behind the tunnel`() {
        validateBindHost(mode = "production", host = "0.0.0.0", behindTunnel = true)
    }
}
