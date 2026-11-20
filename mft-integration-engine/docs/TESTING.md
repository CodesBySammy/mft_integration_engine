# Testing & Verification Guide

This guide describes how to run and interpret the automated test suite of the MFT Integration Engine.

---

## 1. Test Architecture

The test suite consists of two tiers:
1. **Unit & Slice Tests**: Test parsers, checksum calculation, idempotency hashing, retry manager logic, anti-traversal validation, and secret resolution in isolated environments using `@TempDir` and Mockito.
2. **Live SFTP Integration Tests**: Run actual SFTP file uploads, downloads, atomic renames, integrity checks, and route triggers against the two local SFTPGo instances (Server A on port 2022, Server B on port 2222).

---

## 2. Running Tests via Maven

### 2.1 Run the Full Test Suite
```powershell
cd D:\mft\mft-integration-engine
.\mvnw.cmd test
```

### 2.2 Run Specific Test Classes
```powershell
# Run Live SFTP Read Adapter tests
.\mvnw.cmd test -Dtest=SftpLiveIntegrationTest

# Run Live SFTP Write Adapter tests
.\mvnw.cmd test -Dtest=SftpWriteLiveIntegrationTest

# Run Live Raw Copy Route tests
.\mvnw.cmd test -Dtest=RawCopyLiveRouteIntegrationTest

# Run Live Transformed Route tests
.\mvnw.cmd test -Dtest=TransformLiveRouteIntegrationTest

# Run Live Idempotency & Retry tests
.\mvnw.cmd test -Dtest=IdempotencyAndRetryLiveIntegrationTest

# Run REST API Controller tests
.\mvnw.cmd test -Dtest=ConnectionControllerTest
.\mvnw.cmd test -Dtest=RouteAndTransferControllerTest
.\mvnw.cmd test -Dtest=HealthAndFileControllerTest
```

---

## 3. Test Coverage Matrix

| Test Class | Focus Area | Live Server? |
| :--- | :--- | :---: |
| `SftpLiveIntegrationTest` | Remote file discovery, atomic `.tmp` download, directory listing | **Server A (2022)** |
| `SftpWriteLiveIntegrationTest` | 2-phase atomic publish (`.part` -> rename), directory creation | **Server B (2222)** |
| `RawCopyLiveRouteIntegrationTest` | End-to-end `R_RAW_COPY` transfer from Server A to Server B | **Server A & B** |
| `TransformLiveRouteIntegrationTest` | End-to-end `R_TRANSFORM` (fixed-width -> delimited + companion + manifest) | **Server A & B** |
| `IdempotencyAndRetryLiveIntegrationTest` | SHA-256 deduplication, skip behavior, and `allowReprocessing` toggle | **Server A & B** |
| `ConnectionControllerTest` | `GET /api/connections`, credential masking, live connectivity test | **Server A (2022)** |
| `RouteAndTransferControllerTest` | `GET /api/routes`, `POST /execute`, `/api/transfers`, `/api/audit` | Local / Context |
| `HealthAndFileControllerTest` | `GET /api/health`, `/actuator/health`, remote discovery | **Server A (2022)** |
| `FixedWidthLayoutParserTest` | Byte boundary slicing, record type matching, padding rules | Mock / Slice |
| `DelimitedLayoutParserTest` | Pipe delimiter parsing, escaping, and line feeds | Mock / Slice |
| `ChecksumServiceTest` | Streaming SHA-256 calculation, companion file writing/parsing | Mock / Slice |
| `PathValidationUtilTest` | Path traversal prevention (`..`, `\`, absolute paths) | Mock / Slice |
| `RetryManagerTest` | Exponential backoff, jitter, retryable vs non-retryable errors | Mock / Slice |
| `IdempotencyTest` | Deterministic SHA-256 hash generation and lifecycle state updates | Mock / Slice |
| `LocalWorkspaceServiceTest` | Directory structure confinement, quarantine incident logs | Mock / Slice |
