# Framer CMS Provider

The Framer integration is server-only. The iOS app talks to the Ktor API, and the
Ktor API talks to a private `framer-bridge` container that holds the Framer API
key. Do not put a Framer key in the iOS app, this repository, or chat.

## What is configured

- Project: `https://framer.com/projects/Personal-Website--Qi2it0L3x4vLRZFcsOSe`
- Collection: `blog-posts`
- Website ID expected by the app/API: `personal-site`
- Public API hostname: `https://cms.ansenherrick.com`

The default provider is still the local file-backed provider. Framer is enabled
only when `CMS_PROVIDER=framer` is set in the Windows User environment.

## Framer fields

The bridge expects an editable, user-managed collection with these fields:

| App field | Framer field name | Framer type |
| --- | --- | --- |
| `title` | `Title` | String |
| `date` | `Date` | Date |
| `bodyText` | `Body Text` | Formatted Text |
| `subheading` | `Sub Text` | Formatted Text |
| `imageUrl` | `Image Link` | Link or String |
| `imageAltText` | `Image Alt Text` | String |
| `imageSize` | `Image Size` | Enum or String |
| `link` | `Optional link` | Link or String |
| `linkText` | `Link text` | String |

`Image Size` enum cases should be `Small`, `Medium`, `Large`, and `Wide`.
Framer's built-in draft status remains the source of truth for publish state.
The app's **Publish** and **Unpublish** actions only change that CMS item status.
Use **Deploy site** from the phone when you want Framer to publish and deploy the
project to its configured custom domain. A live deployment includes every
unpublished Framer project change, not only the blog post.

## Enable it on the Windows Docker host

Create a Framer API key in Framer, then store it as a Windows User environment
variable. Run these commands in PowerShell one at a time:

```powershell
[Environment]::SetEnvironmentVariable("FRAMER_API_KEY", (Read-Host "Framer API key"), "User")
[Environment]::SetEnvironmentVariable("CMS_PROVIDER", "framer", "User")
```

The collection defaults to `blog-posts`. If Framer reports an ambiguous
collection name, set the collection id explicitly:

```powershell
[Environment]::SetEnvironmentVariable("CMS_FRAMER_COLLECTION", "<collection-id-from-Framer>", "User")
```

Close PowerShell, open a fresh window, then rebuild and start:

```powershell
cd <path-to>\cms-editor\deploy\windows
.\start-cms.ps1 -Build
```

Run the read-only schema check after the containers are up:

```powershell
.\start-cms.ps1 -CheckFramer
```

The check prints the matched collection and fields, but not post content. If it
passes, open the iPhone app and refresh the `Personal site` profile.

## What to test

1. Refresh the post list and confirm it shows the Framer `blog-posts` items.
2. Create a short draft post with only `Date`, `Title`, and `Body Text`.
3. Add an image through the app and confirm the saved Framer item has the R2
   `Image Link`, `Image Alt Text`, and `Image Size`.
4. Edit the title/body and confirm Framer updates the same CMS item.
5. Use **Publish** in the app and confirm the Framer item is published.
6. Use **Deploy site** from the post list menu and confirm the configured public
   domain receives the new deployment.
7. Use **Unpublish** and confirm it only changes the Framer item status.

Existing local JSON posts stay in the Docker volume. They are not automatically
migrated into Framer.
