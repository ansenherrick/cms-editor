package com.ansen.cms.shared

import kotlinx.serialization.Serializable

/**
 * The fields shared by the mobile clients and the server.
 * Dates use ISO-8601 calendar-date strings (for example, 2026-08-16).
 */
@Serializable
data class BlogPostDraft(
    val date: String,
    val title: String,
    val imageUrl: String? = null,
    val imageAltText: String? = null,
    val imageSize: ImageSize? = null,
    val subheading: String? = null,
    val link: String? = null,
    val linkText: String? = null,
    val bodyText: String,
)

@Serializable
data class BlogPost(
    val id: String,
    val date: String,
    val title: String,
    val imageUrl: String? = null,
    val imageAltText: String? = null,
    val imageSize: ImageSize? = null,
    val subheading: String? = null,
    val link: String? = null,
    val linkText: String? = null,
    val bodyText: String,
    val isPublished: Boolean,
)

@Serializable
enum class ImageSize {
    SMALL,
    MEDIUM,
    LARGE,
    WIDE,
}

fun BlogPostDraft.toPost(id: String, isPublished: Boolean) = BlogPost(
    id = id,
    date = date.trim(),
    title = title.trim(),
    imageUrl = imageUrl?.trim()?.ifBlank { null },
    imageAltText = imageAltText?.trim()?.ifBlank { null },
    imageSize = imageSize,
    subheading = subheading?.trim()?.ifBlank { null },
    link = link?.trim()?.ifBlank { null },
    linkText = linkText?.trim()?.ifBlank { null },
    bodyText = bodyText.trim(),
    isPublished = isPublished,
)
