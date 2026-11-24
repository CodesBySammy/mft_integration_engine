# ==============================================================================
# MFT Enterprise Integration Engine - Start All Services
# ==============================================================================
[CmdletBinding()]
param()

$RepoRoot = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path

# 0. Clean up any stale or orphan processes to prevent port conflicts
Write-Host "Ensuring clean port state (2022, 2222, 9080, 9081, 8080)..." -ForegroundColor Yellow
Get-Process -Name "sftpgo" -ErrorAction SilentlyContinue | Stop-Process -Force -ErrorAction SilentlyContinue
Get-CimInstance Win32_Process -Filter "Name = 'java.exe'" -ErrorAction SilentlyContinue |
    Where-Object { $_.CommandLine -like "*mft-integration-engine*" } |
    ForEach-Object { Stop-Process -Id $_.ProcessId -Force -ErrorAction SilentlyContinue }

Start-Sleep -Seconds 1

# 1. Locate Java 21+
$javaExe = $null
$cmd = Get-Command "java" -ErrorAction SilentlyContinue
if ($cmd) { $javaExe = $cmd.Source }
if (-not $javaExe -and $env:JAVA_HOME) {
    $candidate = Join-Path $env:JAVA_HOME "bin\java.exe"
    if (Test-Path $candidate) { $javaExe = $candidate }
}
if (-not $javaExe) {
    $candidates = @(
        (Get-ChildItem "C:\Program Files\Eclipse Adoptium" -Filter "java.exe" -Recurse -ErrorAction SilentlyContinue | Select-Object -First 1),
        (Get-ChildItem "C:\Program Files\Java" -Filter "java.exe" -Recurse -ErrorAction SilentlyContinue | Select-Object -First 1),
        (Get-ChildItem (Join-Path $RepoRoot "jdk") -Filter "java.exe" -Recurse -ErrorAction SilentlyContinue | Select-Object -First 1)
    )
    foreach ($c in $candidates) {
        if ($c -and (Test-Path $c.FullName)) { $javaExe = $c.FullName; break }
    }
}
if (-not $javaExe) { $javaExe = "java" }

# 2. Locate SFTPGo
$sftpgoDir = Join-Path $RepoRoot "sftpgo"
$sftpgoExe = Join-Path $sftpgoDir "sftpgo.exe"
if (-not (Test-Path $sftpgoExe) -and (Test-Path "D:\sftpgo\sftpgo.exe")) {
    $sftpgoDir = "D:\sftpgo"
    $sftpgoExe = "D:\sftpgo\sftpgo.exe"
}

if (-not (Test-Path $sftpgoExe)) {
    Write-Host "SFTPGo not found. Running setup first..." -ForegroundColor Yellow
    & (Join-Path $PSScriptRoot "setup.ps1")
    return
}

# 3. Ensure static web templates exist in sftpgo working directory (prevents UI loading hang)
$bundleDir = Join-Path $RepoRoot "sftpgo-bundle"
if (Test-Path $bundleDir) {
    if (-not (Test-Path (Join-Path $sftpgoDir "templates"))) {
        Copy-Item -Path (Join-Path $bundleDir "templates") -Destination $sftpgoDir -Recurse -Force -ErrorAction SilentlyContinue
    }
    if (-not (Test-Path (Join-Path $sftpgoDir "static"))) {
        Copy-Item -Path (Join-Path $bundleDir "static") -Destination $sftpgoDir -Recurse -Force -ErrorAction SilentlyContinue
    }
    if (-not (Test-Path (Join-Path $sftpgoDir "openapi"))) {
        Copy-Item -Path (Join-Path $bundleDir "openapi") -Destination $sftpgoDir -Recurse -Force -ErrorAction SilentlyContinue
    }
}

$instAConfig = Join-Path $sftpgoDir "instance-a\config"
$instBConfig = Join-Path $sftpgoDir "instance-b\config"
$dataDirA = Join-Path $sftpgoDir "instance-a\data\mft_source"
$uploadDirA = Join-Path $dataDirA "upload"
$dataDirB = Join-Path $sftpgoDir "instance-b\data\mft_destination"
$incomingDirB = Join-Path $dataDirB "incoming"

# Ensure all directories exist
New-Item -ItemType Directory -Path $instAConfig, $instBConfig, $uploadDirA, $incomingDirB -Force | Out-Null

# Resolve canonical paths (dereferences any junctions cleanly)
$canonicalDataDirA = (Resolve-Path $dataDirA).Path
$canonicalDataDirB = (Resolve-Path $dataDirB).Path
$canonicalSftpgoDir = (Resolve-Path $sftpgoDir).Path

# Dynamically write portable users_seed.json for Source (mft_source + admin)
$seedUserA = @{
    users = @(
        @{
            username = "mft_source"
            password = "source_pass"
            status = 1
            home_dir = $canonicalDataDirA
            permissions = @{
                "/" = @("list", "download", "upload", "overwrite", "rename", "create_dirs", "delete")
            }
        }
    )
    admins = @(
        @{
            username = "sameermahindru69@gmail.com"
            password = "Boobremon@69"
            status = 1
            permissions = @("*")
        }
    )
}
Set-Content -Path (Join-Path $instAConfig "users_seed.json") -Value ($seedUserA | ConvertTo-Json -Depth 5) -Force

