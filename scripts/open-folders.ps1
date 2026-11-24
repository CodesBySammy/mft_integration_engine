# ==============================================================================
# MFT Enterprise Integration Engine - Open Source and Destination Folders
# ==============================================================================
[CmdletBinding()]
param()

$RepoRoot = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path
$pathsFile = Join-Path $PSScriptRoot "paths.json"

if (Test-Path $pathsFile) {
    $paths = Get-Content $pathsFile -Raw | ConvertFrom-Json
    $src = $paths.source_folder
    $dst = $paths.destination_folder
} else {
    $src = Join-Path $RepoRoot "sftpgo\instance-a\data\mft_source\upload"
    $dst = Join-Path $RepoRoot "sftpgo\instance-b\data\mft_destination\incoming"
}

if (-not (Test-Path $src)) { New-Item -ItemType Directory -Path $src -Force | Out-Null }
if (-not (Test-Path $dst)) { New-Item -ItemType Directory -Path $dst -Force | Out-Null }

Write-Host "Opening Source Folder     : $src" -ForegroundColor Cyan
Write-Host "Opening Destination Folder: $dst" -ForegroundColor Cyan

Start-Process explorer.exe $src
Start-Process explorer.exe $dst
