# Configuration-Driven MFT Integration Engine with SFTPGo

A robust, enterprise-grade Managed File Transfer (MFT) integration engine built with **Java 21**, **Spring Boot 4.2.0**, and **Spring Integration SFTP** (backed by **Apache MINA SSHD**).

The engine supports dynamic connection resolution, multi-route orchestration, raw binary preservation, fixed-width-to-delimited data transformation, companion SHA-256 generation, two-phase atomic publishing, strict idempotency deduplication, exponential backoff retries, and RESTful monitoring.

---

## Key Features

- **Spring Integration SFTP & Apache MINA SSHD**: Modern, non-deprecated SFTP stack with session pooling (`CachingSessionFactory`). Zero JSch dependency.
- **Dynamic Configuration Registry**: Configure any number of SFTP source and destination endpoints in YAML or external secrets. Lookups are resolved at runtime by `connectionId`.
- **Custom Read & Write Adapters**:
  - `ReadAdapter`: Atomic staging (`.download.tmp`), remote file discovery with glob pattern matching, anti-traversal path confinement.
  - `WriteAdapter`: 2-phase atomic publication (`.part` staging followed by atomic remote rename), directory auto-creation.
- **Cryptographic Integrity & Quarantine**: Streaming SHA-256 computation, companion `.sha256` generation and parsing. Checksum mismatches immediately fail-fast and quarantine corrupted payloads into `workspace/quarantine/` with structured diagnostic incident logs.
- **Multiple Processing Modes**:
  - `RAW_COPY`: Byte-exact preservation verified by matching SHA-256 hashes between source and target.
  - `TRANSFORM`: Parses fixed-width records into structured domain objects (`PositionRecord`), validates constraints (ISO 4217 currencies, dates, non-negative amounts), emits pipe-delimited records (`HDR`/`REC`/`TRL`), generates companion `.sha256`, and outputs machine-readable JSON metadata manifests.
- **Deterministic Idempotency**:
  - SHA-256 composite transfer key derived from `routeId`, `sourceConnectionId`, `sourceFilePath`, `fileSize`, and `sourceChecksum`.
  - Prevents duplicate processing and publication unless `allowReprocessing: true` is explicitly configured.
- **Intelligent Retry Policies**:
  - Discriminates transient network/SFTP failures from non-retryable domain validation/integrity errors.
  - Exponential backoff with configurable multiplier, max delay, and randomized jitter.
- **REST APIs & Observability**:
  - Full management endpoints under `/api/`: health checks, connection tests, remote file discovery, route execution triggers, transfer run tracking, and structured audit logs.
  - Spring Boot Actuator integration (`/actuator/health`).
- **Security by Design**:
  - Zero hardcoded credentials in source code or repositories.
  - Sensitive property masking in logs and REST responses.
  - Strict workspace directory confinement protecting against directory traversal attacks.

---

## Architecture Overview

```
                      +-----------------------------+
                      |    Configuration Registry   |
                      |   (Connections & Routes)    |
                      +--------------+--------------+
                                     |
                                     v
                      +-----------------------------+
                      |      Route Orchestrator     |
                      +--------------+--------------+
                                     |
           +-------------------------+-------------------------+
           |                                                   |
           v                                                   v
+---------------------+                             +---------------------+
|     Read Adapter    |                             |    Write Adapter    |
| (Discovery/Download)|                             |  (2-Phase Staging)  |
+----------+----------+                             +----------+----------+
           |                                                   ^
           v                                                   |
+---------------------+     +---------------------+            |
| Checksum & Integrity| --> |  Processing Engine  | -----------+
|    Verification     |     | (RawCopy/Transform) |
+---------------------+     +---------------------+
           |
           v
+---------------------+
| Idempotency & Retry |
|     Management      |
+---------------------+
```

---

## Quick Start (Windows & PowerShell)

### Prerequisites
- **Java 21** (`java -version` -> 21+)
- **PowerShell 5.1+**
- Included Maven Wrapper (`.\mvnw.cmd`)

### 1. Launch SFTPGo Servers
The project integrates with two live SFTPGo instances (Source Server A on port 2022, Destination Server B on port 2222):
```powershell
# In Terminal 1: Start Source SFTP Server A (Port 2022)
powershell -ExecutionPolicy Bypass -File "D:\sftpgo\start-server-a.ps1"

# In Terminal 2: Start Destination SFTP Server B (Port 2222)
powershell -ExecutionPolicy Bypass -File "D:\sftpgo\start-server-b.ps1"
```

### 2. Run the MFT Integration Engine
```powershell
cd D:\mft\mft-integration-engine
.\mvnw.cmd spring-boot:run -Dspring-boot.run.profiles=local
```

### 3. Verify Health & Trigger Routes
```powershell
# Test SFTP connectivity to Source Server A
Invoke-RestMethod -Uri "http://localhost:8080/api/connections/C_FILE_IN/test" -Method Get

# Execute Raw Copy route (transfers positions.txt from Server A to Server B)
Invoke-RestMethod -Uri "http://localhost:8080/api/routes/R_RAW_COPY/execute" -Method Post -ContentType "application/json"

# Execute Transformed route (Fixed-width -> Delimited + Checksum + Manifest)
Invoke-RestMethod -Uri "http://localhost:8080/api/routes/R_TRANSFORM/execute" -Method Post -ContentType "application/json"

# Inspect transfer run lifecycle and audit logs
Invoke-RestMethod -Uri "http://localhost:8080/api/transfers" -Method Get
Invoke-RestMethod -Uri "http://localhost:8080/api/audit" -Method Get
```

### 4. Run the Full Test Suite
```powershell
.\mvnw.cmd test
```
All **104 tests** execute unit and end-to-end integration verifications against live SFTPGo servers.

---

## Detailed Documentation

- [Architecture & Design](file:///D:/mft/mft-integration-engine/docs/ARCHITECTURE.md)
- [Windows & SFTPGo Setup Guide](file:///D:/mft/mft-integration-engine/docs/SETUP_WINDOWS.md)
- [SFTP Connection Configuration](file:///D:/mft/mft-integration-engine/docs/SFTP_CONNECTIONS.md)
- [Read Adapter & File Discovery](file:///D:/mft/mft-integration-engine/docs/READ_ADAPTER.md)
- [Write Adapter & 2-Phase Publication](file:///D:/mft/mft-integration-engine/docs/WRITE_ADAPTER.md)
- [Checksum, Integrity & Workspace Confinement](file:///D:/mft/mft-integration-engine/docs/CHECKSUM_AND_INTEGRITY.md)
- [Route Execution & Orchestration](file:///D:/mft/mft-integration-engine/docs/ROUTE_EXECUTION.md)
- [Retry Policies & Idempotency](file:///D:/mft/mft-integration-engine/docs/RETRY_AND_IDEMPOTENCY.md)
- [Status Tracking & Audit Logging](file:///D:/mft/mft-integration-engine/docs/STATUS_AND_AUDIT.md)
- [REST API Reference](file:///D:/mft/mft-integration-engine/docs/API_DOCUMENTATION.md)
- [Testing & Verification Guide](file:///D:/mft/mft-integration-engine/docs/TESTING.md)
- [Security & Credential Hardening](file:///D:/mft/mft-integration-engine/docs/SECURITY.md)
- [Troubleshooting & Diagnostics](file:///D:/mft/mft-integration-engine/docs/TROUBLESHOOTING.md)
- [MFT Glossary](file:///D:/mft/mft-integration-engine/docs/GLOSSARY.md)
