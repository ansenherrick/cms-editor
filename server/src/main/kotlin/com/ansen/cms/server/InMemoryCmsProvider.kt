package com.ansen.cms.server

import com.ansen.cms.shared.BlogPost
import com.ansen.cms.shared.BlogPostDraft
import com.ansen.cms.shared.toPost
import com.ansen.cms.shared.validate
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/** Temporary local data source. Replace this with FramerCmsProvider after API access is configured. */
class InMemoryCmsProvider : CmsProvider {
    private val postsByWebsite = ConcurrentHashMap<String, ConcurrentHashMap<String, BlogPost>>()

    override suspend fun listPosts(websiteId: String): List<BlogPost> = postsFor(websiteId)
        .values
        .sortedByDescending { it.date }

    override suspend fun createPost(websiteId: String, draft: BlogPostDraft): BlogPost {
        draft.validate()
        val post = draft.toPost(id = UUID.randomUUID().toString(), isPublished = false)
        postsFor(websiteId)[post.id] = post
        return post
    }

    override suspend fun updatePost(websiteId: String, postId: String, draft: BlogPostDraft): BlogPost {
        draft.validate()
        val posts = postsFor(websiteId)
        val existing = posts[postId] ?: throw PostNotFoundException(postId)
        return draft.toPost(id = postId, isPublished = existing.isPublished).also { posts[postId] = it }
    }

    override suspend fun deletePost(websiteId: String, postId: String) {
        if (postsFor(websiteId).remove(postId) == null) throw PostNotFoundException(postId)
    }

    override suspend fun setPublished(websiteId: String, postId: String, isPublished: Boolean): BlogPost {
        val posts = postsFor(websiteId)
        val existing = posts[postId] ?: throw PostNotFoundException(postId)
        return existing.copy(isPublished = isPublished).also { posts[postId] = it }
    }

    private fun postsFor(websiteId: String) = postsByWebsite.computeIfAbsent(websiteId) {
        ConcurrentHashMap<String, BlogPost>().apply {
            val sample = BlogPost(
                id = "welcome-post",
                date = "2026-08-16",
                title = "Welcome to the CMS editor",
                imageUrl = null,
                imageSize = null,
                subheading = "This local post proves the first API slice is connected.",
                link = null,
                bodyText = "Replace this in-memory provider with Framer once the API connection is ready.",
                isPublished = false,
            )
            put(sample.id, sample)
        }
    }
}
