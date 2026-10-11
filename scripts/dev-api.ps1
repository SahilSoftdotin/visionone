# Starts the API for local development with the settings in infra/.env.
#
# Why this exists: the Healthie adapter needs about ten environment variables, two of them
# credentials, and typing them on every run is how one of them ends up wrong in a way that is
# silent - a missing ICS URL reports NOT_CONNECTED and syncs nothing, and a wrong timezone puts
# every appointment an hour out while looking perfectly fine.
#
# Run this in your own terminal rather than letting the agent background it: a shell you started
# is not subject to the agent's low-memory reaping.
#
#   .\scripts\dev-api.ps1
#
# Then, in a second terminal:
#
#   cd frontend; npm run dev

$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
$envFile = Join-Path $root 'infra\.env'

if (-not (Test-Path $envFile)) {
    Write-Host "Missing $envFile - copy the settings from infra/.env.prod.example" -ForegroundColor Red
    exit 1
}

$loaded = 0
foreach ($line in Get-Content $envFile) {
    if ($line -match '^\s*#' -or $line -notmatch '=') { continue }
    $pair = $line -split '=', 2
    $name = $pair[0].Trim()
    $value = $pair[1].Trim()
    if ($name) {
        [Environment]::SetEnvironmentVariable($name, $value, 'Process')
        $loaded += 1
    }
}

Write-Host "Loaded $loaded settings from infra/.env" -ForegroundColor Green
Write-Host "Scheduling provider : $env:VISIONONE_PROVIDER_SCHEDULING" -ForegroundColor Green
Write-Host "Healthie feed zone  : $env:VISIONONE_HEALTHIE_ICS_ZONE" -ForegroundColor Green
# Deliberately never printed in full - the access token is in the query string.
$icsHost = if ($env:VISIONONE_HEALTHIE_ICS_URL) { ($env:VISIONONE_HEALTHIE_ICS_URL -split '\?')[0] } else { '(not set)' }
Write-Host "Healthie feed       : $icsHost?..." -ForegroundColor Green
Write-Host ''

Set-Location (Join-Path $root 'backend')
& .\gradlew.bat bootRun --args='--spring.profiles.active=local --spring.devtools.restart.enabled=false'
