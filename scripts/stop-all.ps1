# ==============================================================================
# MFT Enterprise Integration Engine - Stop All Services
# ==============================================================================
Write-Host "Stopping all SFTPGo and MFT Engine processes..." -ForegroundColor Yellow

Get-Process -Name "sftpgo" -ErrorAction SilentlyContinue | Stop-Process -Force -ErrorAction SilentlyContinue

# Stop java process running mft-integration-engine
Get-CimInstance Win32_Process -Filter "Name = 'java.exe'" -ErrorAction SilentlyContinue |
    Where-Object { $_.CommandLine -like "*mft-integration-engine*" } |
    ForEach-Object { Stop-Process -Id $_.ProcessId -Force -ErrorAction SilentlyContinue }

Write-Host "All MFT services have been stopped cleanly." -ForegroundColor Green
