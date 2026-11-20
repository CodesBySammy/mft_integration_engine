# ==============================================================================
# MFT Enterprise Integration Engine - Automated 1-Click Setup & Bootstrap Script
# ==============================================================================
[CmdletBinding()]
param()

$ErrorActionPreference = "Stop"
$RepoRoot = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path

Write-Host "=================================================================" -ForegroundColor Cyan
Write-Host "   MFT Enterprise Integration Engine - Automated Setup          " -ForegroundColor Cyan
Write-Host "=================================================================" -ForegroundColor Cyan
Write-Host "Project Root : $RepoRoot" -ForegroundColor DarkGray

# 1. Java 21 Check
Write-Host "`n[1/6] Checking Java Environment..." -ForegroundColor Yellow
try {
    $javaVer = java -version 2>&1 | Out-String
    if ($javaVer -match 'version "(\d+)') {
        $major = [int]$matches[1]
        if ($major -lt 21) {
            Write-Host "WARNING: Found Java version $major. Java 21+ is recommended." -ForegroundColor Yellow
        } else {
            Write-Host "  OK: Java $major detected." -ForegroundColor Green
        }
    } else {
        Write-Host "  OK: Java runtime available." -ForegroundColor Green
    }
} catch {
    Write-Host "ERROR: Java not found in PATH! Please install Java 21 (JDK 21) from https://adoptium.net" -ForegroundColor Red
    exit 1
}

# 2. SFTPGo Binary Discovery or Automated Download
Write-Host "`n[2/6] Verifying SFTPGo Server Engine..." -ForegroundColor Yellow
$sftpgoDir = Join-Path $RepoRoot "sftpgo"
$sftpgoExe = Join-Path $sftpgoDir "sftpgo.exe"

