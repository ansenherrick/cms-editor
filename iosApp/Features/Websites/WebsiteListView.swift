import SwiftUI

struct WebsiteListView: View {
    @StateObject private var store = WebsiteStore()
    @State private var isAddingWebsite = false

    var body: some View {
        NavigationStack {
            Group {
                if store.websites.isEmpty {
                    ContentUnavailableView(
                        "No websites connected",
                        systemImage: "globe",
                        description: Text("Add a local or hosted CMS API to start managing posts.")
                    )
                } else {
                    List {
                        Section("Your websites") {
                            ForEach(store.websites) { website in
                                NavigationLink {
                                    if let api = website.api {
                                        PostListView(website: website, viewModel: PostsViewModel(api: api))
                                    } else {
                                        WebsiteEditorView(store: store, website: website)
                                    }
                                } label: {
                                    VStack(alignment: .leading, spacing: 4) {
                                        Text(website.name).font(.headline)
                                        Text(website.websiteId).font(.subheadline).foregroundStyle(.secondary)
                                    }
                                }
                            }
                            .onDelete { offsets in
                                offsets.map { store.websites[$0] }.forEach(store.delete)
                            }
                        }

                        Section {
                            Text("This prototype stores post data on the local server. Connect a hosted, authenticated CMS API before using it for a live website.")
                                .font(.footnote)
                                .foregroundStyle(.secondary)
                        }
                    }
                }
            }
            .navigationTitle("Websites")
            .toolbar {
                ToolbarItem(placement: .primaryAction) {
                    Button("Add website", systemImage: "plus") { isAddingWebsite = true }
                }
            }
            .sheet(isPresented: $isAddingWebsite) {
                WebsiteEditorView(store: store)
            }
        }
    }
}
