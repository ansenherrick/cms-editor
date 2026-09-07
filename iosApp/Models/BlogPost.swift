import Foundation

struct BlogPost: Codable, Identifiable, Hashable {
    let id: String
    let date: String
    let title: String
    let imageUrl: String?
    let imageAltText: String?
    let imageSize: ImageSize?
    let subheading: String?
    let link: String?
    let linkText: String?
    let bodyText: String
    let isPublished: Bool
}

struct BlogPostDraft: Codable, Equatable {
    var date: String
    var title: String
    var imageUrl: String?
    var imageAltText: String?
    var imageSize: ImageSize?
    var subheading: String?
    var link: String?
    var linkText: String?
    var bodyText: String

    init(post: BlogPost? = nil) {
        date = post?.date ?? Self.today
        title = post?.title ?? ""
        imageUrl = post?.imageUrl
        imageAltText = post?.imageAltText
        imageSize = post?.imageSize
        subheading = post?.subheading?.plainTextFromHTML
        link = post?.link
        linkText = post?.linkText
        bodyText = post?.bodyText.plainTextFromHTML ?? ""
    }

    private static var today: String {
        let formatter = DateFormatter()
        formatter.locale = Locale(identifier: "en_US_POSIX")
        formatter.dateFormat = "yyyy-MM-dd"
        return formatter.string(from: .now)
    }
}

private extension String {
    var plainTextFromHTML: String {
        guard contains("<") else { return self }
        let withLineBreaks = replacingOccurrences(of: "</p>", with: "\n\n", options: .caseInsensitive)
            .replacingOccurrences(of: "<br\\s*/?>", with: "\n", options: [.regularExpression, .caseInsensitive])
            .replacingOccurrences(of: "</h[1-6]>", with: "\n\n", options: [.regularExpression, .caseInsensitive])
        return withLineBreaks
            .replacingOccurrences(of: "<[^>]+>", with: "", options: .regularExpression)
            .decodingBasicHTMLEntities
            .replacingOccurrences(of: "\n\n\n+", with: "\n\n", options: .regularExpression)
            .trimmingCharacters(in: .whitespacesAndNewlines)
    }

    private var decodingBasicHTMLEntities: String {
        replacingOccurrences(of: "&nbsp;", with: " ")
            .replacingOccurrences(of: "&amp;", with: "&")
            .replacingOccurrences(of: "&lt;", with: "<")
            .replacingOccurrences(of: "&gt;", with: ">")
            .replacingOccurrences(of: "&quot;", with: "\"")
            .replacingOccurrences(of: "&#39;", with: "'")
    }
}

enum ImageSize: String, Codable, CaseIterable, Identifiable {
    case small = "SMALL"
    case medium = "MEDIUM"
    case large = "LARGE"
    case wide = "WIDE"

    var id: String { rawValue }
    var label: String { rawValue.capitalized }
}
