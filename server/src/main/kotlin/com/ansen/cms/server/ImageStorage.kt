package com.ansen.cms.server

import software.amazon.awssdk.auth.credentials.AwsBasicCredentials
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider
import software.amazon.awssdk.core.sync.RequestBody
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.s3.S3Client
import software.amazon.awssdk.services.s3.model.PutObjectRequest
import java.net.URI
import java.security.MessageDigest
import java.util.UUID

data class ValidatedImage(
    val bytes: ByteArray,
    val contentType: String,
    val extension: String,
    val width: Int,
    val height: Int,
)

data class StoredImage(
    val objectKey: String,
    val url: String,
    val contentType: String,
    val width: Int,
    val height: Int,
    val checksumSha256: String,
)

interface ImageStorage {
    fun store(websiteId: String, image: ValidatedImage): StoredImage
}

class UnavailableImageStorage : ImageStorage {
    override fun store(websiteId: String, image: ValidatedImage): StoredImage =
        throw IllegalStateException("Image uploads are not configured. Set CMS_IMAGE_STORAGE=r2 in the hosted server configuration.")
}

class R2ImageStorage private constructor(
    private val bucket: String,
    private val publicBaseUrl: String,
    private val client: S3Client,
) : ImageStorage {
    override fun store(websiteId: String, image: ValidatedImage): StoredImage {
        val safeWebsiteId = websiteId.replace(Regex("[^a-zA-Z0-9-]"), "-")
        val objectKey = "posts/$safeWebsiteId/${UUID.randomUUID()}.${image.extension}"
        client.putObject(
            PutObjectRequest.builder()
                .bucket(bucket)
                .key(objectKey)
                .contentType(image.contentType)
                .cacheControl("public, max-age=31536000, immutable")
                .build(),
            RequestBody.fromBytes(image.bytes),
        )
        return StoredImage(
            objectKey = objectKey,
            url = "${publicBaseUrl.trimEnd('/')}/$objectKey",
            contentType = image.contentType,
            width = image.width,
            height = image.height,
            checksumSha256 = MessageDigest.getInstance("SHA-256").digest(image.bytes).joinToString("") { "%02x".format(it) },
        )
    }

    companion object {
        fun fromEnvironment(environment: Map<String, String> = System.getenv()): ImageStorage {
            if (environment["CMS_IMAGE_STORAGE"]?.lowercase() != "r2") return UnavailableImageStorage()
            fun required(name: String) = requireNotNull(environment[name]?.takeIf(String::isNotBlank)) { "$name is required when CMS_IMAGE_STORAGE=r2." }
            val client = S3Client.builder()
                .endpointOverride(URI.create(required("CMS_R2_ENDPOINT")))
                .region(Region.of("auto"))
                .credentialsProvider(StaticCredentialsProvider.create(AwsBasicCredentials.create(
                    required("CMS_R2_ACCESS_KEY_ID"),
                    required("CMS_R2_SECRET_ACCESS_KEY"),
                )))
                .forcePathStyle(true)
                .build()
            return R2ImageStorage(
                bucket = required("CMS_R2_BUCKET"),
                publicBaseUrl = required("CMS_R2_PUBLIC_BASE_URL"),
                client = client,
            )
        }
    }
}
