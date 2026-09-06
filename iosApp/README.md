# iOS client

These SwiftUI files are the first native iOS client for the Ktor server. They target iOS 17+ and require no third-party packages.

## Connect the existing Xcode target

The Xcode project already exists at `../CMSEditor/CMSEditor.xcodeproj`.

1. Open that project and change its iOS deployment target from **iOS 26.5** to
   **iOS 17.0**.
2. Add every `.swift` file in this folder to the `CMSEditor` target, preserving
   the subfolders and leaving **Copy items if needed** unchecked.
3. Remove the generated `CMSEditorApp.swift` and `ContentView.swift` from the
   target. `CmsEditorApp.swift` in this folder is the app entry point.
4. In the target's Info settings, add **App Transport Security Settings → Allow
   Arbitrary Loads = YES** for local HTTP development only. `Config/Info.plist`
   is a reference for this one setting; do not replace the generated Info.plist.

With the Ktor server running, the iOS Simulator reaches it at `http://127.0.0.1:8080`. Before a device or production build, replace this with your HTTPS API URL and remove `NSAllowsArbitraryLoads`.
