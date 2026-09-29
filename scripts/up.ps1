# VisionOne local stack, for PowerShell where `make` is not installed.
param([switch]$Reset)

$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
$compose = Join-Path $root 'infra/docker-compose.yml'

if ($Reset) {
    docker compose -f $compose down -v
    Write-Host 'Stack stopped and all data deleted.' -ForegroundColor Yellow
    exit 0
}

docker compose -f $compose up -d
Write-Host ''
Write-Host 'Waiting for services to report healthy...' -ForegroundColor Cyan

$deadline = (Get-Date).AddMinutes(3)
do {
    Start-Sleep -Seconds 5
    $status = docker compose -f $compose ps --format json | ConvertFrom-Json
    $unhealthy = @($status | Where-Object { $_.Health -and $_.Health -ne 'healthy' })
    Write-Host ('  still starting: {0}' -f ($unhealthy.Count)) -ForegroundColor DarkGray
} while ($unhealthy.Count -gt 0 -and (Get-Date) -lt $deadline)

docker compose -f $compose ps
Write-Host ''
Write-Host 'Postgres  localhost:5432   visionone / visionone' -ForegroundColor Green
Write-Host 'Keycloak  http://localhost:8180   admin / admin' -ForegroundColor Green
Write-Host ''
Write-Host 'Next:  cd backend; .\gradlew.bat bootRun --args="--spring.profiles.active=local"'
Write-Host '       cd frontend; npm install; npm run dev'
