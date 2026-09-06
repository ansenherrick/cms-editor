package com.ansen.cms.server

import com.ansen.cms.shared.BlogPost
import com.ansen.cms.shared.BlogPostDraft
import com.ansen.cms.shared.toPost
import com.ansen.cms.shared.validate
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.nio.file.Files
import java.nio.file.Path
import java.util.UUID

/**
 * Local prototype provider. It persists data on disk so edits survive a server
 * restart, while keeping the same CmsProvider boundary used by a future Framer
 * adapter. It must not be exposed publicly because the prototype has no auth.
 */
class FileBackedCmsProvider(
    private val storagePath: Path,
    private val json: Json = Json { ignoreUnknownKeys = false; prettyPrint = true },
) : CmsProvider {
    private val lock = Mutex()
    private var postsByWebsite: MutableMap<String, MutableMap<String, BlogPost>> = load()

    override suspend fun listPosts(websiteId: String): List<BlogPost> = lock.withLock {
        postsFor(websiteId).values.sortedByDescending { it.date }
    }

    override suspend fun createPost(websiteId: String, draft: BlogPostDraft): BlogPost = lock.withLock {
        draft.validate()
        val post = draft.toPost(UUID.randomUUID().toString(), isPublished = false)
        postsFor(websiteId)[post.id] = post
        persist()
        post
    }

    override suspend fun updatePost(websiteId: String, postId: String, draft: BlogPostDraft): BlogPost = lock.withLock {
        draft.validate()
        val posts = postsFor(websiteId)
        val existing = posts[postId] ?: throw PostNotFoundException(postId)
        val post = draft.toPost(postId, existing.isPublished)
        posts[postId] = post
        persist()
        post
    }

    override suspend fun deletePost(websiteId: String, postId: String) = lock.withLock {
        if (postsFor(websiteId).remove(postId) == null) throw PostNotFoundException(postId)
        persist()
    }

    override suspend fun setPublished(websiteId: String, postId: String, isPublished: Boolean): BlogPost = lock.withLock {
        val posts = postsFor(websiteId)
        val existing = posts[postId] ?: throw PostNotFoundException(postId)
        val post = existing.copy(isPublished = isPublished)
        posts[postId] = post
        persist()
        post
    }

    private fun postsFor(websiteId: String): MutableMap<String, BlogPost> =
        postsByWebsite.getOrPut(websiteId) {
            mutableMapOf(
                "welcome-post" to BlogPost(
                    id = "welcome-post",
                    date = "2026-08-16",
                    title = "Welcome to the CMS editor",
                    imageUrl = null,
                    imageSize = null,
                    subheading = "This local post proves the first API slice is connected.",
                    link = null,
                    bodyText = "This local prototype saves to disk. Connect Framer through a server-side provider when API access is ready.",
                    isPublished = false,
                ),
            )
        }

    private fun load(): MutableMap<String, MutableMap<String, BlogPost>> {
        if (!Files.exists(storagePath)) return mutableMapOf()
        val saved = json.decodeFromString<SavedPosts>(Files.readString(storagePath))
        return saved.websites.mapValuesTo(mutableMapOf()) { (_, posts) -> posts.associateByTo(mutableMapOf()) { it.id } }
    }

    private fun persist() {
        storagePath.parent?.let(Files::createDirectories)
        val saved = SavedPosts(postsByWebsite.mapValues { (_, posts) -> posts.values.toList() })
        Files.writeString(storagePath, json.encodeToString(saved))
    }
}

@Serializable
private data class SavedPosts(val websites: Map<String, List<BlogPost>>)
