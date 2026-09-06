import Combine
import Foundation

struct Website: Codable, Identifiable, Hashable {
    var id: UUID
    var name: String
    var websiteId: String
    var apiBaseURL: String

    init(
        id: UUID = UUID(),
        name: String = "",
        websiteId: String = "",
        apiBaseURL: String = "http://127.0.0.1:8080"
    ) {
        self.id = id
        self.name = name
        self.websiteId = websiteId
        self.apiBaseURL = apiBaseURL
    }

    var api: BlogPostAPI? {
        guard let baseURL = URL(string: apiBaseURL),
              let scheme = baseURL.scheme,
              ["http", "https"].contains(scheme),
              isSecureTransport(baseURL),
              !websiteId.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty
        else { return nil }
        return BlogPostAPI(baseURL: baseURL, websiteId: websiteId, accessToken: AccessTokenStore.accessToken(for: id))
    }

    static let localExample = Website(name: "Personal site", websiteId: "personal-site")

    private func isSecureTransport(_ url: URL) -> Bool {
        if url.scheme == "https" { return true }
        return ["127.0.0.1", "localhost", "::1"].contains(url.host?.lowercased())
    }
}

@MainActor
final class WebsiteStore: ObservableObject {
    @Published private(set) var websites: [Website] = []

    private let storageKey = "saved-websites"

    init() {
        guard let data = UserDefaults.standard.data(forKey: storageKey),
              let saved = try? JSONDecoder().decode([Website].self, from: data)
        else {
            websites = [.localExample]
            save()
            return
        }
        websites = saved.sorted { $0.name.localizedCaseInsensitiveCompare($1.name) == .orderedAscending }
    }

    func save(_ website: Website) {
        if let index = websites.firstIndex(where: { $0.id == website.id }) {
            websites[index] = website
        } else {
            websites.append(website)
        }
        websites.sort { $0.name.localizedCaseInsensitiveCompare($1.name) == .orderedAscending }
        save()
    }

    func delete(_ website: Website) {
        websites.removeAll { $0.id == website.id }
        AccessTokenStore.deleteAccessToken(for: website.id)
        save()
    }

    private func save() {
        guard let data = try? JSONEncoder().encode(websites) else { return }
        UserDefaults.standard.set(data, forKey: storageKey)
    }
}
