import Combine
import Foundation

@MainActor
final class PostsViewModel: ObservableObject {
    @Published private(set) var posts: [BlogPost] = []
    @Published private(set) var isLoading = false
    @Published var errorMessage: String?

    private let api: BlogPostAPI

    init(api: BlogPostAPI) {
        self.api = api
    }

    func loadPosts() async {
        isLoading = true
        defer { isLoading = false }

        do {
            posts = try await api.listPosts()
        } catch {
            errorMessage = error.localizedDescription
        }
    }

    func save(draft: BlogPostDraft, editing post: BlogPost?) async -> Bool {
        do {
            let savedPost: BlogPost
            if let post {
                savedPost = try await api.update(id: post.id, with: draft)
                replace(savedPost)
            } else {
                savedPost = try await api.create(draft)
                posts.insert(savedPost, at: 0)
            }
            return true
        } catch {
            errorMessage = error.localizedDescription
            return false
        }
    }

    func setPublished(_ post: BlogPost, isPublished: Bool) async {
        do {
            replace(try await api.setPublished(id: post.id, isPublished: isPublished))
        } catch {
            errorMessage = error.localizedDescription
        }
    }

    func uploadImage(data: Data, filename: String, contentType: String) async throws -> ImageUpload {
        try await api.uploadImage(data: data, filename: filename, contentType: contentType)
    }

    func delete(_ post: BlogPost) async {
        do {
            try await api.delete(id: post.id)
            posts.removeAll { $0.id == post.id }
        } catch {
            errorMessage = error.localizedDescription
        }
    }

    private func replace(_ post: BlogPost) {
        guard let index = posts.firstIndex(where: { $0.id == post.id }) else { return }
        posts[index] = post
    }
}
