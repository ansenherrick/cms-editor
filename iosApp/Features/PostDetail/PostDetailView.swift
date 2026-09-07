import SwiftUI

struct PostDetailView: View {
    let post: BlogPost
    @ObservedObject var viewModel: PostsViewModel
    @State private var isEditing = false
    @State private var showDeleteConfirmation = false

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 16) {
                Text(post.isPublished ? "Published" : "Draft")
                    .font(.caption.weight(.semibold))
                    .foregroundStyle(post.isPublished ? .green : .orange)
                Text(post.date).foregroundStyle(.secondary)
                if let subheading = post.subheading {
                    Text(subheading.plainTextFromHTML).font(.title3).foregroundStyle(.secondary)
                }
                if let imageAltText = post.imageAltText {
                    Text("Image alt text: \(imageAltText)")
                        .font(.caption)
                        .foregroundStyle(.secondary)
                }
                Text(post.bodyText.plainTextFromHTML).frame(maxWidth: .infinity, alignment: .leading)
                if let link = post.link, let url = URL(string: link) {
                    Link(post.linkText ?? link, destination: url)
                        .font(.headline)
                }
            }
            .padding()
        }
        .navigationTitle(post.title)
        .navigationBarTitleDisplayMode(.inline)
        .toolbar {
            ToolbarItem(placement: .primaryAction) {
                Menu {
                    Button("Edit", systemImage: "pencil") { isEditing = true }
                    Button(post.isPublished ? "Unpublish" : "Publish", systemImage: post.isPublished ? "eye.slash" : "checkmark.circle") {
                        Task { await viewModel.setPublished(post, isPublished: !post.isPublished) }
                    }
                    Divider()
                    Button("Delete", systemImage: "trash", role: .destructive) { showDeleteConfirmation = true }
                } label: {
                    Image(systemName: "ellipsis.circle")
                }
            }
        }
        .sheet(isPresented: $isEditing) {
            PostEditorView(viewModel: viewModel, post: post)
        }
        .confirmationDialog("Delete this post?", isPresented: $showDeleteConfirmation, titleVisibility: .visible) {
            Button("Delete post", role: .destructive) {
                Task { await viewModel.delete(post) }
            }
        } message: {
            Text("This cannot be undone once connected to Framer.")
        }
    }
}

private extension String {
    var plainTextFromHTML: String {
        guard contains("<") else { return self }
        guard let data = data(using: .utf8),
              let attributed = try? NSAttributedString(
                data: data,
                options: [
                    .documentType: NSAttributedString.DocumentType.html,
                    .characterEncoding: String.Encoding.utf8.rawValue,
                ],
                documentAttributes: nil
              ) else {
            return replacingOccurrences(of: "<[^>]+>", with: "", options: .regularExpression)
        }
        return attributed.string.trimmingCharacters(in: .whitespacesAndNewlines)
    }
}
