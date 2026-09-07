# Required values live in Windows User environment variables.
param(
    [switch]$Build,
    [switch]$CheckFramer
)

$ErrorActionPreference = "Stop"

function Get-UserEnvironmentValue([string]$Name) {
    [Environment]::GetEnvironmentVariable($Name, "User")
}

$provider = Get-UserEnvironmentValue "CMS_PROVIDER"
if ([string]::IsNullOrWhiteSpace($provider)) {
    $provider = "file"
}
$provider = $provider.ToLowerInvariant()
if ($provider -notin @("file", "framer")) {
    throw "CMS_PROVIDER must be either 'file' or 'framer'."
}

$required = @(
    "CMS_ACCESS_TOKENS",
    "CMS_R2_ENDPOINT",
    "CMS_R2_ACCESS_KEY_ID",
    "CMS_R2_SECRET_ACCESS_KEY",
    "CF_TUNNEL_TOKEN"
)
if ($provider -eq "framer") {
    $required += "FRAMER_API_KEY"
}

$missing = $required | Where-Object { [string]::IsNullOrWhiteSpace((Get-UserEnvironmentValue $_)) }
if ($missing) {
    throw "Set these Windows user environment variables, then open a new PowerShell window: $($missing -join ', ')"
}

# Compose reads process variables; refresh them from the User values checked above.
foreach ($name in $required) {
    [Environment]::SetEnvironmentVariable($name, (Get-UserEnvironmentValue $name), "Process")
}

$composeArgs = @("--project-directory", $PSScriptRoot, "-f", (Join-Path $PSScriptRoot "compose.yaml"))
$temporaryBridgeToken = $null

if ($provider -eq "framer") {
    $bytes = [byte[]]::new(32)
    $random = [System.Security.Cryptography.RandomNumberGenerator]::Create()
    try {
        $random.GetBytes($bytes)
    } finally {
        $random.Dispose()
    }
    $temporaryBridgeToken = [Convert]::ToBase64String($bytes)
    [Environment]::SetEnvironmentVariable("CMS_FRAMER_BRIDGE_TOKEN", $temporaryBridgeToken, "Process")

    $collection = Get-UserEnvironmentValue "CMS_FRAMER_COLLECTION"
    if ([string]::IsNullOrWhiteSpace($collection)) {
        $collection = "blog-posts"
    }
    [Environment]::SetEnvironmentVariable("CMS_FRAMER_COLLECTION", $collection, "Process")
    $composeArgs += @("-f", (Join-Path $PSScriptRoot "compose.framer.yaml"))
}

try {
    if ($CheckFramer) {
        docker compose @composeArgs exec -T framer-bridge node src/check.mjs
        if ($LASTEXITCODE -ne 0) {
            throw "Framer schema check failed (exit code $LASTEXITCODE)."
        }
        return
    }

    if ($Build) {
        docker compose @composeArgs up --build --detach
    } else {
        docker compose @composeArgs up --detach
    }
    if ($LASTEXITCODE -ne 0) {
        throw "Docker Compose startup failed (exit code $LASTEXITCODE). See the build or startup error above."
    }
    docker compose @composeArgs ps
    if ($LASTEXITCODE -ne 0) {
        throw "Docker Compose status failed (exit code $LASTEXITCODE)."
    }
} finally {
    if ($temporaryBridgeToken) {
        [Environment]::SetEnvironmentVariable("CMS_FRAMER_BRIDGE_TOKEN", $null, "Process")
    }
}
