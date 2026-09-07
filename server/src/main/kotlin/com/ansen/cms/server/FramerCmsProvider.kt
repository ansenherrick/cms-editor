package com.ansen.cms.server

import com.ansen.cms.shared.BlogPost
import com.ansen.cms.shared.BlogPostDraft
import com.ansen.cms.shared.validate
import java.io.IOException
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runInterruptible
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class CmsProviderException(
    val statusCode: Int,
    override val message: String,
) : RuntimeException(message)

class FramerCmsProvider(
    bridgeUrl: String,
    private val bridgeToken: String,
    private val websiteId: String,
    private val httpClient: HttpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(5))
        .followRedirects(HttpClient.Redirect.NEVER)
        .build(),
    private val json: Json = Json { ignoreUnknownKeys = true; explicitNulls = false },
) : CmsProvider {
    private val rpcUri: URI = validateBridgeUrl(bridgeUrl)

    init {
        require(bridgeToken.length >= 32) { "CMS_FRAMER_BRIDGE_TOKEN must be at least 32 characters." }
        require(websiteId.isNotBlank()) { "CMS_WEBSITE_ID is required." }
    }

    override suspend fun listPosts(websiteId: String): List<BlogPost> =
        rpc(websiteId, FramerRequest(operation = "list"), ListSerializer)

    override suspend fun createPost(websiteId: String, draft: BlogPostDraft): BlogPost {
        draft.validate()
        return rpc(websiteId, FramerRequest(operation = "create", draft = draft), PostSerializer)
    }

    override suspend fun updatePost(websiteId: String, postId: String, draft: BlogPostDraft): BlogPost {
        draft.validate()
        return rpc(websiteId, FramerRequest(operation = "update", postId = postId, draft = draft), PostSerializer)
    }

    override suspend fun deletePost(websiteId: String, postId: String) {
        rpc<UnitResponse>(websiteId, FramerRequest(operation = "delete", postId = postId), UnitResponseSerializer)
    }

    override suspend fun setPublished(websiteId: String, postId: String, isPublished: Boolean): BlogPost =
        rpc(websiteId, FramerRequest(operation = "setPublished", postId = postId, isPublished = isPublished), PostSerializer)

    override suspend fun deploySite(websiteId: String): SiteDeployment =
        rpc(websiteId, FramerRequest(operation = "deploy"), SiteDeploymentSerializer)

    private suspend inline fun <reified T> rpc(websiteId: String, request: FramerRequest, serializer: ResponseSerializer<T>): T {
        if (websiteId != this.websiteId) {
            throw CmsProviderException(403, "This API is configured for website '${this.websiteId}'.")
        }

        val requestBody = json.encodeToString(request.copy(websiteId = websiteId))
        val httpRequest = HttpRequest.newBuilder(rpcUri)
            .timeout(Duration.ofSeconds(55))
            .header("Authorization", "Bearer $bridgeToken")
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(requestBody))
            .build()

        val response = try {
            runInterruptible(Dispatchers.IO) {
                httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString())
            }
        } catch (error: IOException) {
            throw CmsProviderException(503, "Framer bridge is not reachable. Check that the bridge container is healthy before retrying.")
        } catch (error: InterruptedException) {
            Thread.currentThread().interrupt()
            throw CmsProviderException(503, "Framer bridge request was interrupted.")
        }

        if (response.statusCode() !in 200..299) throw bridgeError(response)

        return try {
            serializer.decode(json, response.body())
        } catch (error: SerializationException) {
            throw CmsProviderException(502, "Framer bridge returned an unexpected response.")
        }
    }

    private fun bridgeError(response: HttpResponse<String>): CmsProviderException {
        val allowedStatus = setOf(400, 401, 403, 404, 409, 413, 422, 502, 503, 504)
        val status = response.statusCode().takeIf { it in allowedStatus } ?: 502
        val problems = runCatching { json.decodeFromString<ApiError>(response.body()).problems }.getOrNull()
        val message = problems?.joinToString(" ")?.takeIf { it.isNotBlank() }
            ?: "Framer bridge request failed."
        val safeMessage = if (status == 401) "Framer bridge authorization failed." else message
        return CmsProviderException(status, safeMessage)
    }
}

private fun validateBridgeUrl(value: String): URI {
    val uri = URI(value).normalize()
    require(uri.scheme == "http") { "CMS_FRAMER_BRIDGE_URL must use http inside Docker." }
    require(uri.rawQuery == null && uri.rawFragment == null) { "CMS_FRAMER_BRIDGE_URL must not include query or fragment." }
    require(uri.path.isNullOrBlank() || uri.path == "/") { "CMS_FRAMER_BRIDGE_URL must point to the bridge root." }
    require(uri.host in setOf("framer-bridge", "localhost", "127.0.0.1")) { "CMS_FRAMER_BRIDGE_URL must point to the private Framer bridge." }
    return uri.resolve("/rpc")
}

fun configuredCmsProvider(
    storagePath: java.nio.file.Path,
    environment: Map<String, String> = System.getenv(),
): CmsProvider = when (environment["CMS_PROVIDER"]?.lowercase()) {
    null, "", "file" -> FileBackedCmsProvider(storagePath)
    "framer" -> FramerCmsProvider(
        bridgeUrl = environment.required("CMS_FRAMER_BRIDGE_URL"),
        bridgeToken = environment.required("CMS_FRAMER_BRIDGE_TOKEN"),
        websiteId = environment.required("CMS_WEBSITE_ID"),
    )
    else -> throw IllegalArgumentException("CMS_PROVIDER must be 'file' or 'framer'.")
}

private fun Map<String, String>.required(name: String): String =
    get(name)?.takeIf { it.isNotBlank() } ?: throw IllegalArgumentException("$name is required.")

@Serializable
private data class FramerRequest(
    val operation: String,
    val websiteId: String = "",
    val postId: String? = null,
    val draft: BlogPostDraft? = null,
    val isPublished: Boolean? = null,
)

private interface ResponseSerializer<T> {
    fun decode(json: Json, body: String): T
}

private object PostSerializer : ResponseSerializer<BlogPost> {
    override fun decode(json: Json, body: String): BlogPost = json.decodeFromString(body)
}

private object ListSerializer : ResponseSerializer<List<BlogPost>> {
    override fun decode(json: Json, body: String): List<BlogPost> = json.decodeFromString(body)
}

private object SiteDeploymentSerializer : ResponseSerializer<SiteDeployment> {
    override fun decode(json: Json, body: String): SiteDeployment = json.decodeFromString(body)
}

@Serializable
private data class UnitResponse(val ok: Boolean)

private object UnitResponseSerializer : ResponseSerializer<UnitResponse> {
    override fun decode(json: Json, body: String): UnitResponse = json.decodeFromString(body)
}
