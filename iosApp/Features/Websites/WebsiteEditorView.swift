import SwiftUI

struct WebsiteEditorView: View {
    @Environment(\.dismiss) private var dismiss
    @ObservedObject var store: WebsiteStore
    let existingWebsite: Website?
    @State private var website: Website
    @State private var accessToken: String
    @State private var validationMessage: String?

    init(store: WebsiteStore, website: Website? = nil) {
        self.store = store
        existingWebsite = website
        _website = State(initialValue: website ?? Website())
        _accessToken = State(initialValue: website.flatMap { AccessTokenStore.accessToken(for: $0.id) } ?? "")
    }

    var body: some View {
        NavigationStack {
            Form {
                Section("Website") {
                    TextField("Name", text: $website.name)
                    TextField("Website ID", text: $website.websiteId)
                        .textInputAutocapitalization(.never)
                        .autocorrectionDisabled()
                }
                Section("CMS API") {
                    TextField("API base URL", text: $website.apiBaseURL)
                        .textInputAutocapitalization(.never)
                        .keyboardType(.URL)
                        .autocorrectionDisabled()
                    Text("For this local prototype, use http://127.0.0.1:8080 in the iOS Simulator.")
                        .font(.footnote)
                        .foregroundStyle(.secondary)
                }
                Section("Access token") {
                    SecureField("Bearer token", text: $accessToken)
                        .textInputAutocapitalization(.never)
                        .autocorrectionDisabled()
                    Text("Stored only in the iOS Keychain, not in website settings or backups. A production server requires a token.")
                        .font(.footnote)
                        .foregroundStyle(.secondary)
                }
            }
            .navigationTitle(existingWebsite == nil ? "Add website" : "Website settings")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Cancel") { dismiss() }
                }
                ToolbarItem(placement: .confirmationAction) {
                    Button("Save") { save() }
                }
            }
            .alert("Check website settings", isPresented: validationAlert) {
                Button("OK", role: .cancel) { validationMessage = nil }
            } message: {
                Text(validationMessage ?? "")
            }
        }
    }

    private var validationAlert: Binding<Bool> {
        Binding(get: { validationMessage != nil }, set: { if !$0 { validationMessage = nil } })
    }

    private func save() {
        website.name = website.name.trimmingCharacters(in: .whitespacesAndNewlines)
        website.websiteId = website.websiteId.trimmingCharacters(in: .whitespacesAndNewlines)
        website.apiBaseURL = website.apiBaseURL.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !website.name.isEmpty, website.api != nil else {
            validationMessage = "Enter a name, website ID, and an HTTPS API URL. HTTP is allowed only for local loopback development."
            return
        }
        do {
            if accessToken.isEmpty {
                AccessTokenStore.deleteAccessToken(for: website.id)
            } else {
                try AccessTokenStore.save(accessToken: accessToken, for: website.id)
            }
        } catch {
            validationMessage = error.localizedDescription
            return
        }
        store.save(website)
        dismiss()
    }
}
