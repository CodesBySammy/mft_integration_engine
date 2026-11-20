# Troubleshooting & Diagnostics Guide

This document provides diagnostic solutions for common operational and integration issues.

---

## 1. SFTP Connection Failures

### Symptom: `ConnectionRefusedException: Connection refused: getsockopt`
**Cause:** The target SFTPGo server is not running on the configured port (e.g. 2022 or 2222).
**Solution:**
Verify that the SFTPGo instances are active:
```powershell
Get-Process -Name sftpgo -ErrorAction SilentlyContinue
Test-NetConnection -ComputerName localhost -Port 2022
Test-NetConnection -ComputerName localhost -Port 2222
```
If not running, launch them:
```powershell
powershell -ExecutionPolicy Bypass -File "D:\sftpgo\start-server-a.ps1"
powershell -ExecutionPolicy Bypass -File "D:\sftpgo\start-server-b.ps1"
```

---

## 2. Publication / Upload Failures

### Symptom: `SftpException: Permission denied (code 3)`
**Cause:** The user on the SFTP endpoint lacks upload or rename permissions.
**Solution:**
Inspect `users_seed.json` for that instance. Source user `mft_source` has read-only download permissions (`list`, `download`, `delete`). Only `mft_destination` on Server B has full write permissions (`*`).

---

## 3. Checksum Verification Mismatches

### Symptom: `IntegrityException: Checksum mismatch for [file.dat]! Expected: [abc], Calculated: [xyz]`
**Cause:** The downloaded file was truncated, modified in transit, or companion checksum did not match the payload.
**Diagnostic Action:**
Inspect the local quarantine directory:
`D:\mft\workspace\quarantine\<runId>\`
The corrupted file is preserved alongside an `incident_report.json` containing:
- Failure timestamp
- Expected vs calculated checksums
- Target file path
- Exception stack trace

---

## 4. Idempotency Skips

### Symptom: Route executes successfully, but `filesTransferred: 0, filesSkipped: 1`
**Cause:** The file has already been transferred and registered in the `IdempotencyRepository`.
**Solution:**
If you intentionally want to re-transfer the identical file:
1. In YAML: set `allow-reprocessing: true` under the route definition.
2. Or via API: update route configuration or clear the idempotency repository cache.
