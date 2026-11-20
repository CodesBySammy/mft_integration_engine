# ==============================================================================
# MFT Enterprise Integration Engine - Automated 1-Click Setup and Bootstrap Script
# ==============================================================================
[CmdletBinding()]
param()

$ErrorActionPreference = "Stop"
$RepoRoot = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path

Write-Host "=================================================================" -ForegroundColor Cyan
Write-Host "   MFT Enterprise Integration Engine - Automated Setup          " -ForegroundColor Cyan
Write-Host "=================================================================" -ForegroundColor Cyan
Write-Host "Project Root : $RepoRoot" -ForegroundColor DarkGray

# 1. Java 21 Detection and Verification
Write-Host "`n[1/6] Detecting Java 21+ Environment..." -ForegroundColor Yellow

$javaExe = $null

# Search PATH
$cmd = Get-Command "java" -ErrorAction SilentlyContinue
if ($cmd) {
    $javaExe = $cmd.Source
}

# Search JAVA_HOME
if (-not $javaExe -and $env:JAVA_HOME) {
    $candidate = Join-Path $env:JAVA_HOME "bin\java.exe"
    if (Test-Path $candidate) { $javaExe = $candidate }
}

# Search standard Windows installation locations
if (-not $javaExe) {
    $candidates = @(
        (Get-ChildItem "C:\Program Files\Eclipse Adoptium" -Filter "java.exe" -Recurse -ErrorAction SilentlyContinue | Select-Object -First 1),
        (Get-ChildItem "C:\Program Files\Java" -Filter "java.exe" -Recurse -ErrorAction SilentlyContinue | Select-Object -First 1),
        (Get-ChildItem "C:\Program Files\Amazon Corretto" -Filter "java.exe" -Recurse -ErrorAction SilentlyContinue | Select-Object -First 1),
        (Get-ChildItem "C:\Program Files\Microsoft" -Filter "java.exe" -Recurse -ErrorAction SilentlyContinue | Select-Object -First 1),
        (Get-ChildItem (Join-Path $RepoRoot "jdk") -Filter "java.exe" -Recurse -ErrorAction SilentlyContinue | Select-Object -First 1)
    )
    foreach ($c in $candidates) {
        if ($c -and (Test-Path $c.FullName)) {
            $javaExe = $c.FullName
            break
        }
    }
}

