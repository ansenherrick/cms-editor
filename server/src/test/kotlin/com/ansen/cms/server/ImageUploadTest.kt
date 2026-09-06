package com.ansen.cms.server

import io.ktor.client.request.forms.MultiPartFormDataContent
import io.ktor.client.request.forms.formData
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.Headers
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.testing.testApplication
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import javax.imageio.ImageIO
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ImageUploadTest {
    @Test
    fun `accepts PNG at the iOS upload route`() =
        checkUpload(imageBytes("png"), "image/png", HttpStatusCode.Created)

    @Test
    fun `accepts JPEG with content type parameters`() =
        checkUpload(imageBytes("jpg"), "image/jpeg; charset=binary", HttpStatusCode.Created, "image/jpeg")

    @Test
    fun `rejects a MIME type that disagrees with image bytes`() =
        checkUpload(imageBytes("png"), "image/jpeg", HttpStatusCode.UnprocessableEntity)

    @Test
    fun `accepts an image at the ten MiB byte limit`() =
        checkUpload(imageBytes("png").copyOf(10 * 1024 * 1024), "image/png", HttpStatusCode.Created)

    @Test
    fun `rejects an image one byte over the limit before storage`() =
        checkUpload(imageBytes("png").copyOf(10 * 1024 * 1024 + 1), "image/png", HttpStatusCode.UnprocessableEntity)

    @Test
    fun `rejects invalid image data`() =
        checkUpload("not an image".encodeToByteArray(), "image/png", HttpStatusCode.UnprocessableEntity)

    private fun checkUpload(
        bytes: ByteArray,
        declaredType: String,
        expectedStatus: HttpStatusCode,
        expectedType: String = declaredType,
    ) = testApplication {
        val saved = mutableListOf<ValidatedImage>()
        val storage = object : ImageStorage {
            override fun store(websiteId: String, image: ValidatedImage): StoredImage {
                assertEquals("personal-site", websiteId)
                saved.add(image)
                return StoredImage("test/image", "https://example.com/image", image.contentType, image.width, image.height, "test-checksum")
            }
        }
        val access = object : AccessControl {
            override fun authenticate(call: ApplicationCall) =
                CmsPrincipal("test-admin", CmsRole.OWNER, setOf("personal-site"))
        }
        application { module(imageStorage = storage, security = CmsSecurity(access)) }

        val response = client.post("/v1/websites/personal-site/posts/images") {
            setBody(MultiPartFormDataContent(formData {
                append("image", bytes, Headers.build {
                    append(HttpHeaders.ContentType, declaredType)
                    append(HttpHeaders.ContentDisposition, "filename=\"upload\"")
                })
            }))
        }

        assertEquals(expectedStatus, response.status)
        if (expectedStatus == HttpStatusCode.Created) {
            assertEquals(1, saved.size)
            assertEquals(expectedType, saved.single().contentType)
            assertEquals(2, saved.single().width)
            assertEquals(3, saved.single().height)
            assertContentEquals(bytes, saved.single().bytes)
        } else {
            assertTrue(saved.isEmpty(), "Rejected uploads must not reach storage.")
        }
    }

    private fun imageBytes(format: String): ByteArray {
        val output = ByteArrayOutputStream()
        assertTrue(ImageIO.write(BufferedImage(2, 3, BufferedImage.TYPE_INT_RGB), format, output))
        return output.toByteArray()
    }
}