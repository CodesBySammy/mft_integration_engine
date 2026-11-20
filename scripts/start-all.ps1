# ==============================================================================
# MFT Enterprise Integration Engine - Start All Services
# ==============================================================================
[CmdletBinding()]
param()

$RepoRoot = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path

# Locate Java
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

# Locate SFTPGo
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

$instAConfig = Join-Path $sftpgoDir "instance-a\config"
$instBConfig = Join-Path $sftpgoDir "instance-b\config"
$engineDir = Join-Path $RepoRoot "mft-integration-engine"
$jarPath = Join-Path $RepoRoot "bin\mft-integration-engine-0.0.1-SNAPSHOT.jar"
if (-not (Test-Path $jarPath)) {
    $jarPath = Join-Path $engineDir "target\mft-integration-engine-0.0.1-SNAPSHOT.jar"
}

Write-Host "Starting SFTPGo Server A (Port 2022)..." -ForegroundColor Cyan
Start-Process -FilePath $sftpgoExe -ArgumentList "serve --config-dir `"$instAConfig`" --loaddata-from `"$instAConfig\users_seed.json`" --loaddata-mode 0" -WorkingDirectory $sftpgoDir -WindowStyle Minimized

Write-Host "Starting SFTPGo Server B (Port 2222)..." -ForegroundColor Cyan
Start-Process -FilePath $sftpgoExe -ArgumentList "serve --config-dir `"$instBConfig`" --loaddata-from `"$instBConfig\users_seed.json`" --loaddata-mode 0" -WorkingDirectory $sftpgoDir -WindowStyle Minimized

Write-Host "Starting MFT Integration Engine (Port 8080)..." -ForegroundColor Cyan
if (Test-Path $jarPath) {
    Start-Process -FilePath $javaExe -ArgumentList "-jar `"$jarPath`" --spring.profiles.active=local" -WorkingDirectory $RepoRoot -WindowStyle Minimized
} else {
    Start-Process -FilePath "cmd.exe" -ArgumentList "/c cd /d `"$engineDir`" && mvnw.cmd spring-boot:run -Dspring-boot.run.profiles=local" -WorkingDirectory $engineDir -WindowStyle Minimized
}

Start-Sleep -Seconds 5
Start-Process "http://localhost:8080"
Start-Process "http://localhost:9080/web/admin"
Start-Process "http://localhost:9081/web/admin"

Write-Host "All services started! Dashboards opened." -ForegroundColor Green

$pathsFile = Join-Path $PSScriptRoot "paths.json"
if (Test-Path $pathsFile) {
    $paths = Get-Content $pathsFile -Raw | ConvertFrom-Json
    Write-Host "`nTransfer Folders:" -ForegroundColor Cyan
    Write-Host "  -> Drop files in  : $($paths.source_folder)" -ForegroundColor White
    Write-Host "  -> Arrives at     : $($paths.destination_folder)" -ForegroundColor White
}
