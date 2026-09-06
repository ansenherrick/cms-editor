# Syncing between Windows and macOS

Use one private GitHub repository containing this entire project. Work from a
separate clone on each computer. The Windows copy contains the latest backend
repairs; the user confirmed there are no additional changes on the Mac.

## Repository preparation

The original Xcode repository had one commit, f48050f. Its history is imported
into the root repository. The prepared full-project snapshot moves those
original paths under CMSEditor/ and includes the existing updated Xcode project.
Git metadata and a portable history bundle are backed up in an ignored
repair-backups/repo-prep-* directory. Do not upload that backup directory.

The full-project snapshot is staged for your first commit from Windows.
No GitHub remote is configured and nothing has been uploaded by this setup.

## First upload from Windows

Create an empty PRIVATE repository called cms-editor at https://github.com/new.
Do not initialize it with a README, .gitignore, or license.

Open PowerShell in C:\cms-editor\cms-editor. If Git does not yet have your
identity configured, set your preferred commit name and email first. You can
use the GitHub-provided private commit email from your GitHub email settings:

```powershell
git config user.name (Read-Host "Name to use on commits")
git config user.email (Read-Host "Commit email or GitHub noreply email")
```

Review and upload:

```powershell
git status --short
git diff --cached --stat
git commit -m "Consolidate CMS editor project and Windows deployment"
$cmsRepoUrl = Read-Host "Paste the new repository HTTPS URL"
git remote add origin $cmsRepoUrl
git push -u origin main
```

Complete any GitHub authentication prompt locally. Do not put credentials in
the remote URL, source files, or chat. Do not force-push.

## Set up the Mac

Clone into a NEW folder; retain the old Mac copy until this one opens correctly.
Replace YOUR_GITHUB_USERNAME in the URL below:

```sh
git clone https://github.com/YOUR_GITHUB_USERNAME/cms-editor.git cms-editor-synced
cd cms-editor-synced
open CMSEditor/CMSEditor.xcodeproj
```

The Xcode project uses ../iosApp relative to its own directory. Keep the complete
repository layout when opening it. Use the hosted profile in the app:
website ID personal-site, API base URL https://cms.ansenherrick.com, and only
the token portion of CMS_ACCESS_TOKENS in the Bearer token field.

## Everyday workflow on either computer

Before editing, run git status and resolve any unfinished local work, then:

```sh
git pull --ff-only
```

After making and testing changes:

```sh
git status --short
git add <files-you-intend-to-share>
git diff --cached
git commit -m "Describe the change"
git push
```

Commit and push before switching computers. If a pull refuses because the
histories diverged, preserve both sets of changes and resolve the conflict;
do not use a hard reset or force-push to bypass it.

## Deploy backend updates on Windows

Pulling source does not change a running Docker image. After pulling backend
updates, run this from the repository root:

```powershell
.\deploy\windows\start-cms.ps1 -Build
```

Windows User environment variables remain local. Posts remain in the existing
cms-editor-data Docker volume; photos remain in R2. Git does not back up either.
Do not run docker compose down --volumes unless intentionally deleting data.

## Cross-platform conventions

- .gitignore excludes credentials, runtime data, caches, user settings and backups.
- .gitattributes normalizes source line endings and uses CRLF for Windows scripts.
- Keep filename spelling and capitalization consistent in code and on disk.
- Commit Swift/Kotlin source, the Xcode project, assets, build configuration and docs.
- GitHub contains source history. Runtime secrets and application data stay outside it.
