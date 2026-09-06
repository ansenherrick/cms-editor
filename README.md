# CMS Editor

For the current restart point and next steps, see [CONTINUATION.md](CONTINUATION.md).

The initial project contains a Kotlin Multiplatform domain module and a Kotlin/Ktor server. The default local server uses a file-backed provider, so edits survive restarts without any Framer credential in this repository.

## Modules

- `shared`: serializable blog-post models and validation that iOS and Android can reuse.
- `server`: Ktor JSON API and `InMemoryCmsProvider` implementation.

## API

All API routes use a future-ready `websiteId` scope:

- `GET /v1/websites/{websiteId}/posts`
- `POST /v1/websites/{websiteId}/posts`
- `PATCH /v1/websites/{websiteId}/posts/{postId}`
- `DELETE /v1/websites/{websiteId}/posts/{postId}`
- `POST /v1/websites/{websiteId}/posts/{postId}/publish`
- `POST /v1/websites/{websiteId}/posts/{postId}/unpublish`

## Run locally

Install a JDK 21+ and Gradle, then run:

```sh
gradle :server:run
```

The server runs on `http://localhost:8080`; `GET /health` returns its status. By default, local posts are stored in `serverData/cms-posts.json`; override the path with the JVM system property `cms.storage.path` if needed. Authentication and the Framer provider are intentional next steps before exposing this server beyond local development.

## Current iOS status

The Xcode project at `CMSEditor/CMSEditor.xcodeproj` includes the SwiftUI source
in `iosApp/` in place and targets iOS 17+. The app starts with a **Websites**
screen, where local or hosted CMS API profiles can be added, then manages posts
for each selected website ID.

## Next milestone

Add a native SwiftUI post list and editor backed by these routes, then replace `InMemoryCmsProvider` with a Framer-specific server adapter. The mobile app will never contain a Framer API key.

## Security

The local prototype is protected from remote access by default. See
[SECURITY.md](SECURITY.md) for the current authorization model, production
configuration boundary, and requirements before public launch.

## Windows home-PC deployment

The project now includes a Docker Desktop + Cloudflare Tunnel deployment for a
Windows 10 home PC. It keeps the API off the public home network and exposes it
at an HTTPS hostname through Cloudflare. Follow
[deploy/windows/README.md](deploy/windows/README.md). This is a hosting setup,
not a replacement for the still-pending Framer CMS provider.
