package com.ansen.cms.server

import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.plugins.origin
import io.ktor.server.request.header
import io.ktor.server.response.respond
import java.security.MessageDigest
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap

enum class CmsRole { OWNER, EDITOR, VIEWER }

data class CmsPrincipal(
    val subject: String,
    val role: CmsRole,
    val websiteIds: Set<String>,
) {
    fun canAccess(websiteId: String, isWrite: Boolean): Boolean =
        websiteId in websiteIds && (!isWrite || role == CmsRole.OWNER || role == CmsRole.EDITOR)
}

/** Authentication boundary. Replace StaticTokenAccessControl with OIDC/JWT verification before public launch. */
interface AccessControl {
    fun authenticate(call: ApplicationCall): CmsPrincipal?
}

class StaticTokenAccessControl(private val tokens: Map<String, CmsPrincipal>) : AccessControl {
    override fun authenticate(call: ApplicationCall): CmsPrincipal? {
        val supplied = call.request.header(HttpHeaders.Authorization)
            ?.takeIf { it.startsWith("Bearer ", ignoreCase = true) }
            ?.drop(7)
            ?.trim()
            ?: return null
        return tokens.entries.firstOrNull { (token, _) ->
            MessageDigest.isEqual(token.encodeToByteArray(), supplied.encodeToByteArray())
        }?.value
    }
}

/** Local mode only works from loopback; the server entry point also binds to loopback by default. */
object LoopbackOnlyAccessControl : AccessControl {
    override fun authenticate(call: ApplicationCall): CmsPrincipal? {
        val remoteHost = call.request.origin.remoteHost
        return if (remoteHost == "127.0.0.1" || remoteHost == "::1" || remoteHost == "localhost") {
            CmsPrincipal("local-prototype", CmsRole.OWNER, setOf("*"))
        } else null
    }
}

class SlidingWindowRateLimiter(
    private val maxRequests: Int = 120,
    private val windowMillis: Long = 60_000,
) {
    private val requests = ConcurrentHashMap<String, ArrayDeque<Long>>()

    @Synchronized
    fun allows(subject: String, now: Long = System.currentTimeMillis()): Boolean {
        val timestamps = requests.computeIfAbsent(subject) { ArrayDeque() }
        while (timestamps.firstOrNull()?.let { now - it >= windowMillis } == true) timestamps.removeFirst()
        if (timestamps.size >= maxRequests) return false
        timestamps.addLast(now)
        return true
    }
}

class CmsSecurity(
    private val accessControl: AccessControl,
    private val rateLimiter: SlidingWindowRateLimiter = SlidingWindowRateLimiter(),
) {
    suspend fun authorize(
        call: ApplicationCall,
        websiteId: String,
        isWrite: Boolean,
        maxRequestBytes: Long = MAX_REQUEST_BYTES,
    ): CmsPrincipal? {
        val declaredContentLength = call.request.headers[HttpHeaders.ContentLength]?.toLongOrNull()
        if (declaredContentLength != null && declaredContentLength > maxRequestBytes) {
            call.respond(HttpStatusCode.PayloadTooLarge, ApiError(listOf("Request body is too large.")))
            return null
        }
        val principal = accessControl.authenticate(call)
        if (principal == null) {
            call.respond(HttpStatusCode.Unauthorized, ApiError(listOf("Authentication is required.")))
            return null
        }
        if (!rateLimiter.allows(principal.subject)) {
            call.respond(HttpStatusCode.TooManyRequests, ApiError(listOf("Too many requests. Try again shortly.")))
            return null
        }
        val hasWebsiteAccess = "*" in principal.websiteIds || principal.canAccess(websiteId, isWrite)
        if (!hasWebsiteAccess) {
            call.respond(HttpStatusCode.Forbidden, ApiError(listOf("You do not have access to this website.")))
            return null
        }
        return principal
    }

    companion object { const val MAX_REQUEST_BYTES = 1_048_576L }
}

interface AuditLogger {
    fun record(principal: CmsPrincipal, action: String, websiteId: String, postId: String?)
}

object NoopAuditLogger : AuditLogger {
    override fun record(principal: CmsPrincipal, action: String, websiteId: String, postId: String?) = Unit
}

class StructuredAuditLogger(private val write: (String) -> Unit) : AuditLogger {
    override fun record(principal: CmsPrincipal, action: String, websiteId: String, postId: String?) {
        write("cms_audit timestamp=${Instant.now()} actor=${principal.subject} action=$action website=$websiteId post=${postId ?: "-"}")
    }
}

object CmsSecurityConfig {
    fun fromEnvironment(environment: Map<String, String> = System.getenv()): Pair<String, CmsSecurity> {
        val mode = environment["CMS_ENVIRONMENT"]?.lowercase() ?: "local"
        require(mode == "local" || mode == "production") { "CMS_ENVIRONMENT must be local or production." }
        if (mode == "local") return mode to CmsSecurity(LoopbackOnlyAccessControl)

        val rules = environment["CMS_ACCESS_TOKENS"]
            ?.split(';')
            ?.filter(String::isNotBlank)
            ?.associate { rule ->
                val fields = rule.split('|')
                require(fields.size == 4 && fields.all(String::isNotBlank)) {
                    "Each CMS_ACCESS_TOKENS rule must be token|subject|role|website-id,website-id."
                }
                val role = runCatching { CmsRole.valueOf(fields[2].uppercase()) }.getOrElse {
                    throw IllegalArgumentException("CMS_ACCESS_TOKENS contains an invalid role.")
                }
                fields[0] to CmsPrincipal(fields[1], role, fields[3].split(',').map(String::trim).toSet())
            }
            .orEmpty()
        require(rules.isNotEmpty()) { "Production requires CMS_ACCESS_TOKENS. Use OIDC/JWT before public launch." }
        return mode to CmsSecurity(StaticTokenAccessControl(rules))
    }
}
