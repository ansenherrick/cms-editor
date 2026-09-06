package com.ansen.cms.server

import com.ansen.cms.shared.BlogPostDraft
import com.ansen.cms.shared.ImageSize
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class InMemoryCmsProviderTest {
    @Test
    fun `creates a draft then publishes it`() = kotlinx.coroutines.test.runTest {
        val provider = InMemoryCmsProvider()
        val post = provider.createPost(
            websiteId = "my-site",
            draft = BlogPostDraft(
                date = "2026-08-16",
                title = "A new post",
                imageUrl = "https://example.com/hero.jpg",
                imageAltText = "Sunlight through tree leaves",
                imageSize = ImageSize.WIDE,
                bodyText = "The post body.",
            ),
        )

        assertFalse(post.isPublished)
        assertEquals("A new post", post.title)
        assertEquals("Sunlight through tree leaves", post.imageAltText)

        val published = provider.setPublished("my-site", post.id, isPublished = true)

        assertTrue(published.isPublished)
        assertTrue(provider.listPosts("my-site").any { it.id == post.id })
    }
}
