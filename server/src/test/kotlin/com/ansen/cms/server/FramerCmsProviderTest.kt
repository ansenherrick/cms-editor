package com.ansen.cms.server

import com.ansen.cms.shared.BlogPostDraft
import com.ansen.cms.shared.ImageSize
import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.testApplication
import java.net.InetSocketAddress
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlinx.coroutines.test.runTest

class FramerCmsProviderTest {
    @Test
    fun listPostsCallsPrivateBridge() = runTest {
        withBridge("""[{"id":"post-1","date":"2026-09-07","title":"Title","bodyText":"Body","isPublished":false}]""") { bridge ->
            val provider = FramerCmsProvider(bridge.url, bridge.token, "personal-site")

            val posts = provider.listPosts("personal-site")

            assertEquals("post-1", posts.single().id)
            assertEquals("Bearer ${bridge.token}", bridge.requests.single().authorization)
            assertContains(bridge.requests.single().body, """"operation":"list"""")
            assertContains(bridge.requests.single().body, """"websiteId":"personal-site"""")
        }
    }

    @Test
    fun wrongWebsiteFailsBeforeNetwork() = runTest {
        withBridge("""[]""") { bridge ->
            val provider = FramerCmsProvider(bridge.url, bridge.token, "personal-site")

            val error = assertFailsWith<CmsProviderException> {
                provider.listPosts("other-site")
            }

            assertEquals(403, error.statusCode)
            assertEquals(0, bridge.requests.size)
        }
    }

    @Test
    fun setPublishedSerializesFalse() = runTest {
        withBridge("""{"id":"post-1","date":"2026-09-07","title":"Title","bodyText":"Body","isPublished":false}""") { bridge ->
            val provider = FramerCmsProvider(bridge.url, bridge.token, "personal-site")

            provider.setPublished("personal-site", "post-1", isPublished = false)

            assertContains(bridge.requests.single().body, """"operation":"setPublished"""")
            assertContains(bridge.requests.single().body, """"postId":"post-1"""")
            assertContains(bridge.requests.single().body, """"isPublished":false""")
        }
    }

    @Test
    fun bridgeConflictDoesNotRetryOrFallback() = runTest {
        withBridge("""{"problems":["Schema mismatch."]}""", status = 409) { bridge ->
            val provider = FramerCmsProvider(bridge.url, bridge.token, "personal-site")

            val error = assertFailsWith<CmsProviderException> {
                provider.createPost("personal-site", validDraft())
            }

            assertEquals(409, error.statusCode)
            assertContains(error.message, "Schema mismatch.")
            assertEquals(1, bridge.requests.size)
        }
    }

    @Test
    fun malformedSuccessBecomesBadGateway() = runTest {
        withBridge("""{}""") { bridge ->
            val provider = FramerCmsProvider(bridge.url, bridge.token, "personal-site")

            val error = assertFailsWith<CmsProviderException> {
                provider.listPosts("personal-site")
            }

            assertEquals(502, error.statusCode)
        }
    }

    @Test
    fun configurationSelectsFramerOnlyWhenRequested() {
        assertFailsWith<IllegalArgumentException> {
            configuredCmsProvider(
                storagePath = java.nio.file.Path.of("ignored.json"),
                environment = mapOf("CMS_PROVIDER" to "framer"),
            )
        }

        assertFailsWith<IllegalArgumentException> {
            configuredCmsProvider(
                storagePath = java.nio.file.Path.of("ignored.json"),
                environment = mapOf("CMS_PROVIDER" to "typo"),
            )
        }

        val provider = configuredCmsProvider(
            storagePath = java.nio.file.Path.of("ignored.json"),
            environment = mapOf(
                "CMS_PROVIDER" to "framer",
                "CMS_FRAMER_BRIDGE_URL" to "http://framer-bridge:8090",
                "CMS_FRAMER_BRIDGE_TOKEN" to "abcdefghijklmnopqrstuvwxyz0123456789",
                "CMS_WEBSITE_ID" to "personal-site",
            ),
        )

        assertEquals(FramerCmsProvider::class, provider::class)
    }

    @Test
    fun remoteBridgeUrlIsRejected() {
        assertFailsWith<IllegalArgumentException> {
            FramerCmsProvider(
                bridgeUrl = "https://cms.ansenherrick.com",
                bridgeToken = "abcdefghijklmnopqrstuvwxyz0123456789",
                websiteId = "personal-site",
            )
        }
    }

    @Test
    fun appReturnsProviderErrorsAsJson() = testApplication {
        val provider = object : CmsProvider by InMemoryCmsProvider() {
            override suspend fun listPosts(websiteId: String) =
                throw CmsProviderException(503, "Framer bridge is not reachable.")
        }
        application { module(provider) }

        val response = client.get("/v1/websites/personal-site/posts")

        assertEquals(HttpStatusCode.ServiceUnavailable, response.status)
        assertContains(response.bodyAsText(), "Framer bridge is not reachable.")
    }

    private fun validDraft() = BlogPostDraft(
        date = "2026-09-07",
        title = "Title",
        imageUrl = "https://assets.ansenherrick.com/photo.jpg",
        imageAltText = "A photo",
        imageSize = ImageSize.WIDE,
        subheading = "Sub",
        link = "https://ansenherrick.com",
        linkText = "Read more",
        bodyText = "Body",
    )
}

private data class BridgeRequest(val authorization: String?, val body: String)

private data class BridgeFixture(
    val url: String,
    val token: String,
    val requests: List<BridgeRequest>,
)

private suspend fun withBridge(
    responseBody: String,
    status: Int = 200,
    block: suspend (BridgeFixture) -> Unit,
) {
    val requests = CopyOnWriteArrayList<BridgeRequest>()
    val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
    server.createContext("/rpc") { exchange: HttpExchange ->
        requests += BridgeRequest(
            authorization = exchange.requestHeaders.getFirst("Authorization"),
            body = exchange.requestBody.bufferedReader().use { it.readText() },
        )
        exchange.responseHeaders.add("Content-Type", "application/json")
        val bytes = responseBody.toByteArray(Charsets.UTF_8)
        exchange.sendResponseHeaders(status, bytes.size.toLong())
        exchange.responseBody.use { it.write(bytes) }
    }
    server.start()
    try {
        block(
            BridgeFixture(
                url = "http://127.0.0.1:${server.address.port}",
                token = "abcdefghijklmnopqrstuvwxyz0123456789",
                requests = requests,
            ),
        )
    } finally {
        server.stop(0)
    }
}
