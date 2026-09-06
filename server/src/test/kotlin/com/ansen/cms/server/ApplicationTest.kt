package com.ansen.cms.server

import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.patch
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.testApplication
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals

class ApplicationTest {
    @Test
    fun `posts are isolated per website and support the edit lifecycle`() = testApplication {
        application { module(InMemoryCmsProvider()) }

        val create = client.post("/v1/websites/first-site/posts") {
            headers.append(HttpHeaders.ContentType, ContentType.Application.Json.toString())
            setBody("""{"date":"2026-08-26","title":"First site post","bodyText":"A body."}""")
        }
        assertEquals(HttpStatusCode.Created, create.status)
        val postId = Regex("\\\"id\\\":\\\"([^\\\"]+)\\\"").find(create.bodyAsText())!!.groupValues[1]

        val publish = client.post("/v1/websites/first-site/posts/$postId/publish")
        assertEquals(HttpStatusCode.OK, publish.status)
        assertContains(publish.bodyAsText(), "\"isPublished\":true")

        val update = client.patch("/v1/websites/first-site/posts/$postId") {
            headers.append(HttpHeaders.ContentType, ContentType.Application.Json.toString())
            setBody("""{"date":"2026-08-27","title":"Updated post","bodyText":"Updated body."}""")
        }
        assertEquals(HttpStatusCode.OK, update.status)
        assertContains(update.bodyAsText(), "Updated post")
        assertContains(update.bodyAsText(), "\"isPublished\":true")

        val otherSite = client.get("/v1/websites/second-site/posts")
        assertEquals(HttpStatusCode.OK, otherSite.status)
        assertContains(otherSite.bodyAsText(), "Welcome to the CMS editor")
        check(!otherSite.bodyAsText().contains("Updated post"))

        val delete = client.delete("/v1/websites/first-site/posts/$postId")
        assertEquals(HttpStatusCode.NoContent, delete.status)
    }
}
