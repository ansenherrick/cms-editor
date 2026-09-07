package com.ansen.cms.server

import com.ansen.cms.shared.BlogPostDraft
import com.ansen.cms.shared.PostValidationException
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.call
import io.ktor.server.application.install
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.plugins.defaultheaders.DefaultHeaders
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.patch
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import io.ktor.server.routing.routing
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

fun main() {
    val storagePath = System.getProperty("cms.storage.path")
        ?.let { java.nio.file.Path.of(it) }
        ?: java.nio.file.Path.of("serverData", "cms-posts.json")
    val (mode, security) = CmsSecurityConfig.fromEnvironment()
    val host = System.getenv("CMS_BIND_HOST") ?: "127.0.0.1"
    val behindTunnel = System.getenv("CMS_BEHIND_TUNNEL")?.equals("true", ignoreCase = true) == true
    validateBindHost(mode, host, behindTunnel)
    embeddedServer(Netty, port = System.getenv("CMS_PORT")?.toIntOrNull() ?: 8080, host = host, module = {
        module(configuredCmsProvider(storagePath), R2ImageStorage.fromEnvironment(), security, StructuredAuditLogger { environment.log.info(it) })
    }).start(wait = true)
}

/**
 * A Docker container must listen on all of its own interfaces so the separate
 * cloudflared container can reach it. This is allowed only when the deployment
 * explicitly declares that a tunnel is in front of it; the Compose setup does
 * not publish the API port to the Windows host or the Internet.
 */
internal fun validateBindHost(mode: String, host: String, behindTunnel: Boolean) {
    require(mode == "local" || host != "0.0.0.0" || behindTunnel) {
        "Production must use a private bind address or set CMS_BEHIND_TUNNEL=true when an HTTPS tunnel is the only ingress."
    }
}

fun Application.module(
    provider: CmsProvider = InMemoryCmsProvider(),
    imageStorage: ImageStorage = UnavailableImageStorage(),
    security: CmsSecurity = CmsSecurity(LoopbackOnlyAccessControl),
    auditLogger: AuditLogger = NoopAuditLogger,
) {
    install(DefaultHeaders) {
        header("X-Content-Type-Options", "nosniff")
        header("X-Frame-Options", "DENY")
        header("Referrer-Policy", "no-referrer")
        header("Cache-Control", "no-store")
    }
    install(ContentNegotiation) {
        json(Json { ignoreUnknownKeys = false; explicitNulls = false })
    }
    install(StatusPages) {
        exception<PostValidationException> { call, cause ->
            call.respond(HttpStatusCode.UnprocessableEntity, ApiError(cause.problems))
        }
        exception<PostNotFoundException> { call, cause ->
            call.respond(HttpStatusCode.NotFound, ApiError(listOf(cause.message ?: "Post not found.")))
        }
        exception<CmsProviderException> { call, cause ->
            call.respond(HttpStatusCode.fromValue(cause.statusCode), ApiError(listOf(cause.message)))
        }
        exception<IllegalArgumentException> { call, cause ->
            call.respond(HttpStatusCode.BadRequest, ApiError(listOf(cause.message ?: "Invalid request.")))
        }
    }

    routing {
        route("/health") {
            get { call.respond(HealthResponse(status = "ok")) }
        }
        route("/v1/websites/{websiteId}/posts") {
            get {
                val websiteId = call.websiteId()
                security.authorize(call, websiteId, isWrite = false) ?: return@get
                call.respond(provider.listPosts(websiteId))
            }
            post {
                val websiteId = call.websiteId()
                val principal = security.authorize(call, websiteId, isWrite = true) ?: return@post
                val post = provider.createPost(websiteId, call.receive<BlogPostDraft>())
                auditLogger.record(principal, "post.create", websiteId, post.id)
                call.respond(HttpStatusCode.Created, post)
            }
            patch("/{postId}") {
                val websiteId = call.websiteId()
                val principal = security.authorize(call, websiteId, isWrite = true) ?: return@patch
                val postId = call.postId()
                val post = provider.updatePost(websiteId, postId, call.receive<BlogPostDraft>())
                auditLogger.record(principal, "post.update", websiteId, postId)
                call.respond(post)
            }
            delete("/{postId}") {
                val websiteId = call.websiteId()
                val principal = security.authorize(call, websiteId, isWrite = true) ?: return@delete
                val postId = call.postId()
                provider.deletePost(websiteId, postId)
                auditLogger.record(principal, "post.delete", websiteId, postId)
                call.respond(HttpStatusCode.NoContent)
            }
            post("/{postId}/publish") {
                val websiteId = call.websiteId()
                val principal = security.authorize(call, websiteId, isWrite = true) ?: return@post
                val postId = call.postId()
                val post = provider.setPublished(websiteId, postId, isPublished = true)
                auditLogger.record(principal, "post.publish", websiteId, postId)
                call.respond(post)
            }
            post("/{postId}/unpublish") {
                val websiteId = call.websiteId()
                val principal = security.authorize(call, websiteId, isWrite = true) ?: return@post
                val postId = call.postId()
                val post = provider.setPublished(websiteId, postId, isPublished = false)
                auditLogger.record(principal, "post.unpublish", websiteId, postId)
                call.respond(post)
            }
        }
        route("/v1/websites/{websiteId}/posts/images") {
            post {
                val websiteId = call.websiteId()
                val principal = security.authorize(call, websiteId, isWrite = true, maxRequestBytes = 11L * 1024 * 1024) ?: return@post
                val image = call.receiveValidatedImage() ?: return@post
                val stored = imageStorage.store(websiteId, image)
                auditLogger.record(principal, "image.upload", websiteId, stored.objectKey)
                call.respond(HttpStatusCode.Created, ImageUploadResponse(
                    objectKey = stored.objectKey,
                    url = stored.url,
                    contentType = stored.contentType,
                    width = stored.width,
                    height = stored.height,
                    checksumSha256 = stored.checksumSha256,
                ))
            }
        }
    }
}

private fun io.ktor.server.application.ApplicationCall.websiteId(): String =
    parameters["websiteId"]?.takeIf { it.isNotBlank() }
        ?: throw IllegalArgumentException("websiteId is required.")

private fun io.ktor.server.application.ApplicationCall.postId(): String =
    parameters["postId"]?.takeIf { it.isNotBlank() }
        ?: throw IllegalArgumentException("postId is required.")

@Serializable
data class ApiError(val problems: List<String>)

@Serializable
data class HealthResponse(val status: String)
