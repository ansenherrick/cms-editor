import SwiftUI

struct PostListView: View {
    let website: Website
    @StateObject private var viewModel: PostsViewModel
    @State private var isCreatingPost = false

    init(website: Website, viewModel: PostsViewModel) {
        self.website = website
        _viewModel = StateObject(wrappedValue: viewModel)
    }

    var body: some View {
        Group {
            if viewModel.isLoading && viewModel.posts.isEmpty {
                ProgressView("Loading posts…")
            } else if viewModel.posts.isEmpty {
                ContentUnavailableView(
                    "No posts yet",
                    systemImage: "doc.text",
                    description: Text("Create your first blog post.")
                )
            } else {
                List(viewModel.posts) { post in
                    NavigationLink {
                        PostDetailView(post: post, viewModel: viewModel)
                    } label: {
                        PostRow(post: post)
                    }
                }
                .refreshable { await viewModel.loadPosts() }
            }
        }
        .navigationTitle(website.name)
        .toolbar {
            ToolbarItem(placement: .primaryAction) {
                Button("Add post", systemImage: "plus") { isCreatingPost = true }
            }
        }
        .sheet(isPresented: $isCreatingPost) {
            PostEditorView(viewModel: viewModel)
        }
        .task { await viewModel.loadPosts() }
        .alert("Couldn’t complete request", isPresented: errorAlert) {
            Button("OK", role: .cancel) { viewModel.errorMessage = nil }
        } message: {
            Text(viewModel.errorMessage ?? "")
        }
    }

    private var errorAlert: Binding<Bool> {
        Binding(get: { viewModel.errorMessage != nil }, set: { if !$0 { viewModel.errorMessage = nil } })
    }
}

private struct PostRow: View {
    let post: BlogPost

    var body: some View {
        VStack(alignment: .leading, spacing: 5) {
            Text(post.title).font(.headline)
            Text(post.date).font(.subheadline).foregroundStyle(.secondary)
            Text(post.isPublished ? "Published" : "Draft")
                .font(.caption.weight(.medium))
                .foregroundStyle(post.isPublished ? .green : .orange)
        }
        .padding(.vertical, 3)
    }
}
