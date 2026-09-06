import Foundation

struct BlogPostAPI {
    let baseURL: URL
    let websiteId: String
    let accessToken: String?

    static let localDevelopment = BlogPostAPI(
        baseURL: URL(string: "http://127.0.0.1:8080")!,
        websiteId: "personal-site",
        accessToken: nil
    )

    func listPosts() async throws -> [BlogPost] {
        try await send(path: postsPath)
    }

    func create(_ draft: BlogPostDraft) async throws -> BlogPost {
        try await send(path: postsPath, method: "POST", body: try JSONEncoder().encode(draft))
    }

    func update(id: String, with draft: BlogPostDraft) async throws -> BlogPost {
        try await send(path: "\(postsPath)/\(id)", method: "PATCH", body: try JSONEncoder().encode(draft))
    }

    func delete(id: String) async throws {
        let _: EmptyResponse = try await send(path: "\(postsPath)/\(id)", method: "DELETE")
    }

    func setPublished(id: String, isPublished: Bool) async throws -> BlogPost {
        let action = isPublished ? "publish" : "unpublish"
        return try await send(path: "\(postsPath)/\(id)/\(action)", method: "POST")
    }

    func uploadImage(data: Data, filename: String, contentType: String) async throws -> ImageUpload {
        let boundary = "CMSImageBoundary-\(UUID().uuidString)"
        var body = Data()
        body.append("--\(boundary)\r\n".data(using: .utf8)!)
        body.append("Content-Disposition: form-data; name=\"image\"; filename=\"\(filename)\"\r\n".data(using: .utf8)!)
        body.append("Content-Type: \(contentType)\r\n\r\n".data(using: .utf8)!)
        body.append(data)
        body.append("\r\n--\(boundary)--\r\n".data(using: .utf8)!)
        return try await send(
            path: "\(postsPath)/images",
            method: "POST",
            body: body,
            contentType: "multipart/form-data; boundary=\(boundary)"
        )
    }

    private var postsPath: String { "/v1/websites/\(websiteId)/posts" }

    private func send<Response: Decodable>(
        path: String,
        method: String = "GET",
        body: Data? = nil,
        contentType: String? = "application/json"
    ) async throws -> Response {
        var request = URLRequest(url: baseURL.appending(path: path))
        request.httpMethod = method
        request.setValue("application/json", forHTTPHeaderField: "Accept")
        if let accessToken, !accessToken.isEmpty {
            request.setValue("Bearer \(accessToken)", forHTTPHeaderField: "Authorization")
        }

        if let body {
            request.httpBody = body
            request.setValue(contentType, forHTTPHeaderField: "Content-Type")
        }

        let (data, response) = try await URLSession.shared.data(for: request)
        guard let httpResponse = response as? HTTPURLResponse else {
            throw APIError.message("The server returned an invalid response.")
        }
        guard (200..<300).contains(httpResponse.statusCode) else {
            let error = try? JSONDecoder().decode(APIProblems.self, from: data)
            throw APIError.message(error?.problems.joined(separator: "\n") ?? "Request failed (\(httpResponse.statusCode)).")
        }

        if Response.self == EmptyResponse.self { return EmptyResponse() as! Response }
        return try JSONDecoder().decode(Response.self, from: data)
    }
}

struct ImageUpload: Decodable {
    let objectKey: String
    let url: String
    let contentType: String
    let width: Int
    let height: Int
    let checksumSha256: String
}

private struct APIProblems: Decodable {
    let problems: [String]
}

private struct EmptyResponse: Decodable {}

enum APIError: LocalizedError {
    case message(String)

    var errorDescription: String? {
        switch self {
        case let .message(message): message
        }
    }
}