# Dynamically write portable users_seed.json for Destination (mft_dest + mft_destination + admin)
$seedUserB = @{
    users = @(
        @{
            username = "mft_dest"
            password = "dest_pass"
            status = 1
            home_dir = $canonicalDataDirB
            permissions = @{
                "/" = @("list", "download", "upload", "overwrite", "rename", "create_dirs", "delete")
            }
        },
        @{
            username = "mft_destination"
            password = "dest_pass"
            status = 1
            home_dir = $canonicalDataDirB
            permissions = @{
                "/" = @("list", "download", "upload", "overwrite", "rename", "create_dirs", "delete")
            }
        }
    )
    admins = @(
        @{
            username = "myselfsammy69@gmail.com"
            password = "Boobremon@70"
            status = 1
            permissions = @("*")
        }
    )
}
Set-Content -Path (Join-Path $instBConfig "users_seed.json") -Value ($seedUserB | ConvertTo-Json -Depth 5) -Force

# Dynamically set database paths in sftpgo.json to match current machine
$configAFile = Join-Path $instAConfig "sftpgo.json"
if (Test-Path $configAFile) {
    $cfgA = Get-Content $configAFile -Raw | ConvertFrom-Json
    $targetDbA = Join-Path $canonicalSftpgoDir "instance-a\sftpgo.db"
    $cfgA.data_provider.name = $targetDbA
    Set-Content -Path $configAFile -Value ($cfgA | ConvertTo-Json -Depth 5) -Force
}

$configBFile = Join-Path $instBConfig "sftpgo.json"
if (Test-Path $configBFile) {
    $cfgB = Get-Content $configBFile -Raw | ConvertFrom-Json
    $targetDbB = Join-Path $canonicalSftpgoDir "instance-b\sftpgo.db"
    $cfgB.data_provider.name = $targetDbB
    Set-Content -Path $configBFile -Value ($cfgB | ConvertTo-Json -Depth 5) -Force
}

# Update paths.json
$pathsConfig = @{
    source_folder = (Resolve-Path $uploadDirA).Path
    destination_folder = (Resolve-Path $incomingDirB).Path
}
Set-Content -Path (Join-Path $PSScriptRoot "paths.json") -Value ($pathsConfig | ConvertTo-Json -Depth 2) -Force

$engineDir = Join-Path $RepoRoot "mft-integration-engine"
$jarPath = Join-Path $RepoRoot "bin\mft-integration-engine-0.0.1-SNAPSHOT.jar"
if (-not (Test-Path $jarPath)) {
    $jarPath = Join-Path $engineDir "target\mft-integration-engine-0.0.1-SNAPSHOT.jar"
}

# 4. Start SFTP Server A (Port 2022 / Web 9080)
Write-Host "Starting SFTPGo Server A (Port 2022, Web 9080)..." -ForegroundColor Cyan
Start-Process -FilePath $sftpgoExe -ArgumentList "serve --config-dir `"$instAConfig`" --loaddata-from `"$instAConfig\users_seed.json`" --loaddata-mode 0" -WorkingDirectory $sftpgoDir -WindowStyle Minimized

# 5. Start SFTP Server B (Port 2222 / Web 9081)
Write-Host "Starting SFTPGo Server B (Port 2222, Web 9081)..." -ForegroundColor Cyan
Start-Process -FilePath $sftpgoExe -ArgumentList "serve --config-dir `"$instBConfig`" --loaddata-from `"$instBConfig\users_seed.json`" --loaddata-mode 0" -WorkingDirectory $sftpgoDir -WindowStyle Minimized

# 6. Start MFT Integration Engine (Port 8080)
Write-Host "Starting MFT Integration Engine (Port 8080)..." -ForegroundColor Cyan
if (Test-Path $jarPath) {
    Start-Process -FilePath $javaExe -ArgumentList "-jar `"$jarPath`" --spring.profiles.active=local" -WorkingDirectory $RepoRoot -WindowStyle Minimized
} else {
    Start-Process -FilePath "cmd.exe" -ArgumentList "/c cd /d `"$engineDir`" && mvnw.cmd spring-boot:run -Dspring-boot.run.profiles=local" -WorkingDirectory $engineDir -WindowStyle Minimized
}

# 7. Wait and Verify
Write-Host "Waiting for servers to initialize..." -ForegroundColor Yellow
Start-Sleep -Seconds 4

Write-Host "`n============================================================" -ForegroundColor Green
Write-Host " All MFT Services are LIVE!" -ForegroundColor Green
Write-Host "============================================================" -ForegroundColor Green
Write-Host "  MFT Dashboard      : http://localhost:8080" -ForegroundColor Cyan
Write-Host "  Source Web Client  : http://localhost:9080/web/client (mft_source / source_pass)" -ForegroundColor White
Write-Host "  Source Web Admin   : http://localhost:9080/web/admin (sameermahindru69@gmail.com / Boobremon@69)" -ForegroundColor White
Write-Host "  Dest Web Client    : http://localhost:9081/web/client (mft_dest / dest_pass)" -ForegroundColor White
Write-Host "  Dest Web Admin     : http://localhost:9081/web/admin (myselfsammy69@gmail.com / Boobremon@70)" -ForegroundColor White
Write-Host "  Source SFTP Port   : 2022" -ForegroundColor DarkGray
Write-Host "  Dest SFTP Port     : 2222" -ForegroundColor DarkGray

$pathsFile = Join-Path $PSScriptRoot "paths.json"
if (Test-Path $pathsFile) {
    $paths = Get-Content $pathsFile -Raw | ConvertFrom-Json
    Write-Host "`nTransfer Folders:" -ForegroundColor Cyan
    Write-Host "  -> Drop files in   : $($paths.source_folder)" -ForegroundColor White
    Write-Host "  -> Arrives at      : $($paths.destination_folder)" -ForegroundColor White
}
Write-Host "============================================================`n" -ForegroundColor Green

Start-Process "http://localhost:8080"
Start-Process "http://localhost:9080/web/client"
