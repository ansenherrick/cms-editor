# CMS Editor — Continuation Handoff

Last updated: 2026-09-06

## 2026-09-06 - Framer provider implemented

The user provided the Framer project URL
`https://framer.com/projects/Personal-Website--Qi2it0L3x4vLRZFcsOSe` and the
collection name `blog-posts`.

The repo now includes an opt-in Framer CMS provider. `CMS_PROVIDER` defaults to
`file`; setting `CMS_PROVIDER=framer` on the Windows Docker host adds the
private `framer-bridge` service from `deploy/windows/compose.framer.yaml`.
`start-cms.ps1` generates the bridge token at startup and reads the Framer API
key only from the Windows User environment.

Run `.\deploy\windows\start-cms.ps1 -Build`, then
`.\deploy\windows\start-cms.ps1 -CheckFramer` after setting `FRAMER_API_KEY` and
`CMS_PROVIDER=framer`. The schema check is read-only and prints collection/field
metadata, not post contents. Framer site-wide publish/deploy remains manual.

Tests run in this repair session:

```text
node --test test/*.test.mjs
gradle --no-daemon --console=plain :server:test
```

## 2026-09-06 - Repository prepared for Mac/Windows synchronization

The user reports that the repaired services started and the app is working.
No additional changes have been made to the Mac copy. Treat the repaired Windows
source as the starting point for synchronization; FramerCmsProvider is still pending.

The complete project now uses one Git repository at its root. The initial commit
from CMSEditor/.git is preserved as the first commit in main. The full-project
snapshot is staged for the user to commit; earlier Xcode paths move under
CMSEditor/. Original Git metadata and a verified history bundle are kept in an
ignored repair-backups/repo-prep-* folder. No remote has been set or pushed.

Read SYNC.md for the first private GitHub upload and Mac clone instructions.
.gitignore excludes .env files, credentials, runtime data, caches, personal
Xcode state and backups. .gitattributes normalizes cross-platform line endings.

## 2026-09-06 - Docker compilation repair

The failed build log identified a receiver mismatch in ImageUpload.kt:
Ktor FileItem.provider() returns ByteReadChannel, while readLimited accepted
InputStream. A later local edit used streamProvider instead. The upload reader
now uses a suspending ByteReadChannel.readLimited helper and retains the 10 MiB
byte limit. Multipart parts are released in finally and cancellation propagates.

Also corrected full MIME type comparison, checked dimensions before decoding,
and aligned the server route with the existing iOS client and documented
POST /v1/websites/{websiteId}/posts/images endpoint.

The Dockerfile now copies only Gradle configuration and Kotlin sources.
A root .dockerignore allowlist excludes local caches, build output, .env files,
and data. Docker builds now run :server:test before :server:installDist.
Upload regression tests cover PNG, JPEG, MIME mismatch, invalid bytes, and the
exact 10 MiB boundary plus one byte. start-cms.ps1 refreshes its checked Windows
User values into the process environment and throws on Docker failures.

Validation remains pending on the Docker PC: Docker, Gradle, and Java were not
available in the repair session. From the project root, run:
docker build --progress=plain -f deploy/windows/Dockerfile -t cms-editor-cms-api .
Only after a successful build, use deploy/windows/start-cms.ps1 to start services.

Only non-secret edited files were backed up under repair-backups/ before repair.
No credentials, .env contents, containers, or Docker volumes were read or changed.
This copy has no Git history, so earlier edits cannot reliably be attributed to Gordon.

## 2026-09-04 — Windows home-PC hosting preparation

The user selected a Windows 10 PC on wired Ethernet as the initial API host.
`deploy/windows/` now contains a Docker Desktop + Cloudflare Tunnel setup. It
keeps Ktor port 8080 inside the Docker network; the only intended public entry
is a Cloudflare hostname such as `cms.ansenherrick.com`. Required secrets are
read from Windows User environment variables, not from project files.

Read `deploy/windows/README.md` before assisting with the Windows setup. It
also documents the critical current limitation: `FileBackedCmsProvider` is
still active. The host is not yet a Framer publishing system until
`FramerCmsProvider` is implemented. Never ask for or place R2, Framer, access,
or tunnel tokens in chat or source code.

## 2026-08-27 pause point — resume here

The user is pausing to finish changes to their Framer website before publishing
it. Do not restart the project from scratch when they return.

The user has selected **Cloudflare R2 Standard** as the final image-hosting
service. It is not set up in the project yet.

### What the user is doing now

1. Finish the desired Framer layout and CMS changes.
2. Publish the site at least once to its Framer `*.framer.app` URL (or staging,
   if available), then later deploy it to the real domain.
3. Verify the existing Blog Posts collection and `CMSImage` code component render
   on the published site.

Publishing is not needed to create R2 or write the upload code, but it is needed
to verify the public website end-to-end. Publishing Framer does **not** host the
Ktor API; the finished API needs its own public HTTPS host.

### R2 production contract

