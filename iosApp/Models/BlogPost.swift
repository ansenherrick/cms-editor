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
        subheading = post?.subheading
        link = post?.link
        linkText = post?.linkText
        bodyText = post?.bodyText ?? ""
    }

    private static var today: String {
        let formatter = DateFormatter()
        formatter.locale = Locale(identifier: "en_US_POSIX")
        formatter.dateFormat = "yyyy-MM-dd"
        return formatter.string(from: .now)
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
