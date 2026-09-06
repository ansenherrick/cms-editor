package com.ansen.cms.shared

import kotlinx.datetime.LocalDate

class PostValidationException(val problems: List<String>) : IllegalArgumentException(
    problems.joinToString(separator = "; "),
)

fun BlogPostDraft.validate() {
    val problems = buildList {
        if (title.isBlank()) add("Title is required.")
        if (title.trim().length > 160) add("Title must be 160 characters or fewer.")
        if (bodyText.isBlank()) add("Body text is required.")
        if (bodyText.trim().length > 50_000) add("Body text must be 50,000 characters or fewer.")
        if (runCatching { LocalDate.parse(date.trim()) }.isFailure) {
            add("Date must use ISO format YYYY-MM-DD.")
        }
        if (imageUrl.isNullOrBlank() && imageSize != null) {
            add("Image size can only be set when an image URL is provided.")
        }
        if (!imageUrl.isNullOrBlank() && imageSize == null) {
            add("Choose an image size when an image URL is provided.")
        }
        if (!imageUrl.isNullOrBlank() && imageAltText.isNullOrBlank()) {
            add("Image alt text is required when an image URL is provided.")
        }
        if (imageUrl.isNullOrBlank() && !imageAltText.isNullOrBlank()) {
            add("Image alt text can only be set when an image URL is provided.")
        }
        if (!imageUrl.isNullOrBlank() && !imageUrl.isWebUrl()) {
            add("Image URL must begin with http:// or https://.")
        }
        if (!link.isNullOrBlank() && !link.isWebUrl()) {
            add("Link must begin with http:// or https://.")
        }
        if (!link.isNullOrBlank() && linkText.isNullOrBlank()) {
            add("Link text is required when a link is provided.")
        }
        if (link.isNullOrBlank() && !linkText.isNullOrBlank()) {
            add("A link is required when link text is provided.")
        }
    }

    if (problems.isNotEmpty()) throw PostValidationException(problems)
}

private fun String.isWebUrl() = startsWith("https://") || startsWith("http://")