```text
SwiftUI iOS app
    ↓ authenticated HTTPS
Kotlin + Ktor API ──→ Framer CMS Server API
    ↓ short-lived, upload-only authority
Cloudflare R2 Standard ──→ public image URL ──→ Framer Image Link
```

- Use **Standard**, not Infrequent Access, for active blog images.
- The anticipated bucket name is `cms-editor-images`, unless the user chooses a
  different name.
- In production, serve images from an asset domain such as
  `assets.<user-domain>`, not an `r2.dev` development URL.
- The database will store only image metadata: object key, final HTTPS URL, alt
  text, width, height, checksum, and post ID. It must not store image bytes.
- R2 writes must be private and authorized by Ktor. The app can receive only a
  narrowly-scoped, short-lived upload URL, or upload through Ktor. It must never
  contain permanent R2 credentials.
- Do not ask the user to paste Framer or R2 keys into chat, source code, or Git.
  The hosted API will use encrypted runtime configuration.

### Current state that supersedes older notes below

- The user successfully ran the iOS Simulator and confirmed the app is
  responsive and can edit posts.
- The earlier `Security.kt` compilation errors involving the Ktor origin import
  and request Content-Length handling were fixed. Inspect fresh Gradle output if
  a new build error occurs.
- Required fields are ordered first in the app, use a red asterisk only, and the
  date field uses the native date picker. Optional fields use secondary styling.
- `Link Text` was added to the shared model, validation, iOS editor/detail UI,
  `CMS-structure.txt`, and the Framer CMS collection.
- `Image Alt Text` was added to the Framer collection and the local app model,
  validation, and editor. It is required whenever an image URL is supplied.
- Both `Sub Text` and `Body Text` are Framer Formatted Text (rich-text) fields.
- The iOS app may create, edit, and set the Framer CMS item status, but it must
  **not** call Framer's site-wide `publish()` or `deploy()` APIs. The user
  manually publishes the site in Framer after reviewing all pending project
  changes.
- Cloudflare R2 Standard bucket `cms-editor-images` and the public asset domain
  `assets.ansenherrick.com` are active. The Ktor API now includes a protected
  `POST /v1/websites/{websiteId}/posts/images` upload endpoint. It accepts JPEG
  and PNG uploads up to 10 MB, validates image dimensions, writes unique keys to
  R2 when production configuration is present, and returns the final asset URL.
- The iOS editor has a photo picker that normalizes chosen photos to JPEG and
  uploads them before post save. It still needs a hosted API and R2 runtime
  configuration to work outside local development.
- Framer's default `Slug` is retained. Its default `Status` field is the
  draft/published source of truth; map the local `published` boolean to it when
  the Framer provider is implemented.

### Next implementation work after the user returns

1. Confirm the non-sensitive published Framer site/project URL and final R2
   bucket name. Never request any access key in chat.
2. Configure R2 for public image reads through a production asset subdomain;
   keep writes private.
3. Add a Ktor image-upload endpoint that validates MIME type, file size,
   dimensions, filename, and request rate; then safely stores images or creates
   short-lived, single-purpose R2 upload URLs.
4. Add an iOS photo picker, upload progress/errors, preview, and store the final
   R2 URL in `imageUrl` for Framer's **Image Link** field.
5. Implement `FramerCmsProvider` behind `CmsProvider`, keeping the Framer key
   server-only.
6. Before non-local use, implement real single-admin authentication, a managed
   database, hosted HTTPS, and every remaining requirement in `SECURITY.md`.

### R2 runtime configuration

Set these only in the hosted Ktor service's encrypted runtime configuration:

```text
CMS_IMAGE_STORAGE=r2
CMS_R2_ENDPOINT=https://<account-id>.r2.cloudflarestorage.com
CMS_R2_ACCESS_KEY_ID=<R2 access key ID>
CMS_R2_SECRET_ACCESS_KEY=<R2 secret access key>
CMS_R2_BUCKET=cms-editor-images
CMS_R2_PUBLIC_BASE_URL=https://assets.ansenherrick.com
```

Never place these values in the iOS app, project files, or chat. The app’s
upload route is intentionally unavailable until `CMS_IMAGE_STORAGE=r2` is set.

When changing any Framer Code Component source/properties, delete the old canvas
instance, drag in a fresh one, re-bind CMS fields, publish, and verify live.

## Restart here

The local multi-website prototype is wired and compiles successfully as an
unsigned iOS device build. Begin with these steps:

1. From this directory, run `gradle :server:run` (use the Gradle cache workaround
   below if needed). The server binds only to `127.0.0.1:8080`.
2. Open `CMSEditor/CMSEditor.xcodeproj` and select an iPhone Simulator.
3. Run the app. It opens on **Websites** with a preconfigured **Personal site**.
4. Select it to manage its posts, or use **Add website** to create another local
   website ID. Each website ID has independent posts.

The target uses iOS 17+, includes the existing `iosApp/` Swift sources in place,
and enables local HTTP only for Debug builds. There are no third-party iOS
dependencies. An unsigned `iphoneos` build passed on 2026-08-26, and the user
has since confirmed a successful Simulator run.

## What this project is