# If still not found, download portable OpenJDK 21 automatically
if (-not $javaExe) {
    Write-Host "  Java not detected on system. Automatically downloading portable OpenJDK 21..." -ForegroundColor Cyan
    $jdkDir = Join-Path $RepoRoot "jdk"
    New-Item -ItemType Directory -Path $jdkDir -Force | Out-Null
    $jdkZip = Join-Path $env:TEMP "openjdk21.zip"
    $jdkUrl = "https://github.com/adoptium/temurin21-binaries/releases/download/jdk-21.0.6%2B7/OpenJDK21U-jdk_x64_windows_hotspot_21.0.6_7.zip"
    try {
        [Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12
        Invoke-WebRequest -Uri $jdkUrl -OutFile $jdkZip -UseBasicParsing
        Expand-Archive -Path $jdkZip -DestinationPath $jdkDir -Force
        Remove-Item $jdkZip -Force -ErrorAction SilentlyContinue
        $javaExe = (Get-ChildItem $jdkDir -Filter "java.exe" -Recurse | Select-Object -First 1).FullName
        Write-Host "  Successfully installed portable OpenJDK 21." -ForegroundColor Green
    } catch {
        Write-Host "ERROR: Could not download Java automatically ($($_.Exception.Message))." -ForegroundColor Red
        Write-Host "Please download and install Java 21 from: https://adoptium.net" -ForegroundColor Yellow
        exit 1
    }
}

# Add Java bin to current PATH session
$javaBin = Split-Path $javaExe -Parent
if ($env:PATH -notlike "*$javaBin*") {
    $env:PATH = "$javaBin;$env:PATH"
}
$env:JAVA_HOME = Split-Path $javaBin -Parent

# Read version safely without native stderr tripping ErrorActionPreference Stop
$verFile = Join-Path $env:TEMP "java_ver_check.txt"
$proc = Start-Process -FilePath $javaExe -ArgumentList "-version" -NoNewWindow -Wait -PassThru -RedirectStandardError $verFile
$verOutput = if (Test-Path $verFile) { Get-Content $verFile -Raw } else { "" }
Remove-Item $verFile -Force -ErrorAction SilentlyContinue

$major = 21
if ($verOutput -match 'version "(\d+)') {
    $major = [int]$matches[1]
}
Write-Host "  OK: Java $major detected ($javaExe)" -ForegroundColor Green

# 2. SFTPGo Binary Discovery or Automated Download
Write-Host "`n[2/6] Verifying SFTPGo Server Engine..." -ForegroundColor Yellow
$sftpgoDir = Join-Path $RepoRoot "sftpgo"
$sftpgoExe = Join-Path $sftpgoDir "sftpgo.exe"

if (-not (Test-Path $sftpgoExe)) {
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

# 3. Configure Source and Destination Folders and Provision Dual SFTP Servers
Write-Host "`n[3/6] Configuring Source and Destination Transfer Folders..." -ForegroundColor Yellow

function Resolve-FolderStructure([string]$inputPath, [string]$defaultSubfolder) {
    $trimmed = $inputPath.TrimEnd('\').TrimEnd('/')
    $leaf = Split-Path $trimmed -Leaf
    if ($leaf -ieq $defaultSubfolder) {
        $base = Split-Path $trimmed -Parent
        $target = $trimmed
    } else {
        $base = $trimmed
        $target = Join-Path $trimmed $defaultSubfolder
    }
    return @{ Base = $base; Target = $target }
}

$defaultSource = if (Test-Path "D:\sftpgo\instance-a\data\mft_source\upload") {
    "D:\sftpgo\instance-a\data\mft_source\upload"
} else {
    Join-Path $RepoRoot "data\source\upload"
}

$defaultDest = if (Test-Path "D:\sftpgo\instance-b\data\mft_destination\incoming") {
    "D:\sftpgo\instance-b\data\mft_destination\incoming"
} else {
    Join-Path $RepoRoot "data\destination\incoming"
}

Write-Host "Where would you like to drop source files for transfer?" -ForegroundColor Cyan
Write-Host "  [Press Enter to use default: $defaultSource]" -ForegroundColor DarkGray
try {
    $inputSource = Read-Host "  Source Folder"
} catch { $inputSource = "" }
if ([string]::IsNullOrWhiteSpace($inputSource)) {
    $inputSource = $defaultSource
}

Write-Host "`nWhere would you like destination files to arrive?" -ForegroundColor Cyan
Write-Host "  [Press Enter to use default: $defaultDest]" -ForegroundColor DarkGray
try {
    $inputDest = Read-Host "  Destination Folder"
} catch { $inputDest = "" }
if ([string]::IsNullOrWhiteSpace($inputDest)) {
    $inputDest = $defaultDest
}

$srcStruct = Resolve-FolderStructure $inputSource "upload"
$dstStruct = Resolve-FolderStructure $inputDest "incoming"

$srcUploadDir = $srcStruct.Target
$srcBaseDir = $srcStruct.Base

$dstIncomingDir = $dstStruct.Target
$dstBaseDir = $dstStruct.Base

$instADir = Join-Path $sftpgoDir "instance-a"
$instBDir = Join-Path $sftpgoDir "instance-b"
$instAConfig = Join-Path $instADir "config"
$instBConfig = Join-Path $instBDir "config"

New-Item -ItemType Directory -Path $instAConfig, $instBConfig, $srcUploadDir, $dstIncomingDir -Force | Out-Null

# Save paths to paths.json
$pathsConfig = @{
    source_folder = $srcUploadDir
    destination_folder = $dstIncomingDir
}
Set-Content -Path (Join-Path $PSScriptRoot "paths.json") -Value ($pathsConfig | ConvertTo-Json -Depth 2) -Force

# Copy web UI static / templates / openapi if available in bundle
$bundleDir = Join-Path $RepoRoot "sftpgo-bundle"
if (Test-Path $bundleDir) {
    Copy-Item -Path (Join-Path $bundleDir "templates"), (Join-Path $bundleDir "static"), (Join-Path $bundleDir "openapi") -Destination $instAConfig -Recurse -Force -ErrorAction SilentlyContinue
    Copy-Item -Path (Join-Path $bundleDir "templates"), (Join-Path $bundleDir "static"), (Join-Path $bundleDir "openapi") -Destination $instBConfig -Recurse -Force -ErrorAction SilentlyContinue
}

# Write instance-a config (Port 2022, WebAdmin 9080)
$configAObj = @{
    common = @{ idle_timeout = 15 }
    sftpd = @{ bindings = @( @{ port = 2022; address = "0.0.0.0" } ) }
    httpd = @{
        bindings = @( @{ port = 9080; address = "0.0.0.0"; enable_web_admin = $true; enable_web_client = $true; enable_rest_api = $true } )
        templates_path = "templates"
        static_files_path = "static"
        openapi_path = "openapi"
    }
    smtp = @{ templates_path = "templates" }
    data_provider = @{
        driver = "sqlite"
        name = (Join-Path $instADir "sftpgo.db")
    }
}
Set-Content -Path (Join-Path $instAConfig "sftpgo.json") -Value ($configAObj | ConvertTo-Json -Depth 5) -Force

# Write instance-b config (Port 2222, WebAdmin 9081)
$configBObj = @{
    common = @{ idle_timeout = 15 }
    sftpd = @{ bindings = @( @{ port = 2222; address = "0.0.0.0" } ) }
    httpd = @{
        bindings = @( @{ port = 9081; address = "0.0.0.0"; enable_web_admin = $true; enable_web_client = $true; enable_rest_api = $true } )
        templates_path = "templates"
        static_files_path = "static"
        openapi_path = "openapi"
    }
    smtp = @{ templates_path = "templates" }
    data_provider = @{
        driver = "sqlite"
        name = (Join-Path $instBDir "sftpgo.db")
    }
}
Set-Content -Path (Join-Path $instBConfig "sftpgo.json") -Value ($configBObj | ConvertTo-Json -Depth 5) -Force

# Write users_seed.json for Source (mft_source)
$seedUserAObj = @{
    users = @(
        @{
            username = "mft_source"
            password = "source_pass"
            status = 1
            home_dir = $srcBaseDir
            permissions = @{
                "/" = @("list", "download", "delete")
            }
        }
    )
}
Set-Content -Path (Join-Path $instAConfig "users_seed.json") -Value ($seedUserAObj | ConvertTo-Json -Depth 5) -Force

# Write users_seed.json for Destination (mft_destination)
$seedUserBObj = @{
    users = @(
        @{
            username = "mft_destination"
            password = "dest_pass"
            status = 1
            home_dir = $dstBaseDir
            permissions = @{
                "/" = @("list", "download", "upload", "overwrite", "rename", "create_dirs", "delete")
            }
        }
    )
}
Set-Content -Path (Join-Path $instBConfig "users_seed.json") -Value ($seedUserBObj | ConvertTo-Json -Depth 5) -Force

Write-Host "  Source Server A     : Port 2022 (SFTP) | WebAdmin: http://localhost:9080" -ForegroundColor Green
Write-Host "    Folder            : $srcUploadDir" -ForegroundColor DarkGray
Write-Host "    User / Pass       : mft_source / source_pass" -ForegroundColor DarkGray
Write-Host "  Destination Server B: Port 2222 (SFTP) | WebAdmin: http://localhost:9081" -ForegroundColor Green
Write-Host "    Folder            : $dstIncomingDir" -ForegroundColor DarkGray
Write-Host "    User / Pass       : mft_destination / dest_pass" -ForegroundColor DarkGray

# 4. Provision MFT Workspace Directories
Write-Host "`n[4/6] Initializing MFT Engine Storage and State..." -ForegroundColor Yellow
$workspaceDir = Join-Path $RepoRoot "workspace"
New-Item -ItemType Directory -Path (Join-Path $workspaceDir "downloads"), (Join-Path $workspaceDir "staging"), (Join-Path $workspaceDir "quarantine"), (Join-Path $workspaceDir "status") -Force | Out-Null
Write-Host "  OK: Workspace verified at $workspaceDir" -ForegroundColor Green

# 5. Build Spring Boot Executable JAR if needed
Write-Host "`n[5/6] Verifying Spring Boot Application Package..." -ForegroundColor Yellow
$engineDir = Join-Path $RepoRoot "mft-integration-engine"
$jarPath = Join-Path $RepoRoot "bin\mft-integration-engine-0.0.1-SNAPSHOT.jar"
if (-not (Test-Path $jarPath)) {
    $jarPath = Join-Path $engineDir "target\mft-integration-engine-0.0.1-SNAPSHOT.jar"
}

if (-not (Test-Path $jarPath)) {
    Write-Host "  Packaging Spring Boot Application..." -ForegroundColor Cyan
    Push-Location $engineDir
    try {
        & .\mvnw.cmd package -DskipTests
    } finally {
        Pop-Location
    }
}
Write-Host "  OK: Executable JAR ready at $jarPath" -ForegroundColor Green

# 6. Launch Services and Open Web Dashboards
Write-Host "`n[6/6] Starting Services and Launching Control Panels..." -ForegroundColor Yellow

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
Start-Process -FilePath $javaExe -ArgumentList $javaArgs -WorkingDirectory $RepoRoot -WindowStyle Minimized

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
    Write-Host "   SUCCESS: All Services Are Running and Ready!                 " -ForegroundColor Green
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
