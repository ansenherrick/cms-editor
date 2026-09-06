import Foundation
import Security

enum AccessTokenStore {
    private static let service = "Website.CMSEditor"

    static func accessToken(for websiteID: UUID) -> String? {
        var query = baseQuery(websiteID)
        query[kSecReturnData as String] = true
        query[kSecMatchLimit as String] = kSecMatchLimitOne
        var result: CFTypeRef?
        guard SecItemCopyMatching(query as CFDictionary, &result) == errSecSuccess,
              let data = result as? Data
        else { return nil }
        return String(data: data, encoding: .utf8)
    }

    static func save(accessToken: String, for websiteID: UUID) throws {
        let data = Data(accessToken.utf8)
        let query = baseQuery(websiteID)
        let update = [kSecValueData as String: data]
        let updateStatus = SecItemUpdate(query as CFDictionary, update as CFDictionary)
        if updateStatus == errSecSuccess { return }
        guard updateStatus == errSecItemNotFound else { throw KeychainError.unhandled(updateStatus) }

        var create = query
        create[kSecValueData as String] = data
        create[kSecAttrAccessible as String] = kSecAttrAccessibleWhenUnlockedThisDeviceOnly
        let createStatus = SecItemAdd(create as CFDictionary, nil)
        guard createStatus == errSecSuccess else { throw KeychainError.unhandled(createStatus) }
    }

    static func deleteAccessToken(for websiteID: UUID) {
        SecItemDelete(baseQuery(websiteID) as CFDictionary)
    }

    private static func baseQuery(_ websiteID: UUID) -> [String: Any] {
        [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrService as String: service,
            kSecAttrAccount as String: websiteID.uuidString,
        ]
    }
}

enum KeychainError: LocalizedError {
    case unhandled(OSStatus)

    var errorDescription: String? { "Could not securely store the access token." }
}