if (-not (Test-Path $sftpgoExe)) {
    # Check if existing external SFTPGo exists at D:\sftpgo
    if (Test-Path "D:\sftpgo\sftpgo.exe") {
        Write-Host "  Found SFTPGo at D:\sftpgo. Reusing local installation." -ForegroundColor Green
        $sftpgoDir = "D:\sftpgo"
        $sftpgoExe = "D:\sftpgo\sftpgo.exe"
    } else {
        Write-Host "  SFTPGo binary not found locally. Downloading portable SFTPGo v2.6.2 for Windows..." -ForegroundColor Cyan
        New-Item -ItemType Directory -Path $sftpgoDir -Force | Out-Null
        $zipPath = Join-Path $sftpgoDir "sftpgo.zip"
        $downloadUrl = "https://github.com/drakkan/sftpgo/releases/download/v2.6.2/sftpgo_v2.6.2_windows_x86_64.zip"

        try {
            [Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12
            Invoke-WebRequest -Uri $downloadUrl -OutFile $zipPath -UseBasicParsing
            Expand-Archive -Path $zipPath -DestinationPath $sftpgoDir -Force
            Remove-Item -Path $zipPath -Force -ErrorAction SilentlyContinue
            Write-Host "  Successfully downloaded and unpacked SFTPGo." -ForegroundColor Green
        } catch {
            Write-Host "ERROR: Failed to automatically download SFTPGo ($($_.Exception.Message))." -ForegroundColor Red
            Write-Host "Please place 'sftpgo.exe' inside: $sftpgoDir" -ForegroundColor Yellow
            exit 1
        }
    }
} else {
    Write-Host "  OK: SFTPGo binary found at $sftpgoExe" -ForegroundColor Green
}

# 3. Provision Server A and Server B Configuration & Data Directories
Write-Host "`n[3/6] Provisioning Dual SFTP Servers & Seed Users..." -ForegroundColor Yellow

$instADir = Join-Path $sftpgoDir "instance-a"
$instBDir = Join-Path $sftpgoDir "instance-b"
$instAConfig = Join-Path $instADir "config"
$instBConfig = Join-Path $instBDir "config"

$srcUploadDir = Join-Path $instADir "data\mft_source\upload"
$dstIncomingDir = Join-Path $instBDir "data\mft_destination\incoming"

New-Item -ItemType Directory -Path $instAConfig, $instBConfig, $srcUploadDir, $dstIncomingDir -Force | Out-Null

# Copy web UI static / templates / openapi if available in bundle
$bundleDir = Join-Path $RepoRoot "sftpgo-bundle"
if (Test-Path $bundleDir) {
    Copy-Item -Path (Join-Path $bundleDir "templates"), (Join-Path $bundleDir "static"), (Join-Path $bundleDir "openapi") -Destination $instAConfig -Recurse -Force -ErrorAction SilentlyContinue
    Copy-Item -Path (Join-Path $bundleDir "templates"), (Join-Path $bundleDir "static"), (Join-Path $bundleDir "openapi") -Destination $instBConfig -Recurse -Force -ErrorAction SilentlyContinue
}

# Write instance-a config (Port 2022, WebAdmin 9080)
$jsonConfigA = @"
{
  "common": { "idle_timeout": 15 },
  "sftpd": { "bindings": [ { "port": 2022, "address": "0.0.0.0" } ] },
  "httpd": {
    "bindings": [ { "port": 9080, "address": "0.0.0.0", "enable_web_admin": true, "enable_web_client": true, "enable_rest_api": true } ],
    "templates_path": "templates",
    "static_files_path": "static",
    "openapi_path": "openapi"
  },
  "smtp": { "templates_path": "templates" },
  "data_provider": {
    "driver": "sqlite",
    "name": "$($instADir.Replace('\', '\\'))\\sftpgo.db"
  }
}
"@
Set-Content -Path (Join-Path $instAConfig "sftpgo.json") -Value $jsonConfigA -Force

# Write instance-b config (Port 2222, WebAdmin 9081)
$jsonConfigB = @"
{
  "common": { "idle_timeout": 15 },
  "sftpd": { "bindings": [ { "port": 2222, "address": "0.0.0.0" } ] },
  "httpd": {
    "bindings": [ { "port": 9081, "address": "0.0.0.0", "enable_web_admin": true, "enable_web_client": true, "enable_rest_api": true } ],
    "templates_path": "templates",
    "static_files_path": "static",
    "openapi_path": "openapi"
  },
  "smtp": { "templates_path": "templates" },
  "data_provider": {
    "driver": "sqlite",
    "name": "$($instBDir.Replace('\', '\\'))\\sftpgo.db"
  }
}
"@
Set-Content -Path (Join-Path $instBConfig "sftpgo.json") -Value $jsonConfigB -Force

# Write users_seed.json for Source (mft_source)
$seedUserA = @"
{
  "users": [
    {
      "username": "mft_source",
      "password": "source_pass",
      "status": 1,
      "home_dir": "$((Join-Path $instADir 'data\mft_source').Replace('\', '\\'))",
      "permissions": {
        "/": [ "list", "download", "delete" ]
      }
    }
  ]
}
"@
Set-Content -Path (Join-Path $instAConfig "users_seed.json") -Value $seedUserA -Force

# Write users_seed.json for Destination (mft_destination)
$seedUserB = @"
{
  "users": [
    {
      "username": "mft_destination",
      "password": "dest_pass",
      "status": 1,
      "home_dir": "$((Join-Path $instBDir 'data\mft_destination').Replace('\', '\\'))",
      "permissions": {
        "/": [ "list", "download", "upload", "overwrite", "rename", "create_dirs", "delete" ]
      }
    }
  ]
}
"@
Set-Content -Path (Join-Path $instBConfig "users_seed.json") -Value $seedUserB -Force

Write-Host "  Source Server A     : Port 2022 (SFTP) | WebAdmin: http://localhost:9080" -ForegroundColor Green
Write-Host "    Folder            : $srcUploadDir" -ForegroundColor DarkGray
Write-Host "    User / Pass       : mft_source / source_pass" -ForegroundColor DarkGray
Write-Host "  Destination Server B: Port 2222 (SFTP) | WebAdmin: http://localhost:9081" -ForegroundColor Green
Write-Host "    Folder            : $dstIncomingDir" -ForegroundColor DarkGray
Write-Host "    User / Pass       : mft_destination / dest_pass" -ForegroundColor DarkGray

# 4. Provision MFT Workspace Directories
Write-Host "`n[4/6] Initializing MFT Engine Storage & State..." -ForegroundColor Yellow
$workspaceDir = Join-Path $RepoRoot "workspace"
New-Item -ItemType Directory -Path (Join-Path $workspaceDir "downloads"), (Join-Path $workspaceDir "staging"), (Join-Path $workspaceDir "quarantine"), (Join-Path $workspaceDir "status") -Force | Out-Null
Write-Host "  OK: Workspace verified at $workspaceDir" -ForegroundColor Green

# 5. Build Spring Boot Executable JAR if needed
Write-Host "`n[5/6] Verifying Spring Boot Application Package..." -ForegroundColor Yellow
$engineDir = Join-Path $RepoRoot "mft-integration-engine"
$jarPath = Join-Path $engineDir "target\mft-integration-engine-0.0.1-SNAPSHOT.jar"

if (-not (Test-Path $jarPath)) {
    Write-Host "  Packaging Spring Boot Application..." -ForegroundColor Cyan
    Push-Location $engineDir
    try {
        & .\mvnw.cmd package -DskipTests
    } finally {
        Pop-Location
    }
}
Write-Host "  OK: Executable JAR ready." -ForegroundColor Green

# 6. Launch Services & Open Web Dashboards
Write-Host "`n[6/6] Starting Services & Launching Control Panels..." -ForegroundColor Yellow

# Kill any previous stale instances
Get-Process -Name "sftpgo" -ErrorAction SilentlyContinue | Stop-Process -Force -ErrorAction SilentlyContinue

# Launch Server A
$argA = "serve --config-dir `"$instAConfig`" --loaddata-from `"$instAConfig\users_seed.json`" --loaddata-mode 0"
Start-Process -FilePath $sftpgoExe -ArgumentList $argA -WorkingDirectory $sftpgoDir -WindowStyle Minimized

# Launch Server B
$argB = "serve --config-dir `"$instBConfig`" --loaddata-from `"$instBConfig\users_seed.json`" --loaddata-mode 0"
Start-Process -FilePath $sftpgoExe -ArgumentList $argB -WorkingDirectory $sftpgoDir -WindowStyle Minimized

# Launch Spring Boot Engine
$javaArgs = "-jar `"$jarPath`" --spring.profiles.active=local"
Start-Process -FilePath "java" -ArgumentList $javaArgs -WorkingDirectory $engineDir -WindowStyle Minimized

Write-Host "  Waiting for engine initialization..." -ForegroundColor Cyan
$started = $false
for ($i = 0; $i -lt 20; $i++) {
    Start-Sleep -Seconds 1
    try {
        $res = Invoke-RestMethod -Uri "http://localhost:8080/api/health" -TimeoutSec 2 -ErrorAction SilentlyContinue
        if ($res.data.status -eq "UP") {
            $started = $true
            break
        }
    } catch {}
}

if ($started) {
    Write-Host "`n=================================================================" -ForegroundColor Green
    Write-Host "   SUCCESS: All Services Are Running & Ready!                   " -ForegroundColor Green
    Write-Host "=================================================================" -ForegroundColor Green
} else {
    Write-Host "`nServices started in background. Connecting to dashboards..." -ForegroundColor Yellow
}

# Open all dashboards in default browser
Start-Process "http://localhost:8080"
Start-Process "http://localhost:9080/web/admin"
Start-Process "http://localhost:9081/web/admin"

Write-Host "`nDashboards Opened:" -ForegroundColor Cyan
Write-Host "  -> MFT Control Dashboard   : http://localhost:8080" -ForegroundColor White
Write-Host "  -> Server A (Source) Admin : http://localhost:9080/web/admin" -ForegroundColor White
Write-Host "  -> Server B (Dest) Admin   : http://localhost:9081/web/admin" -ForegroundColor White
Write-Host "`nDrop files into: $srcUploadDir" -ForegroundColor Yellow
Write-Host "Files transfer to: $dstIncomingDir`n" -ForegroundColor Yellow