A personal mobile CMS editor for the blog on the user's Framer website. The current product is single-admin, but the server routes use a `websiteId` scope so it can later grow into a multi-user website-editing platform.

The editable Framer CMS fields are defined in the repository-level `../CMS-structure.txt` file:

- Date
- Title
- Image Link
- Image Alt Text
- Image Size (`Small`, `Medium`, `Large`, `Wide`)
- Sub Text (Formatted Text)
- Optional link
- Link text
- Body Text (Formatted Text)
- Status (Framer's default draft/published field)

## Architecture decisions

```text
SwiftUI iOS app / future Android app
                 ↓ HTTPS
         Kotlin + Ktor server
                 ↓
      Framer CMS Server API (future)
```

- The backend is Kotlin/Ktor, not Node.
- The mobile client must never contain a Framer API key.
- Framer-specific code will live behind `CmsProvider` as `FramerCmsProvider`.
- `FileBackedCmsProvider` is active in the local server entry point. Its data is
  local only and stored in `serverData/cms-posts.json` by default.
- Do not expose the server to the public internet yet: its static-token gate is
  only an interim control and must be replaced by public account authentication.

## Security status

- Local mode is loopback-only. Production mode requires explicit token rules,
  website-scoped roles, and a non-public bind host; see `SECURITY.md`.
- The iOS app stores access tokens in the Keychain and accepts HTTP only for
  loopback development URLs.
- These are deployment safeguards, not a substitute for a public identity
  system. Before launch, replace static rules with OIDC/JWT and a database-backed
  role model, then complete every item in `SECURITY.md`.

## Current working state

The user successfully ran the local server and iOS Simulator. The Ktor
`Security.kt` compilation issues reported during setup were fixed.

The server has these routes:

- `GET /health`
- `GET /v1/websites/{websiteId}/posts`
- `POST /v1/websites/{websiteId}/posts`
- `PATCH /v1/websites/{websiteId}/posts/{postId}`
- `DELETE /v1/websites/{websiteId}/posts/{postId}`
- `POST /v1/websites/{websiteId}/posts/{postId}/publish`
- `POST /v1/websites/{websiteId}/posts/{postId}/unpublish`

`websiteId` for local iOS development is `personal-site`.

The API server is expected to be running in a Terminal window at `http://127.0.0.1:8080`. `GET /health` returned `{"status":"ok"}`.

The SwiftUI source exists in `iosApp/` and is included in the Xcode target in
place. The prototype contains:

- saved website profiles (name, website ID, API base URL)
- per-website post list
- add/edit post form
- publish/unpublish action
- delete confirmation
- `URLSession` API client
- local development API configuration at `http://127.0.0.1:8080`

Swift syntax parsing passed with `xcrun swiftc -parse`.

## Local commands

From this `cms-editor` directory:

```sh
GRADLE_USER_HOME="$HOME/.gradle-cms-editor" gradle :server:test
GRADLE_USER_HOME="$HOME/.gradle-cms-editor" gradle :server:run
```

`gradle :server:run` stays at **EXECUTING** while the server is intentionally running. Stop it with `Control + C`.

### Gradle workaround

The default `~/.gradle/native` cache produced this error on the user's Mac:

```text
Failed to load native library 'libnative-platform.dylib' for Mac OS X aarch64
```

Use the `GRADLE_USER_HOME="$HOME/.gradle-cms-editor"` prefix above to bypass that cache. The user may also manually delete `~/.gradle/native` in their own Terminal if they prefer; do not delete it automatically without asking.

## iOS project layout

```text
cms-editor/
├── CMSEditor/
│   └── CMSEditor.xcodeproj/   # Generated Xcode project; open this file
└── iosApp/                    # CMS SwiftUI source; add it to the target in place
```

Keep the CMS source under `iosApp/`; do not duplicate it inside the generated
Xcode folder. See **Restart here** for the remaining wiring and first-run steps.

## Important files

- `shared/src/commonMain/kotlin/com/ansen/cms/shared/BlogPost.kt`: shared CMS data model.
- `shared/src/commonMain/kotlin/com/ansen/cms/shared/PostValidation.kt`: server-side field validation.
- `server/src/main/kotlin/com/ansen/cms/server/Application.kt`: Ktor entry point and routes.
- `server/src/main/kotlin/com/ansen/cms/server/CmsProvider.kt`: future Framer adapter boundary.
- `server/src/main/kotlin/com/ansen/cms/server/InMemoryCmsProvider.kt`: temporary mock CMS data.
- `iosApp/Services/BlogPostAPI.swift`: iOS API client and development base URL.
- `iosApp/Features/PostList/PostListView.swift`: initial native posts screen.
- `iosApp/Features/PostEditor/PostEditorView.swift`: post form.

## Next development work

Follow **Next implementation work after the user returns** near the top of this
file. That list supersedes the original prototype-only order and starts with the
R2 upload path.

## Safety reminders

- Do not read, commit, print, or place keys/tokens in source files.
- Do not use Framer credentials in the SwiftUI app.
- Do not make the unauthenticated local Ktor server public.
