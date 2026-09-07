import PhotosUI
import SwiftUI
import UIKit

struct PostEditorView: View {
    @Environment(\.dismiss) private var dismiss
    @ObservedObject var viewModel: PostsViewModel
    let post: BlogPost?
    @State private var draft: BlogPostDraft
    @State private var isSaving = false
    @State private var selectedPhoto: PhotosPickerItem?
    @State private var isShowingCamera = false
    @State private var isUploadingImage = false
    @State private var imageUploadError: String?

    init(viewModel: PostsViewModel, post: BlogPost? = nil) {
        self.viewModel = viewModel
        self.post = post
        _draft = State(initialValue: BlogPostDraft(post: post))
    }

    var body: some View {
        NavigationStack {
            Form {
                Section {
                    requiredTextField("Title", text: $draft.title)
                    DatePicker(selection: dateBinding, displayedComponents: .date) {
                        requiredLabel("Date")
                    }
                    VStack(alignment: .leading, spacing: 8) {
                        requiredLabel("Body text")
                        TextEditor(text: $draft.bodyText).frame(minHeight: 180)
                    }
                } header: {
                    Text("Post details")
                } footer: {
                    Text("Fields marked with * are required.")
                }

                Section("Image") {
                    PhotosPicker(selection: $selectedPhoto, matching: .images) {
                        Label(draft.imageUrl == nil ? "Choose photo" : "Replace photo", systemImage: "photo")
                    }
                    .disabled(isUploadingImage)
                    .onChange(of: selectedPhoto) { _, newItem in
                        guard let newItem else { return }
                        Task { await uploadPhoto(newItem) }
                    }
                    if cameraAvailable {
                        Button {
                            isShowingCamera = true
                        } label: {
                            Label("Take photo", systemImage: "camera")
                        }
                        .disabled(isUploadingImage)
                    }
                    if isUploadingImage {
                        ProgressView("Uploading image…")
                    }
                    TextField("Image URL", text: optionalBinding(\.imageUrl), axis: .vertical)
                        .textInputAutocapitalization(.never)
                        .keyboardType(.URL)
                    TextField("Image alt text", text: optionalBinding(\.imageAltText), axis: .vertical)
                    Picker("Image size", selection: $draft.imageSize) {
                        Text("No image").tag(ImageSize?.none)
                        ForEach(ImageSize.allCases) { size in
                            Text(size.label).tag(ImageSize?.some(size))
                        }
                    }
                }

                Section("Optional content") {
                    VStack(alignment: .leading, spacing: 8) {
                        Text("Sub text (rich text)")
                            .font(.subheadline)
                            .foregroundStyle(.secondary)
                        TextEditor(text: optionalBinding(\.subheading))
                            .frame(minHeight: 100)
                    }
                }

                Section("Link") {
                    TextField("Optional link", text: optionalBinding(\.link), axis: .vertical)
                        .textInputAutocapitalization(.never)
                        .keyboardType(.URL)
                    TextField("Link text", text: optionalBinding(\.linkText))
                }

            }
            .navigationTitle(post == nil ? "New post" : "Edit post")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Cancel") { dismiss() }
                }
                ToolbarItem(placement: .confirmationAction) {
                    Button(isSaving ? "Saving…" : "Save") {
                        Task { await save() }
                    }
                    .disabled(isSaving)
                }
            }
            .alert("Couldn’t upload image", isPresented: imageUploadAlert) {
                Button("OK", role: .cancel) { imageUploadError = nil }
            } message: {
                Text(imageUploadError ?? "")
            }
            .sheet(isPresented: $isShowingCamera) {
                CameraPicker(
                    onCapture: { image in
                        isShowingCamera = false
                        Task { await uploadPhoto(image) }
                    },
                    onCancel: { isShowingCamera = false }
                )
            }
        }
    }

    private func optionalBinding(_ keyPath: WritableKeyPath<BlogPostDraft, String?>) -> Binding<String> {
        Binding(
            get: { draft[keyPath: keyPath] ?? "" },
            set: { draft[keyPath: keyPath] = $0.isEmpty ? nil : $0 }
        )
    }

    private func requiredTextField(_ title: String, text: Binding<String>) -> some View {
        LabeledContent {
            TextField("", text: text)
                .multilineTextAlignment(.trailing)
        } label: {
            requiredLabel(title)
        }
    }

    private func requiredLabel(_ title: String) -> some View {
        HStack(spacing: 2) {
            Text(title).foregroundStyle(.primary)
            Text("*")
                .foregroundStyle(.red)
                .accessibilityHidden(true)
        }
    }

    private var dateBinding: Binding<Date> {
        Binding(
            get: { Self.apiDateFormatter.date(from: draft.date) ?? .now },
            set: { draft.date = Self.apiDateFormatter.string(from: $0) }
        )
    }

    private static let apiDateFormatter: DateFormatter = {
        let formatter = DateFormatter()
        formatter.calendar = Calendar(identifier: .gregorian)
        formatter.locale = Locale(identifier: "en_US_POSIX")
        formatter.timeZone = .current
        formatter.dateFormat = "yyyy-MM-dd"
        return formatter
    }()

    private func save() async {
        isSaving = true
        defer { isSaving = false }
        if await viewModel.save(draft: draft, editing: post) { dismiss() }
    }

    private var imageUploadAlert: Binding<Bool> {
        Binding(get: { imageUploadError != nil }, set: { if !$0 { imageUploadError = nil } })
    }

    private var cameraAvailable: Bool {
        UIImagePickerController.isSourceTypeAvailable(.camera)
    }

    private func uploadPhoto(_ item: PhotosPickerItem) async {
        isUploadingImage = true
        defer {
            isUploadingImage = false
            selectedPhoto = nil
        }
        do {
            guard let sourceData = try await item.loadTransferable(type: Data.self),
                  let image = UIImage(data: sourceData) else {
                throw APIError.message("The selected photo could not be prepared for upload.")
            }
            try await uploadImage(image)
        } catch {
            imageUploadError = error.localizedDescription
        }
    }

    private func uploadPhoto(_ image: UIImage) async {
        isUploadingImage = true
        defer { isUploadingImage = false }
        do {
            try await uploadImage(image)
        } catch {
            imageUploadError = error.localizedDescription
        }
    }

    private func uploadImage(_ image: UIImage) async throws {
        guard let jpegData = compressedJPEGData(for: image) else {
            throw APIError.message("The photo could not be prepared for upload.")
        }
        let upload = try await viewModel.uploadImage(data: jpegData, filename: "blog-photo.jpg", contentType: "image/jpeg")
        draft.imageUrl = upload.url
        if draft.imageAltText == nil { draft.imageAltText = "" }
    }

    private func compressedJPEGData(for image: UIImage) -> Data? {
        let maximumDimension: CGFloat = 2_048
        let scale = min(1, maximumDimension / max(image.size.width, image.size.height))
        let targetSize = CGSize(width: image.size.width * scale, height: image.size.height * scale)
        let resized = UIGraphicsImageRenderer(size: targetSize).image { _ in
            image.draw(in: CGRect(origin: .zero, size: targetSize))
        }
        return resized.jpegData(compressionQuality: 0.85)
    }
}

private struct CameraPicker: UIViewControllerRepresentable {
    let onCapture: (UIImage) -> Void
    let onCancel: () -> Void

    func makeUIViewController(context: Context) -> UIImagePickerController {
        let picker = UIImagePickerController()
        picker.sourceType = .camera
        picker.cameraCaptureMode = .photo
        picker.delegate = context.coordinator
        return picker
    }

    func updateUIViewController(_ uiViewController: UIImagePickerController, context: Context) {}

    func makeCoordinator() -> Coordinator {
        Coordinator(parent: self)
    }

    final class Coordinator: NSObject, UINavigationControllerDelegate, UIImagePickerControllerDelegate {
        private let parent: CameraPicker

        init(parent: CameraPicker) {
            self.parent = parent
        }

        func imagePickerControllerDidCancel(_ picker: UIImagePickerController) {
            parent.onCancel()
        }

        func imagePickerController(
            _ picker: UIImagePickerController,
            didFinishPickingMediaWithInfo info: [UIImagePickerController.InfoKey: Any]
        ) {
            guard let image = info[.originalImage] as? UIImage else {
                parent.onCancel()
                return
            }
            parent.onCapture(image)
        }
    }
}
