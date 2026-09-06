package com.ansen.cms.server

import io.ktor.http.HttpStatusCode
import io.ktor.http.content.PartData
import io.ktor.server.application.ApplicationCall
import io.ktor.server.request.receiveMultipart
import io.ktor.server.response.respond
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.readAvailable
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.Serializable
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import javax.imageio.ImageIO

@Serializable
data class ImageUploadResponse(
    val objectKey: String,
    val url: String,
    val contentType: String,
    val width: Int,
    val height: Int,
    val checksumSha256: String,
)

suspend fun ApplicationCall.receiveValidatedImage(): ValidatedImage? {
    var image: ValidatedImage? = null
    var error: String? = null
    val multipart = receiveMultipart()
    while (true) {
        val part = multipart.readPart() ?: break
        try {
            if (part is PartData.FileItem && part.name == "image" && image == null && error == null) {
                val bytes = try {
                    part.provider().readLimited(MAX_IMAGE_BYTES)
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {
                    error = "Upload a JPEG or PNG image no larger than 10 MB."
                    null
                }
                if (bytes != null) {
                    val declaredType = part.contentType?.let { "${it.contentType}/${it.contentSubtype}" }?.lowercase()
                    image = inspectImage(bytes, declaredType)
                    if (image == null) error = "Upload a JPEG or PNG image no larger than 10 MB."
                }
            }
        } finally {
            part.release()
        }
    }
    error?.let {
        respond(HttpStatusCode.UnprocessableEntity, ApiError(listOf(it)))
        return null
    }
    if (image == null) {
        respond(HttpStatusCode.BadRequest, ApiError(listOf("A multipart image field is required.")))
        return null
    }
    return image
}

private fun inspectImage(bytes: ByteArray, declaredContentType: String?): ValidatedImage? {
    try {
        ImageIO.createImageInputStream(ByteArrayInputStream(bytes)).use { input ->
            val reader = ImageIO.getImageReaders(input).asSequence().firstOrNull() ?: return null
            try {
                reader.input = input
                val format = reader.formatName.lowercase()
                val (contentType, extension) = when (format) {
                    "jpeg", "jpg" -> "image/jpeg" to "jpg"
                    "png" -> "image/png" to "png"
                    else -> return null
                }
                if (declaredContentType != null && declaredContentType !in setOf(contentType, "application/octet-stream")) return null
                // Check dimensions before allocating the decoded image.
                val width = reader.getWidth(0)
                val height = reader.getHeight(0)
                if (width !in 1..MAX_IMAGE_DIMENSION || height !in 1..MAX_IMAGE_DIMENSION) return null
                if (width.toLong() * height > MAX_IMAGE_PIXELS) return null
                val image = reader.read(0)
                return ValidatedImage(bytes, contentType, extension, image.width, image.height)
            } finally {
                reader.dispose()
            }
        }
    } catch (_: Exception) {
        return null
    }
    return null
}

private suspend fun ByteReadChannel.readLimited(limit: Int): ByteArray {
    val output = ByteArrayOutputStream()
    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
    while (true) {
        val count = readAvailable(buffer)
        if (count < 0) break
        if (count > limit - output.size()) throw IllegalArgumentException("Image files must be 10 MB or smaller.")
        output.write(buffer, 0, count)
    }
    return output.toByteArray()
}

private const val MAX_IMAGE_BYTES = 10 * 1024 * 1024
private const val MAX_IMAGE_DIMENSION = 8_000
private const val MAX_IMAGE_PIXELS = 40_000_000L