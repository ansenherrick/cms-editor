import SwiftUI

struct PostListView: View {
    let website: Website
    @StateObject private var viewModel: PostsViewModel
    @State private var isCreatingPost = false
    @State private var showDeployConfirmation = false

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
                Menu {
                    Button("Add post", systemImage: "plus") { isCreatingPost = true }
                    Button("Deploy site", systemImage: "arrow.up.circle") { showDeployConfirmation = true }
                        .disabled(viewModel.isDeploying)
                } label: {
                    Image(systemName: "ellipsis.circle")
                }
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
        .alert("Done", isPresented: successAlert) {
            Button("OK", role: .cancel) { viewModel.successMessage = nil }
        } message: {
            Text(viewModel.successMessage ?? "")
        }
        .confirmationDialog("Deploy site changes?", isPresented: $showDeployConfirmation, titleVisibility: .visible) {
            Button("Deploy site") {
                Task { await viewModel.deploySite() }
            }
        } message: {
            Text("This publishes and deploys all pending Framer project changes to the configured public domain.")
        }
    }

    private var errorAlert: Binding<Bool> {
        Binding(get: { viewModel.errorMessage != nil }, set: { if !$0 { viewModel.errorMessage = nil } })
    }

    private var successAlert: Binding<Bool> {
        Binding(get: { viewModel.successMessage != nil }, set: { if !$0 { viewModel.successMessage = nil } })
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
