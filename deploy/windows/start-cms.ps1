# Required values live in Windows User environment variables.
param([switch]$Build)

$ErrorActionPreference = "Stop"

$required = @(
    "CMS_ACCESS_TOKENS",
    "CMS_R2_ENDPOINT",
    "CMS_R2_ACCESS_KEY_ID",
    "CMS_R2_SECRET_ACCESS_KEY",
    "CF_TUNNEL_TOKEN"
)

$missing = $required | Where-Object { [string]::IsNullOrWhiteSpace([Environment]::GetEnvironmentVariable($_, "User")) }
if ($missing) {
    throw "Set these Windows user environment variables, then open a new PowerShell window: $($missing -join ', ')"
}

# Compose reads process variables; refresh them from the User values checked above.
foreach ($name in $required) {
    [Environment]::SetEnvironmentVariable($name, [Environment]::GetEnvironmentVariable($name, "User"), "Process")
}

if ($Build) {
    docker compose --project-directory $PSScriptRoot up --build --detach
} else {
    docker compose --project-directory $PSScriptRoot up --detach
}
if ($LASTEXITCODE -ne 0) {
    throw "Docker Compose startup failed (exit code $LASTEXITCODE). See the build or startup error above."
}
docker compose --project-directory $PSScriptRoot ps
if ($LASTEXITCODE -ne 0) {
    throw "Docker Compose status failed (exit code $LASTEXITCODE)."
}