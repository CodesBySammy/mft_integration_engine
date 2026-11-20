# ==============================================================================
# MFT Enterprise Integration Engine - 1-Line Remote Installer
# ==============================================================================
# Usage:
#   irm https://raw.githubusercontent.com/CodesBySammy/mft_integration_engine/main/scripts/quick-install.ps1 | iex
# ==============================================================================
[CmdletBinding()]
param(
    [string]$InstallPath = "$HOME\mft_integration_engine"
)

$ErrorActionPreference = "Stop"

Write-Host "=================================================================" -ForegroundColor Cyan
Write-Host "   MFT Enterprise Engine - 1-Click Remote Quick Installer       " -ForegroundColor Cyan
Write-Host "=================================================================" -ForegroundColor Cyan
Write-Host "Target Directory: $InstallPath" -ForegroundColor DarkGray

if (-not (Test-Path $InstallPath)) {
    New-Item -ItemType Directory -Path $InstallPath -Force | Out-Null
}

$zipUrl = "https://github.com/CodesBySammy/mft_integration_engine/archive/refs/heads/main.zip"
$tempZip = Join-Path $env:TEMP "mft_engine_main.zip"
$tempExtract = Join-Path $env:TEMP "mft_extract_$(Get-Random)"

Write-Host "`n[1/3] Downloading latest release from GitHub..." -ForegroundColor Yellow
[Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12
Invoke-WebRequest -Uri $zipUrl -OutFile $tempZip -UseBasicParsing

Write-Host "[2/3] Extracting files into $InstallPath..." -ForegroundColor Yellow
Expand-Archive -Path $tempZip -DestinationPath $tempExtract -Force
$innerFolder = (Get-ChildItem -Path $tempExtract -Directory | Select-Object -First 1).FullName
Copy-Item -Path "$innerFolder\*" -Destination $InstallPath -Recurse -Force

Remove-Item -Path $tempZip, $tempExtract -Recurse -Force -ErrorAction SilentlyContinue

Write-Host "[3/3] Launching automated setup & starting all dashboards..." -ForegroundColor Yellow
Set-Location $InstallPath
& ".\setup.bat"
