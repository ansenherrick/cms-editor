# Self-host on Windows 10 with Cloudflare Tunnel

This deployment keeps the Ktor API on the home PC and exposes it only through
Cloudflare. It does **not** open a router port or publish Docker port `8080` to
your home network.

## Before you begin

1. Install [Docker Desktop for Windows](https://docs.docker.com/desktop/setup/install/windows-install/), start it, and enable its WSL 2 backend.
2. Copy the full `cms-editor` directory to the Windows PC. Do not copy any real keys into the project files.
3. In Cloudflare Zero Trust, create a **remotely-managed Cloudflare Tunnel** named `cms-editor-home`.
4. Add a public hostname:
   - Hostname: `cms.ansenherrick.com`
   - Service type: `HTTP`
   - URL: `http://cms-api:8080`
5. Copy the tunnel token from Cloudflare. It belongs in the Windows user
   environment only, never in this repository.

Cloudflare Tunnel makes outbound-only connections from the PC, so no static
home IP, dynamic-DNS service, or router port-forwarding is required.

## Set the private configuration once

Open **PowerShell** and run the following commands one at a time. Enter the
actual value when Windows prompts you. Do not paste any of these values into
source files or chat.

```powershell
[Environment]::SetEnvironmentVariable("CF_TUNNEL_TOKEN", (Read-Host "Cloudflare tunnel token"), "User")
[Environment]::SetEnvironmentVariable("CMS_R2_ENDPOINT", (Read-Host "R2 endpoint"), "User")
[Environment]::SetEnvironmentVariable("CMS_R2_ACCESS_KEY_ID", (Read-Host "R2 access key ID"), "User")
```

Set `CMS_R2_SECRET_ACCESS_KEY` in the Windows
**Environment Variables** interface: Settings → System → About → Advanced
system settings → Environment Variables → User variables → New. Enter the real
value there. This avoids writing it to a file or leaving it in PowerShell
history.

Create an app access token locally (for example, with a password manager), then
set the following in the same Windows Environment Variables interface. Replace
the placeholders there; do not put the actual token in this document:

```text
CMS_ACCESS_TOKENS=<app-access-token>|ansen|OWNER|personal-site
```

Generate an access token with a password manager and avoid `|` and `;` in it,
because those characters separate fields in the server configuration.

Use the R2 bucket and public image domain already chosen for this project:

```text
CMS_R2_BUCKET=cms-editor-images
CMS_R2_PUBLIC_BASE_URL=https://assets.ansenherrick.com
```

The Compose file supplies those two non-secret values. Set all other variables
above as **User** variables, close PowerShell, then open a fresh window so it
receives them.

## Optional: connect posts to Framer

The default provider stores posts in the local Docker volume. To use Framer's
`blog-posts` collection instead, create a Framer API key in Framer and set these
Windows User variables:

```powershell
[Environment]::SetEnvironmentVariable("FRAMER_API_KEY", (Read-Host "Framer API key"), "User")
[Environment]::SetEnvironmentVariable("CMS_PROVIDER", "framer", "User")
```

The startup script generates a private bridge token each time it starts; do not
set `CMS_FRAMER_BRIDGE_TOKEN` yourself. See
[../../FRAMER.md](../../FRAMER.md) for the field mapping and test checklist.

## Start it

In a new PowerShell window:

```powershell
cd <path-to>\cms-editor\deploy\windows
.\start-cms.ps1 -Build
```

Wait for both services to show `running`, then visit:

```text
https://cms.ansenherrick.com/health
```

It should return `{"status":"ok"}`. If it does, add a website profile in the
iPhone app with:

```text
Name: Personal site
Website ID: personal-site
API URL: https://cms.ansenherrick.com
Access token: the same value used in CMS_ACCESS_TOKENS
```

## Make it survive reboots

Docker restarts the two containers automatically once Docker Desktop is running.
In Docker Desktop, enable **Start Docker Desktop when you sign in**. Then open
Windows Task Scheduler and create a task that runs at logon:

```text
powershell.exe -ExecutionPolicy Bypass -File <path-to>\cms-editor\deploy\windows\start-cms.ps1
```

Run it under the same Windows account that owns the User environment variables.

## Operate and recover

```powershell
# Status and logs
docker compose --project-directory . ps
docker compose --project-directory . logs --tail 100 cms-api
docker compose --project-directory . logs --tail 100 framer-bridge
docker compose --project-directory . logs --tail 100 cloudflared

# Stop without deleting data
docker compose --project-directory . down

# Start again (add -Build only after changing the app code)
.\start-cms.ps1
```

The local prototype stores its fallback data in Docker volume `cms-editor-data`.
Do not run `docker compose down --volumes` unless you intentionally want to
erase that local fallback data.

## Framer check

When `CMS_PROVIDER=framer`, run this after startup to verify the collection and
field mapping without reading post content:

```powershell
.\start-cms.ps1 -CheckFramer
```
