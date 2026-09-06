package com.ansen.cms.server

import com.ansen.cms.shared.BlogPost
import com.ansen.cms.shared.BlogPostDraft

interface CmsProvider {
    suspend fun listPosts(websiteId: String): List<BlogPost>
    suspend fun createPost(websiteId: String, draft: BlogPostDraft): BlogPost
    suspend fun updatePost(websiteId: String, postId: String, draft: BlogPostDraft): BlogPost
    suspend fun deletePost(websiteId: String, postId: String)
    suspend fun setPublished(websiteId: String, postId: String, isPublished: Boolean): BlogPost
}

class PostNotFoundException(postId: String) : NoSuchElementException("Post '$postId' was not found.")
